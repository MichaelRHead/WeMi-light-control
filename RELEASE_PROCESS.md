# WeMi Light Control — 5-Agent Autonomous Round-Robin Release Process (`RELEASE_PROCESS.md`)

This document defines the **5-Agent / 6-Stage Autonomous Round-Robin Release Review & Sign-Off Process** for **WeMi Light Control** (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`).

All five specialized agents synchronize **entirely through files checked into GitHub (`origin/main`)** — specifically the live state machine in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — without requiring manual human handoffs between review stages.

---

## 1. Participating Agents & Round-Robin Ring Order

Every Google Play Store release candidate traverses a strict 6-stage round-robin ring where the **Google AI Studio Agent (`ai_studio_ux_cuj`)** initiates the release review in **Stage 1** and concludes it in **Stage 6** once all four Antigravity specialist agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, and `license_governance`) have signed off:

```
  ┌───────────────────────────────────────────────────────────────────────────────────────────────┐
  │                                     GITHUB (origin/main)                                      │
  │                          Synchronized via RELEASE_SIGN_OFF.md State Machine                   │
  └──────┬──────────────────┬──────────────────┬──────────────────┬──────────────────┬────────────┘
         │                  │                  │                  │                  │
         ▼                  ▼                  ▼                  ▼                  ▼
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │   STAGE 1    │   │   STAGE 2    │   │   STAGE 3    │   │   STAGE 4    │   │   STAGE 5    │
  │  AI Studio   │──▶│ Discovery &  │──▶│ Telemetry &  │──▶│ Code Quality │──▶│  License &   │
  │  UX & CUJs   │   │ Room Group   │   │  Debugging   │   │ & E2E Tests  │   │ Brand Gov.   │
  │  (Initiate)  │   │   Review 1   │   │   Review 2   │   │   Review 3   │   │   Review 4   │
  └──────────────┘   └──────────────┘   └──────────────┘   └──────────────┘   └──────┬───────┘
         ▲                                                                           │
         │                                  STAGE 6                                  │
         └─────────────────────────── AI Studio Concludes ◀──────────────────────────┘
                                   (APPROVED_FOR_PLAY_STORE)
```

| Stage | Agent Role ID (`CURRENT_TURN`) | Agent Name | Release Review Mandate & Verification Gate | Exclusively Owned Files |
| :---: | :--- | :--- | :--- | :--- |
| **Stage 1 (Initiate)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Initiator & Finalizer)* | 1. Verifies `applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `namespace = "org.suppressingfire.wemi"`, and incremented `versionCode` / `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts).<br>2. Audits UX flows, the 5 Compose Layout Invariants (`AutoShrinkSwitchName`, 3-state hardware icons with `#23272F` standby `I/O` & red Disconnected ring, uniform `68.dp` room cards, top-first dimmer drawer, non-blocking toast), 48dp+ touch targets, TalkBack semantics, Back navigation, and Critical User Journeys against [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).<br>3. Finalizes user-facing release notes in [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) (plus [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt) <= 500 chars and [`play_store_assets/STORE_LISTING.md`](play_store_assets/STORE_LISTING.md)) and runs `compile_applet` + `gradle :app:testDebugUnitTest`.<br>4. Runs `bash scripts/advance_release_turn.sh initiate "<summary>"` to update [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) (`RELEASE_STATUS: IN_REVIEW`, `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, `CURRENT_TURN: discovery_and_grouping`), ready for GitHub sync to `origin/main`. | [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) |
| **Stage 2 (Review 1)** | `discovery_and_grouping` | **Antigravity Device Discovery & Room Grouping Agent** | 1. Audits SSDP `M-SEARCH` & `/24` unicast discovery ([`SsdpDiscoveryEngine.kt`](app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt), [`DiscoveryCoordinator.kt`](app/src/main/java/org/suppressingfire/wemi/data/DiscoveryCoordinator.kt)).<br>2. Verifies 3-tier hardware identity matching (`matchesDiscoveredDevice`: `<serialNumber>` → `<UDN>`/`USN` → `(ipAddress, port)`/`<friendlyName>`).<br>3. Verifies `CamelCase` normalization (`normalizeDeviceNameForInference`), specificity-ordered room inference (`inferRoomFromName`: `"Ensuite"` before `"Bedroom"`), non-destructive migration (`resolveDeviceRoomAssignment`), and Belkin soft-AP SSID filtering (`isWemoDeviceSetupSsid`).<br>4. Runs discovery & room-grouping unit tests (`./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.SkuAndCryptoTest --tests org.suppressingfire.wemi.SoapAndSelfHealingTest`).<br>5. Records `SIGN_OFF_DISCOVERY_AND_GROUPING: SIGNED_OFF`, sets `CURRENT_TURN: telemetry_and_debugging`, and pushes to `origin/main`. | [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md), [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) |
| **Stage 3 (Review 2)** | `telemetry_and_debugging` | **Antigravity Telemetry & Debugging Agent** | 1. Audits `10.22.22.1` zero-cloud provisioning diagnostics ([`SetupDiagnostics.kt`](app/src/main/java/org/suppressingfire/wemi/network/SetupDiagnostics.kt), [`ProvisionerClient.kt`](app/src/main/java/org/suppressingfire/wemi/network/ProvisionerClient.kt), [`ProvisionDebugPanel.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionDebugPanel.kt)).<br>2. Verifies the `[NET]`, `[PROBE]`, `[FLAGS]`, `[TARGET AP]`, `[STEP 1..6]` screenshot card, Live SOAP Packet Log inspector ([`MoreScreen.kt`](app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt)), Inline Switch Diagnostics Drawer ([`DeviceDiagnosticsDrawer.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceDiagnosticsDrawer.kt)), and `wemi-telemetry-latest.txt` export (`TelemetryFileExporter`).<br>3. Verifies zero plaintext/ciphertext Wi-Fi password leakage (`passwordLen` only).<br>4. Runs provisioning & telemetry tests (`./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest`).<br>5. Records `SIGN_OFF_TELEMETRY_AND_DEBUGGING: SIGNED_OFF`, sets `CURRENT_TURN: code_quality_and_testing`, and pushes to `origin/main`. | [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) |
| **Stage 4 (Review 3)** | `code_quality_and_testing` | **Antigravity Code Quality & End-to-End Testing Agent** | 1. Audits human readability, single-responsibility modularity, and file line budgets against [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md).<br>2. Verifies Wi-Fi socket binding (`wifiSocketFactory`), coroutine/GENA/SQLite lifecycle cleanup, and executable [`gradlew`](gradlew) (`100755`).<br>3. Executes the full mandatory verification gate (`./gradlew testDebugUnitTest --rerun-tasks assembleDebug`) covering all unit, UPnP/SOAP/GENA protocol, and full-stack Compose + Robolectric + [`FakeUpnpServer.kt`](app/src/test/java/org/suppressingfire/wemi/FakeUpnpServer.kt) End-to-End UI tests (`29/29+` passing).<br>4. Records `SIGN_OFF_CODE_QUALITY_AND_TESTING: SIGNED_OFF`, sets `CURRENT_TURN: license_governance`, and pushes to `origin/main`. | [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md), [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) |
| **Stage 5 (Review 4)** | `license_governance` | **Antigravity License & Brand Governance Agent** | 1. Audits root [`LICENSE`](LICENSE) (**MIT License**) parity with the in-app Open Source License card (`open_source_licenses_card`, `mit_license_text_surface`, and `third_party_notices_surface`) in [`MoreScreen.kt`](app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) against [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md).<br>2. Audits [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and [`app/build.gradle.kts`](app/build.gradle.kts) to verify all dependencies are permissive (`MIT`, `Apache-2.0`, `BSD`) with zero copyleft (`GPL`/`AGPL`/`LGPL`) libraries and `includeInApk = false`.<br>3. Verifies `org.suppressingfire.wemi` package/filename hygiene (no `com.example` or `WeMo*.kt` files) and `"WeMo"` trademark boundaries across `strings.xml`, `metadata.json`, and `play_store_assets/`.<br>4. Runs `./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest`.<br>5. Records `SIGN_OFF_LICENSE_GOVERNANCE: SIGNED_OFF`, sets `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`, and pushes to `origin/main`. | [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md), [`.agents/skills/wemi-license-governance/SKILL.md`](.agents/skills/wemi-license-governance/SKILL.md), [`LicenseAndBrandGovernanceTest.kt`](app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt) |
| **Stage 6 (Conclude)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Finalizer)* | 1. Confirms all 5 agents have status `SIGNED_OFF` for the current `ROUND_NUMBER` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).<br>2. Ensures [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt), and [`IMPLEMENTED.md`](IMPLEMENTED.md) include any fixes landed during Stages 2–5, and runs final `compile_applet` + `gradle :app:testDebugUnitTest` verification.<br>3. Runs `bash scripts/advance_release_turn.sh conclude "<summary>"` to set `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA: <sha>`, and `CONCLUDED_AT: <timestamp>` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md). | Final Play Store Release Conclusion |

