---
name: wemi-security-and-privacy
description: Stage 4 Security & Privacy review invariants for WeMi Light Control covering Android IPC/widgets, local-LAN SSRF/CRLF/DoS, untrusted SQLite rules.db parsing, Wi-Fi credential lifecycle, backup/Privacy Policy parity, telemetry minimization, and CI/CD supply-chain security. EXCLUSIVELY OWNED by security_and_privacy (read-only for all other agents).
---

# WeMi Security & Privacy Skill (`wemi-security-and-privacy`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP (READ-ONLY FOR OTHER AGENTS)**
> - **Owner**: **Antigravity Security & Privacy Agent (`security_and_privacy`)** is the **sole author and maintainer** of [`SECURITY_AND_PRIVACY.md`](../../../SECURITY_AND_PRIVACY.md) and [`.agents/skills/wemi-security-and-privacy/SKILL.md`](SKILL.md).
> - **Policy for All Other Agents**: **DO NOT MODIFY, OVERWRITE, OR DELETE THESE SECURITY & PRIVACY FILES.** All other agents must read and follow the invariants below (and the general methodology in [`security-and-privacy`](../security-and-privacy/SKILL.md)) when changing Android components, LAN networking, `rules.db` handling, credentials, backups, telemetry, or CI workflows, and treat these files as **strictly read-only**.

---

## 1. Core WeMi Security & Privacy Invariants

Always apply the full STRIDE + LINDDUN + OWASP MASVS/IoT methodology from [`security-and-privacy`](../security-and-privacy/SKILL.md) alongside these WeMi-specific invariants documented in [`SECURITY_AND_PRIVACY.md`](../../../SECURITY_AND_PRIVACY.md):

1. **Android Manifest & Widget IPC Hardening ([`AndroidManifest.xml`](../../../app/src/main/AndroidManifest.xml), [`widget/`](../../../app/src/main/java/org/suppressingfire/wemi/widget))**:
   - Confirm only the 4 normal LAN permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`) are declared (zero Location/storage/background-tracking permissions).
   - Audit exported receivers/activities (`WeMiSwitchWidgetProvider`, `SwitchWidgetConfigureActivity`, `MainActivity`), `PendingIntent` mutability/nonces, and `FileProvider` path scopes so zero-permission co-resident apps cannot forge widget bindings or toggle physical switches.
2. **Local-LAN Network & Anti-SSRF / Anti-CRLF / Anti-DoS Bounds ([`LanHttpClient.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/LanHttpClient.kt), [`SsdpDiscoveryEngine.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt), [`GenaSubscriptionManager.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/GenaSubscriptionManager.kt), [`HardwareRulesProtocol.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesProtocol.kt))**:
   - Verify untrusted SSDP `LOCATION` URLs, `<ruleDbPath>` paths, and manual host inputs cannot redirect cleartext HTTP requests to public internet hosts, metadata endpoints (`169.254.169.254`), or unexpected loopback ports.
   - Verify CR/LF/NUL injection is blocked across HTTP methods, request targets, headers, SSDP packets, and parsed `<serialNumber>`/`<UDN>` strings.
   - Verify response size limits, header limits, slowloris deadlines, and GENA `:8989` concurrency/authentication guards prevent LAN DoS and spoofed switch states.
3. **Untrusted XML, ZIP & LAN SQLite Hardening ([`XmlAndSkuParser.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/XmlAndSkuParser.kt), [`HardwareRulesDbCodec.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbCodec.kt), [`HardwareRulesDbMutator.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbMutator.kt), [`HardwareRulesSafetyValidator.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesSafetyValidator.kt), [`HardwareRulesBackupStore.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesBackupStore.kt))**:
   - Preserve zero-XXE XML parsing and `escapeXml` escaping on outbound SOAP payloads.
   - Preserve ZIP-slip path rejection, streamed `MAX_UNZIPPED_READ_BYTES` cap, canonical-path `noBackupFilesDir` backup confinement, parameterized SQL queries, and rejection of untrusted SQLite `TRIGGER`s, `VIEW`s, virtual tables, and `trusted_schema` execution (including on raw backup restores).
4. **Wi-Fi Credential & Secret Lifecycle ([`ProvisionApDialog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApDialog.kt), [`ProvisionApFormSections.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApFormSections.kt), [`ProvisionerClient.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/ProvisionerClient.kt), [`PasswordCrypto.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/PasswordCrypto.kt))**:
   - Confirm Wi-Fi passwords stay masked by default, are never leaked into `android.util.Log`, SOAP logs, telemetry files, Room, `SharedPreferences`, or `savedInstanceState` Bundles, and that `<password>` / `<ruleDbBody>` redaction remains intact.
