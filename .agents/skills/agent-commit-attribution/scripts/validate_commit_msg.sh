#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Commit Message Format & Attribution Validator
# ==============================================================================
# Validates that a Git commit message follows the readable, trailer-attributed
# convention:
#   1. Conventional Commits subject line (<= 72 chars, no trailing '.', and no
#      bracketed '[...Agent]' tag on the subject line)
#   2. Blank line separating subject and body
#   3. Non-empty description body (no bare one-liners or trailer-only commits;
#      prose/bullet lines wrapped cleanly, hard max 100 chars, target <= 72)
#   4. Blank line separating body and Git trailer block
#   5. Mandatory canonical attribution trailer:
#        Agent: <Canonical Agent Name> (<role_id>)
#
# Usage:
#   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh <commit-msg-file|->
#   bash .agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh --install-hook
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

if [[ "${1:-}" == "--install-hook" ]]; then
  REPO_ROOT="$(git rev-parse --show-toplevel 2>/dev/null || (cd "${SCRIPT_DIR}/../../../.." && pwd))"
  HOOK_PATH="${REPO_ROOT}/.git/hooks/commit-msg"
  mkdir -p "$(dirname "${HOOK_PATH}")"
  cat > "${HOOK_PATH}" <<'HOOKEOF'
#!/usr/bin/env bash
set -euo pipefail
REPO_ROOT="$(git rev-parse --show-toplevel)"
REPO_VALIDATOR="${REPO_ROOT}/.agents/skills/agent-commit-attribution/scripts/validate_commit_msg.sh"
GLOBAL_VALIDATOR="${HOME}/.gemini/skills/agent-commit-attribution/scripts/validate_commit_msg.sh"
if [[ -f "${REPO_VALIDATOR}" ]]; then
  exec bash "${REPO_VALIDATOR}" "$1"
elif [[ -f "${GLOBAL_VALIDATOR}" ]]; then
  exec bash "${GLOBAL_VALIDATOR}" "$1"
fi
HOOKEOF
  chmod +x "${HOOK_PATH}"
  echo "[agent-commit-attribution] Installed Git commit-msg hook at ${HOOK_PATH}"
  exit 0
fi

MSG_FILE="${1:-}"
if [[ -z "${MSG_FILE}" ]]; then
  echo "Usage: $0 <commit-msg-file|-> | --install-hook" >&2
  exit 2
fi

TMP_STDIN=""
if [[ "${MSG_FILE}" == "-" ]]; then
  TMP_STDIN="$(mktemp)"
  trap 'rm -f "${TMP_STDIN}"' EXIT
  cat > "${TMP_STDIN}"
  MSG_FILE="${TMP_STDIN}"
elif [[ ! -r "${MSG_FILE}" ]]; then
  echo "Error: cannot read commit message file '${MSG_FILE}'" >&2
  exit 2
fi

python3 - "${MSG_FILE}" <<'PYEOF'
import re
import sys

MAX_SUBJECT_LEN = 72
MAX_BODY_LINE_LEN = 100

CONVENTIONAL_RE = re.compile(
    r"^(feat|fix|refactor|test|docs|chore|perf|style|build|ci|release)"
    r"(\([A-Za-z0-9._/-]+\))?!?: \S.*$"
)
EXEMPT_SUBJECT_RE = re.compile(
    r"^(Merge (branch|remote-tracking branch|pull request|tag) |Revert \"|(fixup|squash|amend)! )"
)
SUBJECT_AGENT_TAG_RE = re.compile(r"\[[^\[\]]*\bAgent\b[^\[\]]*\]", re.IGNORECASE)
SCISSOR_RE = re.compile(r"^# -+ >8 -+\s*$")
TRAILER_RE = re.compile(r"^[A-Za-z][A-Za-z0-9-]*:[ \t]+\S.*$")
AGENT_TRAILER_KEY_RE = re.compile(r"^Agent:[ \t]*(.*)$")
AGENT_TRAILER_VALID_RE = re.compile(r"^Agent:[ \t]+.+Agent[ \t]+\([a-z0-9_]+\)[ \t]*$")
BULLET_PREFIX_RE = re.compile(r"^\s*(?:[-*+]|\d+[.)])\s+")


def read_message(path: str) -> str:
    if path == "-":
        return sys.stdin.read()
    with open(path, "r", encoding="utf-8", errors="replace") as fh:
        return fh.read()


raw = read_message(sys.argv[1]).replace("\r\n", "\n").replace("\r", "\n")

# Strip git verbose diff scissor section and comment lines
clean_lines = []
for line in raw.split("\n"):
    if SCISSOR_RE.match(line):
        break
    if line.lstrip().startswith("#"):
        continue
    clean_lines.append(line.rstrip())

# Drop leading and trailing blank lines
while clean_lines and clean_lines[0] == "":
    clean_lines.pop(0)
while clean_lines and clean_lines[-1] == "":
    clean_lines.pop()

errors = []

if not clean_lines:
    errors.append("Commit message is empty.")