---

## 2. Zero-Touch Synchronization via GitHub (`RELEASE_SIGN_OFF.md`)

### 2.1 Deterministic State Header in `RELEASE_SIGN_OFF.md`
At the top of [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) is a machine-readable `RELEASE_STATE` block:

```markdown
<!-- RELEASE_STATE_BEGIN -->
RELEASE_VERSION: v21.0
VERSION_CODE: 21
ROUND_NUMBER: 1
RELEASE_STATUS: AWAITING_AI_STUDIO_INITIATION
CURRENT_TURN: ai_studio_ux_cuj
CANDIDATE_BASE_SHA: 64d7660
SIGN_OFF_AI_STUDIO_UX_CUJ: PENDING
SIGN_OFF_DISCOVERY_AND_GROUPING: PENDING
SIGN_OFF_TELEMETRY_AND_DEBUGGING: PENDING
SIGN_OFF_CODE_QUALITY_AND_TESTING: PENDING
SIGN_OFF_LICENSE_GOVERNANCE: PENDING
FINAL_RELEASE_SHA: NONE
CONCLUDED_AT: NONE
<!-- RELEASE_STATE_END -->
```

Valid values for `RELEASE_STATUS`:
- `AWAITING_AI_STUDIO_INITIATION` — Waiting for `ai_studio_ux_cuj` to start the release review round.
- `IN_REVIEW` — Round-robin review is actively progressing through `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing` → `license_governance`.
- `AWAITING_FINAL_CONCLUSION` — All 5 agents have `SIGNED_OFF`; waiting for `ai_studio_ux_cuj` to conclude the release.
- `APPROVED_FOR_PLAY_STORE` — All 5 agents signed off and `ai_studio_ux_cuj` concluded the release.