5. **Privacy Policy, Backup XML & Reset Parity ([`PRIVACY_POLICY.md`](../../../play_store_assets/PRIVACY_POLICY.md), [`DATA_SAFETY_GUIDE.md`](../../../play_store_assets/DATA_SAFETY_GUIDE.md), [`backup_rules.xml`](../../../app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](../../../app/src/main/res/xml/data_extraction_rules.xml), [`SetupDiagnostics.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/SetupDiagnostics.kt), [`SwitchRepository.kt`](../../../app/src/main/java/org/suppressingfire/wemi/data/SwitchRepository.kt))**:
   - Confirm zero `android.util.Log` / `println` calls exist in `app/src/main/java/`.
   - Verify backup XML rules, telemetry exports (`solarLoc=set|unset`, clipboard handling, quasi-identifiers), and Reset (`resetLanHomesSwitchesAndRooms`) behavior remain consistent with `PRIVACY_POLICY.md` and `DATA_SAFETY_GUIDE.md`.
6. **CI/CD & Supply-Chain Security ([`.github/workflows/play-store-promote.yml`](../../../.github/workflows/play-store-promote.yml), [`scripts/`](../../../scripts/), [`gradle/wrapper/gradle-wrapper.properties`](../../../gradle/wrapper/gradle-wrapper.properties))**:
   - Confirm zero hardcoded secrets, zero shell expression injection (all GitHub inputs passed via `env:`), `chmod 600` temp credentials cleaned in `if: always()`, least-privilege workflow `permissions:`, and `100644` non-executable script modes.

---

## 2. Stage 4 Release Review Checklist (`security_and_privacy`)

In the **7-Agent / 8-Stage Autonomous Round-Robin Release Process** ([`RELEASE_PROCESS.md`](../../../RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](../../../RELEASE_SIGN_OFF.md)), `security_and_privacy` is **Stage 4 (Review 3)**, executing immediately after `telemetry_and_debugging` (Stage 3) and immediately before `code_quality_and_testing` (Stage 5):

1. **Pull Latest `origin/main` & Inspect Release Diff**:
   ```bash
   git pull --rebase origin main
   git log --oneline -n 15
   ```
2. **Audit New Changes & Baseline Backlog**:
   - Review all diffs since the previous release against the 6 invariants above and [`SECURITY_AND_PRIVACY.md`](../../../SECURITY_AND_PRIVACY.md).
   - Verify zero newly introduced Critical or High security/privacy vulnerabilities, zero new `android.util.Log`/`println` calls in `app/src/main/java/`, zero unreviewed permissions or exported components, and zero CI secret or expression-injection regressions.
   - Update [`SECURITY_AND_PRIVACY.md`](../../../SECURITY_AND_PRIVACY.md) backlog statuses if any tracked baseline items (`SEC-*`, `PRIV-*`, `CI-*`) were remediated in the release.
   - **Respect Peer Ownership**: Never modify files owned exclusively by `ai_studio_ux_cuj`, `discovery_and_grouping`, `telemetry_and_debugging`, or `license_governance`.
3. **Run Security & Privacy Verification Checks**:
   ```bash
   sh gradlew testDebugUnitTest \
     --tests org.suppressingfire.wemi.ExampleUnitTest \
     --tests org.suppressingfire.wemi.SkuAndCryptoTest \
     --tests org.suppressingfire.wemi.RuleAndTransportTest \
     --tests org.suppressingfire.wemi.HardwareRulesTest \
     --tests org.suppressingfire.wemi.HardwareRulesMutationTest \
     --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest \
     --tests org.suppressingfire.wemi.TelemetryDiagnosticsTest \
     --tests org.suppressingfire.wemi.HomeScreenWidgetTest \
     --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest
   PYTHONDONTWRITEBYTECODE=1 python3 -W default -m unittest discover -s scripts -p "test_*.py" -v
   ```
   *(If committing any security/privacy code fix during Stage 4, commit with `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh security_and_privacy ...` and run the full `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` pre-push gate per [`AGENTS.md`](../../../AGENTS.md) Section 1; if a cross-cutting upstream fix requires re-verification, run `bash scripts/advance_release_turn.sh request_rereview security_and_privacy <target_role> "<reason>"`.)*
4. **Sign Off & Advance Turn to `code_quality_and_testing` (Stage 5)**:
   ```bash
   bash scripts/advance_release_turn.sh sign_off security_and_privacy "<concise plain-English verification summary>" --verified "<brief checks>"
   ```
5. **End Turn Cleanly (Quota-Safe Zero-Idle-Polling Policy)**:
   - **Never re-arm `scripts/await_release_turn.sh` after signing off** (`SIGNED_OFF`) or between releases (`APPROVED_FOR_PLAY_STORE` / `PROMOTED_TO_CLOSED_TESTING` / `CURRENT_TURN: NONE`), and always honor `[QUOTA GUARD] DO NOT RE-ARM`.
   - Arm `bash scripts/await_release_turn.sh security_and_privacy 15 510` (`WaitMsBeforeAsync: 5000`) **only** while an active release round is `IN_REVIEW` and `SIGN_OFF_SECURITY_AND_PRIVACY` is `PENDING`.
   - Check `/home/antigravity/.gemini/usage/agy-usage --check-self` and recommend starting a fresh specialist conversation before the next release cycle once `>= 400` steps.
