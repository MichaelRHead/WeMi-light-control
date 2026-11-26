# Threat Modeling, Severity Calibration & Reporting Guide

Reference guide for the `security-and-privacy` skill.

---

## 1. STRIDE (Security) & LINDDUN (Privacy) Quick Matrices

### STRIDE (Security Threats)
| Category | Core Question | Typical Codebase Manifestations |
| :--- | :--- | :--- |
| **S — Spoofing** | Can an attacker impersonate a device, server, user, or app component? | Forged SSDP responses, spoofed GENA `NOTIFY` callbacks, unverified hardware identity on IP reuse, spoofed broadcast intents. |
| **T — Tampering** | Can an attacker alter data in transit, at rest, or in build/CI pipelines? | Cleartext MITM, unauthenticated CBC ciphertext tampering, malicious SQLite triggers/views in device-fetched DBs, unpinned CI actions. |
| **R — Repudiation** | Can actions occur without user awareness or auditability? | Silent background state changes, unauthenticated local triggers, unlogged destructive resets or rule overwrites. |
| **I — Information Disclosure** | Can secrets or sensitive data leak to unauthorized parties? | Passwords in saved state/logs/telemetry, recoverable ciphertexts in logs, cloud backup leaks, broad `FileProvider`, PII in shared diagnostics. |
| **D — Denial of Service** | Can untrusted input exhaust memory, threads, sockets, storage, or brick hardware? | Unbounded HTTP/ZIP decompression (OOM/ZIP bomb), slowloris against embedded HTTP servers, regex ReDoS, malformed device config bricking IoT firmware. |
| **E — Elevation of Privilege** | Can an unprivileged caller invoke privileged operations? | Exported components without permission checks, mutable `PendingIntent`, SSRF into internal/localhost services, SQL/XML/shell command injection. |

### LINDDUN (Privacy Threats)
| Category | Core Question | Typical Codebase Manifestations |
| :--- | :--- | :--- |
| **L — Linkability** | Can distinct exports, records, or identifiers be correlated across time/contexts? | Persistent device MACs, hardware serial numbers, UDNs, and router BSSIDs exported in cleartext logs/telemetry. |
| **I — Identifiability** | Can data directly or indirectly identify a person or physical home? | Router SSID/BSSID (geolocatable via Wi-Fi BSSID databases), solar latitude/longitude coordinates, custom home/room/device names. |
| **N — Non-repudiation** | Does persistent telemetry prove a user's presence or activity? | Stored motion sensor timestamps, lighting schedules, and timestamped command logs retained beyond need. |
| **D — Detectability** | Can an observer infer presence, habits, or home occupancy? | Unencrypted LAN polling/GENA traffic, lock-screen widget state, periodic outbound pings, schedule rules exposing wake/sleep times. |
| **D — Disclosure of Information** | Is personal/home data exposed to third parties (cloud backups, OS keyboards, other apps)? | Android Auto Backup / device transfer including Room DB or `SharedPreferences`, keyboard learning on password/SSID/coordinate fields, overly wide share exports. |
| **U — Unawareness** | Does the user fail to realize what data is stored, backed up, or exported? | Telemetry export including SSIDs/BSSIDs/MACs/coordinates without warning; silent cloud backup of local-only app state. |
| **N — Non-compliance** | Does behavior diverge from Privacy Policy, Play Data Safety, or legal obligations? | Undisclosed permissions, policy claiming "never leaves device" while Auto Backup uploads DB/prefs to Google Drive, undocumented data retention. |

---

## 2. Severity Calibration Rubric

Rate findings by **practical real-world risk**, not theoretical worst-case CVSS alone:

| Severity | Criteria | Examples |
| :--- | :--- | :--- |
| **Critical** | Remote or zero-interaction compromise, RCE, arbitrary credential theft, systemic data exfiltration, or CI release-signing takeover. | Workflow command injection with signing secrets; RCE via untrusted parser; plaintext credential exfiltration over internet. |
| **High** | Practical compromise by a same-LAN peer, co-resident malicious app, or routine user action with severe impact (secret exposure, SSRF, ZIP Slip/OOM crash, malicious SQLite execution, unauthorized privileged IPC). | ZIP Slip path traversal; SQLite trigger/view execution on untrusted DB; recoverable Wi-Fi password/ciphertext in shared logs or saved state; exported receiver allowing arbitrary device control without validation. |
| **Medium** | Exploitable under realistic preconditions with bounded blast radius, meaningful privacy leakage, or missing defense-in-depth on sensitive surfaces. | Unscoped `usesCleartextTraffic="true"` allowing accidental WAN HTTP; unbounded HTTP response/GENA DoS on LAN; cloud Auto Backup uploading sensitive home/location data contrary to local-only privacy claims; XML injection in LAN SOAP requests; unpinned third-party GitHub Actions. |
| **Low** | Minor information disclosure, edge-case hardening gaps, limited-impact spoofing, or partial redaction inconsistencies. | Missing `FLAG_SECURE` on credential screen; missing keyboard incognito flag; non-atomic temp file cleanup; diagnostic export containing router BSSID/MAC without redaction option; noisy debug logs in release builds. |
| **Hardening / Info** | No direct exploit path today, or inherent to external hardware protocol, but worth documenting for architectural resilience or future-proofing. | Legacy hardware-mandated AES-128-CBC/MD5 encryption; unauthenticated Belkin UPnP LAN protocol constraints; defense-in-depth compiler/lint/test guardrails. |

---

## 3. Finding Card Template

Every detailed finding in an audit report should include:
- **ID & Title** (e.g., `SEC-01`, `PRIV-01`, `CI-01`)
- **Severity & Classification** (`High` | CWE-xxx / OWASP MASVS-xxx / LINDDUN-xxx)
- **Location** (exact clickable file paths + line numbers)
- **Evidence** (concise factual summary of what the code does)
- **Attack / Privacy Scenario** (attacker class, preconditions, step-by-step exploitation or leak, blast radius)
- **Remediation** (minimal concrete fix + verification / regression test idea)