else:
    subject = clean_lines[0].strip()

    if EXEMPT_SUBJECT_RE.match(subject):
        sys.exit(0)

    if not CONVENTIONAL_RE.match(subject):
        errors.append(
            "Subject line must follow Conventional Commits: '<type>(<scope>): <summary>' "
            "(allowed types: feat, fix, refactor, test, docs, chore, perf, style, build, ci, release)."
        )

    if SUBJECT_AGENT_TAG_RE.search(subject):
        errors.append(
            "Remove '[...Agent]' from the subject line; put attribution in the 'Agent:' trailer instead."
        )

    if len(subject) > MAX_SUBJECT_LEN:
        errors.append(
            f"Subject line is {len(subject)} chars (max {MAX_SUBJECT_LEN} so GitHub does not truncate it)."
        )

    if subject.endswith("."):
        errors.append("Remove trailing period '.' from the commit subject line.")

    if len(clean_lines) < 2:
        errors.append("Commit description body is missing (bare one-line commits are not allowed).")
    else:
        if clean_lines[1] != "":
            errors.append("Line 2 must be a blank line separating the subject from the commit body.")

        # Split remaining lines (with 1-based line numbers) into paragraphs
        tail = [(idx + 1, line) for idx, line in enumerate(clean_lines) if idx >= 1]
        # Skip the expected blank line at index 1
        if tail and tail[0][1] == "":
            tail = tail[1:]

        paragraphs = []
        current = []
        for item in tail:
            if item[1] == "":
                if current:
                    paragraphs.append(current)
                    current = []
            else:
                current.append(item)
        if current:
            paragraphs.append(current)

        def is_trailer_paragraph(para):
            saw_trailer = False
            for _, text in para:
                if TRAILER_RE.match(text):
                    saw_trailer = True
                elif saw_trailer and text.startswith((" ", "\t")) and text.strip():
                    continue
                else:
                    return False
            return saw_trailer

        trailer_para = []
        body_paragraphs = paragraphs

        if paragraphs and is_trailer_paragraph(paragraphs[-1]):
            trailer_para = paragraphs[-1]
            body_paragraphs = paragraphs[:-1]
        elif paragraphs:
            # Detect trailers glued directly to the bottom of the body without a blank line
            last_para = paragraphs[-1]
            split_idx = len(last_para)
            while split_idx > 0:
                text = last_para[split_idx - 1][1]
                if TRAILER_RE.match(text):
                    split_idx -= 1
                elif text.startswith((" ", "\t")) and split_idx - 1 > 0 and TRAILER_RE.match(last_para[split_idx - 2][1]):
                    split_idx -= 1
                else:
                    break
            if split_idx < len(last_para):
                trailer_para = last_para[split_idx:]
                body_paragraphs = paragraphs[:-1] + ([last_para[:split_idx]] if split_idx > 0 else [])
                errors.append(
                    "Add a blank line before the Git trailer block ('Verified:' / 'Agent:')."
                )

        body_lines = [item for para in body_paragraphs for item in para]
        if not body_lines:
            errors.append(
                "Commit description body is missing (only trailers found; explain what changed and why)."
            )
        else:
            in_fence = False
            for lineno, line in body_lines:
                stripped = line.strip()
                if stripped.startswith("```"):
                    in_fence = not in_fence
                    continue
                if in_fence or line.startswith("    ") or stripped.startswith("|"):
                    continue
                if len(line) > MAX_BODY_LINE_LEN:
                    content = BULLET_PREFIX_RE.sub("", line).strip()
                    tokens = content.split()
                    if len(tokens) > 1:
                        longest = max(len(t) for t in tokens)
                        # Only reject wrappable prose (skip lines dominated by one long URL/path)
                        if len(line) - longest > 24:
                            errors.append(
                                f"Body line {lineno} is {len(line)} chars "
                                f"(max {MAX_BODY_LINE_LEN}; wrap prose/bullets at <= 72 chars)."
                            )

        agent_lines = [text for _, text in trailer_para if AGENT_TRAILER_KEY_RE.match(text)]
        if not agent_lines:
            # Check if someone placed Agent: inside body instead of trailer block
            body_agent = [text for _, text in body_lines if AGENT_TRAILER_KEY_RE.match(text)]
            if body_agent:
                errors.append(
                    "Place 'Agent: <Canonical Agent Name> (<role_id>)' in the bottom trailer block "
                    "separated by a blank line."
                )
            else:
                errors.append(
                    "Commit message must end with an 'Agent: <Canonical Agent Name> (<role_id>)' trailer."
                )
        elif not any(AGENT_TRAILER_VALID_RE.match(a) for a in agent_lines):
            errors.append(
                "Invalid 'Agent:' trailer format. Expected: 'Agent: <Canonical Agent Name> (<role_id>)'."
            )

if errors:
    sys.stderr.write(
        "==============================================================================\n"
        "[agent-commit-attribution] COMMIT REJECTED\n"
        "------------------------------------------------------------------------------\n"
    )
    for err in errors:
        sys.stderr.write(f"  - {err}\n")
    sys.stderr.write(
        "\nRequired format (subject <= 72 chars, body wrapped <= 72 chars):\n"
        "  <type>(<scope>): <concise imperative summary>\n"
        "\n"
        "  - <concise bullet or short paragraph explaining what changed and why>\n"
        "\n"
        "  Verified: <concise test/build verification>   # optional\n"
        "  Agent: <Canonical Agent Name> (<role_id>)\n"
        "\nCanonical Agent Names:\n"
        "  - Google AI Studio Agent (ai_studio_ux_cuj)\n"
        "  - Antigravity Device Discovery & Room Grouping Agent (discovery_and_grouping)\n"
        "  - Antigravity Telemetry & Debugging Agent (telemetry_and_debugging)\n"
        "  - Antigravity Code Quality & E2E Testing Agent (code_quality_and_testing)\n"
        "  - Antigravity License & Brand Governance Agent (license_governance)\n"
        "  - Antigravity General Agent (antigravity_general)\n"
        "==============================================================================\n"
    )
    sys.exit(1)

sys.exit(0)
PYEOF
