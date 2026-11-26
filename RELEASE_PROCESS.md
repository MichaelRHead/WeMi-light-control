# WeMi Light Control — 6-Agent / 7-Stage Autonomous Release Process (`RELEASE_PROCESS.md`)

This document defines the **6-Agent / 7-Stage Autonomous Release Review & Play Store Promotion Process** for **WeMi Light Control** (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`).

All six specialized agents synchronize **entirely through files checked into GitHub (`origin/main`)** — specifically the live state machine in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) and the event-driven promotion workflow in [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) — without requiring manual human handoffs between stages.

---

## 1. Participating Agents & 7-Stage Release Ring

Every Google Play Store release candidate traverses a strict 6-stage review ring where the **Google AI Studio Agent (`ai_studio_ux_cuj`)** initiates the release review in **Stage 1** and concludes it in **Stage 6** once all four Antigravity specialist agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, and `license_governance`) have signed off. Once AI Studio uploads the approved release binary to Google Play **Internal testing** and pushes the concluded `RELEASE_SIGN_OFF.md` (`APPROVED_FOR_PLAY_STORE`) or `app/build.gradle.kts` version bump to `main`, **Stage 7 (`app_promoter`)** automatically promotes the internal build to **Closed testing (`alpha`)** via GitHub Actions and updates [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md):

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
                                               │
            AI Studio uploads .aab to Play `internal` + pushes RELEASE_SIGN_OFF.md / version bump
                                               ▼
                             ┌───────────────────────────────────┐
                             │       STAGE 7 (Event-Driven)      │
                             │  Antigravity App Promoter Agent   │
                             │   (`app_promoter` / GH Actions)   │
                             │  Promotes `internal` -> `alpha`   │
                             │   (PROMOTED_TO_CLOSED_TESTING)    │
                             └───────────────────────────────────┘
```

| Stage | Agent Role ID (`CURRENT_TURN`) | Agent Name | Release Review / Promotion Mandate & Verification Gate | Exclusively Owned Files |
| :---: | :--- | :--- | :--- | :--- |
| **Stage 1 (Initiate)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Initiator & Finalizer)* | 1. Verifies `applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `namespace = "org.suppressingfire.wemi"`, and incremented `versionCode` / `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts).<br>2. **Obsolete UI & UX Cleanup Audit**: Proactively audits all phone, tablet, dialog, drawer, widget, and Android TV screens for obsolete/no-op toggles, leftover dev/asset-showcase cards, redundant destructive actions, un-gated demo presets in Real LAN Mode, stale developer copy, and context-irrelevant controls (e.g., conditional scenes), removing or polishing them before sign-off.<br>3. Audits UX flows, the 5 Compose Layout Invariants (`AutoResizingDeviceNameText`, 3-state hardware icons with `#23272F` standby `I/O` & red Disconnected ring, uniform `68.dp` room cards, top-first dimmer drawer, non-blocking toast), 48dp+ touch targets, TalkBack semantics, Back navigation, and Critical User Journeys against [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).<br>4. Finalizes user-facing release notes in [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) (plus [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt) <= 500 chars and [`play_store_assets/STORE_LISTING.md`](play_store_assets/STORE_LISTING.md)) and runs `compile_applet` + `gradle :app:testDebugUnitTest`.<br>5. Runs `bash scripts/advance_release_turn.sh initiate "<summary>"` to update [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) (`RELEASE_STATUS: IN_REVIEW`, `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, `CURRENT_TURN: discovery_and_grouping`), ready for GitHub sync to `origin/main`. | [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) |
| **Stage 2 (Review 1)** | `discovery_and_grouping` | **Antigravity Device Discovery & Room Grouping Agent** | 1. Audits SSDP `M-SEARCH` & `/24` unicast discovery ([`SsdpDiscoveryEngine.kt`](app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt), [`DiscoveryCoordinator.kt`](app/src/main/java/org/suppressingfire/wemi/data/DiscoveryCoordinator.kt)).<br>2. Verifies 3-tier hardware identity matching (`DeviceEntity.matchesDiscovered` / `matchesDiscoveredDevice`: `<serialNumber>` → `<UDN>`/`USN` → `(ipAddress, port)`/`<friendlyName>`).<br>3. Verifies `CamelCase` normalization (`normalizeDeviceNameForInference`), specificity-ordered room inference (`inferRoomFromName`: `"Ensuite"` before `"Bedroom"`), non-destructive migration (`resolveDeviceRoomAssignment`), Belkin soft-AP SSID filtering (`isWemoDeviceSetupSsid`), and live Step 2 on-device hardware rules capability detection, in-place `rules.db` mutation, automatic clock sync, solar `LOCATIONINFO`, backup restore & anti-brick validation ([`HardwareRulesClient.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesClient.kt), [`HardwareRulesWriteEngine.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesWriteEngine.kt), [`HardwareRulesDbMutator.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbMutator.kt), [`HardwareRulesDbCodec.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbCodec.kt), [`HardwareRulesSafetyValidator.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesSafetyValidator.kt), [`HardwareRulesCoordinator.kt`](app/src/main/java/org/suppressingfire/wemi/data/HardwareRulesCoordinator.kt)).<br>4. Runs discovery, room-grouping, ChromeOS & hardware-rules tests (`sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.ExampleUnitTest --tests org.suppressingfire.wemi.SkuAndCryptoTest --tests org.suppressingfire.wemi.SoapAndSelfHealingTest --tests org.suppressingfire.wemi.RepositorySelfHealingTest --tests org.suppressingfire.wemi.DiscoveryAndGroupingIntegrationTest --tests org.suppressingfire.wemi.ChromeOsArcVmDiscoveryTest --tests org.suppressingfire.wemi.HardwareRulesTest --tests org.suppressingfire.wemi.HardwareRulesMutationTest --tests org.suppressingfire.wemi.HardwareRulesE2ETest`).<br>5. Records `SIGN_OFF_DISCOVERY_AND_GROUPING: SIGNED_OFF`, sets `CURRENT_TURN: telemetry_and_debugging`, and pushes to `origin/main`. | [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md), [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) |
| **Stage 3 (Review 2)** | `telemetry_and_debugging` | **Antigravity Telemetry & Debugging Agent** | 1. Audits `10.22.22.1` zero-cloud provisioning diagnostics ([`SetupDiagnostics.kt`](app/src/main/java/org/suppressingfire/wemi/network/SetupDiagnostics.kt), [`ProvisionerClient.kt`](app/src/main/java/org/suppressingfire/wemi/network/ProvisionerClient.kt), [`SetupApProber.kt`](app/src/main/java/org/suppressingfire/wemi/network/SetupApProber.kt), [`ProvisionDebugPanel.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionDebugPanel.kt)).<br>2. Verifies the `[NET]`, `[PROBE]`, `[FLAGS]`, `[TARGET AP]`, `[STEP 1..6]` screenshot card, Live SOAP Packet Log inspector ([`MoreScreen.kt`](app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) & [`MoreControlAndLogCards.kt`](app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreControlAndLogCards.kt)), Inline Switch Diagnostics Drawer ([`DeviceDiagnosticsDrawer.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceDiagnosticsDrawer.kt)), and `BuildConfig`-version-stamped `wemi-telemetry-latest.txt` export (`TelemetryFileExporter`).<br>3. Verifies zero plaintext/ciphertext Wi-Fi password leakage (`plainLen`/`encLen` only) and cloud-backup exclusion ([`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml) & [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml)).<br>4. **Documentation Audit & User-Guide Review ([`wemi-doc-audit-and-review`](.agents/skills/wemi-doc-audit-and-review/SKILL.md))**: Audits [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/README.md`](docs/README.md), [`docs/user-guide/*.md`](docs/user-guide/), owned telemetry docs, [`play_store_assets/DATA_SAFETY_GUIDE.md`](play_store_assets/DATA_SAFETY_GUIDE.md), and editable shared skills against live release code for stale behaviors/labels, missing major-feature guides, plain-English clarity, and broken relative links; updates editable & owned docs while reporting any drift in peer-owned read-only specs without modifying them.<br>5. Runs provisioning, telemetry, governance & Play asset validation (`sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest --tests org.suppressingfire.wemi.ProvisioningAndGenaCujE2ETest --tests org.suppressingfire.wemi.TelemetryDiagnosticsTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest` and `PYTHONDONTWRITEBYTECODE=1 python3 -W default scripts/sync_play_assets.py validate-local`).<br>6. Records `SIGN_OFF_TELEMETRY_AND_DEBUGGING: SIGNED_OFF`, sets `CURRENT_TURN: code_quality_and_testing`, and pushes to `origin/main`. | [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`.agents/skills/wemi-doc-audit-and-review/SKILL.md`](.agents/skills/wemi-doc-audit-and-review/SKILL.md) *(Stage 3 review skill)* |
| **Stage 4 (Review 3)** | `code_quality_and_testing` | **Antigravity Code Quality & End-to-End Testing Agent** | 1. Audits human readability, single-responsibility modularity, and file line budgets against [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md).<br>2. Verifies Wi-Fi socket binding (`wifiSocketFactory`), coroutine/GENA/SQLite lifecycle cleanup, and non-executable (`100644`) git modes on [`gradlew`](gradlew) & scripts (invoked via `sh`/`bash`/`python3`).<br>3. Executes the full mandatory verification gate (`sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug`) covering all unit, UPnP/SOAP/GENA protocol, hardware-rules, repository self-healing, widget, telemetry, governance, ChromeOS ARCVM, and full-stack Compose + Robolectric + [`FakeUpnpServer.kt`](app/src/test/java/org/suppressingfire/wemi/FakeUpnpServer.kt) End-to-End UI tests (`134` passing across `26` suites).<br>4. Records `SIGN_OFF_CODE_QUALITY_AND_TESTING: SIGNED_OFF`, sets `CURRENT_TURN: license_governance`, and pushes to `origin/main`. | [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md), [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) |
| **Stage 5 (Review 4)** | `license_governance` | **Antigravity License & Brand Governance Agent** | 1. Audits root [`LICENSE`](LICENSE) (**MIT License**) parity with the in-app Open Source License card (`open_source_licenses_card`, `mit_license_text_surface`, and `third_party_notices_surface`) in [`MoreScreen.kt`](app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) against [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md).<br>2. Audits [`gradle/libs.versions.toml`](gradle/libs.versions.toml) and [`app/build.gradle.kts`](app/build.gradle.kts) to verify all dependencies are permissive (`MIT`, `Apache-2.0`, `BSD`) with zero copyleft (`GPL`/`AGPL`/`LGPL`) libraries and `includeInApk = false`.<br>3. Verifies `org.suppressingfire.wemi` package/filename hygiene (no `com.example` or `WeMo*.kt` files) and `"WeMo"` trademark boundaries across `strings.xml`, `metadata.json`, and `play_store_assets/`.<br>4. Runs `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest`.<br>5. Records `SIGN_OFF_LICENSE_GOVERNANCE: SIGNED_OFF`, sets `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`, and pushes to `origin/main`. | [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md), [`.agents/skills/wemi-license-governance/SKILL.md`](.agents/skills/wemi-license-governance/SKILL.md), [`LicenseAndBrandGovernanceTest.kt`](app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt) |
| **Stage 6 (Conclude)** | `ai_studio_ux_cuj` | **Google AI Studio Agent** *(Finalizer)* | 1. Confirms all 5 agents have status `SIGNED_OFF` for the current `ROUND_NUMBER` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).<br>2. Ensures [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt), and [`IMPLEMENTED.md`](IMPLEMENTED.md) include any fixes landed during Stages 2–5, and runs final `compile_applet` + `gradle :app:testDebugUnitTest` verification.<br>3. Runs `bash scripts/advance_release_turn.sh conclude "<summary>"` to set `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA: <sha>`, and `CONCLUDED_AT: <timestamp>` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).<br>4. Pushes the concluded ledger to GitHub, uploads the signed `.aab` to Google Play **Internal testing**, and commits any resulting `app/build.gradle.kts` version bump to `main` (either the `RELEASE_SIGN_OFF.md` conclusion push or the `app/build.gradle.kts` bump triggers Stage 7 automatically). | Final Play Store Release Conclusion & Internal Upload |
| **Stage 7 (Promote)** | `app_promoter` | **Antigravity App Promoter Agent** *(Event-Driven GitHub Actions)* | 1. Triggers automatically on `push` to `main` touching [`app/build.gradle.kts`](app/build.gradle.kts) or [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) via [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) (zero Antigravity LLM/VM polling cost; also supports manual `workflow_dispatch` fallback).<br>2. Enforces 3 safety gates in [`scripts/promote_play_release.py`](scripts/promote_play_release.py) (`auto-promote`): (a) when triggered solely by `app/build.gradle.kts`, `versionCode` actually increased, (b) `RELEASE_SIGN_OFF.md` has `RELEASE_STATUS == APPROVED_FOR_PLAY_STORE`, and (c) Google Play `internal` track holds a newer build than Closed testing (`alpha`) matching `current_version_code`, `previous_version_code`, or `RELEASE_SIGN_OFF.md` `VERSION_CODE` (polling briefly up to `5 × 20s` for Play Console processing).<br>3. Promotes the verified `internal` build to Closed testing (`alpha`, status `completed`), carrying `play_store_assets/RELEASE_NOTES.txt` (`en-US`) when the internal release has no notes (with `sync_store_assets=false` by default so promotion and asset edits do not collide in Play review).<br>4. Runs `bash scripts/advance_release_turn.sh promote "<summary>" --version-code "<vc>" --from-track "<from>" --track "<to>"` to set `RELEASE_STATUS` (`PROMOTED_TO_CLOSED_TESTING` for `alpha`, or track-derived status), `SIGN_OFF_APP_PROMOTER: PROMOTED`, `PROMOTED_VERSION_CODE`, `PROMOTED_TRACK`, and `PROMOTED_AT` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), committing with `Agent: Antigravity App Promoter Agent (app_promoter)` and pushing to `origin/main`.<br>5. **Post-Rollout Asset Sync & Notification:** Waits via `mode=release-status` until `alpha` reaches `RELEASE_LIFECYCLE_STATE_PUBLISHED`, *then* runs [`scripts/sync_play_assets.py`](scripts/sync_play_assets.py) (`mode=sync-assets`) to idempotently sync changed Store Listing copy (`play_store_assets/STORE_LISTING.md`) and all 7 PNG slots (`16` PNGs), verifies `mode=diff-assets` (`IN SYNC`) and post-sync rollout (`RELEASE_LIFECYCLE_STATE_PUBLISHED`), and only then sends the `[Antigravity Agent]` completion email. | [`.agents/skills/wemi-app-promoter/SKILL.md`](.agents/skills/wemi-app-promoter/SKILL.md), [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml), [`scripts/promote_play_release.py`](scripts/promote_play_release.py), [`scripts/test_promote_play_release.py`](scripts/test_promote_play_release.py), [`scripts/sync_play_assets.py`](scripts/sync_play_assets.py), [`scripts/test_sync_play_assets.py`](scripts/test_sync_play_assets.py) |

