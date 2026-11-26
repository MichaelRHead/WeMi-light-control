#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Agent-Attributed Git Commit Helper (commit_as_agent.sh)
# ==============================================================================
# Formats, normalizes, validates, and executes a readable Git commit with:
#   - Clean <= 72-char Conventional Commits subject (no '[...Agent]' subject tag)
#   - Reflowable plain-text paragraphs / bullet list (no 72-col hard wrapping
#     that causes jagged double-wrapping on mobile GitHub)
#   - Optional 'Verified: <summary>' trailer
#   - Mandatory 'Agent: <Canonical Agent Name> (<role_id>)' Git trailer
#
# Usage:
#   bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
#     <role_id> "<type>(<scope>): <concise summary>" "<body text or bullets>" \
#     [--verified "<verification summary>"] [--amend] [--dry-run]
#
#   bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
#     <role_id> "<type>(<scope>): <concise summary>" --body-file - \
#     --verified "<verification summary>" <<'EOF'
#   - First change bullet explaining what and why.
#   - Second change bullet.
#   EOF
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATOR="${SCRIPT_DIR}/validate_commit_msg.sh"

usage() {
  cat >&2 <<EOF
Usage:
  $0 <role_id> "<type>(<scope>): <summary>" "<body text or bullets>" ["<verified>"]
  $0 <role_id> "<type>(<scope>): <summary>" --body-file <path|-> [--verified "<verified>"] [--amend] [--dry-run]

Options:
  -v, --verified <text>   Add a 'Verified: <text>' trailer above 'Agent:'
  -F, --body-file <path>  Read commit description body from file ('-' for stdin)
      --amend             Pass --amend to 'git commit'
      --dry-run           Format, validate, and print message without committing

Roles:
  ai_studio_ux_cuj | discovery_and_grouping | telemetry_and_debugging |
  security_and_privacy | code_quality_and_testing | license_governance |
  app_promoter | antigravity_general
EOF
  exit 2
}

POSITIONAL=()
VERIFIED_TEXT=""
BODY_FILE=""
DO_AMEND="false"
DRY_RUN="false"

while (( $# > 0 )); do
  case "$1" in
    -v|--verified)
      [[ $# -ge 2 ]] || usage
      VERIFIED_TEXT="$2"
      shift 2
      ;;
    -F|--body-file)
      [[ $# -ge 2 ]] || usage
      BODY_FILE="$2"
      shift 2
      ;;
    --amend)
      DO_AMEND="true"
      shift
      ;;
    --dry-run)
      DRY_RUN="true"
      shift
      ;;
    -h|--help)
      usage
      ;;
    --)
      shift
      while (( $# > 0 )); do
        POSITIONAL+=("$1")
        shift
      done
      ;;
    -[A-Za-z]*|--[A-Za-z]*)
      if [[ "$1" != *[[:space:]]* ]]; then
        echo "[ERROR] Unknown option '$1'." >&2
        usage
      fi
      POSITIONAL+=("$1")
      shift
      ;;
    *)
      POSITIONAL+=("$1")
      shift
      ;;
  esac
done

ROLE_ID="${POSITIONAL[0]:-}"
RAW_SUBJECT="${POSITIONAL[1]:-}"
DESCRIPTION_BODY="${POSITIONAL[2]:-}"
if [[ -z "${VERIFIED_TEXT}" && -n "${POSITIONAL[3]:-}" ]]; then
  VERIFIED_TEXT="${POSITIONAL[3]}"
fi

if [[ -n "${BODY_FILE}" ]]; then
  if [[ "${BODY_FILE}" == "-" ]]; then
    DESCRIPTION_BODY="$(cat)"
  elif [[ -r "${BODY_FILE}" ]]; then
    DESCRIPTION_BODY="$(cat "${BODY_FILE}")"
  else
    echo "[ERROR] Cannot read body file '${BODY_FILE}'." >&2
    exit 2
  fi
fi

if [[ -z "${ROLE_ID}" || -z "${RAW_SUBJECT}" || -z "${DESCRIPTION_BODY}" ]]; then
  usage
fi

