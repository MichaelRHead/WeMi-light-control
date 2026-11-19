---
name: wemi-release-coordinator
description: Coordinate the 6-agent / 7-stage autonomous Google Play Store release review and Closed testing promotion process (AI Studio UX/CUJs -> Discovery & Room Grouping -> Telemetry & Debugging -> Code Quality & E2E Testing -> License & Brand Governance -> AI Studio Final Conclusion -> Event-Driven App Promoter Closed Testing Promotion) via RELEASE_SIGN_OFF.md, RELEASE_PROCESS.md, scripts/await_release_turn.sh, scripts/advance_release_turn.sh, and .github/workflows/play-store-promote.yml.
---

# WeMi 6-Agent / 7-Stage Autonomous Release Coordinator (`wemi-release-coordinator`)

Use this skill whenever preparing, reviewing, advancing, monitoring, or promoting a Google Play Store release in `MichaelRHead/WeMi-light-control`.

---

## 1. The 6-Agent / 7-Stage Release Ring

Every Play Store release candidate traverses 6 review stages synchronized via [`RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md) on `origin/main`, followed by event-driven Stage 7 promotion to Google Play Closed testing (`alpha`):

1. **Stage 1 (Initiate) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Verifies `versionCode` / `versionName` and `namespace = "org.suppressingfire.wemi"` in `app/build.gradle.kts`, audits UX/accessibility/navigation/CUJs (`UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`), updates `docs/user-guide/release-notes.md` & `play_store_assets/RELEASE_NOTES.txt` (do not create a root `RELEASE_NOTES.md`), runs `bash scripts/advance_release_turn.sh initiate "<summary>"` (recording `SIGN_OFF_AI_STUDIO_UX_CUJ: SIGNED_OFF`, `RELEASE_STATUS: IN_REVIEW`, `CURRENT_TURN: discovery_and_grouping`), and syncs to `origin/main`.
2. **Stage 2 (Review 1) — `discovery_and_grouping` (Antigravity Device Discovery & Room Grouping Agent)**:
   - Audits SSDP/unicast discovery (`SsdpDiscoveryEngine.kt`, `DiscoveryCoordinator.kt`), 3-tier hardware identity matching (`DeviceEntity.matchesDiscovered` / `matchesDiscoveredDevice`), soft-AP SSID filtering (`isWemoDeviceSetupSsid`), and `CamelCase` / specificity-ordered room inference (`normalizeDeviceNameForInference`, `inferRoomFromName`, `resolveDeviceRoomAssignment` in `DeviceEntities.kt`).
   - Runs `./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.ExampleUnitTest --tests org.suppressingfire.wemi.SkuAndCryptoTest --tests org.suppressingfire.wemi.SoapAndSelfHealingTest --tests org.suppressingfire.wemi.RepositorySelfHealingTest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off discovery_and_grouping "<verification summary>"` (setting `CURRENT_TURN: telemetry_and_debugging`).
3. **Stage 3 (Review 2) — `telemetry_and_debugging` (Antigravity Telemetry & Debugging Agent)**:
   - Audits `10.22.22.1` zero-cloud provisioning diagnostics (`SetupDiagnostics.kt`, `ProvisionerClient.kt`, `SetupApProber.kt`, `ProvisionDebugPanel.kt`), live SOAP packet log export (`MoreScreen.kt` & `MoreControlAndLogCards.kt`), inline switch diagnostics (`DeviceDiagnosticsDrawer.kt`), `BuildConfig`-version-stamped `wemi-telemetry-latest.txt` export (`TelemetryFileExporter`), cloud-backup exclusion (`backup_rules.xml` & `data_extraction_rules.xml`), and zero Wi-Fi password leakage (`plainLen`/`encLen` only).
   - Runs `./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest --tests org.suppressingfire.wemi.ProvisioningAndGenaCujE2ETest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off telemetry_and_debugging "<verification summary>"` (setting `CURRENT_TURN: code_quality_and_testing`).
4. **Stage 4 (Review 3) — `code_quality_and_testing` (Antigravity Code Quality & End-to-End Testing Agent)**:
   - Audits file modularity & readability (`.agents/skills/android-code-modularization/SKILL.md`), Wi-Fi socket binding (`wifiSocketFactory`), SQLite/GENA/coroutine lifecycle cleanup, and `gradlew` & `scripts/*.sh` executable mode (`100755`).
   - Runs the full release gate: `./gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` (`73/73` unit, protocol, governance, and Compose E2E tests across `15` suites).
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off code_quality_and_testing "<verification summary>"` (setting `CURRENT_TURN: license_governance`).
5. **Stage 5 (Review 4) — `license_governance` (Antigravity License & Brand Governance Agent)**:
   - Audits root `LICENSE` (**MIT License**) parity with `MoreScreen.kt` (`open_source_licenses_card`, `mit_license_text_surface`, and `third_party_notices_surface`), permissive-only dependency allowlist (`gradle/libs.versions.toml` & `app/build.gradle.kts` with `includeInApk = false`), `org.suppressingfire.wemi` package/filename hygiene, and Play Store trademark boundaries (`LICENSE_GOVERNANCE.md`).
   - Runs `./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest`.
   - Advances turn: `bash scripts/advance_release_turn.sh sign_off license_governance "<verification summary>"` (setting `RELEASE_STATUS: AWAITING_FINAL_CONCLUSION` and `CURRENT_TURN: ai_studio_ux_cuj`).
6. **Stage 6 (Conclude) — `ai_studio_ux_cuj` (Google AI Studio Agent)**:
   - Verifies all 5 review stages are `SIGNED_OFF` in `RELEASE_SIGN_OFF.md`, confirms `docs/user-guide/release-notes.md` & `play_store_assets/RELEASE_NOTES.txt` completeness, runs `bash scripts/advance_release_turn.sh conclude "<summary>"` (setting `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`, `FINAL_RELEASE_SHA`, and `CONCLUDED_AT`), syncs to `origin/main`, and publishes the signed `.aab` to Google Play **Internal testing** + pushes the `app/build.gradle.kts` version bump.
7. **Stage 7 (Promote) — `app_promoter` (Antigravity App Promoter Agent — Event-Driven GitHub Actions)**:
   - Fires automatically via [`.github/workflows/play-store-promote.yml`](../../.github/workflows/play-store-promote.yml) on `push` to `main` touching `app/build.gradle.kts` (zero Antigravity LLM quota cost).
   - Validates via [`scripts/promote_play_release.py`](../../scripts/promote_play_release.py) (`auto-promote`) that `versionCode` increased, `RELEASE_SIGN_OFF.md` has `RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, and Google Play `internal` holds a newer build than `alpha` (polling briefly for Play processing), then promotes `internal` → `alpha` (Closed testing) with `play_store_assets/RELEASE_NOTES.txt` fallback.
   - Records `RELEASE_STATUS: PROMOTED_TO_CLOSED_TESTING`, `SIGN_OFF_APP_PROMOTER: PROMOTED`, `PROMOTED_VERSION_CODE`, `PROMOTED_TRACK`, and `PROMOTED_AT` via `bash scripts/advance_release_turn.sh promote ...` (`Agent: Antigravity App Promoter Agent (app_promoter)`).
   - Manual fallback: `gh workflow run play-store-promote.yml -f mode=list-tracks` (read-only), `-f mode=auto-promote` (re-check/promote), or `-f mode=promote -f from_track=internal -f to_track=alpha`.

---

## 2. Quota-Safe Background Wakeup Workflow (Zero Idle Quota Drain)

> [!IMPORTANT]
> **5-Hour Quota Protection Rules (Mandatory)**:
> 1. **Never poll `24/7` between releases**: Do **NOT** arm `scripts/await_release_turn.sh` when `RELEASE_STATUS` is `APPROVED_FOR_PLAY_STORE`, `PROMOTED_TO_CLOSED_TESTING`, or `AWAITING_AI_STUDIO_INITIATION` (`CURRENT_TURN: NONE`), and never poll for event-driven Stage 7 (`app_promoter`). Non-daemon `run_command` tasks hit `exebox`'s `9m50s` ceiling and wake the full LLM conversation on every timeout!
> 2. **Never re-arm after signing off your stage**: Once your role has recorded `SIGNED_OFF` for the current `(RELEASE_VERSION, ROUND_NUMBER)`, **end your turn immediately without re-arming `await_release_turn.sh`**.
> 3. **Honor `[QUOTA GUARD] DO NOT RE-ARM`**: Whenever `await_release_turn.sh` prints `[QUOTA GUARD] DO NOT RE-ARM`, stop immediately and wait for the next user prompt.
> 4. **Rotate Conversations at `>= 400` Steps**: Check `/home/antigravity/.gemini/usage/agy-usage --check-self`. Multi-day `1,000+`-step conversations cost `3.5x–5.0x` quota per turn; start a fresh specialist conversation for each new release cycle.
> 5. **Auto-Sync Quota Guard Patch**: In a `WeMi-light-control` checkout, if `grep -q "QUOTA GUARD" scripts/await_release_turn.sh` is false and `/home/antigravity/.gemini/usage/wemi_release_patch/apply_to_wemi_repo.sh` exists, run `bash /home/antigravity/.gemini/usage/wemi_release_patch/apply_to_wemi_repo.sh` first so `origin/main` gets the quota-safe script.

### Step 1: Arm Your Turn Watcher *Only* During an Active `IN_REVIEW` Round While `PENDING`
When a release review round is actively `IN_REVIEW` and your stage (Stages 2–5) is still `PENDING`, launch [`scripts/await_release_turn.sh`](../../scripts/await_release_turn.sh) (`WaitMsBeforeAsync: 5000`, `15`s poll interval, `510`s exebox-safe window, max `2` upstream waits):
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
   bash scripts/advance_release_turn.sh sign_off <your_role> "<short plain-English summary of what passed>" --verified "<e.g., 73/73 tests passing, lint clean, debug APK builds>"
   ```
4. **End your turn cleanly** — do **not** re-arm `await_release_turn.sh` once your stage is `SIGNED_OFF`! If `/home/antigravity/.gemini/usage/agy-usage --check-self` reports `ROTATE_RECOMMENDED` or `ROTATE_URGENT` (`>= 400` steps), advise starting a fresh conversation for the next release.
