#!/usr/bin/env bash
# ==============================================================================
# WeMi Light Control — Quota-Guarded Release Turn Watcher (await_release_turn.sh)
# ==============================================================================
# Polls origin/main:RELEASE_SIGN_OFF.md ONLY while an active release review
# (RELEASE_STATUS: IN_REVIEW) is progressing toward <agent_role> and <agent_role>
# is still PENDING for the current round.
#
# Quota Protection Invariants:
#   1. Immediate exit (no background loop, DO NOT RE-ARM) when RELEASE_STATUS is
#      APPROVED_FOR_PLAY_STORE, PROMOTED_TO_CLOSED_TESTING, or CURRENT_TURN is NONE.
#   2. Immediate exit (DO NOT RE-ARM) as soon as <agent_role> has SIGNED_OFF the
#      current (RELEASE_VERSION, ROUND_NUMBER), or for event-driven Stage 7
#      (`app_promoter`, triggered automatically via GitHub Actions).
#   3. Clamps MAX_WAIT_SEC <= 510s (8.5m) so exebox never hits its 9m50s RPC
#      deadline crash, and caps consecutive upstream idle waits to MAX_IDLE_REARMS=2
#      (~25m max) so stalled upstream stages can never drain 5-hour LLM quota.
#
# Usage:
#   bash scripts/await_release_turn.sh <agent_role> [poll_interval_sec] [max_wait_sec]
# ==============================================================================
set -euo pipefail

ROLE="${1:-}"
POLL_INTERVAL_SEC="${2:-15}"
MAX_WAIT_SEC="${3:-510}"
MAX_IDLE_REARMS="${MAX_IDLE_REARMS:-2}"

if [[ -z "${ROLE}" ]]; then
  echo "Usage: $0 <discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|license_governance|ai_studio_ux_cuj|app_promoter> [poll_interval_sec] [max_wait_sec]" >&2
  exit 2
fi

case "${ROLE}" in
  discovery_and_grouping|telemetry_and_debugging|code_quality_and_testing|license_governance|ai_studio_ux_cuj)
    ;;
  app_promoter)
    echo "[RELEASE COORDINATOR] Role 'app_promoter' (Stage 7) is event-driven via .github/workflows/play-store-promote.yml on push to app/build.gradle.kts."
    echo "[QUOTA GUARD] DO NOT RE-ARM await_release_turn.sh for app_promoter. Promotion runs automatically in GitHub Actions at zero LLM quota cost."
    exit 0
    ;;
  *)
    echo "[ERROR] Invalid agent role '${ROLE}'." >&2
    exit 2
    ;;
esac

# Clamp MAX_WAIT_SEC below exebox's ~590s (9m50s) BidiExecuteUvm RPC ceiling
if (( MAX_WAIT_SEC > 510 )); then
  MAX_WAIT_SEC=510
fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${REPO_ROOT}"

# Surface fast (<30ms) conversation context-bloat status if agy-usage is installed
if [[ -x "/home/antigravity/.gemini/usage/agy-usage" ]]; then
  /home/antigravity/.gemini/usage/agy-usage --check-self 2>/dev/null || true
fi

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

role_signoff_key() {
  case "$1" in
    discovery_and_grouping)   echo "SIGN_OFF_DISCOVERY_AND_GROUPING" ;;
    telemetry_and_debugging)  echo "SIGN_OFF_TELEMETRY_AND_DEBUGGING" ;;
    code_quality_and_testing) echo "SIGN_OFF_CODE_QUALITY_AND_TESTING" ;;
    license_governance)       echo "SIGN_OFF_LICENSE_GOVERNANCE" ;;
    ai_studio_ux_cuj)         echo "SIGN_OFF_AI_STUDIO_UX_CUJ" ;;
  esac
}

STATE_DIR="/home/antigravity/.gemini/usage/watcher_state"
mkdir -p "${STATE_DIR}" 2>/dev/null || STATE_DIR="/tmp"

