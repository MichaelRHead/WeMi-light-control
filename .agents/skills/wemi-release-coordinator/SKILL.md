---
name: wemi-release-coordinator
description: 7-agent / 8-stage release review and Play Store promotion coordinator via RELEASE_SIGN_OFF.md, await_release_turn.sh, and advance_release_turn.sh.
---

# WeMi 7-Agent / 8-Stage Autonomous Release Coordinator (`wemi-release-coordinator`)

Use this skill whenever preparing, reviewing, advancing, monitoring, or promoting a Google Play Store release in `MichaelRHead/WeMi-light-control`.

---

## 1. The 7-Agent / 8-Stage Release Ring

Every Play Store release candidate traverses 7 review stages synchronized via [`RELEASE_SIGN_OFF.md`](../../../RELEASE_SIGN_OFF.md) on `origin/main`, followed by event-driven Stage 8 promotion to Google Play Closed testing (`alpha`):

1. **Stage 1 (Initiate) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Verifies `versionCode` / `versionName` and `namespace = "org.suppressingfire.wemi"` in `app/build.gradle.kts`, performs a **Stage 1 Obsolete UI & UX Cleanup Audit** across all phone, tablet, dialog, drawer, widget, and Android TV surfaces (removing dead/no-op toggles, leftover dev cards, redundant destructive controls, un-gated demo presets in Real LAN Mode, stale developer copy, and context-irrelevant UI controls), audits UX/accessibility/navigation/CUJs (`UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`), updates `docs/user-guide/release-notes.md` & `play_store_assets/RELEASE_NOTES.txt` (do not create a root `RELEASE_NOTES.md`), runs `bash scripts/advance_release_turn.sh initiate "<summary>"` (recording `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, `RELEASE_STATUS: IN_REVIEW`, `CURRENT_TURN: discovery_and_grouping`), and syncs to `origin/main`.
2. **Stage 2 (Review 1) — `discovery_and_grouping` (Antigravity Device Discovery & Room Grouping Agent)**:
   - Audits SSDP/unicast discovery (`SsdpDiscoveryEngine.kt`, `DiscoveryCoordinator.kt`), Household Config Sync identity dedup & LAN discovery (`HouseholdSyncCoordinator.kt`, `HouseholdSyncCodec.kt`, `HouseholdSyncLanClient.kt`, `HouseholdSyncLanServer.kt`), 3-tier hardware identity matching (`DeviceEntity.matchesDiscovered` / `matchesDiscoveredDevice`), soft-AP SSID filtering (`isWemoDeviceSetupSsid`), `CamelCase` / specificity-ordered room inference (`normalizeDeviceNameForInference`, `inferRoomFromName`, `resolveDeviceRoomAssignment` in `DeviceEntities.kt` & `RoomAndCategoryRules.kt`), and live Step 2 on-device hardware rules capability detection, in-place `rules.db` mutation, automatic clock sync, solar `LOCATIONINFO`, backup restore & anti-brick validation (`HardwareRulesClient.kt`, `HardwareRulesWriteEngine.kt`, `HardwareRulesDbMutator.kt`, `HardwareRulesDbCodec.kt`, `HardwareRulesSafetyValidator.kt`, `HardwareRulesCoordinator.kt`).
   - Runs `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.ExampleUnitTest --tests org.suppressingfire.wemi.SkuAndCryptoTest --tests org.suppressingfire.wemi.SoapAndSelfHealingTest --tests org.suppressingfire.wemi.RepositorySelfHealingTest --tests org.suppressingfire.wemi.DiscoveryAndGroupingIntegrationTest --tests org.suppressingfire.wemi.ChromeOsArcVmDiscoveryTest --tests org.suppressingfire.wemi.HardwareRulesTest --tests org.suppressingfire.wemi.HardwareRulesMutationTest --tests org.suppressingfire.wemi.HardwareRulesE2ETest --tests org.suppressingfire.wemi.HouseholdSyncTest --tests org.suppressingfire.wemi.HouseholdSyncE2ETest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off discovery_and_grouping "<verification summary>"` (setting `CURRENT_TURN: telemetry_and_debugging`).
3. **Stage 3 (Review 2) — `telemetry_and_debugging` (Antigravity Telemetry & Debugging Agent)**:
   - Audits `10.22.22.1` zero-cloud provisioning diagnostics (`SetupDiagnostics.kt`, `TelemetryRedactor.kt`, `ProvisionerClient.kt`, `SetupApProber.kt`, `ProvisionDebugPanel.kt`), live SOAP packet log export (`MoreScreen.kt` & `MoreControlAndLogCards.kt`), unified inline switch diagnostics (`DeviceDiagnosticsDrawer.kt`), `BuildConfig`-version-stamped privacy-redacted `wemi-telemetry-latest.txt` export (`TelemetryFileExporter`), cloud-backup exclusion (`backup_rules.xml` & `data_extraction_rules.xml`), and zero Wi-Fi password leakage (`plainLen`/`encLen` only).
   - Performs a **Documentation Audit & User-Guide Review** via [`wemi-doc-audit-and-review`](../wemi-doc-audit-and-review/SKILL.md): audits `USER_GUIDE.md`, `docs/README.md`, `docs/user-guide/*.md`, owned telemetry docs, `play_store_assets/DATA_SAFETY_GUIDE.md`, `play_store_assets/PRIVACY_POLICY.md`, and editable shared skills against live code for stale behavior/labels, missing major-feature guides, plain-English readability, and broken relative links, updating editable/owned docs and reporting any drift in peer-owned read-only specs without editing them.
   - Runs `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest --tests org.suppressingfire.wemi.ProvisioningAndGenaCujE2ETest --tests org.suppressingfire.wemi.TelemetryDiagnosticsTest --tests org.suppressingfire.wemi.SecurityAndPrivacyHardeningTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest` and `PYTHONDONTWRITEBYTECODE=1 python3 -W default scripts/sync_play_assets.py validate-local`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off telemetry_and_debugging "<verification summary>"` (setting `CURRENT_TURN: security_and_privacy`).
4. **Stage 4 (Review 3) — `security_and_privacy` (Antigravity Security & Privacy Agent; see [`wemi-security-and-privacy`](../wemi-security-and-privacy/SKILL.md) & [`security-and-privacy`](../security-and-privacy/SKILL.md))**:
   - Audits Android manifest permissions, exported IPC components (`AndroidManifest.xml`, `widget/`), `PendingIntent` safety, `FileProvider` scopes, local-LAN SSRF/CRLF/DoS/GENA spoofing & Household Sync PAKE/crypto bounds (`LanHttpClient.kt`, `LanEndpointPolicy.kt`, `SsdpDiscoveryEngine.kt`, `GenaSubscriptionManager.kt`, `HouseholdSyncLanServer.kt`, `HouseholdSyncLanClient.kt`, `HouseholdSyncCrypto.kt`), untrusted XML/ZIP/JSON/SQLite `rules.db` parsing (`StrictJsonCodec.kt`, `HardwareRulesDbCodec.kt`, `HardwareRulesDbMutator.kt`, `HardwareRulesSafetyValidator.kt`, `HardwareRulesBackupStore.kt`), Wi-Fi credential lifecycle (`ProvisionApDialog.kt`, `PasswordCrypto.kt`), backup/Privacy-Policy/Data-Safety parity (`backup_rules.xml`, `data_extraction_rules.xml`, `PRIVACY_POLICY.md`, `DATA_SAFETY_GUIDE.md`), telemetry export minimization & reset erasure (`SetupDiagnostics.kt`, `TelemetryRedactor.kt`, `SwitchRepository.kt`), and CI/CD secret & supply-chain security (`.github/workflows/play-store-promote.yml`) against [`SECURITY_AND_PRIVACY.md`](../../../SECURITY_AND_PRIVACY.md).
   - Runs `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.ExampleUnitTest --tests org.suppressingfire.wemi.SkuAndCryptoTest --tests org.suppressingfire.wemi.RuleAndTransportTest --tests org.suppressingfire.wemi.HardwareRulesTest --tests org.suppressingfire.wemi.HardwareRulesMutationTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest --tests org.suppressingfire.wemi.TelemetryDiagnosticsTest --tests org.suppressingfire.wemi.SecurityAndPrivacyHardeningTest --tests org.suppressingfire.wemi.HouseholdSyncTest --tests org.suppressingfire.wemi.HomeScreenWidgetTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest` and `PYTHONDONTWRITEBYTECODE=1 python3 -W default -m unittest discover -s scripts -p "test_*.py" -v`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off security_and_privacy "<verification summary>"` (setting `CURRENT_TURN: code_quality_and_testing`).
5. **Stage 5 (Review 4) — `code_quality_and_testing` (Antigravity Code Quality & End-to-End Testing Agent)**:
   - Audits file modularity & readability (`.agents/skills/android-code-modularization/SKILL.md`), Wi-Fi socket binding (`wifiSocketFactory`), SQLite/GENA/coroutine lifecycle cleanup, and `gradlew` & script non-executable git modes (`100644`, invoked via `sh`/`bash`/`python3`).
   - Runs the full release gate: `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` (`159+` unit, protocol, hardware-rules, household-sync, security, widget, telemetry, governance, ChromeOS ARCVM, and Compose E2E tests across `31+` suites).
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off code_quality_and_testing "<verification summary>"` (setting `CURRENT_TURN: license_governance`).
6. **Stage 6 (Review 5) — `license_governance` (Antigravity License & Brand Governance Agent)**:
   - Audits root `LICENSE` (**MIT License**) parity with `MoreScreen.kt` (`open_source_licenses_card`, `mit_license_text_surface`, and `third_party_notices_surface`), permissive-only dependency allowlist (`gradle/libs.versions.toml` & `app/build.gradle.kts` with `includeInApk = false`), `org.suppressingfire.wemi` package/filename hygiene, and Play Store trademark boundaries (`LICENSE_GOVERNANCE.md`).
   - Runs `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off license_governance "<verification summary>"` (setting `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`).
7. **Stage 7 (Conclude) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Verifies all 6 review stages are `SIGNED_OFF` in `RELEASE_SIGN_OFF.md`, confirms `docs/user-guide/release-notes.md` & `play_store_assets/RELEASE_NOTES.txt` completeness, runs `bash scripts/advance_release_turn.sh conclude "<summary>"` (setting `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA`, and `CONCLUDED_AT`), syncs to `origin/main`, and publishes the signed `.aab` to Google Play **Internal testing** + pushes any `app/build.gradle.kts` version bump.
8. **Stage 8 (Promote & Asset Sync) — `app_promoter` (Antigravity App Promoter Agent — Event-Driven GitHub Actions; see [`wemi-app-promoter`](../wemi-app-promoter/SKILL.md))**:
   - Fires automatically via [`.github/workflows/play-store-promote.yml`](../../../.github/workflows/play-store-promote.yml) on `push` to `main` touching `app/build.gradle.kts` or `RELEASE_SIGN_OFF.md` (zero Antigravity LLM quota cost).
   - Validates via [`scripts/promote_play_release.py`](../../../scripts/promote_play_release.py) (`auto-promote`) that `versionCode` increased when triggered solely by `app/build.gradle.kts`, `RELEASE_SIGN_OFF.md` has `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, and Google Play `internal` holds a newer build than `alpha` matching `current_version_code`, `previous_version_code`, or `RELEASE_SIGN_OFF.md` `VERSION_CODE` (polling briefly for Play processing), then promotes `internal` → `alpha` (Closed testing) with `play_store_assets/RELEASE_NOTES.txt` fallback.
   - Records `RELEASE_STATUS` (`PROMOTED_TO_CLOSED_TESTING` for `alpha`, or track-derived status), `SIGN_OFF_APP_PROMOTER: PROMOTED`, `PROMOTED_VERSION_CODE`, `PROMOTED_TRACK`, and `PROMOTED_AT` via `bash scripts/advance_release_turn.sh promote ... --from-track <from> --track <to>` (`Agent: Antigravity App Promoter Agent (app_promoter)`).
   - After `mode=release-status` confirms the promoted build reached `RELEASE_LIFECYCLE_STATE_PUBLISHED` on `alpha`, runs [`scripts/sync_play_assets.py`](../../../scripts/sync_play_assets.py) (`mode=sync-assets`, decoupled from promotion via `sync_store_assets=false` default so edits never collide in Play review) to idempotently sync changed Store Listing copy (`play_store_assets/STORE_LISTING.md`, `<= 30 / 80 / 4000` chars) and all 7 PNG slots (`16` PNGs), verifies `mode=diff-assets` (`IN SYNC`) and post-sync rollout, and only then sends the `[Antigravity Agent]` completion email.
   - Manual / on-demand fallback: `gh workflow run play-store-promote.yml -f mode=list-tracks` (read-only), `-f mode=release-status -f to_track=alpha -f version_code=<vc>`, `-f mode=diff-assets` (read-only Play asset diff), `-f mode=sync-assets` (post-rollout asset sync), `-f mode=auto-promote`, or `-f mode=promote -f from_track=internal -f to_track=alpha`.

---

## 2. Quota-Safe Background Wakeup Workflow (Zero Idle Quota Drain)

> [!IMPORTANT]
> **5-Hour Quota Protection Rules (Mandatory)**:
> 1. **Never poll `24/7` between releases**: Do **NOT** arm `scripts/await_release_turn.sh` when `RELEASE_STATUS` is `APPROVED_FOR_PLAY_STORE`, `PROMOTED_TO_CLOSED_TESTING`, or `AWAITING_AI_STUDIO_INITIATION` (`CURRENT_TURN: NONE`), and never poll for event-driven Stage 8 (`app_promoter`). Non-daemon `run_command` tasks hit `exebox`'s `9m50s` ceiling and wake the full LLM conversation on every timeout!
> 2. **Never re-arm after signing off your stage**: Once your role has recorded `SIGNED_OFF` for the current `(RELEASE_VERSION, ROUND_NUMBER)`, **end your turn immediately without re-arming `await_release_turn.sh`**.
> 3. **Honor `[QUOTA GUARD] DO NOT RE-ARM`**: Whenever `await_release_turn.sh` prints `[QUOTA GUARD] DO NOT RE-ARM`, stop immediately and wait for the next user prompt.
> 4. **Rotate Conversations at `>= 400` Steps**: Check `/home/antigravity/.gemini/usage/agy-usage --check-self`. Multi-day `1,000+`-step conversations cost `3.5x–5.0x` quota per turn; start a fresh specialist conversation for each new release cycle.

### Step 1: Arm Your Turn Watcher *Only* During an Active `IN_REVIEW` Round While `PENDING`
When a release review round is actively `IN_REVIEW` and your stage (Stages 2–6) is still `PENDING`, launch [`scripts/await_release_turn.sh`](../../../scripts/await_release_turn.sh) (`WaitMsBeforeAsync: 5000`, `15`s poll interval, `510`s exebox-safe window, max `2` upstream waits):
```bash
# Only while RELEASE_STATUS == IN_REVIEW and your SIGN_OFF_<ROLE> == PENDING:
bash scripts/await_release_turn.sh <your_role> 15 510
```
If no release is `IN_REVIEW`, your role already `SIGNED_OFF`, or `<your_role>` is `app_promoter`, `await_release_turn.sh` exits in `<1s` within `WaitMsBeforeAsync` and prints `[QUOTA GUARD] DO NOT RE-ARM`.

### Step 2: Execute Your Turn & Stop Cleanly After Sign-Off
When `scripts/await_release_turn.sh <your_role>` activates your turn:
1. Pull the latest `origin/main`:
   ```bash
   git pull --rebase origin main
   ```
2. Perform your domain audit and run your required Gradle verification tests. If committing any domain fixes during your turn, follow [`agent-commit-attribution`](../agent-commit-attribution/SKILL.md) — write for a human reader in plain English (what changed and why, no symbol dumps or semicolon chains) via `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh <your_role> "<plain-English subject>" "<short plain-English bullets>" --verified "<brief checks>"`.
3. Record your sign-off and hand off `CURRENT_TURN` to the next agent in the ring with a short 1–2 sentence plain-English summary (avoid dumping long symbol lists into the commit message):
   ```bash
   bash scripts/advance_release_turn.sh sign_off <your_role> "<short plain-English summary of what passed>" --verified "<e.g., 134/134 tests passing, lint clean, debug APK builds>"
   ```
4. **End your turn cleanly** — do **not** re-arm `await_release_turn.sh` once your stage is `SIGNED_OFF`! If `/home/antigravity/.gemini/usage/agy-usage --check-self` reports `ROTATE_RECOMMENDED` or `ROTATE_URGENT` (`>= 400` steps), advise starting a fresh conversation for the next release.
