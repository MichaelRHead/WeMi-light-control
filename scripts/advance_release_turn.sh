#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Release State Machine Transition (advance_release_turn.sh)
# ==============================================================================
# Atomically updates RELEASE_SIGN_OFF.md, commits, rebases against origin/main,
# and pushes to GitHub to advance the 6-agent / 7-stage release process
# (Stages 1-6 review ring + Stage 7 automated Closed testing promotion).
#
# Usage:
#   bash scripts/advance_release_turn.sh initiate "<summary>" [--verified "<checks>"] [--no-push]
#   bash scripts/advance_release_turn.sh sign_off <role> "<summary>" [--verified "<checks>"] [--no-push]
#   bash scripts/advance_release_turn.sh conclude "<summary>" [--verified "<checks>"] [--no-push]
#   bash scripts/advance_release_turn.sh promote "<summary>" [--version-code "<vc>"] [--from-track "<track>"] [--track "<track>"] [--verified "<checks>"] [--no-push]
#   bash scripts/advance_release_turn.sh request_rereview <from_role> <target_role> "<reason>" [--no-push]
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

ACTION="${1:-}"
if [[ -z "${ACTION}" ]]; then
  echo "Usage:" >&2
  echo "  $0 initiate \"<summary>\" [--verified \"<checks>\"] [--no-push]" >&2
  echo "  $0 sign_off <discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|license_governance|ai_studio_ux_cuj> \"<summary>\" [--verified \"<checks>\"] [--no-push]" >&2
  echo "  $0 conclude \"<summary>\" [--verified \"<checks>\"] [--no-push]" >&2
  echo "  $0 promote \"<summary>\" [--version-code \"<vc>\"] [--from-track \"<track>\"] [--track \"<track>\"] [--verified \"<checks>\"] [--no-push]" >&2
  echo "  $0 request_rereview <from_role> <target_role> \"<reason>\" [--no-push]" >&2
  exit 2
fi