START_TS="$(date +%s)"
FIRST_PASS=1

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
    ROLE_KEY="$(role_signoff_key "${ROLE}")"
    ROLE_SIGN="$(extract_field "${SIGN_OFF_CONTENT}" "${ROLE_KEY}")"
    ORIGIN_SHA="$(git rev-parse --short origin/main 2>/dev/null || echo "unknown")"

    REARM_FILE="${STATE_DIR}/rearms_${ROLE}_${REL_VER}_r${ROUND_NUM}.count"

    # 1. Direct CURRENT_TURN match -> Activate immediately!
    if [[ "${CUR_TURN}" == "${ROLE}" ]]; then
      if [[ "${ROLE}" != "ai_studio_ux_cuj" || "${REL_STATUS}" == "AWAITING_FINAL_CONCLUSION" || "${REL_STATUS}" == "AWAITING_AI_STUDIO_INITIATION" ]]; then
        rm -f "${REARM_FILE}" 2>/dev/null || true
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
        echo "  4. DO NOT re-arm await_release_turn.sh after signing off — end your turn cleanly!"
        exit 0
      fi
    fi

    # 2. Fallback for Stage 2 (discovery_and_grouping)
    if [[ "${ROLE}" == "discovery_and_grouping" ]]; then
      if [[ "${REL_STATUS}" == "IN_REVIEW" && "${AI_SIGN}" == SIGNED_OFF* && "${DISC_SIGN}" == "PENDING" ]]; then
        rm -f "${REARM_FILE}" 2>/dev/null || true
        echo "=================================================================="
        echo "[RELEASE COORDINATOR] AI Studio Stage 1 sign-off detected for '${ROLE}'!"
        echo "  RELEASE_VERSION : ${REL_VER} (versionCode = ${VER_CODE}, Round ${ROUND_NUM})"
        echo "  ORIGIN_MAIN_SHA : ${ORIGIN_SHA}"
        echo "=================================================================="
        exit 0
      fi
    fi

    # 3. Quota Guard A: No active release review in progress -> STOP IMMEDIATELY
    if [[ "${REL_STATUS}" == "APPROVED_FOR_PLAY_STORE" || "${REL_STATUS}" == "PROMOTED_TO_CLOSED_TESTING" || "${CUR_TURN}" == "NONE" || "${REL_STATUS}" == "AWAITING_AI_STUDIO_INITIATION" ]]; then
      rm -f "${REARM_FILE}" 2>/dev/null || true
      echo "[RELEASE COORDINATOR] No active review in progress (${REL_VER}: RELEASE_STATUS=${REL_STATUS}, CURRENT_TURN=${CUR_TURN})."
      echo "[QUOTA GUARD] DO NOT RE-ARM await_release_turn.sh between releases. End your turn cleanly now to preserve 5-hour quota."
      exit 0
    fi

    # 4. Quota Guard B: Specialist role already SIGNED_OFF this round -> STOP IMMEDIATELY
    if [[ "${ROLE}" != "ai_studio_ux_cuj" && "${ROLE_SIGN}" == SIGNED_OFF* ]]; then
      rm -f "${REARM_FILE}" 2>/dev/null || true
      echo "[RELEASE COORDINATOR] Role '${ROLE}' has already SIGNED_OFF ${REL_VER} Round ${ROUND_NUM} (CURRENT_TURN=${CUR_TURN}, RELEASE_STATUS=${REL_STATUS})."
      echo "[QUOTA GUARD] DO NOT RE-ARM await_release_turn.sh after signing off your stage. End your turn cleanly now."
      exit 0
    fi

    if (( FIRST_PASS == 1 )); then
      echo "[RELEASE COORDINATOR] Watching origin/main:RELEASE_SIGN_OFF.md for PENDING role '${ROLE}' during active ${REL_VER} Round ${ROUND_NUM} (CURRENT_TURN=${CUR_TURN}, poll=${POLL_INTERVAL_SEC}s, max_wait=${MAX_WAIT_SEC}s)..."
      FIRST_PASS=0
    fi
  fi

  NOW_TS="$(date +%s)"
  if (( NOW_TS - START_TS >= MAX_WAIT_SEC )); then
    PREV_COUNT=0
    if [[ -n "${REARM_FILE:-}" && -f "${REARM_FILE}" ]]; then
      PREV_COUNT="$(cat "${REARM_FILE}" 2>/dev/null || echo 0)"
    fi
    NEXT_COUNT=$(( PREV_COUNT + 1 ))
    if [[ -n "${REARM_FILE:-}" ]]; then
      echo "${NEXT_COUNT}" > "${REARM_FILE}" 2>/dev/null || true
    fi

    if (( NEXT_COUNT <= MAX_IDLE_REARMS )); then
      echo "[RELEASE COORDINATOR] Active IN_REVIEW wait window (${MAX_WAIT_SEC}s, ${NEXT_COUNT}/${MAX_IDLE_REARMS}) elapsed for PENDING role '${ROLE}' (upstream CURRENT_TURN=${CUR_TURN:-unknown})."
      echo "[QUOTA GUARD] Re-arm allowed (${NEXT_COUNT}/${MAX_IDLE_REARMS}): bash scripts/await_release_turn.sh ${ROLE}"
    else
      echo "[RELEASE COORDINATOR] Max consecutive upstream idle waits (${MAX_IDLE_REARMS} x ${MAX_WAIT_SEC}s) reached for '${ROLE}' while upstream remains at '${CUR_TURN:-unknown}'."
      echo "[QUOTA GUARD] DO NOT RE-ARM automatically — stop and wait for upstream completion or user prompt to protect your 5-hour quota."
    fi
    exit 0
  fi

  sleep "${POLL_INTERVAL_SEC}"
done
