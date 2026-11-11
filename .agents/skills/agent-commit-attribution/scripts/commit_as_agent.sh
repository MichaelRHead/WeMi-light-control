#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Agent-Attributed Git Commit Helper (commit_as_agent.sh)
# ==============================================================================
# Formats and executes a Git commit that includes both:
#   - [<Short Agent Name>] on the subject line, and
#   - A commit description body ending with:
#     Agent: <Canonical Agent Name> (<role_id>)
#
# Usage:
#   bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
#     <role_id> "<type>(<scope>): <summary>" "<commit description>"
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VALIDATOR="${SCRIPT_DIR}/validate_commit_msg.sh"

ROLE_ID="${1:-}"
RAW_SUBJECT="${2:-}"
DESCRIPTION_BODY="${3:-}"

if [[ -z "${ROLE_ID}" || -z "${RAW_SUBJECT}" || -z "${DESCRIPTION_BODY}" ]]; then
  echo "Usage: $0 <role_id> \"<type>(<scope>): <summary>\" \"<commit description>\"" >&2
  echo "Roles: ai_studio_ux_cuj | discovery_and_grouping | telemetry_and_debugging | code_quality_and_testing | license_governance | antigravity_general" >&2
  exit 2
fi

case "${ROLE_ID}" in
  ai_studio_ux_cuj)
    CANONICAL_NAME="Google AI Studio Agent"
    SHORT_TAG="[Google AI Studio Agent]"
    ;;
  discovery_and_grouping)
    CANONICAL_NAME="Antigravity Device Discovery & Room Grouping Agent"
    SHORT_TAG="[Discovery & Room Grouping Agent]"
    ;;
  telemetry_and_debugging)
    CANONICAL_NAME="Antigravity Telemetry & Debugging Agent"
    SHORT_TAG="[Telemetry & Debugging Agent]"
    ;;
  code_quality_and_testing)
    CANONICAL_NAME="Antigravity Code Quality & E2E Testing Agent"
    SHORT_TAG="[Code Quality & E2E Testing Agent]"
    ;;
  license_governance)
    CANONICAL_NAME="Antigravity License & Brand Governance Agent"
    SHORT_TAG="[License & Brand Governance Agent]"
    ;;
  antigravity_general)
    CANONICAL_NAME="Antigravity General Agent"
    SHORT_TAG="[Antigravity General Agent]"
    ;;
  *)
    echo "[ERROR] Unknown role_id '${ROLE_ID}'." >&2
    echo "Expected one of: ai_studio_ux_cuj, discovery_and_grouping, telemetry_and_debugging, code_quality_and_testing, license_governance, antigravity_general" >&2
    exit 2
    ;;
esac

# Append [<Short Agent Name>] to subject if not already present
if [[ "${RAW_SUBJECT}" == *"[${CANONICAL_NAME}]"* || "${RAW_SUBJECT}" == *"${SHORT_TAG}"* ]]; then
  FINAL_SUBJECT="${RAW_SUBJECT}"
else
  FINAL_SUBJECT="${RAW_SUBJECT} ${SHORT_TAG}"
fi

TMP_MSG="$(mktemp)"
trap 'rm -f "${TMP_MSG}"' EXIT

cat > "${TMP_MSG}" <<EOF
${FINAL_SUBJECT}

${DESCRIPTION_BODY}

Agent: ${CANONICAL_NAME} (${ROLE_ID})
EOF

bash "${VALIDATOR}" "${TMP_MSG}"
git commit -F "${TMP_MSG}"
