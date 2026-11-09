#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Release State Machine Transition (advance_release_turn.sh)
# ==============================================================================
# Atomically updates RELEASE_SIGN_OFF.md, preserves +x on gradlew and scripts,
# commits, rebases against origin/main, and pushes to GitHub to wake the next
# agent in the 4-agent round-robin ring.
#
# Usage:
#   bash scripts/advance_release_turn.sh initiate "<summary>" [--no-push]
#   bash scripts/advance_release_turn.sh sign_off <role> "<summary>" [--no-push]
#   bash scripts/advance_release_turn.sh conclude "<summary>" [--no-push]
#   bash scripts/advance_release_turn.sh request_rereview <from_role> <target_role> "<reason>" [--no-push]
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

ACTION="${1:-}"
if [[ -z "${ACTION}" ]]; then
  echo "Usage:" >&2
  echo "  $0 initiate \"<summary>\" [--no-push]" >&2
  echo "  $0 sign_off <discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|ai_studio_ux_cuj> \"<summary>\" [--no-push]" >&2
  echo "  $0 conclude \"<summary>\" [--no-push]" >&2
  echo "  $0 request_rereview <from_role> <target_role> \"<reason>\" [--no-push]" >&2
  exit 2
fi

NO_PUSH="false"
for arg in "$@"; do
  if [[ "${arg}" == "--no-push" ]]; then
    NO_PUSH="true"
  fi
done

# Pull latest origin/main before mutating RELEASE_SIGN_OFF.md (when .git is present)
if [[ -d ".git" ]]; then
  git fetch origin main --quiet 2>/dev/null || true
else
  NO_PUSH="true"
fi

# Ensure executable bits on gradlew and release scripts
chmod +x gradlew scripts/await_release_turn.sh scripts/advance_release_turn.sh 2>/dev/null || true

PST_NOW="$(TZ=America/Los_Angeles date "+%Y-%m-%d %H:%M %Z")"
HEAD_SHA="$(git rev-parse --short HEAD 2>/dev/null || awk -F': *' '/^CANDIDATE_BASE_SHA:/ { print $2; exit }' RELEASE_SIGN_OFF.md | tr -d '\r' || echo "ai-studio")"
if [[ -z "${HEAD_SHA}" || "${HEAD_SHA}" == "NONE" ]]; then
  HEAD_SHA="v20.0-rc"
fi

python3 - "${ACTION}" "${PST_NOW}" "${HEAD_SHA}" "$@" <<'PYEOF'
import re
import sys
from pathlib import Path

action = sys.argv[1]
pst_now = sys.argv[2]
head_sha = sys.argv[3]
raw_args = [a for a in sys.argv[4:] if a != "--no-push"]

gradle_text = Path("app/build.gradle.kts").read_text(encoding="utf-8")
ver_code_match = re.search(r"versionCode\s*=\s*(\d+)", gradle_text)
ver_name_match = re.search(r'versionName\s*=\s*"([^"]+)"', gradle_text)
gradle_ver_code = ver_code_match.group(1) if ver_code_match else "20"
gradle_ver_name = f"v{ver_name_match.group(1)}" if ver_name_match else "v20.0"

sign_off_path = Path("RELEASE_SIGN_OFF.md")
text = sign_off_path.read_text(encoding="utf-8")

def get_state(key: str, default: str = "") -> str:
    m = re.search(rf"^{re.escape(key)}:\s*(.*)$", text, flags=re.MULTILINE)
    return m.group(1).strip() if m else default

def set_state(content: str, key: str, value: str) -> str:
    return re.sub(
        rf"^{re.escape(key)}:\s*.*$",
        f"{key}: {value}",
        content,
        flags=re.MULTILINE,
    )