---

## 2. Zero-Touch Synchronization via GitHub (`RELEASE_SIGN_OFF.md`)

### 2.1 Deterministic State Header in `RELEASE_SIGN_OFF.md`
At the top of [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) is a machine-readable `RELEASE_STATE` block:

```markdown
<!-- RELEASE_STATE_BEGIN -->
RELEASE_VERSION: v28.0
VERSION_CODE: 28
ROUND_NUMBER: 1
RELEASE_STATUS: PROMOTED_TO_CLOSED_TESTING
CURRENT_TURN: NONE
CANDIDATE_BASE_SHA: v28.0-rc
SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF (v28.0-rc @ 2026-11-18 23:24 PST)
SIGN_OFF_DISCOVERY_AND_GROUPING: SIGNED_OFF (5d2ef7a @ 2026-11-19 00:30 PST)
SIGN_OFF_TELEMETRY_AND_DEBUGGING: SIGNED_OFF (609e8e8 @ 2026-11-19 00:38 PST)
SIGN_OFF_CODE_QUALITY_AND_TESTING: SIGNED_OFF (6be607d @ 2026-11-19 00:43 PST)
SIGN_OFF_LICENSE_GOVERNANCE: SIGNED_OFF (672083b @ 2026-11-19 00:47 PST)
SIGN_OFF_APP_PROMOTER: PROMOTED (vc=29 internal -> alpha @ 2026-11-19 02:38 PST)
FINAL_RELEASE_SHA: v28.0-rc
CONCLUDED_AT: 2026-11-19 01:04 PST
PROMOTED_VERSION_CODE: 29
PROMOTED_TRACK: alpha
PROMOTED_AT: 2026-11-19 02:38 PST
<!-- RELEASE_STATE_END -->
```