### 2.2 Autonomous Reactive Wakeup for Antigravity Agents (`scripts/await_release_turn.sh`)
Each of the four Antigravity agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, and `license_governance`) runs inside a persistent Linux VM with Antigravity's **Reactive Wakeup** system (when an asynchronous `run_command` background task completes and exits `0`, Antigravity automatically wakes up the agent conversation without any user prompt).

1. **Arming the Autonomous Watcher**:
   At the end of its turn, each Antigravity agent launches [`scripts/await_release_turn.sh`](scripts/await_release_turn.sh) via `run_command` with `IsDaemon: false` and `WaitMsBeforeAsync: 1000`:
   ```bash
   # Discovery & Room Grouping Agent (Stage 2):
   bash scripts/await_release_turn.sh discovery_and_grouping

   # Telemetry & Debugging Agent (Stage 3):
   bash scripts/await_release_turn.sh telemetry_and_debugging

   # Code Quality & E2E Testing Agent (Stage 4):
   bash scripts/await_release_turn.sh code_quality_and_testing

   # License & Brand Governance Agent (Stage 5):
   bash scripts/await_release_turn.sh license_governance
   ```
2. **Zero-Token Background Polling**:
   - While waiting, `scripts/await_release_turn.sh <role>` polls `git fetch origin main --quiet` every 10 seconds inside the shell process (consuming zero LLM tokens).
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
   - Any self-contained domain fix landed by `discovery_and_grouping` (Stage 2) or `telemetry_and_debugging` (Stage 3) will automatically be compiled and tested against the entire 29-test Robolectric + Compose E2E suite in Stage 4 (`code_quality_and_testing`) and audited for license/package/trademark compliance in Stage 5 (`license_governance`).
   - For scoped domain fixes, the reviewing agent commits the fix, runs its domain tests, and runs `bash scripts/advance_release_turn.sh sign_off <role> "<fix + verification summary>"` to advance forward to the next stage.