def update_table_row(content: str, stage_prefix: str, status: str, sha: str, ts: str, summary: str) -> str:
    clean_summary = summary.replace("|", "/").replace("\n", " ").strip()
    pattern = rf"^(\|\s*\*\*{re.escape(stage_prefix)}\*\*\s*\|\s*`[^`]+`\s*\|\s*\*\*[^*]+\*\*\s*\|)\s*`[^`]+`\s*\|\s*`[^`]+`\s*\|\s*`[^`]+`\s*\|.*\|$"
    replacement = rf"\1 `{status}` | `{sha}` | `{ts}` | {clean_summary} |"
    return re.sub(pattern, replacement, content, flags=re.MULTILINE)

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
    text = set_state(text, "FINAL_RELEASE_SHA", "NONE")
    text = set_state(text, "CONCLUDED_AT", "NONE")

    text = update_table_row(text, "1 (Initiate)", "SIGNED_OFF", head_sha, pst_now, summary)
    text = update_table_row(text, "2 (Review 1)", "PENDING", "-", "-", "Awaiting Discovery & Room Grouping Agent review.")
    text = update_table_row(text, "3 (Review 2)", "PENDING", "-", "-", "Awaiting Telemetry & Debugging Agent review.")
    text = update_table_row(text, "4 (Review 3)", "PENDING", "-", "-", "Awaiting Code Quality & E2E Testing Agent review.")
    text = update_table_row(text, "5 (Conclude)", "PENDING", "-", "-", "Awaiting Stages 1-4 sign-off.")
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
        "code_quality_and_testing": ("SIGN_OFF_CODE_QUALITY_AND_TESTING", "4 (Review 3)", "ai_studio_ux_cuj", "AWAITING_FINAL_CONCLUSION"),
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
    summary = raw_args[1] if len(raw_args) > 1 else f"All 4 agents signed off; {gradle_ver_name} approved for Google Play Store release."
    text = set_state(text, "RELEASE_STATUS", "APPROVED_FOR_PLAY_STORE")
    text = set_state(text, "CURRENT_TURN", "NONE")
    text = set_state(text, "FINAL_RELEASE_SHA", head_sha)
    text = set_state(text, "CONCLUDED_AT", pst_now)
    text = update_table_row(text, "5 (Conclude)", "SIGNED_OFF", head_sha, pst_now, summary)
    text = append_audit_row(text, round_num, "ai_studio_ux_cuj", "APPROVED_FOR_PLAY_STORE", "NONE", head_sha, summary)

elif action == "request_rereview":
    if len(raw_args) < 4:
        sys.exit("Usage: advance_release_turn.sh request_rereview <from_role> <target_role> \"<reason>\"")
    from_role = raw_args[1]
    target_role = raw_args[2]
    reason = raw_args[3]
    new_round = str(int(round_num) + 1)
    text = set_state(text, "ROUND_NUMBER", new_round)
    text = set_state(text, "RELEASE_STATUS", "IN_REVIEW")
    text = set_state(text, "CURRENT_TURN", target_role)

    order = ["discovery_and_grouping", "telemetry_and_debugging", "code_quality_and_testing"]
    key_map = {
        "discovery_and_grouping": ("SIGN_OFF_DISCOVERY_AND_GROUPING", "2 (Review 1)"),
        "telemetry_and_debugging": ("SIGN_OFF_TELEMETRY_AND_DEBUGGING", "3 (Review 2)"),
        "code_quality_and_testing": ("SIGN_OFF_CODE_QUALITY_AND_TESTING", "4 (Review 3)"),
    }
    if target_role in order:
        idx = order.index(target_role)
        for r in order[idx:]:
            k, stg = key_map[r]
            text = set_state(text, k, "PENDING")
            text = update_table_row(text, stg, "PENDING", "-", "-", f"Re-review requested in Round {new_round} by {from_role}.")
    text = append_audit_row(text, new_round, from_role, "REQUESTED_REREVIEW", target_role, head_sha, reason)
else:
    sys.exit(f"Unknown action: {action}")

sign_off_path.write_text(text, encoding="utf-8")
PYEOF

if [[ "${NO_PUSH}" == "true" ]]; then
  echo "[RELEASE COORDINATOR] Updated RELEASE_SIGN_OFF.md locally (--no-push)."
  exit 0
fi

ROLE_TAG="${2:-release}"
git add RELEASE_SIGN_OFF.md gradlew scripts/await_release_turn.sh scripts/advance_release_turn.sh
git commit -m "chore(release): ${ACTION} by ${ROLE_TAG} in RELEASE_SIGN_OFF.md"
git pull --rebase origin main
git push origin main
echo "[RELEASE COORDINATOR] Pushed updated RELEASE_SIGN_OFF.md to origin/main."