Valid values for `RELEASE_STATUS`:
- `AWAITING_AI_STUDIO_INITIATION` — Waiting for `ai_studio_ux_cuj` to start the release review round.
- `IN_REVIEW` — Round-robin review is actively progressing through `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing` → `license_governance`.
- `AWAITING_FINAL_CONCLUSION` — All 5 review agents have `SIGNED_OFF`; waiting for `ai_studio_ux_cuj` to conclude the release.
- `APPROVED_FOR_PLAY_STORE` — Stages 1–6 approved and concluded; ready for AI Studio Play `internal` upload + `app/build.gradle.kts` version bump to trigger Stage 7 (`app_promoter`).
- `PROMOTED_TO_CLOSED_TESTING` — Stage 7 (`app_promoter`) promoted the Play `internal` build to Closed testing (`alpha`) and closed the release cycle (guards against accidental promotion of future unreviewed version bumps until the next Stages 1–6 cycle reaches `APPROVED_FOR_PLAY_STORE`).

### 2.2 Quota-Safe Reactive Wakeup for Antigravity Agents (`scripts/await_release_turn.sh`)
Each of the four Antigravity review agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, and `license_governance`) runs inside a Linux VM with Antigravity's **Reactive Wakeup** system. Stage 7 (`app_promoter`) is **100% event-driven in GitHub Actions** and never polls. Because non-daemon `run_command` tasks hit `exebox`'s `~9m50s` RPC ceiling and wake the full LLM conversation on exit, **watchers must never run `24/7` between releases or after a stage has signed off**:

1. **Arming Only While `IN_REVIEW` & `PENDING`**:
   During an active `RELEASE_STATUS: IN_REVIEW` round while its own `SIGN_OFF_<ROLE>` is still `PENDING`, an upstream-waiting Antigravity specialist launches [`scripts/await_release_turn.sh`](scripts/await_release_turn.sh) (`IsDaemon: false`, `WaitMsBeforeAsync: 5000`, `15`s poll interval, `510`s exebox-safe wait window, capped at `MAX_IDLE_REARMS=2` / `~25` min max):
   ```bash
   bash scripts/await_release_turn.sh <role> 15 510
   ```
2. **Immediate Quota-Guard Short-Circuits (`<1s` Synchronous Exit)**:
   - When `RELEASE_STATUS` is `APPROVED_FOR_PLAY_STORE`, `PROMOTED_TO_CLOSED_TESTING`, or `AWAITING_AI_STUDIO_INITIATION` (`CURRENT_TURN: NONE`), when `<role>` has already `SIGNED_OFF` the current `(RELEASE_VERSION, ROUND_NUMBER)`, or when invoked for event-driven `app_promoter`, `await_release_turn.sh` exits immediately with `[QUOTA GUARD] DO NOT RE-ARM` so idle sessions never loop in the background.
   - While actively waiting during `IN_REVIEW`, `scripts/await_release_turn.sh` polls `git fetch origin main --quiet` every 15 seconds (including Stage 2 fallback detection when `ai_studio_ux_cuj` marks `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`).
