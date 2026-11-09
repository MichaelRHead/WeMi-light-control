# WeMi Light Control — 4-Agent Autonomous Round-Robin Release Process (`RELEASE_PROCESS.md`)

This document defines the **4-Agent Autonomous Round-Robin Release Review & Sign-Off Process** for **WeMi Light Control** (`com.aistudio.wemiswitch.kxpqzr`).

All four specialized agents synchronize **entirely through files checked into GitHub (`origin/main`)** — specifically the live state machine in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — without requiring manual human handoffs between review stages.

---

## 1. Participating Agents & Round-Robin Ring Order

Every Google Play Store release candidate traverses a strict 5-stage round-robin ring where the **Google AI Studio Agent (`ai_studio_ux_cuj`)** initiates the release review and concludes it once all three technical specialist agents have signed off:

```
  ┌────────────────────────────────────────────────────────────────────────────┐
  │                        GITHUB (origin/main)                                │
  │             Synchronized via RELEASE_SIGN_OFF.md State Machine             │
  └──────┬──────────────────┬──────────────────┬──────────────────┬────────────┘
         │                  │                  │                  │
         ▼                  ▼                  ▼                  ▼
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │   STAGE 1    │   │   STAGE 2    │   │   STAGE 3    │   │   STAGE 4    │
  │  AI Studio   │──▶│ Discovery &  │──▶│ Telemetry &  │──▶│ Code Quality │
  │  UX & CUJs   │   │ Room Group   │   │  Debugging   │   │ & E2E Tests  │
  │  (Initiate)  │   │   Review     │   │   Review     │   │    Review    │
  └──────────────┘   └──────────────┘   └──────────────┘   └──────┬───────┘
         ▲                                                        │
         │                        STAGE 5                         │
         └────────────────── AI Studio Concludes ◀────────────────┘
                        (APPROVED_FOR_PLAY_STORE)
```

| Stage | Agent Role ID (`CURRENT_TURN`) | Agent Name | Release Review Mandate & Verification Gate | Exclusively Owned Files |
| :---: | :--- | :--- | :--- | :--- |
| **Stage 1 (Initiate)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Initiator & Finalizer)* | 1. Bumps `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts).<br>2. Audits UX flows, 48dp+ accessibility targets, TalkBack semantics, Back navigation, and Critical User Journeys against [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).<br>3. Updates [`RELEASE_NOTES.md`](RELEASE_NOTES.md) & [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md).<br>4. Initializes [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) (`RELEASE_STATUS: IN_REVIEW`, `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, `CURRENT_TURN: discovery_and_grouping`) and pushes to `origin/main`. | [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) |
| **Stage 2 (Review 1)** | `discovery_and_grouping` | **Antigravity Device Discovery & Room Grouping Agent** | 1. Audits SSDP `M-SEARCH` & `/24` unicast discovery ([`WeMoSsdpDiscoveryEngine.kt`](app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt)).<br>2. Verifies 3-tier hardware identity matching (`matchesDiscoveredDevice`: `<serialNumber>` → `<UDN>`/`USN` → `(ipAddress, port)`/`<friendlyName>`).<br>3. Verifies `CamelCase` normalization (`normalizeDeviceNameForInference`), specificity-ordered room inference (`inferRoomFromName`: `"Ensuite"` before `"Bedroom"`), non-destructive migration (`resolveDeviceRoomAssignment`), and Belkin soft-AP SSID filtering (`isWemoDeviceSetupSsid`).<br>4. Runs discovery & room-grouping unit tests (`./gradlew testDebugUnitTest --tests com.example.WeMoSkuAndCryptoTest --tests com.example.WeMoSoapAndSelfHealingTest`).<br>5. Records `SIGN_OFF_DISCOVERY_AND_GROUPING: SIGNED_OFF`, sets `CURRENT_TURN: telemetry_and_debugging`, and pushes to `origin/main`. | [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md), [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) |
| **Stage 3 (Review 2)** | `telemetry_and_debugging` | **Antigravity Telemetry & Debugging Agent** | 1. Audits `10.22.22.1` zero-cloud provisioning diagnostics ([`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt), [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoProvisionDebugPanel.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionDebugPanel.kt)).<br>2. Verifies the `[NET]`, `[PROBE]`, `[FLAGS]`, `[TARGET AP]`, `[STEP 1..5]` screenshot card, Live SOAP Packet Log inspector ([`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt)), Inline Switch Diagnostics Drawer ([`WeMoDeviceDiagnosticsDrawer.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceDiagnosticsDrawer.kt)), and `wemi-telemetry-latest.txt` export (`WeMoTelemetryFileExporter`).<br>3. Verifies zero plaintext/ciphertext Wi-Fi password leakage (`passwordLen` only).<br>4. Runs provisioning & telemetry tests (`./gradlew testDebugUnitTest --tests com.example.WeMoGenaProvisioningAndRulesTest`).<br>5. Records `SIGN_OFF_TELEMETRY_AND_DEBUGGING: SIGNED_OFF`, sets `CURRENT_TURN: code_quality_and_testing`, and pushes to `origin/main`. | [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) |
| **Stage 4 (Review 3)** | `code_quality_and_testing` | **Antigravity Code Quality & End-to-End Testing Agent** | 1. Audits human readability, single-responsibility modularity, and file line budgets against [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md).<br>2. Verifies Wi-Fi socket binding (`wifiSocketFactory`), coroutine/GENA/SQLite lifecycle cleanup, and executable [`gradlew`](gradlew) (`100755`).<br>3. Executes the full mandatory verification gate (`./gradlew testDebugUnitTest --rerun-tasks assembleDebug`) covering all unit, UPnP/SOAP/GENA protocol, and full-stack Compose + Robolectric + [`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt) End-to-End UI tests (`26/26+` passing).<br>4. Records `SIGN_OFF_CODE_QUALITY_AND_TESTING: SIGNED_OFF`, sets `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`, and pushes to `origin/main`. | [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md), [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md), [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) |
| **Stage 5 (Conclude)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Finalizer)* | 1. Confirms all 4 agents have status `SIGNED_OFF` for the current `ROUND_NUMBER` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).<br>2. Ensures [`RELEASE_NOTES.md`](RELEASE_NOTES.md), [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), and [`IMPLEMENTED.md`](IMPLEMENTED.md) include any fixes landed during Stages 2–4.<br>3. Sets `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA: <sha>`, and `CONCLUDED_AT: <timestamp>` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), and pushes the final release commit to `origin/main`. | Final Play Store Release Conclusion |

