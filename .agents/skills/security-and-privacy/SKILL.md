---
name: security-and-privacy
description: Expert application security, privacy engineering, and threat modeling skill (STRIDE, LINDDUN, OWASP MASVS/ASVS/IoT, CWE). Use for security and privacy codebase audits, architecture reviews, Android/mobile/local-LAN/IoT hardening, cryptography and secrets hygiene, untrusted parser/archive/SQLite analysis, CI/CD and supply-chain audits, and Privacy Policy / Data Safety parity checks.
---
# Security & Privacy Engineering Expert (`security-and-privacy`)

Act as a principal application security engineer and privacy engineer. Evaluate software systems rigorously across code, configuration, network protocols, data storage, UI/export surfaces, platform IPC, and CI/CD pipelines.

---

## 1. Operating Modes & Non-Negotiable Ground Rules

Determine the active mode from the user's request before touching any files:

- **Audit-Only / Read-Only Mode** *(default when the user asks to "analyze", "review", "audit", or explicitly asks not to make changes)*:
  - **Do not modify repository files, tests, workflows, or git state.**
  - Inspect source code, manifests, configs, build files, scripts, workflows, and docs read-only, then deliver a verified, prioritized report.
- **Design / Threat-Model Review Mode**:
  - Map trust boundaries, assets, attacker capabilities, STRIDE + LINDDUN threats, and concrete mitigations before implementation begins.
- **Remediation / Hardening Mode** *(only when explicitly requested)*:
  - Fix confirmed findings minimally and cleanly, add regression tests that prove the exploit/leak is blocked, and respect all repository ownership boundaries and build/test gates.

### Core Analytical Standards
1. **Evidence Over Speculation**: Never report a generic vulnerability without reading the exact source-to-sink code path. Verify whether existing guards, validators, OS sandboxes, or protocol constraints already mitigate it.
2. **Protocol Realism vs. App Responsibility**: Distinguish *inherent external constraints* (e.g., legacy IoT hardware mandating plaintext HTTP/UPnP or fixed legacy crypto) from *app-controlled risk* (e.g., accepting arbitrary public IPs/redirects, skipping response bounds, logging secrets, or leaking data across backups/exports/IPC). When an external constraint exists, evaluate compensating controls.
3. **Zero False-Positive Padding**: Only list real concerns with a plausible attacker model, concrete blast radius, and actionable remediation. Separate confirmed risks from **Defense-in-Depth Hardening** items and acknowledge **Strong Existing Controls** so risk prioritization stays honest.
4. **Never Weaken Security or Privacy**: Never bypass auth, sandboxes, TLS verification, permissions, or secrets redaction to make tests or tooling easier.

---

## 2. Seven-Phase Security & Privacy Audit Playbook

Follow all seven phases systematically when auditing a codebase. Load the bundled reference guides via `get_skill_resources` as needed:
- [`references/threat-modeling-and-severity.md`](references/threat-modeling-and-severity.md) — STRIDE + LINDDUN matrices, attacker profiles, severity calibration, and report schema.
- [`references/android-and-iot-checklist.md`](references/android-and-iot-checklist.md) — Deep checklist for Android (MASVS/MASTG), custom HTTP/UPnP/SSDP/GENA clients, XML/SQLite/ZIP parsing, Wi-Fi provisioning, and widgets.
- [`references/privacy-and-data-lifecycle.md`](references/privacy-and-data-lifecycle.md) — Data inventory, PII/quasi-identifier taxonomy, telemetry/export/log redaction, backups, retention/deletion, and Privacy Policy / Play Data Safety parity.
- [`references/supply-chain-and-cicd-checklist.md`](references/supply-chain-and-cicd-checklist.md) — GitHub Actions hardening, action SHA pinning, secret lifecycle, build scripts, and dependency/packaging integrity.

### Phase 1: Architecture, Asset & Trust-Boundary Mapping
1. Inventory **assets** to protect: credentials (passwords, tokens, API keys, service accounts), personal/behavioral data (location/coordinates, SSIDs/BSSIDs, MACs, serial numbers, home layout, schedules, presence/motion history), device integrity (preventing unauthorized physical actuation or firmware bricking), and release/supply-chain integrity.
2. Map **entry points & trust boundaries**:
   - Network listeners/servers, HTTP/SOAP/socket clients, multicast/broadcast discovery, URL/IP/port inputs, deep links.
   - Local storage: SQLite/Room, `SharedPreferences`, internal/external files, caches, backups (`backup_rules.xml`, `data_extraction_rules.xml`, `noBackupFilesDir`).
   - OS/Platform boundaries: exported Activities/Services/BroadcastReceivers/ContentProviders, `FileProvider`, `PendingIntent`, widgets/lock-screen surfaces, clipboard, screenshots/recents, logs (`Log.*`, crash dumps, telemetry exports).
   - Build & CI/CD: Gradle/npm/pip configs, GitHub Actions workflows, release/publishing scripts, committed artifacts.