3. **Automatic Agent Wakeup, Review, Handoff & Clean Stop (`scripts/advance_release_turn.sh`)**:
   - As soon as `CURRENT_TURN` matches `<role>`, `scripts/await_release_turn.sh` prints the release state summary and **exits `0`**, waking that agent's conversation.
   - The agent runs `git pull --rebase origin main`, executes its domain review and tests, and advances the state machine using [`scripts/advance_release_turn.sh`](scripts/advance_release_turn.sh) with a short plain-English summary:
     ```bash
     bash scripts/advance_release_turn.sh sign_off <role> "<short plain-English summary>" --verified "<brief checks>"
     ```
   - `scripts/advance_release_turn.sh` updates [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), formats a concise human-readable commit with `Agent: <Canonical Agent Name> (<role_id>)` per [`.agents/skills/agent-commit-attribution/SKILL.md`](.agents/skills/agent-commit-attribution/SKILL.md), rebases against `origin/main`, and pushes to GitHub.
   - **Clean Stop**: Once `sign_off` succeeds, the agent **ends its turn without re-arming `await_release_turn.sh`**, and recommends rotating to a fresh conversation if `/home/antigravity/.gemini/usage/agy-usage --check-self` reports `>= 400` steps.

---

## 3. Handling Fixes & Re-Reviews During the Round-Robin