case "${ROLE_ID}" in
  ai_studio_ux_cuj)
    CANONICAL_NAME="Google AI Studio Agent"
    ;;
  discovery_and_grouping)
    CANONICAL_NAME="Antigravity Device Discovery & Room Grouping Agent"
    ;;
  telemetry_and_debugging)
    CANONICAL_NAME="Antigravity Telemetry & Debugging Agent"
    ;;
  security_and_privacy)
    CANONICAL_NAME="Antigravity Security & Privacy Agent"
    ;;
  code_quality_and_testing)
    CANONICAL_NAME="Antigravity Code Quality & E2E Testing Agent"
    ;;
  license_governance)
    CANONICAL_NAME="Antigravity License & Brand Governance Agent"
    ;;
  app_promoter)
    CANONICAL_NAME="Antigravity App Promoter Agent"
    ;;
  antigravity_general)
    CANONICAL_NAME="Antigravity General Agent"
    ;;
  *)
    echo "[ERROR] Unknown role_id '${ROLE_ID}'." >&2
    echo "Expected one of: ai_studio_ux_cuj, discovery_and_grouping, telemetry_and_debugging, security_and_privacy, code_quality_and_testing, license_governance, app_promoter, antigravity_general" >&2
    exit 2
    ;;
esac

TMP_MSG="$(mktemp)"
trap 'rm -f "${TMP_MSG}"' EXIT

python3 - "${RAW_SUBJECT}" "${DESCRIPTION_BODY}" "${VERIFIED_TEXT}" "${CANONICAL_NAME}" "${ROLE_ID}" "${TMP_MSG}" <<'PYEOF'
import re
import sys

raw_subject, raw_body, verified_arg, canonical_name, role_id, out_path = sys.argv[1:7]

LONG_BULLET_THRESHOLD = 80
AGENT_TAG_RE = re.compile(r"\s*\[[^\[\]]*\bAgent\b[^\[\]]*\]\s*$", re.IGNORECASE)
TRAILER_RE = re.compile(r"^([A-Za-z][A-Za-z0-9-]*):[ \t]+(.+)$")
BULLET_RE = re.compile(r"^(\s*)([-*+]|\d+[.)])\s+(.*)$")
SECTION_LABEL_RE = re.compile(r"^[A-Za-z][A-Za-z0-9 &/_-]{0,28}:$")
BOLD_HEADING_RE = re.compile(r"^\*\*[^*]+\*\*:?$")
BLOCKQUOTE_RE = re.compile(r"^(\s*>+)\s?(.*)$")
ATX_HEADING_RE = re.compile(r"^#{1,6}\s+\S")


def is_section_label(text: str) -> bool:
    if BOLD_HEADING_RE.match(text):
        return True
    return bool(SECTION_LABEL_RE.match(text) and len(text.split()) <= 3)


subject = raw_subject.strip()
stripped_subject = AGENT_TAG_RE.sub("", subject).strip()
if stripped_subject != subject:
    sys.stderr.write(
        "[agent-commit-attribution] Stripped trailing '[...Agent]' tag from subject; "
        "attribution belongs in the 'Agent:' trailer.\n"
    )
    subject = stripped_subject

body_lines = raw_body.replace("\r\n", "\n").replace("\r", "\n").split("\n")
while body_lines and not body_lines[0].strip():
    body_lines.pop(0)
while body_lines and not body_lines[-1].strip():
    body_lines.pop()

# Separate any trailing Git trailer block already supplied in the body
preserved_trailers = []
extracted_verified = ""

# Find start of trailing trailer lines (ignoring trailing blanks)
idx = len(body_lines)
found_trailers = []
while idx > 0:
    line = body_lines[idx - 1].rstrip()
    m = TRAILER_RE.match(line)
    if m:
        found_trailers.append((m.group(1), m.group(2).strip()))
        idx -= 1
    else:
        break

if found_trailers and (idx == 0 or not body_lines[idx - 1].strip()):
    found_trailers.reverse()
    body_lines = body_lines[:idx]
    while body_lines and not body_lines[-1].strip():
        body_lines.pop()
    for key, val in found_trailers:
        low = key.lower()
        if low == "agent":
            continue
        if low == "verified":
            if not extracted_verified:
                extracted_verified = val
        else:
            preserved_trailers.append(f"{key}: {val}")

verified_final = verified_arg.strip() or extracted_verified


