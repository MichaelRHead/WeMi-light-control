---
name: wemi-release-coordinator
description: Coordinate the 4-agent autonomous round-robin Google Play Store release review process (AI Studio UX/CUJs -> Discovery & Room Grouping -> Telemetry & Debugging -> Code Quality & E2E Testing -> AI Studio Final Conclusion) via RELEASE_SIGN_OFF.md, RELEASE_PROCESS.md, scripts/await_release_turn.sh, and scripts/advance_release_turn.sh.
---

# WeMi 4-Agent Autonomous Round-Robin Release Coordinator (`wemi-release-coordinator`)

Use this skill whenever preparing, reviewing, advancing, or monitoring a Google Play Store release in `MichaelRHead/WeMi-light-control`.

---

## 1. The 4-Agent Round-Robin Ring

Every Play Store release candidate (`v20.0+`) is reviewed once by each of the 4 domain agents in a deterministic round-robin ring synchronized via [`RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md) on `origin/main`:

1. **Stage 1 (Initiate) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Bumps `versionCode` / `versionName` in `app/build.gradle.kts`, audits UX/accessibility/navigation/CUJs (`UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`), updates `RELEASE_NOTES.md` & `docs/user-guide/release-notes.md`, records `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, sets `RELEASE_STATUS: IN_REVIEW` and `CURRENT_TURN: discovery_and_grouping`, and pushes to `origin/main`.
2. **Stage 2 (Review 1) — `discovery_and_grouping` (Antigravity Device Discovery & Room Grouping Agent)**:
   - Audits SSDP/unicast discovery (`WeMoSsdpDiscoveryEngine.kt`, `WeMoDiscoveryCoordinator.kt`), 3-tier hardware identity matching (`matchesDiscoveredDevice`), soft-AP SSID filtering (`isWemoDeviceSetupSsid`), and `CamelCase` / specificity-ordered room inference (`normalizeDeviceNameForInference`, `inferRoomFromName`, `resolveDeviceRoomAssignment` in `WeMoEntities.kt`).
   - Runs `./gradlew testDebugUnitTest --tests com.example.WeMoSkuAndCryptoTest --tests com.example.WeMoSoapAndSelfHealingTest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off discovery_and_grouping "<verification summary>"` (setting `CURRENT_TURN: telemetry_and_debugging`).
3. **Stage 3 (Review 2) — `telemetry_and_debugging` (Antigravity Telemetry & Debugging Agent)**:
   - Audits `10.22.22.1` zero-cloud provisioning diagnostics (`WeMoSetupDiagnostics.kt`, `WeMoProvisionerClient.kt`, `WeMoProvisionDebugPanel.kt`), live SOAP packet log export (`WeMoMoreScreen.kt`), inline switch diagnostics (`WeMoDeviceDiagnosticsDrawer.kt`), `wemi-telemetry-latest.txt` export (`WeMoTelemetryFileExporter`), and zero Wi-Fi password leakage (`passwordLen` only).
   - Runs `./gradlew testDebugUnitTest --tests com.example.WeMoGenaProvisioningAndRulesTest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off telemetry_and_debugging "<verification summary>"` (setting `CURRENT_TURN: code_quality_and_testing`).
4. **Stage 4 (Review 3) — `code_quality_and_testing` (Antigravity Code Quality & End-to-End Testing Agent)**:
   - Audits file modularity & readability (`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`), Wi-Fi socket binding (`wifiSocketFactory`), SQLite/GENA/coroutine lifecycle cleanup, and `gradlew` executable mode (`100755`).
   - Runs the full release gate: `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` (`26/26+` unit, protocol, and Compose E2E tests).
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off code_quality_and_testing "<verification summary>"` (setting `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`).
5. **Stage 5 (Conclude) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Verifies all 4 stages are `SIGNED_OFF` in `RELEASE_SIGN_OFF.md`, confirms `RELEASE_NOTES.md` & `docs/user-guide/release-notes.md` completeness, sets `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA`, and `CONCLUDED_AT`, and pushes to `origin/main`.

---

## 2. Autonomous Background Wakeup Workflow (Zero Human Prompting Needed)

Antigravity conversations automatically wake up when an asynchronous background `run_command` task (`IsDaemon: false`, `WaitMsBeforeAsync: 1000`) exits with code `0`.

### Step 1: Arm Your Agent's Turn Watcher
Whenever you finish a turn in one of the three Antigravity specialist conversations, ensure [`scripts/await_release_turn.sh`](../../scripts/await_release_turn.sh) is running in the background for your role (`IsDaemon: false`, `WaitMsBeforeAsync: 1000`):
```bash
# In the Discovery & Room Grouping Agent conversation:
bash scripts/await_release_turn.sh discovery_and_grouping

# In the Telemetry & Debugging Agent conversation:
bash scripts/await_release_turn.sh telemetry_and_debugging

# In the Code Quality & E2E Testing Agent conversation:
bash scripts/await_release_turn.sh code_quality_and_testing
```

### Step 2: Execute Your Turn When Woken Up
When `scripts/await_release_turn.sh <your_role>` exits `0` and wakes your conversation:
1. Pull the latest `origin/main`:
   ```bash
   git pull --rebase origin main
   ```
2. Perform your domain audit and run your required Gradle verification tests (fixing any issues within your domain if found).
3. Record your sign-off and hand off `CURRENT_TURN` to the next agent in the ring:
   ```bash
   bash scripts/advance_release_turn.sh sign_off <your_role> "<concise summary of checks and tests passed>"
   ```
4. Re-arm your background watcher before ending your turn:
   ```bash
   bash scripts/await_release_turn.sh <your_role>
   ```