1. **Scoped Domain Fixes (Default — Continue Forward)**:
   - Any self-contained domain fix landed by `discovery_and_grouping` (Stage 2) or `telemetry_and_debugging` (Stage 3) will automatically be compiled and tested against the entire `134`-test (`26`-suite) unit, hardware-rules, widget, telemetry, ChromeOS ARCVM, Robolectric, and Compose E2E suite in Stage 4 (`code_quality_and_testing`) and audited for license/package/trademark compliance in Stage 5 (`license_governance`).
   - For scoped domain fixes, the reviewing agent commits the fix in plain English using `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh <role> "<plain-English subject <= 72 chars>" "<short plain-English bullets>" --verified "<brief checks>"`, runs its domain tests, and runs `bash scripts/advance_release_turn.sh sign_off <role> "<short plain-English summary>" --verified "<brief checks>"` to advance forward to the next stage.
2. **Cross-Cutting Fixes Requiring Re-Review (`request_rereview`)**:
   - If an agent in Stages 3–5 discovers a regression that requires an earlier agent in the ring to re-verify its domain (up to `MAX_ROUNDS = 3`), it runs:
     ```bash
     bash scripts/advance_release_turn.sh request_rereview <my_role> <target_role> "<reason for re-review>"
     ```
   - This increments `ROUND_NUMBER`, resets affected downstream sign-offs (including Stage 7) to `PENDING`, sets `CURRENT_TURN: <target_role>`, and pushes to `origin/main`.

---

## 4. Quick-Start Instructions for Each Agent

### For Google AI Studio Agent (`ai_studio_ux_cuj`)
> **Environment Note**: The Google AI Studio cloud build container does not mount a local `.git` folder; GitHub pushes and pulls are performed via the AI Studio GitHub Sync UI. `scripts/advance_release_turn.sh` automatically detects when `.git` is absent and updates [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) in-place (`--no-push`).