---

## 2. Zero-Touch Synchronization via GitHub (`RELEASE_SIGN_OFF.md`)

### 2.1 Deterministic State Header in `RELEASE_SIGN_OFF.md`
At the top of [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) is a machine-readable `RELEASE_STATE` block:

```markdown
<!-- RELEASE_STATE_BEGIN -->
RELEASE_VERSION: v20.0
VERSION_CODE: 20
ROUND_NUMBER: 1
RELEASE_STATUS: AWAITING_AI_STUDIO_INITIATION
CURRENT_TURN: ai_studio_ux_cuj
CANDIDATE_BASE_SHA: 60b4e8a
SIGN_OFF_AI_STUDIO_UX_CUJ: PENDING
SIGN_OFF_DISCOVERY_AND_GROUPING: PENDING
SIGN_OFF_TELEMETRY_AND_DEBUGGING: PENDING
SIGN_OFF_CODE_QUALITY_AND_TESTING: PENDING
FINAL_RELEASE_SHA: NONE
CONCLUDED_AT: NONE
<!-- RELEASE_STATE_END -->
```

Valid values for `RELEASE_STATUS`:
- `AWAITING_AI_STUDIO_INITIATION` — Waiting for `ai_studio_ux_cuj` to start the release review round.
- `IN_REVIEW` — Round-robin review is actively progressing through `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing`.
- `AWAITING_FINAL_CONCLUSION` — All 4 agents have `SIGNED_OFF`; waiting for `ai_studio_ux_cuj` to conclude the release.
- `APPROVED_FOR_PLAY_STORE` — All 4 agents signed off and `ai_studio_ux_cuj` concluded the release.

### 2.2 Autonomous Reactive Wakeup for Antigravity Agents (`scripts/await_release_turn.sh`)
Each of the three Antigravity agents (`discovery_and_grouping`, `telemetry_and_debugging`, and `code_quality_and_testing`) runs inside a persistent Linux VM with Antigravity's **Reactive Wakeup** system (when an asynchronous `run_command` background task completes and exits `0`, Antigravity automatically wakes up the agent conversation without any user prompt).

1. **Arming the Autonomous Watcher**:
   At the end of its turn, each Antigravity agent launches [`scripts/await_release_turn.sh`](scripts/await_release_turn.sh) via `run_command` with `IsDaemon: false` and `WaitMsBeforeAsync: 1000`:
   ```bash
   # Discovery & Room Grouping Agent (Stage 2):
   bash scripts/await_release_turn.sh discovery_and_grouping

   # Telemetry & Debugging Agent (Stage 3):
   bash scripts/await_release_turn.sh telemetry_and_debugging

   # Code Quality & E2E Testing Agent (Stage 4):
   bash scripts/await_release_turn.sh code_quality_and_testing
   ```
2. **Zero-Token Background Polling**:
   - While waiting, `scripts/await_release_turn.sh <role>` polls `git fetch origin main --quiet` every 15 seconds inside the shell process (consuming zero LLM tokens).
   - It inspects `git show origin/main:RELEASE_SIGN_OFF.md` for `CURRENT_TURN: <role>`.
   - **Auto-Initiation Fallback**: If `<role>` is `discovery_and_grouping` and `ai_studio_ux_cuj` pushes a new `versionCode` in `app/build.gradle.kts` without manually updating `CURRENT_TURN`, `await_release_turn.sh` automatically detects the version bump and wakes up `discovery_and_grouping` to begin Stage 2.
