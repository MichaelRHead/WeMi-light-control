#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Commit Message Format & Attribution Validator
# ==============================================================================
# Validates that a Git commit message is clear for human readers and properly
# attributed:
#   1. Plain-English Conventional Commits subject (<= 72 chars, no trailing '.',
#      and no bracketed '[...Agent]' tag on the subject line)
#   2. Blank line separating subject and body
#   3. Concise, human-readable plain-text body (no walls of text > 260 chars per
#      bullet/paragraph, no body > 1,000 chars, no semicolon chains, no nested
#      parentheses, and no dense code-symbol dumps)
#   4. Blank line separating body and Git trailer block
#   5. Brief optional 'Verified:' trailer (<= 120 chars) and mandatory:
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
MAX_BODY_LINE_LEN = 260
MAX_BODY_TOTAL_LEN = 1000
MAX_VERIFIED_LEN = 120
MAX_SEMICOLONS_PER_LINE = 1
TIP_CODE_TOKENS_PER_LINE = 5
MAX_CODE_TOKENS_PER_LINE = 7

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
VERIFIED_TRAILER_RE = re.compile(r"^Verified:[ \t]*(.*)$", re.IGNORECASE)
MARKDOWN_DECORATION_RE = re.compile(r"(\*\*[^*\n]+\*\*|`[^`\n]+`)")
URL_RE = re.compile(r"https?://\S+")
NESTED_PARENS_RE = re.compile(r"\([^()]*\([^()]+\)[^()]*\)")

# Code-identifier heuristics (excluding everyday brand/product names)
COMMON_PROPER_NOUNS = {
    "github", "wemi", "wemo", "playstore", "talkback", "openwrt",
    "androidx", "youtube", "javascript", "typescript", "devops",
    "ios", "macos", "wifi", "upnp", "ssdp", "soap", "gena", "xml",
    "http", "https", "json", "apk", "sdk", "jdk", "cli", "gui", "api",
    "robolectric", "compose", "gradle", "kotlin", "android",
}
FILE_EXT_RE = re.compile(
    r"^[A-Za-z0-9_.*-]+(?:/[A-Za-z0-9_.*-]+)*\.(?:kt|kts|java|xml|md|sh|json|txt|so|pro|toml|yml|yaml|py|gradle)$",
    re.IGNORECASE,
)
PATH_RE = re.compile(r"^[A-Za-z0-9_.-]+(?:/[A-Za-z0-9_.*-]+)+$")
DOTTED_SYMBOL_RE = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*\.[A-Za-z_][A-Za-z0-9_().]*$")
CAMEL_SYMBOL_RE = re.compile(r"^(?:[a-z]+[A-Z][A-Za-z0-9]*|[A-Z][a-z]+[A-Z][A-Za-z0-9]*)$")
SNAKE_SYMBOL_RE = re.compile(r"^[A-Za-z0-9]+_[A-Za-z0-9_]+$")
CLI_FLAG_RE = re.compile(r"^--[a-z][a-z0-9-]+$")
METRIC_TAG_RE = re.compile(r"^(?:max=\d+L?|100755|\d+/\d+L)$")


