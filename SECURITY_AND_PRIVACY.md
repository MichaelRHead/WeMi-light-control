# WeMi Light Control — Security & Privacy Specification (`SECURITY_AND_PRIVACY.md`)

**Repository**: `MichaelRHead/WeMi-light-control` (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`, `v36.0` / `versionCode = 36`)  
**Document & Skill Owner**: **Antigravity Security & Privacy Agent (`security_and_privacy`) — EXCLUSIVE OWNER**  
**Companion Stage 4 Skill**: [`.agents/skills/wemi-security-and-privacy/SKILL.md`](.agents/skills/wemi-security-and-privacy/SKILL.md)  
**General Security & Privacy Methodology Skill**: [`.agents/skills/security-and-privacy/SKILL.md`](.agents/skills/security-and-privacy/SKILL.md)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR OTHER AGENTS)**
> - **Owner**: **Antigravity Security & Privacy Agent (`security_and_privacy`)** is the **sole author and maintainer** of [`SECURITY_AND_PRIVACY.md`](SECURITY_AND_PRIVACY.md) and [`.agents/skills/wemi-security-and-privacy/SKILL.md`](.agents/skills/wemi-security-and-privacy/SKILL.md).
> - **Policy for All Other Agents (including the Google AI Studio Agent, the Antigravity Device Discovery & Room Grouping Agent, the Antigravity Debugging & Support Agent, the Antigravity Code Quality & E2E Testing Agent, the Antigravity License & Brand Governance Agent, the Antigravity App Promoter Agent, CLI agents, and external coding assistants)**: **DO NOT MODIFY, OVERWRITE, RENAME, OR DELETE THESE TWO FILES.** All other agents **must** read, preserve, and respect the security boundaries, privacy lifecycle invariants, untrusted-input guards, and CI/CD supply-chain rules defined here when modifying Android components, local-LAN networking, `rules.db` parsing, persistence/backups, telemetry, or release workflows, but must treat these files as **strictly read-only**.

Related documentation:
- [`AGENTS.md`](AGENTS.md) & [`ARCHITECTURE.md`](ARCHITECTURE.md) — Concise Agent Guide & On-Demand Architecture/Test Catalog
- [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — 7-Agent / 8-Stage Autonomous Round-Robin Release Review, Sign-Off & Play Store Promotion State Machine
- [`.agents/skills/agent-commit-attribution/SKILL.md`](.agents/skills/agent-commit-attribution/SKILL.md) — Mandatory Agent Commit Description Attribution Convention
- [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design Guidelines *(Owned Exclusively by the Google AI Studio Agent)*
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry & `10.22.22.1` Provisioning Diagnostics *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) — Open-Source MIT License Parity, Minimal Permissive Dependency Policy & Brand/Trademark Governance *(Owned Exclusively by the Antigravity License & Brand Governance Agent)*
- [`play_store_assets/PRIVACY_POLICY.md`](play_store_assets/PRIVACY_POLICY.md) & [`play_store_assets/DATA_SAFETY_GUIDE.md`](play_store_assets/DATA_SAFETY_GUIDE.md) — User-Facing Privacy Policy & Google Play Data Safety Declarations
- [`IMPLEMENTED.md`](IMPLEMENTED.md) & [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Reverse-Chronological Change Log & Roadmap

---

## 1. Domain Responsibility & Threat Model

**WeMi Light Control** is a local-network Android, Chromebook (ChromeOS), Home Screen Widget, and Android TV controller for Belkin's discontinued smart switch platform (`F7C030`, `WLS040`/`WLS0403`, `F7C059`/`WDS060`, `F7C063`/`WSP080`, `F7C028`). Because legacy Belkin firmware mandates unauthenticated plaintext HTTP/UPnP/SOAP/SSDP/GENA traffic on the local LAN and an open `10.22.22.1` setup hotspot, the **Antigravity Security & Privacy Agent (`security_and_privacy`)** owns **Stage 4 (Review 3)** of the **7-Agent / 8-Stage** release ring — executing immediately after `telemetry_and_debugging` (Stage 3) and immediately before `code_quality_and_testing` (Stage 5).

### 1.1 Trust Boundaries & Attacker Profiles (STRIDE + LINDDUN)

| Profile | Threat Actor | Primary Attack Surfaces in WeMi |
| :--- | :--- | :--- |
| **T1 — Rogue LAN / Shared Wi-Fi Peer** | Compromised IoT device, hostile guest on shared Wi-Fi, or ARP-spoofing MITM | Spoofed UDP SSDP `LOCATION` headers, forged `/setup.xml` `<serialNumber>`/`<UDN>` values, unauthenticated `NOTIFY` packets to GENA port `8989`, slowloris HTTP responses, and crafted `/rules.db` SQLite-in-ZIP archives |
| **T2 — Co-Resident Unprivileged Android App** | Malicious zero-permission app installed on the same phone, tablet, Chromebook, or TV | Exported manifest components (`WeMiSwitchWidgetProvider`, `SwitchWidgetConfigureActivity`, `MainActivity`), `PendingIntent` mutability, and `FileProvider` share URIs |
| **T3 — Device / Backup / OS-Surface Observer** | Cloud Auto Backup (Google Drive), device-to-device transfer, clipboard history, Android Recents snapshots, or Activity `savedInstanceState` Bundles | `AndroidManifest.xml` `allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml`, `rememberSaveable` secret fields, clipboard copies, and in-memory cryptographic byte arrays |
| **T4 — Accidental User Oversharing** | User posting diagnostic exports or screenshots in public issue trackers or support forums | `wemi-telemetry-latest.txt` (`TelemetryFileExporter`), `SETUP DIAGNOSTICS` screenshot panel, `SharedSoapLogStore`, and incomplete reset erasure |
| **T5 — Remote / SSRF Target** | External internet servers, localhost services, or link-local metadata endpoints (`169.254.169.254`) | Unvalidated SSDP `LOCATION` URLs, `<ruleDbPath>` URLs, manual IP inputs, and `LanHttpClient` scheme/host/CRLF parsing |
| **T6 — CI/CD & Supply-Chain Attacker** | Compromised GitHub Action tag, hijacked PyPI wheel, or tampered build wrapper | [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) (`PLAY_SERVICE_ACCOUNT_JSON`, `permissions:`), `gradle-wrapper.properties`, and [`scripts/setup_dev_env.sh`](scripts/setup_dev_env.sh) |

---

## 2. Non-Negotiable Security & Privacy Invariants

Every release candidate reviewed at **Stage 4 (`security_and_privacy`)** is evaluated against these seven security and privacy invariants (applying the methodology in [`.agents/skills/security-and-privacy/SKILL.md`](.agents/skills/security-and-privacy/SKILL.md)):

1. **Invariant 1 — Least-Privilege Android Manifest & IPC Boundaries ([`AndroidManifest.xml`](app/src/main/AndroidManifest.xml), [`widget/`](app/src/main/java/org/suppressingfire/wemi/widget))**:
   - Declare only the 4 normal install-time LAN permissions (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`); never add Location, Contacts, Storage, or background-tracking permissions without explicit user approval and policy updates.
   - Exported components (`android:exported="true"`) must never act as a confused deputy for co-resident zero-permission apps: keep internal widget actions off exported receivers where possible, validate active `AppWidgetManager` IDs/providers and single-use nonces on mutable pin `PendingIntent`s, and keep `FileProvider` `exported="false"` with `grantUriPermissions="true"` scoped strictly to explicit cache/telemetry paths.