3. **Automatic Agent Wakeup, Review & Handoff (`scripts/advance_release_turn.sh`)**:
   - As soon as `CURRENT_TURN` matches `<role>`, `scripts/await_release_turn.sh` prints the release state summary and **exits `0`**.
   - Antigravity immediately wakes up that agent's conversation.
   - The agent runs `git pull --rebase origin main`, executes its domain review and tests, and advances the state machine using [`scripts/advance_release_turn.sh`](scripts/advance_release_turn.sh):
     ```bash
     bash scripts/advance_release_turn.sh sign_off <role> "<concise verification summary>"
     ```
   - `scripts/advance_release_turn.sh` updates [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), ensures [`gradlew`](gradlew) retains `100755` executable mode, commits, rebases against `origin/main`, and pushes to GitHub — which immediately triggers the next agent's `await_release_turn.sh` watcher!
   - Finally, the agent re-arms `bash scripts/await_release_turn.sh <role>` in the background and ends its turn.

---

## 3. Handling Fixes & Re-Reviews During the Round-Robin

1. **Scoped Domain Fixes (Default — Continue Forward)**:
   - Because `code_quality_and_testing` (Stage 4) is the **final** technical reviewer before returning to `ai_studio_ux_cuj`, any self-contained domain fix landed by `discovery_and_grouping` (Stage 2) or `telemetry_and_debugging` (Stage 3) will automatically be compiled and tested against the entire 26-test Robolectric + Compose E2E suite in Stage 4.
   - For scoped domain fixes, the reviewing agent commits the fix, runs its domain tests, and runs `bash scripts/advance_release_turn.sh sign_off <role> "<fix + verification summary>"` to advance forward to the next stage.
2. **Cross-Cutting Fixes Requiring Re-Review (`request_rereview`)**:
   - If an agent in Stage 3 or Stage 4 discovers a regression that requires an earlier agent in the ring to re-verify its domain (up to `MAX_ROUNDS = 3`), it runs:
     ```bash
     bash scripts/advance_release_turn.sh request_rereview <my_role> <target_role> "<reason for re-review>"
     ```
   - This increments `ROUND_NUMBER`, resets affected downstream sign-offs to `PENDING`, sets `CURRENT_TURN: <target_role>`, and pushes to `origin/main` so `<target_role>` automatically wakes up for the new round.

---

## 4. Quick-Start Instructions for Each Agent

### For Google AI Studio Agent (`ai_studio_ux_cuj`)
- **To Start a Release Review (Stage 1)**:
  1. Ensure `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts), [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`RELEASE_NOTES.md`](RELEASE_NOTES.md), and [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) are updated for the target release.
  2. In [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), update the `RELEASE_STATE` block and table:
     - Set `RELEASE_VERSION: v20.0` (or new version) and `VERSION_CODE: 20`
     - Set `ROUND_NUMBER: 1`
     - Set `RELEASE_STATUS: IN_REVIEW`
     - Set `CURRENT_TURN: discovery_and_grouping`
     - Set `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`
     - Set `SIGN_OFF_DISCOVERY_AND_GROUPING: PENDING`, `SIGN_OFF_TELEMETRY_AND_DEBUGGING: PENDING`, `SIGN_OFF_CODE_QUALITY_AND_TESTING: PENDING`
     - Append a Stage 1 initiation row to the Audit Log table in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) and push to `origin/main`.
- **To Conclude a Release Review (Stage 5)**:
  1. When `CURRENT_TURN: ai_studio_ux_cuj` and `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` (all 4 `SIGN_OFF_*` fields are `SIGNED_OFF`), perform a final check of `RELEASE_NOTES.md` and `docs/user-guide/release-notes.md`.
  2. In [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), set:
     - `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`
     - `CURRENT_TURN: NONE`
     - `FINAL_RELEASE_SHA: <current_head_sha>`
     - `CONCLUDED_AT: <YYYY-MM-DD HH:MM PST>`
  3. Append the Stage 5 conclusion row to the Audit Log in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) and push to `origin/main`.

### For Antigravity Specialist Agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`)
1. Always keep `bash scripts/await_release_turn.sh <your_role>` running as a background task (`run_command` with `IsDaemon: false`, `WaitMsBeforeAsync: 1000`).
2. When woken up by `await_release_turn.sh`:
   - Run `git pull --rebase origin main`.
   - Execute your stage's review checklist and automated tests from Section 1 above.
   - Run `bash scripts/advance_release_turn.sh sign_off <your_role> "<summary of checks & tests passed>"` (which updates `RELEASE_SIGN_OFF.md`, restores `100755` on `gradlew`, commits, rebases, and pushes to `origin/main`).
   - Re-launch `bash scripts/await_release_turn.sh <your_role>` in the background before finishing your turn.