def count_code_tokens(line: str) -> int:
    text = URL_RE.sub("", line)
    raw_tokens = re.findall(r"[A-Za-z0-9_./:-]+(?:--[a-z0-9-]+)?|--[a-z][a-z0-9-]+", text)
    count = 0
    for tok in raw_tokens:
        clean = tok.strip(".,:;()[]{}'\"`<>")
        if not clean or clean.lower() in COMMON_PROPER_NOUNS:
            continue
        if (
            FILE_EXT_RE.match(clean)
            or CLI_FLAG_RE.match(clean)
            or METRIC_TAG_RE.match(clean)
            or SNAKE_SYMBOL_RE.match(clean)
            or DOTTED_SYMBOL_RE.match(clean)
            or CAMEL_SYMBOL_RE.match(clean)
            or (PATH_RE.match(clean) and clean.count("/") >= 2)
        ):
            count += 1
    return count


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
warnings = []

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
            bullet_re = re.compile(r"^(\s*)(?:[-*+]|\d+[.)])\s+(.*)$")
            logical_units = []
            saw_markdown_decoration = False
            in_fence = False

            for para in body_paragraphs:
                i = 0
                n = len(para)
                while i < n:
                    lineno, line = para[i]
                    stripped = line.strip()
                    if stripped.startswith("```"):
                        in_fence = not in_fence
                        i += 1
                        continue
                    if in_fence or line.startswith("    ") or stripped.startswith("|"):
                        i += 1
                        continue

                    if MARKDOWN_DECORATION_RE.search(line):
                        saw_markdown_decoration = True

                    bm = bullet_re.match(line)
                    if bm:
                        parts = [line.strip()]
                        i += 1
                        while i < n:
                            _, nxt = para[i]
                            nxt_s = nxt.strip()
                            if (
                                nxt_s.startswith("```")
                                or nxt_s.startswith("|")
                                or bullet_re.match(nxt)
                            ):
                                break
                            if nxt.startswith((" ", "\t")):
                                if MARKDOWN_DECORATION_RE.search(nxt):
                                    saw_markdown_decoration = True
                                parts.append(nxt_s)
                                i += 1
                            else:
                                break
                        logical_units.append((lineno, " ".join(parts)))
                        continue

                    parts = [stripped]
                    i += 1
                    while i < n:
                        _, nxt = para[i]
                        nxt_s = nxt.strip()
                        if (
                            nxt_s.startswith("```")
                            or nxt_s.startswith("|")
                            or nxt.startswith("    ")
                            or bullet_re.match(nxt)
                        ):
                            break
                        if MARKDOWN_DECORATION_RE.search(nxt):
                            saw_markdown_decoration = True
                        parts.append(nxt_s)
                        i += 1
                    logical_units.append((lineno, " ".join(parts)))

            total_prose_chars = 0
            for lineno, unit in logical_units:
                text_no_urls = URL_RE.sub("", unit).strip()
                total_prose_chars += len(text_no_urls)

                if len(text_no_urls) > MAX_BODY_LINE_LEN:
                    errors.append(
                        f"Body item at line {lineno} is {len(text_no_urls)} chars (max {MAX_BODY_LINE_LEN}). "
                        "Write shorter plain-English sentences or split into concise bullets."
                    )

                if text_no_urls.count(";") > MAX_SEMICOLONS_PER_LINE:
                    errors.append(
                        f"Body item at line {lineno} chains multiple clauses with semicolons; "
                        "split into separate plain-English bullets instead."
                    )

                if NESTED_PARENS_RE.search(text_no_urls):
                    errors.append(
                        f"Body item at line {lineno} contains nested parentheses; "
                        "rewrite in simpler, direct sentences."
                    )

                code_toks = count_code_tokens(unit)
                if code_toks > MAX_CODE_TOKENS_PER_LINE:
                    errors.append(
                        f"Body item at line {lineno} packs {code_toks} code symbols/filenames into one item "
                        f"(max {MAX_CODE_TOKENS_PER_LINE}). Summarize what changed in plain English; "
                        "Git already shows the diff."
                    )
                elif code_toks >= TIP_CODE_TOKENS_PER_LINE:
                    warnings.append(
                        f"Body item at line {lineno} mentions {code_toks} code symbols/filenames; "
                        "prefer plain English explaining what changed and why."
                    )

            if total_prose_chars > MAX_BODY_TOTAL_LEN:
                errors.append(
                    f"Commit body is {total_prose_chars} chars (max {MAX_BODY_TOTAL_LEN}). "
                    "Keep commit messages brief and human-readable; let the diff carry file-by-file details."
                )

            if saw_markdown_decoration:
                warnings.append(
                    "GitHub renders commit message bodies as plain text (not Markdown); "
                    "prefer clean plain text without **bold** or `backticks` in commits "
                    "(reserve Markdown for PRs and release notes)."
                )

        for _, text in trailer_para:
            vm = VERIFIED_TRAILER_RE.match(text)
            if vm and len(vm.group(1).strip()) > MAX_VERIFIED_LEN:
                errors.append(
                    f"Verified trailer is {len(vm.group(1).strip())} chars (max {MAX_VERIFIED_LEN}). "
                    "Keep it brief (e.g. 'Verified: 73/73 tests passing, lint clean, debug APK builds')."
                )

        agent_lines = [text for _, text in trailer_para if AGENT_TRAILER_KEY_RE.match(text)]
        if not agent_lines:
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
        "\nRequired format (plain English for human readers, subject <= 72 chars):\n"
        "  <type>(<scope>): <plain-English summary>\n"
        "\n"
        "  Optional 1-sentence context explaining the problem or motivation.\n"
        "\n"
        "  - Short plain-English bullet (<= 260 chars, no symbol dumps or ';' chains).\n"
        "\n"
        "  Verified: 73/73 tests passing, lint clean, debug APK builds   # optional, <= 120 chars\n"
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

for tip in warnings:
    sys.stderr.write(f"[agent-commit-attribution] Tip: {tip}\n")

sys.exit(0)
PYEOF