def format_body(lines):
    out = []
    i = 0
    n = len(lines)
    in_fence = False
    warned_atx = False
    in_bullet_block = False
    prev_bullet_block_long = False

    while i < n:
        line = lines[i].rstrip()
        stripped = line.strip()

        if stripped.startswith("```"):
            in_fence = not in_fence
            out.append(line)
            in_bullet_block = False
            prev_bullet_block_long = False
            i += 1
            continue

        if in_fence or stripped.startswith("|"):
            out.append(line)
            in_bullet_block = False
            prev_bullet_block_long = False
            i += 1
            continue

        if not stripped:
            if out and out[-1] != "":
                out.append("")
            in_bullet_block = False
            prev_bullet_block_long = False
            i += 1
            continue

        if ATX_HEADING_RE.match(stripped) and not warned_atx:
            sys.stderr.write(
                "[agent-commit-attribution] Tip: avoid '#' headings in commit bodies "
                "(Git strips leading '#' lines as comments); use a plain section label like 'Changes:' instead.\n"
            )
            warned_atx = True

        bm = BULLET_RE.match(line)
        if bm:
            indent, marker, text = bm.group(1), bm.group(2), bm.group(3).strip()
            prefix = f"{indent}{marker} "
            cont_indent = " " * len(prefix)
            parts = [text]
            i += 1
            while i < n:
                nxt = lines[i].rstrip()
                nxt_stripped = nxt.strip()
                if (
                    not nxt_stripped
                    or nxt_stripped.startswith("```")
                    or nxt_stripped.startswith("|")
                    or BULLET_RE.match(nxt)
                    or BLOCKQUOTE_RE.match(nxt)
                    or is_section_label(nxt_stripped)
                ):
                    break
                leading_spaces = len(nxt) - len(nxt.lstrip(" "))
                if leading_spaces >= len(cont_indent) + 4:
                    break
                if nxt.startswith((" ", "\t")):
                    parts.append(nxt_stripped)
                    i += 1
                else:
                    break
            formatted = f"{prefix}{' '.join(parts)}"
            is_long = len(formatted) > LONG_BULLET_THRESHOLD
            if indent == "":
                if in_bullet_block and out and out[-1] != "" and (prev_bullet_block_long or is_long):
                    out.append("")
                prev_bullet_block_long = is_long
            else:
                if is_long:
                    prev_bullet_block_long = True
            in_bullet_block = True
            out.append(formatted)
            continue

        if line.startswith("    "):
            out.append(line)
            in_bullet_block = False
            prev_bullet_block_long = False
            i += 1
            continue

        qm = BLOCKQUOTE_RE.match(line)
        if qm:
            q_prefix = f"{qm.group(1)} "
            q_parts = [qm.group(2).strip()] if qm.group(2).strip() else []
            i += 1
            while i < n:
                nxt = lines[i].rstrip()
                nxt_qm = BLOCKQUOTE_RE.match(nxt)
                if not nxt_qm or nxt_qm.group(1) != qm.group(1) or not nxt_qm.group(2).strip():
                    break
                q_parts.append(nxt_qm.group(2).strip())
                i += 1
            if q_parts:
                out.append(f"{q_prefix}{' '.join(q_parts)}")
            else:
                out.append(qm.group(1))
            in_bullet_block = False
            prev_bullet_block_long = False
            continue

        if is_section_label(stripped):
            out.append(stripped)
            in_bullet_block = False
            prev_bullet_block_long = False
            i += 1
            continue

        # Prose paragraph — unfold wrapped continuation lines into one reflowable line
        para_parts = [stripped]
        i += 1
        while i < n:
            nxt = lines[i].rstrip()
            nxt_stripped = nxt.strip()
            if (
                not nxt_stripped
                or nxt_stripped.startswith("```")
                or nxt_stripped.startswith("|")
                or nxt.startswith("    ")
                or BULLET_RE.match(nxt)
                or BLOCKQUOTE_RE.match(nxt)
                or is_section_label(nxt_stripped)
            ):
                break
            para_parts.append(nxt_stripped)
            i += 1
        out.append(" ".join(para_parts))
        in_bullet_block = False
        prev_bullet_block_long = False

    while out and out[-1] == "":
        out.pop()
    return "\n".join(out)


formatted_body = format_body(body_lines)

trailer_lines = list(preserved_trailers)
if verified_final:
    trailer_lines.append(f"Verified: {verified_final}")
trailer_lines.append(f"Agent: {canonical_name} ({role_id})")

message = f"{subject}\n\n{formatted_body}\n\n" + "\n".join(trailer_lines) + "\n"
with open(out_path, "w", encoding="utf-8") as fh:
    fh.write(message)
PYEOF

bash "${VALIDATOR}" "${TMP_MSG}"

if [[ "${DRY_RUN}" == "true" ]]; then
  cat "${TMP_MSG}"
  exit 0
fi

if [[ "${DO_AMEND}" == "true" ]]; then
  git commit --amend -F "${TMP_MSG}"
else
  git commit -F "${TMP_MSG}"
fi