2. **Cross-Cutting Fixes Requiring Re-Review (`request_rereview`)**:
   - If an agent in Stages 3–5 discovers a regression that requires an earlier agent in the ring to re-verify its domain (up to `MAX_ROUNDS = 3`), it runs:
     ```bash
     bash scripts/advance_release_turn.sh request_rereview <my_role> <target_role> "<reason for re-review>"
     ```
   - This increments `ROUND_NUMBER`, resets affected downstream sign-offs to `PENDING`, sets `CURRENT_TURN: <target_role>`, and pushes to `origin/main` so `<target_role>` automatically wakes up for the new round.

---

## 4. Quick-Start Instructions for Each Agent

### For Google AI Studio Agent (`ai_studio_ux_cuj`)
> **Environment Note**: The Google AI Studio cloud build container does not mount a local `.git` folder; GitHub pushes and pulls are performed via the AI Studio GitHub Sync UI. `scripts/advance_release_turn.sh` automatically detects when `.git` is absent and updates [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) in-place (`--no-push`).

- **To Start a Release Review (Stage 1)**:
  1. Ensure `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts), [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), and [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt) are updated for the target release (do **not** create a duplicate root `RELEASE_NOTES.md`).
  2. Run `compile_applet` and `gradle :app:testDebugUnitTest` to verify the candidate compiles and passes all unit/E2E tests.
  3. Run:
     ```bash
     bash scripts/advance_release_turn.sh initiate "<UX, accessibility, CUJ & release notes verification summary>"
     ```
     This sets `RELEASE_STATUS: IN_REVIEW`, `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, and `CURRENT_TURN: discovery_and_grouping` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).
  4. Instruct the user to click **Sync / Push to GitHub** in AI Studio so `origin/main` receives Stage 1 and automatically wakes up Stage 2 (`discovery_and_grouping`) → Stage 3 (`telemetry_and_debugging`) → Stage 4 (`code_quality_and_testing`) → Stage 5 (`license_governance`).
- **To Conclude a Release Review (Stage 6)**:
  1. Once the user pulls the completed Stage 2–5 reviews from GitHub (`CURRENT_TURN: ai_studio_ux_cuj` and `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION`, with all 5 `SIGN_OFF_*` fields set to `SIGNED_OFF`), perform a final check of [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), [`IMPLEMENTED.md`](IMPLEMENTED.md), and `compile_applet`.
  2. Run:
     ```bash
     bash scripts/advance_release_turn.sh conclude "<Final release approval summary>"
     ```
     This sets `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA`, and `CONCLUDED_AT` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).
  3. Instruct the user to push the final concluded ledger to GitHub and publish to the Google Play Store.

### For Antigravity Specialist Agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, `license_governance`)
1. Always keep `bash scripts/await_release_turn.sh <your_role>` running as a background task (`run_command` with `IsDaemon: false`, `WaitMsBeforeAsync: 1000`).
2. When woken up by `await_release_turn.sh`:
   - Run `git pull --rebase origin main`.
   - Execute your stage's review checklist and automated tests from Section 1 above.
   - Run `bash scripts/advance_release_turn.sh sign_off <your_role> "<summary of checks & tests passed>"` (which updates `RELEASE_SIGN_OFF.md`, restores `100755` on `gradlew`, commits, rebases, and pushes to `origin/main`).
   - Re-launch `bash scripts/await_release_turn.sh <your_role>` in the background before finishing your turn.