2. **Invariant 2 — Strict Local-LAN Networking, Anti-SSRF, Anti-CRLF & DoS Bounds ([`LanHttpClient.kt`](app/src/main/java/org/suppressingfire/wemi/network/LanHttpClient.kt), [`SsdpDiscoveryEngine.kt`](app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt), [`GenaSubscriptionManager.kt`](app/src/main/java/org/suppressingfire/wemi/network/GenaSubscriptionManager.kt), [`HardwareRulesProtocol.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesProtocol.kt))**:
   - Pin all LAN fetches (`<ruleDbPath>`, SSDP `LOCATION`, manual IPs) to local private IPv4 networks (never allow untrusted LAN responses to redirect WeMi to internet hosts, link-local metadata IPs, or arbitrary loopback ports outside hermetic tests).
   - Reject CR/LF/NUL (`\r`, `\n`, `\u0000`) in HTTP request targets, header keys/values, SSDP search targets, and parsed hardware identifiers (`serialNumber`, `udn`).
   - Bound response body sizes, header counts, connection concurrency, and total wall-clock deadlines on both outbound `LanHttpClient` calls and the inbound GENA `:8989` listener; bind/authenticate GENA `NOTIFY` updates to active subscriptions.
3. **Invariant 3 — Untrusted Parser, Archive & LAN SQLite Hardening ([`XmlAndSkuParser.kt`](app/src/main/java/org/suppressingfire/wemi/network/XmlAndSkuParser.kt), [`HardwareRulesDbCodec.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbCodec.kt), [`HardwareRulesDbMutator.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesDbMutator.kt), [`HardwareRulesSafetyValidator.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesSafetyValidator.kt), [`HardwareRulesBackupStore.kt`](app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesBackupStore.kt))**:
   - Preserve zero-XXE XML parsing and `escapeXml` output encoding on all outbound SOAP parameters.
   - Preserve ZIP-slip path rejection, streamed decompressed byte caps (`MAX_UNZIPPED_READ_BYTES`), canonical-path backup confinement inside `noBackupFilesDir`, SQLite magic-byte verification, and parameterized SQL mutations.
   - Reject untrusted SQLite schema traps (`TRIGGER`s, `VIEW`s, virtual tables, untrusted schema execution) before querying or mutating any LAN-fetched or restored `rules.db`.