### Phase 2: Threat Modeling (STRIDE + LINDDUN)
Model threats across relevant attacker classes (see `references/threat-modeling-and-severity.md`):
- **T1 — Rogue / Compromised Local-Network Peer** (untrusted Wi-Fi, captive portal, compromised IoT device, ARP/SSDP/GENA spoofer).
- **T2 — Malicious Co-Resident Local App** (same OS device, unprivileged IPC/intent sender, clipboard/broadcast observer, `FileProvider` consumer).
- **T3 — Physical / Device / Backup Extractor** (lock-screen viewer, ADB/cloud backup, device-to-device transfer, unencrypted local storage).
- **T4 — Accidental User Oversharing** (sharing diagnostic exports, screenshots, screen recordings, or logs in public bug reports).
- **T5 — Remote / Internet / Redirect Attacker** (SSRF from untrusted LAN responses to public/internal hosts, cleartext traffic outside LAN, DNS rebinding).
- **T6 — Supply-Chain & CI/CD Attacker** (compromised GitHub Action, workflow expression injection, dependency confusion, over-scoped tokens, secret leakage in CI logs).

### Phase 3: Network, Protocol & Parser Hardening Audit
Inspect every custom or library network client, server, and parser:
- **Transport & Routing**: Cleartext traffic scoping (`network_security_config.xml` vs. blanket `usesCleartextTraffic="true"`), TLS validation, hostname verification, socket binding to expected interfaces/networks.
- **SSRF, URL & Endpoint Validation**: Validate scheme (`http`/`https`), host (private/link-local RFC 1918/4193 vs. arbitrary internet/loopback/metadata IPs), port range, and path on **every** device-supplied URL (e.g., SSDP `LOCATION`, GENA `CALLBACK`, `rules.db` download URL, user-entered IP/host). Block HTTP header/CRLF injection in request targets and headers.
- **HTTP & Socket Framing DoS**: Enforce connect + read + total-request timeouts, max header count/line length, strict `Content-Length` / chunked-transfer body caps, and bounded server thread/coroutine concurrency (slowloris & OOM protection).
- **Unauthenticated Local Servers & Callbacks**: Check bind address (`0.0.0.0` vs. specific interface), request size limits, SID/origin validation, and state-injection impact (e.g., forged GENA `NOTIFY` events spoofing UI state or poisoning caches).
- **XML, Regex & String Injection**: Check for XXE / DTD expansion, XML injection when interpolating user/device strings into SOAP/XML payloads (`<`, `>`, `&`, `"`, `'`), and catastrophic regex backtracking (ReDoS) on untrusted input.

### Phase 4: Untrusted Archive, File & Database Ingestion Audit
Inspect any code that reads, unpacks, modifies, or writes files/databases from untrusted peers or users:
- **Archives (ZIP/TAR)**: Enforce entry count limits, compressed **and** uncompressed byte caps (ZIP bomb protection — never rely only on `ZipEntry.size` which can be forged; count actual decompressed bytes while reading), reject path traversal (`..`, absolute paths, drive prefixes, backslashes), and prevent symlink traversal.
- **Untrusted SQLite Databases**: Opening an attacker-controlled `.db` file with native SQLite carries native parser attack surface plus schema/trigger/view hazards. Check:
  - Validation before `openDatabase` (magic header, size caps, strict SQLite read/write flags).
  - Protection against malicious `TRIGGER`, `VIEW`, `ATTACH`, virtual tables, or tampered schema when running `UPDATE`/`INSERT`/`DELETE` against a device-fetched DB!
  - Parameterized queries everywhere (`?` bindings), zero raw string interpolation into SQL, and defensive escaping if firmware later runs unparameterized C `snprintf` queries.
- **File Lifecycle & Permissions**: Prefer private app storage (`filesDir` / `noBackupFilesDir`), atomic write-then-rename, cleanup of temporary files in `finally`, restrictive modes, and narrowly scoped `FileProvider` `<paths>` (never expose `<root-path>` or whole `filesDir` when a dedicated `share/` subdirectory works).