NO_PUSH="false"
VERIFIED_ARG=""
PROMO_VC_ARG=""
PROMO_FROM_TRACK_ARG=""
PROMO_TRACK_ARG=""
CLEAN_ARGS=()
while (( $# > 0 )); do
  case "$1" in
    --no-push)
      NO_PUSH="true"
      shift
      ;;
    -v|--verified)
      if (( $# < 2 )); then
        echo "[ERROR] --verified requires a value." >&2
        exit 2
      fi
      VERIFIED_ARG="$2"
      shift 2
      ;;
    --version-code)
      if (( $# < 2 )); then
        echo "[ERROR] --version-code requires a value." >&2
        exit 2
      fi
      PROMO_VC_ARG="$2"
      shift 2
      ;;
    --from-track)
      if (( $# < 2 )); then
        echo "[ERROR] --from-track requires a value." >&2
        exit 2
      fi
      PROMO_FROM_TRACK_ARG="$2"
      shift 2
      ;;
    --track)
      if (( $# < 2 )); then
        echo "[ERROR] --track requires a value." >&2
        exit 2
      fi
      PROMO_TRACK_ARG="$2"
      shift 2
      ;;
    *)
      CLEAN_ARGS+=("$1")
      shift
      ;;
  esac
done

ACTION="${CLEAN_ARGS[0]:-}"

# Pull latest origin/main before mutating RELEASE_SIGN_OFF.md (when .git is present)
if [[ -d ".git" ]]; then
  git fetch origin main --quiet 2>/dev/null || true
else
  NO_PUSH="true"
fi

PST_NOW="$(TZ=America/Los_Angeles date "+%Y-%m-%d %H:%M %Z")"
GRADLE_VER_NAME="$(sed -n 's/.*versionName *= *"\([^"]*\)".*/v\1-rc/p' app/build.gradle.kts | tr -d '\r' | head -n 1 || true)"
HEAD_SHA="$(git rev-parse --short HEAD 2>/dev/null || echo "${GRADLE_VER_NAME:-v28.0-rc}")"
if [[ -z "${HEAD_SHA}" || "${HEAD_SHA}" == "NONE" ]]; then
  HEAD_SHA="v28.0-rc"
fi

python3 - "${ACTION}" "${PST_NOW}" "${HEAD_SHA}" "${PROMO_VC_ARG}" "${PROMO_FROM_TRACK_ARG}" "${PROMO_TRACK_ARG}" "${CLEAN_ARGS[@]}" <<'PYEOF'
import re
import sys
from pathlib import Path

action = sys.argv[1]
pst_now = sys.argv[2]
head_sha = sys.argv[3]
promo_vc_arg = sys.argv[4].strip()
promo_from_track_arg = sys.argv[5].strip()
promo_track_arg = sys.argv[6].strip()
raw_args = list(sys.argv[7:])

gradle_text = Path("app/build.gradle.kts").read_text(encoding="utf-8")
ver_code_match = re.search(r"versionCode\s*=\s*(\d+)", gradle_text)
ver_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
gradle_ver_code = ver_code_match.group(1) if ver_code_match else "28"
gradle_ver_name = f"v{ver_name_match.group(1)}" if ver_name_match else "v28.0"

sign_off_path = Path("RELEASE_SIGN_OFF.md")
text = sign_off_path.read_text(encoding="utf-8")

STATE_BEGIN = "<!-- RELEASE_STATE_BEGIN -->"
STATE_END = "<!-- RELEASE_STATE_END -->"
MAX_ROUNDS = 3


def _state_block_match(content: str):
    m = re.search(
        rf"({re.escape(STATE_BEGIN)})(.*?)({re.escape(STATE_END)})",
        content,
        flags=re.DOTALL,
    )
    if not m:
        sys.exit("RELEASE_SIGN_OFF.md is missing RELEASE_STATE_BEGIN/END markers.")
    return m


def get_state(key: str, default: str = "") -> str:
    block_body = _state_block_match(text).group(2)
    m = re.search(rf"^{re.escape(key)}:\s*(.*)$", block_body, flags=re.MULTILINE)
    return m.group(1).strip() if m else default


def set_state(content: str, key: str, value: str) -> str:
    block_m = _state_block_match(content)
    block_body = block_m.group(2)
    pattern = rf"^{re.escape(key)}:\s*.*$"
    if re.search(pattern, block_body, flags=re.MULTILINE):
        new_body = re.sub(
            pattern,
            lambda _: f"{key}: {value}",
            block_body,
            count=1,
            flags=re.MULTILINE,
        )
    else:
        suffix = "" if block_body.endswith("\n") else "\n"
        new_body = f"{block_body}{suffix}{key}: {value}\n"
    return content[:block_m.start(2)] + new_body + content[block_m.end(2):]


def update_table_row(content: str, stage_prefix: str, status: str, sha: str, ts: str, summary: str) -> str:
    clean_summary = summary.replace("|", "/").replace("\n", " ").strip()
    pattern = rf"^(\|\s*\*\*{re.escape(stage_prefix)}\*\*\s*\|\s*`[^`]+`\s*\|\s*\*\*[^*]+\*\*\s*\|)\s*`[^`]+`\s*\|\s*`[^`]+`\s*\|\s*`[^`]+`\s*\|.*\|$"
    return re.sub(
        pattern,
        lambda m: f"{m.group(1)} `{status}` | `{sha}` | `{ts}` | {clean_summary} |",
        content,
        flags=re.MULTILINE,
    )


def append_audit_row(content: str, round_num: str, role_id: str, act_label: str, next_turn: str, sha: str, notes: str) -> str:
    clean_notes = notes.replace("|", "/").replace("\n", " ").strip()
    row = f"| {pst_now} | `{round_num}` | `{role_id}` | `{act_label}` | `{next_turn}` | `{sha}` | {clean_notes} |\n<!-- AUDIT_LOG_END -->"
    return content.replace("<!-- AUDIT_LOG_END -->", row)


round_num = get_state("ROUND_NUMBER", "1")

if action == "initiate":
    summary = raw_args[1] if len(raw_args) > 1 else f"Initiated {gradle_ver_name} release review; UX & CUJs verified."
    text = set_state(text, "RELEASE_VERSION", gradle_ver_name)
    text = set_state(text, "VERSION_CODE", gradle_ver_code)
    text = set_state(text, "ROUND_NUMBER", "1")
    text = set_state(text, "RELEASE_STATUS", "IN_REVIEW")
    text = set_state(text, "CURRENT_TURN", "discovery_and_grouping")
    text = set_state(text, "CANDIDATE_BASE_SHA", head_sha)
    text = set_state(text, "SIGN_OFF_AI_STUDIO_UX_CUJ", f"SIGNED_OFF ({head_sha} @ {pst_now})")
    text = set_state(text, "SIGN_OFF_DISCOVERY_AND_GROUPING", "PENDING")
    text = set_state(text, "SIGN_OFF_TELEMETRY_AND_DEBUGGING", "PENDING")
    text = set_state(text, "SIGN_OFF_CODE_QUALITY_AND_TESTING", "PENDING")
    text = set_state(text, "SIGN_OFF_LICENSE_GOVERNANCE", "PENDING")
    text = set_state(text, "SIGN_OFF_APP_PROMOTER", "PENDING")
    text = set_state(text, "FINAL_RELEASE_SHA", "NONE")
    text = set_state(text, "CONCLUDED_AT", "NONE")
    text = set_state(text, "PROMOTED_VERSION_CODE", "NONE")
    text = set_state(text, "PROMOTED_TRACK", "NONE")
    text = set_state(text, "PROMOTED_AT", "NONE")
    text = re.sub(
        r"^## 2\. Current Release Candidate Sign-Off Status \(`v[^`]+` / `versionCode = \d+`\)$",
        lambda _: f"## 2. Current Release Candidate Sign-Off Status (`{gradle_ver_name}` / `versionCode = {gradle_ver_code}`)",
        text,
        flags=re.MULTILINE,
    )

    text = update_table_row(text, "1 (Initiate)", "SIGNED_OFF", head_sha, pst_now, summary)
    text = update_table_row(text, "2 (Review 1)", "PENDING", "-", "-", "Awaiting Discovery & Room Grouping Agent review.")
    text = update_table_row(text, "3 (Review 2)", "PENDING", "-", "-", "Awaiting Telemetry & Debugging Agent review.")
    text = update_table_row(text, "4 (Review 3)", "PENDING", "-", "-", "Awaiting Code Quality & E2E Testing Agent review.")
    text = update_table_row(text, "5 (Review 4)", "PENDING", "-", "-", "Awaiting License & Brand Governance Agent review.")
    text = update_table_row(text, "6 (Conclude)", "PENDING", "-", "-", "Awaiting Stages 1-5 sign-off.")
    text = update_table_row(text, "7 (Promote)", "PENDING", "-", "-", "Awaiting Stage 6 approval and AI Studio internal testing upload.")
    text = append_audit_row(text, "1", "ai_studio_ux_cuj", "INITIATED_RELEASE", "discovery_and_grouping", head_sha, summary)

elif action == "sign_off":
    if len(raw_args) < 3:
        sys.exit("Usage: advance_release_turn.sh sign_off <role> \"<summary>\"")
    role = raw_args[1]
    summary = raw_args[2]

    transitions = {
        "ai_studio_ux_cuj": ("SIGN_OFF_AI_STUDIO_UX_CUJ", "1 (Initiate)", "discovery_and_grouping", "IN_REVIEW"),
        "discovery_and_grouping": ("SIGN_OFF_DISCOVERY_AND_GROUPING", "2 (Review 1)", "telemetry_and_debugging", "IN_REVIEW"),
        "telemetry_and_debugging": ("SIGN_OFF_TELEMETRY_AND_DEBUGGING", "3 (Review 2)", "code_quality_and_testing", "IN_REVIEW"),
        "code_quality_and_testing": ("SIGN_OFF_CODE_QUALITY_AND_TESTING", "4 (Review 3)", "license_governance", "IN_REVIEW"),
        "license_governance": ("SIGN_OFF_LICENSE_GOVERNANCE", "5 (Review 4)", "ai_studio_ux_cuj", "AWAITING_FINAL_CONCLUSION"),
    }
    if role not in transitions:
        sys.exit(f"Unknown role: {role}")

    state_key, stage_label, next_turn, next_status = transitions[role]
    text = set_state(text, state_key, f"SIGNED_OFF ({head_sha} @ {pst_now})")
    text = set_state(text, "RELEASE_STATUS", next_status)
    text = set_state(text, "CURRENT_TURN", next_turn)
    text = update_table_row(text, stage_label, "SIGNED_OFF", head_sha, pst_now, summary)
    text = append_audit_row(text, round_num, role, "SIGNED_OFF", next_turn, head_sha, summary)

elif action == "conclude":
    required_sign_offs = (
        ("ai_studio_ux_cuj", "SIGN_OFF_AI_STUDIO_UX_CUJ"),
        ("discovery_and_grouping", "SIGN_OFF_DISCOVERY_AND_GROUPING"),
        ("telemetry_and_debugging", "SIGN_OFF_TELEMETRY_AND_DEBUGGING"),
        ("code_quality_and_testing", "SIGN_OFF_CODE_QUALITY_AND_TESTING"),
        ("license_governance", "SIGN_OFF_LICENSE_GOVERNANCE"),
    )
    missing_sign_offs = [
        f"{role_id} ({state_key}={get_state(state_key, 'MISSING')})"
        for role_id, state_key in required_sign_offs
        if not get_state(state_key, "").startswith("SIGNED_OFF")
    ]
    if missing_sign_offs:
        sys.exit(
            "Cannot conclude release before all 5 review stages are SIGNED_OFF. Pending/missing: "
            + ", ".join(missing_sign_offs)
        )

    summary = raw_args[1] if len(raw_args) > 1 else f"All 5 agents signed off; {gradle_ver_name} approved for Google Play Store release."
    text = set_state(text, "RELEASE_STATUS", "APPROVED_FOR_PLAY_STORE")
    text = set_state(text, "CURRENT_TURN", "NONE")
    text = set_state(text, "FINAL_RELEASE_SHA", head_sha)
    text = set_state(text, "CONCLUDED_AT", pst_now)
    text = set_state(text, "SIGN_OFF_APP_PROMOTER", "PENDING")
    text = set_state(text, "PROMOTED_VERSION_CODE", "NONE")
    text = set_state(text, "PROMOTED_TRACK", "NONE")
    text = set_state(text, "PROMOTED_AT", "NONE")
    text = update_table_row(text, "6 (Conclude)", "SIGNED_OFF", head_sha, pst_now, summary)
    text = update_table_row(text, "7 (Promote)", "PENDING", "-", "-", "Ready for automatic Closed testing promotion once AI Studio pushes internal build and version bump.")
    text = append_audit_row(text, round_num, "ai_studio_ux_cuj", "APPROVED_FOR_PLAY_STORE", "NONE", head_sha, summary)

elif action == "promote":
    promo_vc = promo_vc_arg or gradle_ver_code
    promo_from_track = promo_from_track_arg or "internal"
    promo_track = promo_track_arg or "alpha"
    if promo_track == "alpha":
        promo_status = "PROMOTED_TO_CLOSED_TESTING"
    elif promo_track == "beta":
        promo_status = "PROMOTED_TO_OPEN_TESTING"
    elif promo_track == "production":
        promo_status = "PROMOTED_TO_PRODUCTION"
    else:
        safe_track = re.sub(r"[^A-Za-z0-9]+", "_", promo_track).strip("_").upper() or "TRACK"
        promo_status = f"PROMOTED_TO_{safe_track}"
    summary = (
        raw_args[1]
        if len(raw_args) > 1
        else f"Promoted build {promo_vc} from {promo_from_track} to {promo_track} via Google Play Developer API."
    )
    text = set_state(text, "RELEASE_STATUS", promo_status)
    text = set_state(text, "CURRENT_TURN", "NONE")
    text = set_state(text, "SIGN_OFF_APP_PROMOTER", f"PROMOTED (vc={promo_vc} {promo_from_track} -> {promo_track} @ {pst_now})")
    text = set_state(text, "PROMOTED_VERSION_CODE", promo_vc)
    text = set_state(text, "PROMOTED_TRACK", promo_track)
    text = set_state(text, "PROMOTED_AT", pst_now)
    text = update_table_row(text, "7 (Promote)", "PROMOTED", head_sha, pst_now, summary)
    text = append_audit_row(text, round_num, "app_promoter", promo_status, "NONE", head_sha, summary)

elif action == "request_rereview":
    if len(raw_args) < 4:
        sys.exit("Usage: advance_release_turn.sh request_rereview <from_role> <target_role> \"<reason>\"")
    from_role = raw_args[1]
    target_role = raw_args[2]
    reason = raw_args[3]

    order = [
        "ai_studio_ux_cuj",
        "discovery_and_grouping",
        "telemetry_and_debugging",
        "code_quality_and_testing",
        "license_governance",
    ]
    key_map = {
        "ai_studio_ux_cuj": ("SIGN_OFF_AI_STUDIO_UX_CUJ", "1 (Initiate)"),
        "discovery_and_grouping": ("SIGN_OFF_DISCOVERY_AND_GROUPING", "2 (Review 1)"),
        "telemetry_and_debugging": ("SIGN_OFF_TELEMETRY_AND_DEBUGGING", "3 (Review 2)"),
        "code_quality_and_testing": ("SIGN_OFF_CODE_QUALITY_AND_TESTING", "4 (Review 3)"),
        "license_governance": ("SIGN_OFF_LICENSE_GOVERNANCE", "5 (Review 4)"),
    }
    if from_role not in key_map:
        sys.exit(f"Invalid from_role '{from_role}'. Expected one of: {', '.join(order)}")
    if target_role not in key_map:
        sys.exit(f"Invalid target_role '{target_role}'. Expected one of: {', '.join(order)}")
    if not round_num.isdigit():
        sys.exit(f"Invalid ROUND_NUMBER '{round_num}' in RELEASE_SIGN_OFF.md.")
    next_round_int = int(round_num) + 1
    if next_round_int > MAX_ROUNDS:
        sys.exit(f"Cannot request re-review: ROUND_NUMBER would exceed MAX_ROUNDS ({MAX_ROUNDS}).")
    new_round = str(next_round_int)

    text = set_state(text, "ROUND_NUMBER", new_round)
    text = set_state(text, "RELEASE_STATUS", "IN_REVIEW")
    text = set_state(text, "CURRENT_TURN", target_role)
    text = set_state(text, "SIGN_OFF_APP_PROMOTER", "PENDING")
    text = set_state(text, "PROMOTED_VERSION_CODE", "NONE")
    text = set_state(text, "PROMOTED_TRACK", "NONE")
    text = set_state(text, "PROMOTED_AT", "NONE")

    idx = order.index(target_role)
    for r in order[idx:]:
        k, stg = key_map[r]
        text = set_state(text, k, "PENDING")
        text = update_table_row(text, stg, "PENDING", "-", "-", f"Re-review requested in Round {new_round} by {from_role}.")
    text = update_table_row(text, "6 (Conclude)", "PENDING", "-", "-", f"Awaiting Round {new_round} re-review sign-offs.")
    text = update_table_row(text, "7 (Promote)", "PENDING", "-", "-", f"Awaiting Round {new_round} re-review and Stage 6 approval.")
    text = append_audit_row(text, new_round, from_role, "REQUESTED_REREVIEW", target_role, head_sha, reason)
else:
    sys.exit(f"Unknown action: {action}")

sign_off_path.write_text(text, encoding="utf-8")
PYEOF

if [[ "${NO_PUSH}" == "true" ]]; then
  echo "[RELEASE COORDINATOR] Updated RELEASE_SIGN_OFF.md locally (--no-push)."
  exit 0
fi

TMP_COMMIT_INFO="$(mktemp)"
trap 'rm -f "${TMP_COMMIT_INFO}"' EXIT

python3 - "${VERIFIED_ARG}" "${TMP_COMMIT_INFO}" "${PROMO_VC_ARG}" "${PROMO_FROM_TRACK_ARG}" "${PROMO_TRACK_ARG}" "${CLEAN_ARGS[@]}" <<'PYEOF'
import json
import re
import sys
from pathlib import Path

verified_arg = sys.argv[1].strip()
out_path = sys.argv[2]
promo_vc_arg = sys.argv[3].strip()
promo_from_track_arg = sys.argv[4].strip()
promo_track_arg = sys.argv[5].strip()
args = list(sys.argv[6:])
action = args[0] if args else ""

sign_off = Path("RELEASE_SIGN_OFF.md").read_text(encoding="utf-8")
block_match = re.search(
    r"<!--\s*RELEASE_STATE_BEGIN\s*-->(.*?)<!--\s*RELEASE_STATE_END\s*-->",
    sign_off,
    flags=re.DOTALL,
)
state_scope = block_match.group(1) if block_match else ""


def state_val(key, default=""):
    m = re.search(rf"^{re.escape(key)}:\s*(.*)$", state_scope, flags=re.MULTILINE)
    return m.group(1).strip() if m else default

rel_ver = state_val("RELEASE_VERSION", "v28.0")
ver_code = state_val("VERSION_CODE", "28")
round_num = state_val("ROUND_NUMBER", "1")

ROLE_META = {
    "ai_studio_ux_cuj": {
        "short": "UX & design review",
        "stage": "Stage 1 of 7",
        "next": "Discovery & Room Grouping",
        "default_bullet": "Verified UX layouts, accessibility, user journeys, and Play Store release notes.",
    },
    "discovery_and_grouping": {
        "short": "discovery review",
        "stage": "Stage 2 of 7",
        "next": "Telemetry & Debugging",
        "default_bullet": "Verified switch discovery, device identity matching, and automatic room grouping.",
    },
    "telemetry_and_debugging": {
        "short": "telemetry review",
        "stage": "Stage 3 of 7",
        "next": "Code Quality & Testing",
        "default_bullet": "Verified local setup diagnostics, telemetry export, and password redaction.",
    },
    "code_quality_and_testing": {
        "short": "quality & test review",
        "stage": "Stage 4 of 7",
        "next": "License & Brand Governance",
        "default_bullet": "Verified code modularity, lifecycle safety, script permissions, and full test suite.",
    },
    "license_governance": {
        "short": "license & brand review",
        "stage": "Stage 5 of 7",
        "next": "Google AI Studio for final release approval",
        "default_bullet": "Verified in-app MIT license parity, permissive dependencies, and Play Store trademark compliance.",
    },
    "app_promoter": {
        "short": "closed testing promotion",
        "stage": "Stage 7 of 7",
        "next": "Closed testing testers on Google Play",
        "default_bullet": "Promoted internal Play Store build to Closed testing via Google Play Developer API.",
    },
}

NESTED_PARENS_RE = re.compile(r"\([^()]*\([^()]+\)[^()]*\)")
MARKDOWN_DECORATION_RE = re.compile(r"(\*\*[^*\n]+\*\*|`[^`\n]+`)")
CODE_TOKEN_RE = re.compile(
    r"(?:[A-Za-z0-9_.*-]+(?:/[A-Za-z0-9_.*-]+)*\.(?:kt|kts|java|xml|md|sh|json|txt|so|pro|toml|yml|yaml|py|gradle)"
    r"|--[a-z][a-z0-9-]+"
    r"|[A-Za-z0-9]+_[A-Za-z0-9_]+"
    r"|[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_().]*"
    r"|[a-z]+[A-Z][A-Za-z0-9]*)"
)


def is_concise_human_summary(text: str) -> bool:
    s = " ".join(text.split()).lstrip("-*• ").strip()
    if not s or len(s) > 220:
        return False
    if s.count(";") > 1 or NESTED_PARENS_RE.search(s):
        return False
    if MARKDOWN_DECORATION_RE.search(s) or "`" in s or "**" in s:
        return False
    if len(CODE_TOKEN_RE.findall(s)) > 5:
        return False
    return True


def extract_verified(raw_text: str) -> str:
    if verified_arg:
        return verified_arg[:120]
    parts = []
    m_tests = re.search(r"(\d+/\d+)[^.,;]*tests", raw_text, flags=re.IGNORECASE)
    if m_tests:
        parts.append(f"{m_tests.group(1)} tests passing")
    if re.search(r"0\s+lint(?:Debug)?\s+errors", raw_text, flags=re.IGNORECASE):
        parts.append("lint clean")
    if re.search(r"assembleDebug\s+green|green\s+assembleDebug", raw_text, flags=re.IGNORECASE):
        parts.append("debug APK builds")
    return ", ".join(parts)


def make_bullets(raw_summary: str, fallback_bullet: str):
    clean = " ".join(raw_summary.split()).lstrip("-*• ").strip()
    if is_concise_human_summary(clean):
        if not clean.endswith((".", "!", "?")):
            clean += "."
        return [f"- {clean}", "- Full sign-off ledger updated in RELEASE_SIGN_OFF.md."]
    if clean:
        sys.stderr.write(
            "[RELEASE COORDINATOR] Saved full audit notes to RELEASE_SIGN_OFF.md "
            "and kept the Git commit message concise for human readers.\n"
        )
    return [f"- {fallback_bullet}", "- Full audit details are recorded in RELEASE_SIGN_OFF.md."]


if action == "initiate":
    acting_role = "ai_studio_ux_cuj"
    raw_summary = args[1] if len(args) > 1 else ""
    subject = f"chore(release): start {rel_ver} release review"
    lead = f"Started {rel_ver} (versionCode {ver_code}) release review with UX sign-off. Handing off to Discovery & Room Grouping."
    bullets = make_bullets(raw_summary, ROLE_META[acting_role]["default_bullet"])
    verified = extract_verified(raw_summary)
elif action == "sign_off":
    acting_role = args[1] if len(args) > 1 else "code_quality_and_testing"
    raw_summary = args[2] if len(args) > 2 else ""
    meta = ROLE_META.get(acting_role, {
        "short": "release review",
        "stage": "Review stage",
        "next": "the next reviewer",
        "default_bullet": "Completed release verification with no blocking issues.",
    })
    subject = f"chore(release): sign off {rel_ver} {meta['short']}"
    lead = f"Completed {meta['stage']} for {rel_ver} (versionCode {ver_code}). Handing off to {meta['next']}."
    bullets = make_bullets(raw_summary, meta["default_bullet"])
    verified = extract_verified(raw_summary)
elif action == "conclude":
    acting_role = "ai_studio_ux_cuj"
    raw_summary = args[1] if len(args) > 1 else ""
    subject = f"chore(release): approve {rel_ver} for Play Store"
    lead = f"All 5 review stages passed for {rel_ver} (versionCode {ver_code}). Release is approved for Google Play Store."
    bullets = make_bullets(raw_summary, "Confirmed all stage sign-offs and final release notes are ready for publishing.")
    verified = extract_verified(raw_summary)
elif action == "promote":
    acting_role = "app_promoter"
    raw_summary = args[1] if len(args) > 1 else ""
    promo_vc = promo_vc_arg or state_val("PROMOTED_VERSION_CODE", ver_code)
    promo_from_track = promo_from_track_arg or "internal"
    promo_track = promo_track_arg or state_val("PROMOTED_TRACK", "alpha")
    track_label = {
        "alpha": "closed testing",
        "beta": "open testing",
        "production": "production",
    }.get(promo_track, f"{promo_track} track")
    if promo_vc and promo_vc != ver_code:
        subject = f"chore(release): promote {rel_ver} build {promo_vc} to {track_label}"
    else:
        subject = f"chore(release): promote {rel_ver} to {track_label}"
    if len(subject) > 72:
        subject = f"chore(release): promote build {promo_vc} to {track_label}"
    lead = f"Completed Stage 7 of 7 for {rel_ver}. Promoted Play build {promo_vc} from {promo_from_track} to {track_label}."
    bullets = make_bullets(
        raw_summary,
        f"Promoted build {promo_vc} from {promo_from_track} to {track_label} via Google Play Developer API.",
    )
    verified = extract_verified(raw_summary)
elif action == "request_rereview":
    acting_role = args[1] if len(args) > 1 else "code_quality_and_testing"
    target_role = args[2] if len(args) > 2 else "discovery_and_grouping"
    raw_reason = args[3] if len(args) > 3 else ""
    target_meta = ROLE_META.get(target_role, {"short": "release review"})
    subject = f"chore(release): request {rel_ver} {target_meta['short']}"
    if len(subject) > 72:
        subject = f"chore(release): request {rel_ver} re-review"
    lead = f"Requested a Round {round_num} follow-up {target_meta['short']} for {rel_ver}."
    bullets = make_bullets(raw_reason, "Follow-up verification requested before release sign-off.")
    verified = verified_arg[:120]
else:
    acting_role = "antigravity_general"
    subject = f"chore(release): update {rel_ver} sign-off ledger"
    lead = f"Updated the {rel_ver} release sign-off ledger."
    bullets = ["- Full release state recorded in RELEASE_SIGN_OFF.md."]
    verified = verified_arg[:120]

body = lead + "\n\n" + "\n\n".join(bullets)
Path(out_path).write_text(
    json.dumps({
        "role": acting_role,
        "subject": subject,
        "body": body,
        "verified": verified,
    }),
    encoding="utf-8",
)
PYEOF

ACTING_ROLE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["role"])' "${TMP_COMMIT_INFO}")"
COMMIT_SUBJECT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["subject"])' "${TMP_COMMIT_INFO}")"
COMMIT_BODY="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["body"])' "${TMP_COMMIT_INFO}")"
COMMIT_VERIFIED="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["verified"])' "${TMP_COMMIT_INFO}")"

git add RELEASE_SIGN_OFF.md

if [[ -n "${COMMIT_VERIFIED}" ]]; then
  bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
    "${ACTING_ROLE}" \
    "${COMMIT_SUBJECT}" \
    "${COMMIT_BODY}" \
    --verified "${COMMIT_VERIFIED}"
else
  bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
    "${ACTING_ROLE}" \
    "${COMMIT_SUBJECT}" \
    "${COMMIT_BODY}"
fi

git pull --rebase origin main
git push origin main
echo "[RELEASE COORDINATOR] Pushed updated RELEASE_SIGN_OFF.md to origin/main."