4. **Invariant 4 — Secret & Credential Lifecycle Hygiene ([`ProvisionApDialog.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApDialog.kt), [`ProvisionApFormSections.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApFormSections.kt), [`ProvisionerClient.kt`](app/src/main/java/org/suppressingfire/wemi/network/ProvisionerClient.kt), [`PasswordCrypto.kt`](app/src/main/java/org/suppressingfire/wemi/network/PasswordCrypto.kt))**:
   - Never persist Wi-Fi passwords or decryptable Belkin AES password ciphertexts to disk, Room, `SharedPreferences`, `android.util.Log`, SOAP logs, telemetry exports, or Android `savedInstanceState` Bundles (`rememberSaveable`).
   - Mask Wi-Fi passwords by default in the UI, clear secrets on completion/dismissal, and redact `<password>` and `<ruleDbBody>` payloads in all diagnostic surfaces (`plainLen`/`encLen` metadata only).
5. **Invariant 5 — Privacy-Policy, Data-Safety & Backup Parity ([`PRIVACY_POLICY.md`](play_store_assets/PRIVACY_POLICY.md), [`DATA_SAFETY_GUIDE.md`](play_store_assets/DATA_SAFETY_GUIDE.md), [`backup_rules.xml`](app/src/main/res/xml/backup_rules.xml), [`data_extraction_rules.xml`](app/src/main/res/xml/data_extraction_rules.xml))**:
   - Maintain strict truthfulness between app storage/backup behavior and Play Store disclosures (`"No data collected"`, `"No data shared"`, `"strictly on-device"`).
   - Ensure zero `android.util.Log` / `println` leakage in production code, exclude diagnostic artifacts and `hardware_rules_backups` (and enforce explicit policy alignment for Room DB & `SharedPreferences`) from cloud Auto Backup and device transfer, and verify that user-triggered Reset (`resetLanHomesSwitchesAndRooms`) erases all claimed local data cleanly.
6. **Invariant 6 — Telemetry & Export Data Minimization ([`SetupDiagnostics.kt`](app/src/main/java/org/suppressingfire/wemi/network/SetupDiagnostics.kt))**:
   - Keep Solar Location coordinates out of telemetry exports (`solarLoc=set|unset`).
   - Avoid silent clipboard writes outside explicit Copy actions, mark sensitive clipboard data (`EXTRA_IS_SENSITIVE` on API 33+), and protect geolocatable quasi-identifiers (neighbor/home SSIDs, MACs, serials, UDNs, DNS IPs) with clear disclosure or redaction controls.
