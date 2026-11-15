#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Autonomous Release Turn Watcher (await_release_turn.sh)
# ==============================================================================
# Polls origin/main:RELEASE_SIGN_OFF.md in the background (zero LLM token cost)
# and exits 0 as soon as CURRENT_TURN matches <agent_role>, triggering
# Antigravity's Reactive Wakeup to resume the agent conversation automatically.
#
# Usage:
#   bash scripts/await_release_turn.sh <agent_role> [poll_interval_sec] [max_wait_sec]
#
# Roles:
#   - discovery_and_grouping
#   - telemetry_and_debugging
#   - code_quality_and_testing
#   - license_governance
#   - ai_studio_ux_cuj
# ==============================================================================
set -euo pipefail

ROLE="${1:-}"
POLL_INTERVAL_SEC="${2:-10}"
MAX_WAIT_SEC="${3:-90}"

if [[ -z "${ROLE}" ]]; then
  echo "Usage: $0 <discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|license_governance|ai_studio_ux_cuj> [poll_interval_sec] [max_wait_sec]" >&2
  exit 2
fi

case "${ROLE}" in
  discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|license_governance|ai_studio_ux_cuj)
    ;;
  *)
    echo "[ERROR] Invalid agent role '${ROLE}'." >&2
    exit 2
    ;;
esac

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# Ensure only one watcher runs per role in this VM
PID_FILE="/tmp/wemi_await_release_turn_${ROLE}.pid"
if [[ -f "${PID_FILE}" ]]; then
  OLD_PID="$(cat "${PID_FILE}" 2>/dev/null || true)"
  if [[ -n "${OLD_PID}" && "${OLD_PID}" != "$$" ]] && kill -0 "${OLD_PID}" 2>/dev/null; then
    kill "${OLD_PID}" 2>/dev/null || true
  fi
fi
echo "$$" > "${PID_FILE}"
trap 'rm -f "${PID_FILE}"' EXIT

extract_field() {
  local content="$1"
  local key="$2"
  printf '%s\n' "${content}" | awk -F': *' -v k="${key}" '$1 == k { print $2; exit }' | tr -d '\r'
}

START_TS="$(date +%s)"
echo "[RELEASE COORDINATOR] Watching origin/main:RELEASE_SIGN_OFF.md for role '${ROLE}' (poll=${POLL_INTERVAL_SEC}s)..."

while true; do
  git fetch origin main --quiet 2>/dev/null || true

  if SIGN_OFF_CONTENT="$(git show origin/main:RELEASE_SIGN_OFF.md 2>/dev/null)"; then
    REL_VER="$(extract_field "${SIGN_OFF_CONTENT}" "RELEASE_VERSION")"
    VER_CODE="$(extract_field "${SIGN_OFF_CONTENT}" "VERSION_CODE")"
    ROUND_NUM="$(extract_field "${SIGN_OFF_CONTENT}" "ROUND_NUMBER")"
    REL_STATUS="$(extract_field "${SIGN_OFF_CONTENT}" "RELEASE_STATUS")"
    CUR_TURN="$(extract_field "${SIGN_OFF_CONTENT}" "CURRENT_TURN")"
    AI_SIGN="$(extract_field "${SIGN_OFF_CONTENT}" "SIGN_OFF_AI_STUDIO_UX_CUJ")"
    DISC_SIGN="$(extract_field "${SIGN_OFF_CONTENT}" "SIGN_OFF_DISCOVERY_AND_GROUPING")"
    ORIGIN_SHA="$(git rev-parse --short origin/main 2>/dev/null || echo "unknown")"

    # 1. Direct CURRENT_TURN match
    if [[ "${CUR_TURN}" == "${ROLE}" ]]; then
      if [[ "${ROLE}" != "ai_studio_ux_cuj" || "${REL_STATUS}" == "AWAITING_FINAL_CONCLUSION" || "${REL_STATUS}" == "AWAITING_AI_STUDIO_INITIATION" ]]; then
        echo "=================================================================="
        echo "[RELEASE COORDINATOR] Turn activated for '${ROLE}'!"
        echo "  RELEASE_VERSION : ${REL_VER} (versionCode = ${VER_CODE}, Round ${ROUND_NUM})"
        echo "  RELEASE_STATUS  : ${REL_STATUS}"
        echo "  CURRENT_TURN    : ${CUR_TURN}"
        echo "  ORIGIN_MAIN_SHA : ${ORIGIN_SHA}"
        echo "=================================================================="
        echo "Next steps for '${ROLE}':"
        echo "  1. git pull --rebase origin main"
        echo "  2. Execute your stage review checklist in RELEASE_PROCESS.md"
        echo "  3. bash scripts/advance_release_turn.sh sign_off ${ROLE} \"<verification summary>\""
        echo "  4. Re-arm: bash scripts/await_release_turn.sh ${ROLE}"
        exit 0
      fi
    fi

    # 2. Fallback for Stage 2 (discovery_and_grouping):
    #    Wake up if RELEASE_STATUS is IN_REVIEW and AI Studio marked
    #    SIGN_OFF_AI_STUDIO_UX_CUJ as SIGNED_OFF while SIGN_OFF_DISCOVERY_AND_GROUPING
    #    is still PENDING.
    if [[ "${ROLE}" == "discovery_and_grouping" ]]; then
      if [[ "${REL_STATUS}" == "IN_REVIEW" && "${AI_SIGN}" == SIGNED_OFF* && "${DISC_SIGN}" == "PENDING" ]]; then
        echo "=================================================================="
        echo "[RELEASE COORDINATOR] AI Studio Stage 1 sign-off detected for '${ROLE}'!"
        echo "  RELEASE_VERSION : ${REL_VER} (versionCode = ${VER_CODE}, Round ${ROUND_NUM})"
        echo "  ORIGIN_MAIN_SHA : ${ORIGIN_SHA}"
        echo "=================================================================="
        exit 0
      fi
    fi
  fi

  NOW_TS="$(date +%s)"
  if (( NOW_TS - START_TS >= MAX_WAIT_SEC )); then
    echo "[RELEASE COORDINATOR] Max wait (${MAX_WAIT_SEC}s) reached for '${ROLE}'. Re-arming recommended."
    exit 0
  fi

  sleep "${POLL_INTERVAL_SEC}"
done