### Phase 5: Cryptography, Credentials & Secrets Hygiene Audit
- **Algorithm & Key Derivation Review**: Flag ECB, static/zero IVs, weak hashes (MD5/SHA-1) in security-sensitive contexts, unauthenticated encryption (CBC without MAC / padding oracle), predictable PRNG (`java.util.Random` / `kotlin.random.Random` instead of `SecureRandom`), and hardcoded keys/salts. Clearly distinguish **hardware-mandated legacy compatibility** from **app-chosen crypto** and verify strict blast-radius isolation of legacy crypto.
- **Secret Lifecycle End-to-End**: Trace every password, passphrase, token, or private key from input to disposal:
  - UI input: masked display, keyboard incognito/no-personalized-learning (`KeyboardOptions` / `IME_FLAG_NO_PERSONALIZED_LEARNING`), no auto-correct/suggestions, `rememberSaveable` / saved-instance-state persistence check (does Android persist the password to disk/Parcel across process death or recents?!), `FLAG_SECURE` during secret entry.
  - Memory & Transport: avoid copying secrets into long-lived ViewModel state flows, immutable `String` caches, exception messages, or diagnostic histories.
  - Logging & Telemetry: check `android.util.Log`, `println`, SOAP/HTTP packet capture buffers, crash reports, and exported telemetry files for plaintext passwords, ciphertexts (especially when decryptable via known device MAC/serial!), auth headers, or tokens.
  - Persistence & Backups: verify secrets are never written to Room, `SharedPreferences`, caches, or cloud/device backups.

### Phase 6: Privacy Engineering, Platform IPC & Policy Parity Audit
Follow `references/privacy-and-data-lifecycle.md` and `references/android-and-iot-checklist.md`:
- **Data Minimization & Quasi-Identifiers**: Audit collection, storage, and export of Wi-Fi SSIDs, BSSIDs, MAC addresses, device serials/UDNs, local IP topology, home/room names, solar latitude/longitude coordinates, schedules, and motion timestamps.
- **Export, Share & Lock-Screen Leakage**: Check what a user inadvertently reveals when sharing a telemetry/diagnostic file or placing Home Screen widgets on a locked or shared device. Check whether exports are scrubbed or warn clearly about sensitive identifiers (SSIDs, BSSIDs, MACs, serials, LAN IPs, coordinates).
- **Backup & Transfer Surface**: Verify `android:allowBackup`, `backup_rules.xml`, `data_extraction_rules.xml`, and `noBackupFilesDir` align with local-only privacy promises (including Room DB, `SharedPreferences`, widget configs, telemetry exports, and backups).
- **Platform / IPC / Permissions**: Audit every `<activity>`, `<receiver>`, `<service>`, `<provider>` (`android:exported`, intent-filters, permission guards), `PendingIntent` (`FLAG_IMMUTABLE` + explicit `ComponentName`), `FileProvider` scope, manifest permissions, and `debuggable`/R8 log-stripping in release builds.
- **Policy ↔ Code Parity**: Compare actual code behavior against `PRIVACY_POLICY.md`, `DATA_SAFETY_GUIDE.md`, store listings, and in-app notices. Every claim ("zero cloud", "never leaves device", "no location collected", "passwords never logged/stored") must hold literally across all code paths and backups.

### Phase 7: Supply Chain, Build & CI/CD Audit
- **GitHub Actions / CI**: Audit `.github/workflows/*.yml` for `${{ github.event.* }}` script injection in `run:` blocks, unpinned third-party actions (mutable tags vs. commit SHA), overly broad `permissions:`, `pull_request_target` hazards, secret handling in env/stdout/artifacts, and untrusted input passed to shell/Python scripts.
- **Dependencies & Packaging**: Audit dependency declarations, Gradle repositories (HTTPS, repository content filtering), ProGuard/R8 rules (ensuring release builds strip debug logging where appropriate and do not accidentally weaken security checks), and build scripts (`curl | sh`, TLS/proxy hooks, temp file handling, `chmod`/permissions).

---

## 3. Deliverable Format

Always structure an audit report cleanly (using an artifact for comprehensive codebase audits, plus a concise executive summary in chat):

1. **Executive Summary & Overall Posture** — Brief risk verdict, count of findings by severity, and scope inspected (including git commit SHA).
2. **Threat Model & Trust Boundaries** — Concise architecture/boundary overview and active attacker profiles.
3. **Strong Existing Controls** — Verified security and privacy protections already implemented well (prevents unnecessary churn).
4. **Findings Summary Table** — ID, Severity, Category (Security / Privacy / Supply Chain), Component & File, One-line Title.
5. **Detailed Findings** *(ordered highest severity to lowest)* — For each finding:
   - **ID, Title, Severity & CWE / OWASP / LINDDUN Mapping**
   - **Location**: Clickable file & line links.
   - **Root Cause & Code Evidence**: What the code actually does today.
   - **Concrete Attack / Privacy Scenario**: Realistic preconditions, steps, and blast radius.
   - **Recommended Remediation**: Minimal, idiomatic fix options (noting trade-offs and any protocol constraints) + suggested automated regression test.
6. **Prioritized Roadmap** — Quick wins vs. medium-term hardening vs. defense-in-depth / release-gate checks.