7. **Invariant 7 — CI/CD, Secret & Supply-Chain Hygiene ([`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml), [`scripts/`](scripts/), [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties))**:
   - Never commit secrets or interpolate untrusted GitHub contexts directly into shell commands (`env:` bindings required).
   - Keep `PLAY_SERVICE_ACCOUNT_JSON` restricted to `mktemp` (`chmod 600`) inside `$RUNNER_TEMP` with `if: always()` cleanup, minimize workflow `permissions:`, track SHA-pinning / hash-locked Python dependencies / environment gates for privileged Play Store promotion jobs, and preserve non-executable (`100644`) git modes on `gradlew` and scripts.

---

## 3. Verified Baseline Controls & Open Hardening Backlog (`v36.0`)

### 3.1 Strong Controls Verified at `v36.0`
- Zero cloud backend, zero analytics/ad/crash SDKs, minimal pure-Kotlin/AndroidX runtime dependencies, zero bundled `.so`/`.ttf` binaries, `dependenciesInfo.includeInApk = false`, R8 minification enabled on release.
- Exact 4 normal install-time LAN permissions (zero Location/GPS permissions even for Solar Location) and zero `android.util.Log` / `println` calls in `app/src/main/java/`.
- Zero XXE attack surface (regex-based UPnP parsing) with `XmlAndSkuParser.escapeXml` on outbound `ChangeFriendlyName` and `ConnectHomeNetwork` values.
- ZIP-slip rejection, streamed `MAX_UNZIPPED_READ_BYTES` cap, canonical-path `noBackupFilesDir` backup confinement, `<ruleDbPath>` IP/port SSRF pinning, and `<password>` / `<ruleDbBody>` telemetry redaction + `solarLoc=set|unset`.
- Clean CI secret handling via `env:` bindings, `chmod 600` temp file in `$RUNNER_TEMP`, and `if: always()` cleanup.

### 3.2 Tracked Baseline Findings (Post-`v36.0` Remediation Backlog)

The initial read-only audit at `v36.0` cataloged **11 tracked hardening items** (`0` Critical, `3` High, `5` Medium, `3` Low). Stage 4 reviews **must block any newly introduced High/Critical regressions** immediately and track progressive burn-down of these baseline items across post-`v36.0` releases:

| ID | Severity | Area | Status | Summary |
| :---: | :---: | :--- | :---: | :--- |
| **SEC-01** | **High** | Android IPC (`widget/`) | `OPEN_BACKLOG` | Move `ACTION_TOGGLE_WIDGET` / `ACTION_WIDGET_PINNED` off exported `WeMiSwitchWidgetProvider` into a non-exported receiver with single-use pin nonce and active `AppWidgetManager` ID checks. |
| **SEC-02** | **High** | Network / SSRF (`SsdpDiscoveryEngine`, `LanHttpClient`, `GenaSubscriptionManager`) | `OPEN_BACKLOG` | Enforce `http://` + RFC 1918 IPv4 allowlist (loopback test-only) on SSDP `LOCATION` and `LanHttpClient`, reject CRLF/NUL in HTTP methods/targets/headers, and restrict `<serialNumber>`/`<UDN>` chars. |
| **SEC-03** | **High** | Untrusted SQLite (`HardwareRulesDbCodec`, `HardwareRulesDbMutator`, `HardwareRulesSafetyValidator`) | `OPEN_BACKLOG` | Set `PRAGMA trusted_schema = OFF`, reject `TRIGGER`/`VIEW`/virtual tables in `sqlite_master` unconditionally (including backup restores), verify core objects are `type = 'table'`, and cap parsed row counts. |
| **PRIV-01** | **Medium** | Backup / Privacy Policy Parity | `OPEN_BACKLOG` | Resolve Android Cloud Auto Backup uploading Room DB & `SharedPreferences` vs. "strictly on-device" policy copy (exclude from `<cloud-backup>` or explicitly disclose OS-managed backup). |
| **SEC-04** | **Medium** | GENA HTTP Server (`GenaSubscriptionManager`) | `OPEN_BACKLOG` | Bind GENA `:8989` to LAN IPv4, add `Semaphore(8)` + header/time caps, and authenticate `NOTIFY` via per-subscription random path token + `SID` + `sourceIp`. |
| **SEC-05** | **Medium** | Secret Lifecycle (`ProvisionApDialog`, `PasswordCrypto`) | `OPEN_BACKLOG` | Switch Wi-Fi `password` and `passwordVisible` from `rememberSaveable` to `remember`, auto-hide on `ON_PAUSE`, clear on dismiss/success, and zero crypto byte arrays in `finally`. |
| **PRIV-02** | **Medium** | Telemetry Quasi-Identifiers & Reset Completeness | `OPEN_BACKLOG` | Add redacted-by-default sharing or clear disclosure for SSIDs/MACs/serials/DNS IPs, mark clipboard `EXTRA_IS_SENSITIVE` (no silent clipboard write on Save/Share), and wipe telemetry/logs/debug snapshot on Reset. |
| **CI-01** | **Medium** | CI/CD Supply Chain (`play-store-promote.yml`) | `OPEN_BACKLOG` | SHA-pin GitHub Actions, hash-lock `pip` requirements (`--require-hashes`), narrow top-level `permissions: contents: read`, and add GitHub Environment protection for Play releases. |
| **SEC-06** | **Low** | Network Hardening (`LanHttpClient`, manual host fields) | `OPEN_BACKLOG` | Cap response headers (`<= 64`), enforce overall slowloris wall-clock deadline, fail fast when `Content-Length > maxResponseBytes`, and validate manual IP/AP host inputs as RFC 1918 IPv4. |
| **SEC-07** | **Low** | IPC & Parser Hardening | `OPEN_BACKLOG` | Verify widget provider in `SwitchWidgetConfigureActivity`, cap ZIP directory-entry iteration, allowlist `tableName` in `PRAGMA table_info`, fix port-less URL fallback, strip CR/LF in `buildSsdpMSearchPacket`, and bound `sanitizeXmlSnippet` input before regexes. |
| **CI-02** | **Low** | Build & Dev-Env Integrity | `OPEN_BACKLOG` | Verify Gradle wrapper `distributionSha256Sum` (checking AI Studio compatibility), add SHA-256 verification to `setup_dev_env.sh` downloads, and consider R8 `android.util.Log` strip rules. |

---

## 4. Stage 4 Release Review Gate (`security_and_privacy`)

In the **7-Agent / 8-Stage Autonomous Release Process** ([`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md)), `security_and_privacy` executes at **Stage 4 (Review 3)** — immediately after Stage 3 (`telemetry_and_debugging`) and immediately before Stage 5 (`code_quality_and_testing`):

1. **Pull Latest `origin/main` & Scope Diff**: Inspect commits since the previous release and verify no peer-owned files were touched outside their ownership rules.
2. **Audit All 7 Security & Privacy Invariants**:
   - Check `AndroidManifest.xml`, exported components, `PendingIntent`s, `FileProvider`, and `backup_rules.xml` / `data_extraction_rules.xml`.
   - Check `LanHttpClient`, `SsdpDiscoveryEngine`, `GenaSubscriptionManager`, `ProvisionerClient`, `PasswordCrypto`, `HardwareRules*`, `SetupDiagnostics`, and `SwitchRepository.resetLanHomesSwitchesAndRooms`.
   - Verify `PRIVACY_POLICY.md` and `DATA_SAFETY_GUIDE.md` remain truthful to live runtime behavior.
   - Check `.github/workflows/*.yml`, `scripts/*.py`, and build configs for secret safety, expression injection, permissions, and supply-chain regressions.
3. **Remediate or Gate Blocking Regressions**: Fix newly introduced security/privacy regressions (with targeted regression tests) or request re-review (`request_rereview`) if an upstream domain change introduced a cross-cutting vulnerability.
4. **Run Focused Verification Suite** (full `134+`-test & APK gate follows immediately in Stage 5 `code_quality_and_testing`; run the full gate as well if pushing standalone fixes):
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
5. **Sign Off & Advance Turn to `code_quality_and_testing` (Stage 5)**:
   ```bash
   bash scripts/advance_release_turn.sh sign_off security_and_privacy "<concise plain-English verification summary>" --verified "<brief checks>"
   ```
6. **Quota-Safe Clean Stop**: End your turn immediately after signing off without re-arming `scripts/await_release_turn.sh`.