- **To Start a Release Review (Stage 1)**:
  1. Perform a full **Obsolete UI & UX Cleanup Audit** across phone, tablet, dialog, drawer, widget, and Android TV screens (removing dead/no-op toggles, leftover dev/asset cards, redundant destructive controls, un-gated demo presets in Real LAN Mode, stale developer copy, and context-irrelevant UI controls) and verify all 5 Compose Layout Invariants and CUJs against [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).
  2. Ensure `versionCode` and `versionName` in [`app/build.gradle.kts`](app/build.gradle.kts), [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), and [`play_store_assets/RELEASE_NOTES.txt`](play_store_assets/RELEASE_NOTES.txt) are updated for the target release (do **not** create a duplicate root `RELEASE_NOTES.md`).
  3. Run `compile_applet` and `gradle :app:testDebugUnitTest` to verify the candidate compiles and passes all unit/E2E tests.
  4. Run:
     ```bash
     bash scripts/advance_release_turn.sh initiate "<UX, accessibility, CUJ & release notes verification summary>"
     ```
     This sets `RELEASE_STATUS: IN_REVIEW`, `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, and `CURRENT_TURN: discovery_and_grouping` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).
  5. Instruct the user to click **Sync / Push to GitHub** in AI Studio so `origin/main` receives Stage 1 and activates Stage 2 (`discovery_and_grouping`) → Stage 3 (`telemetry_and_debugging`) → Stage 4 (`code_quality_and_testing`) → Stage 5 (`license_governance`).
- **To Conclude a Release Review & Trigger Automated Promotion (Stage 6 → Stage 7)**:
  1. Once the user pulls the completed Stage 2–5 reviews from GitHub (`CURRENT_TURN: ai_studio_ux_cuj` and `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION`, with all 5 review `SIGN_OFF_*` fields set to `SIGNED_OFF`), perform a final check of [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), [`IMPLEMENTED.md`](IMPLEMENTED.md), and `compile_applet`.
  2. Run:
     ```bash
     bash scripts/advance_release_turn.sh conclude "<Final release approval summary>"
     ```
     This sets `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA`, and `CONCLUDED_AT` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).
  3. Sync the concluded ledger to GitHub, then publish the release binary from AI Studio to Google Play **Internal testing** and push any resulting `app/build.gradle.kts` version bump to `main` — Stage 7 (`app_promoter`) will automatically promote it to **Closed testing (`alpha`)** and update [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md).

### For Antigravity Review Agents (`discovery_and_grouping`, `telemetry_and_debugging`, `code_quality_and_testing`, `license_governance`)
1. **Only while an active release round is `IN_REVIEW` and your stage is `PENDING`**, arm `bash scripts/await_release_turn.sh <your_role> 15 510` (`WaitMsBeforeAsync: 5000`). Never poll between releases (`APPROVED_FOR_PLAY_STORE` / `PROMOTED_TO_CLOSED_TESTING`) or after signing off your stage.
2. When `await_release_turn.sh` activates your turn:
   - Run `git pull --rebase origin main`.
   - Execute your stage's review checklist and automated tests from Section 1 above.
   - Run `bash scripts/advance_release_turn.sh sign_off <your_role> "<summary of checks & tests passed>"`.
   - **Stop cleanly** without re-arming `await_release_turn.sh`, and start a fresh specialist conversation before the next release if your session exceeds `~400` steps.

### For Antigravity App Promoter Agent (`app_promoter`) — Stage 7 Event-Driven Automation & Asset Sync
See [`.agents/skills/wemi-app-promoter/SKILL.md`](.agents/skills/wemi-app-promoter/SKILL.md) for the complete asset mapping and playbook.
1. **Automatic Zero-Quota Execution**: Whenever `app/build.gradle.kts` or `RELEASE_SIGN_OFF.md` is pushed to `main`, [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) runs `scripts/promote_play_release.py auto-promote` automatically in GitHub Actions (never run `scripts/await_release_turn.sh` for `app_promoter`).
2. **Built-In Safety Gates & Two-Phase Post-Rollout Asset Sync**:
   - Skips cleanly (exit `0`) if triggered solely by `app/build.gradle.kts` without a `versionCode` increase, if `RELEASE_SIGN_OFF.md` is not in `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, or if Closed testing (`alpha`) already serves the latest `internal` build.
   - **Phase 1 (Binary Promotion):** Polls Google Play `internal` briefly (`5 × 20s`) in case the uploaded `.aab` is still finishing processing, promotes `internal` → `alpha` (attaching `play_store_assets/RELEASE_NOTES.txt` if the internal release has no notes, with `sync_store_assets=false` by default), and runs `scripts/advance_release_turn.sh promote` to record `PROMOTED_TO_CLOSED_TESTING` in [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md). Watch `mode=release-status` until `alpha` reaches `RELEASE_LIFECYCLE_STATE_PUBLISHED`.
   - **Phase 2 (Post-Rollout Asset Sync & Notification):** Only *after* Closed testing rollout finishes (`RELEASE_LIFECYCLE_STATE_PUBLISHED`), run [`scripts/sync_play_assets.py`](scripts/sync_play_assets.py) (`mode=sync-assets`) to idempotently sync changed Store Listing copy (`play_store_assets/STORE_LISTING.md`, `<= 30 / 80 / 4000` chars) and all 7 Play Console PNG slots (`16` PNGs) using SHA-256/SHA-1 diffing (requires `Manage store presence` in Play Console; skips writes when already in sync). Confirm `mode=diff-assets` (`IN SYNC`) and post-sync rollout (`RELEASE_LIFECYCLE_STATE_PUBLISHED`) before sending the completion email.
3. **Manual Fallback, Track Inspection & Post-Rollout Asset Sync (`workflow_dispatch`)**:
   - Validate local listing limits and PNGs offline: `PYTHONDONTWRITEBYTECODE=1 python3 scripts/sync_play_assets.py validate-local`
   - Inspect live Play tracks read-only: `gh workflow run play-store-promote.yml -f mode=list-tracks`
   - Watch live Play rollout status read-only: `gh workflow run play-store-promote.yml -f mode=release-status -f to_track=alpha -f version_code=<vc>`
   - Compare repo `play_store_assets/` against live Play Console read-only: `gh workflow run play-store-promote.yml -f mode=diff-assets`
   - Sync `play_store_assets/` after Closed testing rollout completes: `gh workflow run play-store-promote.yml -f mode=sync-assets`
   - Re-run guarded Stage 7 binary promotion if Play upload finished late: `gh workflow run play-store-promote.yml -f mode=auto-promote`
   - Promote manually to any track (e.g., custom closed track, `beta`, or `production`): `gh workflow run play-store-promote.yml -f mode=promote -f from_track=internal -f to_track=alpha`
