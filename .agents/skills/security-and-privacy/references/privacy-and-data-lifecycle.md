# Privacy Engineering & Data-Lifecycle Checklist

Reference guide for the `security-and-privacy` skill. Grounded in LINDDUN, NIST Privacy Framework, GDPR/CCPA data-minimization principles, and Google Play Data Safety requirements.

---

## 1. Personal Data & Smart-Home Quasi-Identifier Inventory

In local-first and smart-home apps, privacy risks often come not from traditional PII (email/SSN) but from **high-precision quasi-identifiers and behavioral signals**:

| Data Element | Why It Is Privacy-Sensitive | Key Surfaces to Check |
| :--- | :--- | :--- |
| **Home Wi-Fi Password** | Grants full LAN & internet network access | Setup UI state (`rememberSaveable`), IME keyboard cache, `ViewModel` state, SOAP packet log, telemetry export, crash/exception text, Logcat, AES ciphertext + MAC/serial pair. |
| **Wi-Fi Router SSID & BSSID (MAC)** | BSSIDs map directly to physical street addresses via public Wi-Fi geolocation databases (WiGLE, Apple/Google location services); SSIDs often contain family surnames or addresses. | Room DB, `SharedPreferences`, telemetry exports, screenshots, cloud Auto Backup, Logcat. |
| **Solar Latitude / Longitude** | Exact physical home coordinates (`LOCATIONINFO` in switch rules DB, UI dialogs, backups). | Hardware rules backups (`hardware_rules_backups/*.zip`), in-memory snapshots, telemetry exports, logs, cloud backups. |
| **Device Serial Numbers, MACs & UDNs** | Globally unique, permanent hardware identifiers enabling cross-export linkability. | Room DB, telemetry exports, Logcat, cloud backups, hardware backups. |
| **Home Names, Room Names & Device Names** | Reveals household structure, occupancy, occupants' names (e.g., "Sarah's Bedroom"), and security-relevant devices ("Front Door", "Alarm", "Porch"). | Room DB, widgets (lock screen), telemetry exports, backups. |
| **Motion Timestamps & Lighting Schedules** | Reveals exact home occupancy patterns, sleep/wake hours, and vacation absences. | Room DB (`lastMotionEpochMs`), on-device `rules.db` backups, telemetry exports, widgets. |
| **Local IP Topology & Subnet** | Reveals internal network layout, router IP, active devices, and VPN/ChromeOS virtual networking. | Telemetry exports, Logcat, Room DB. |

---

## 2. Backup, Transfer & Residual Data Audit

Check every storage location against the app's stated privacy guarantees:

1. **Android Auto Backup & Device Transfer**:
   - Inspect `AndroidManifest.xml` (`android:allowBackup`, `android:fullBackupContent`, `android:dataExtractionRules`).
   - Remember: **If `allowBackup="true"` and `<backup-rules>` only `<exclude>` specific files, EVERYTHING ELSE (Room SQLite databases, `SharedPreferences`, widget configs, internal files) IS UPLOADED TO GOOGLE DRIVE CLOUD BACKUP by default!**
   - Verify whether `PRIVACY_POLICY.md` or `DATA_SAFETY_GUIDE.md` claims "never leaves your device" or "zero cloud storage" while Room DB (`wemi_switch_database` with SSIDs, MACs, serials, room names, motion timestamps) or `SharedPreferences` (`wemi_home_widgets`, last subnet hints) are implicitly included in `<cloud-backup>`!
   - Remember that `noBackupFilesDir` is excluded from Auto Backup by the OS, whereas `filesDir`, `databases/`, and `shared_prefs/` are **included** unless explicitly excluded in BOTH `backup_rules.xml` (API <= 30) and `data_extraction_rules.xml` (`<cloud-backup>` and `<device-transfer>`, API 31+).
2. **Data Erasure & Reset Completeness**:
   - When a user invokes a "Reset", "Clear Data", or "Delete Device / Home" action, verify **every** backing store is cleared:
     - Room tables (`devices`, `homes`, etc.)
     - `SharedPreferences` (widget bindings, subnet hints, cached preferences)
     - On-disk backups (e.g., `hardware_rules_backups/` containing `LOCATIONINFO` lat/lon and rules!)
     - Exported telemetry files (`wemi-telemetry-latest.txt` or cached share files)
     - In-memory caches (`SoapLogStore`, setup diagnostics buffer, ViewModel state flows).
3. **Diagnostic Exports & Clipboard Hygiene**:
   - Every user-initiated export (copy-to-clipboard or Save/Share file) should:
     - Redact all secrets AND any key-derivable ciphertexts (e.g., Wi-Fi password AES blobs).
     - Minimize or mask quasi-identifiers (BSSIDs, full MACs, serials, solar lat/lon) or clearly inform the user what sensitive home/network identifiers are included before sharing.
     - Use `ClipDescription.EXTRA_IS_SENSITIVE` (API 33+) if sensitive content could ever reach clipboard, and keep exported files strictly local and ephemeral/overwritten cleanly.

---

## 3. UI, Keyboard & Lock-Screen Privacy
- **Password / Secret Input Fields**:
  - Use `PasswordVisualTransformation()`, `KeyboardType.Password`, `autoCorrectEnabled = false`, and avoid persisting the password in `rememberSaveable` (which serializes to Android's saved-instance `Bundle` and can survive in ActivityManager state across process death!).
  - Clear password state immediately after submission or dialog dismissal.
  - Consider `FLAG_SECURE` while a password field is visible or sensitive diagnostics are shown (balancing user screenshot troubleshooting needs if clearly documented).
- **Home Screen Widgets & Android TV**:
  - Note whether widgets display room/device names and live ON/OFF status on shared/unlocked screens or lock screens (on Android versions supporting lock-screen widgets).

---

## 4. Policy, Manifest & Store Compliance Parity
Cross-check four artifacts simultaneously:
1. **Actual Code & Dependencies** (permissions used, sockets opened, files written, libraries bundled).
2. **`AndroidManifest.xml` & Backup XMLs** (declared permissions, `allowBackup`, exported components, `usesCleartextTraffic`).
3. **Privacy Policy (`PRIVACY_POLICY.md` / web policy)** (exact claims about data collection, transmission, cloud storage, retention, deletion, location, third parties).
4. **Store Disclosures (`DATA_SAFETY_GUIDE.md`, Play listing)** (data types collected/shared, ephemeral processing, encryption in transit, deletion mechanism).

Flag **any discrepancy** where documentation over-promises ("data never leaves your device", "no location data stored") compared to actual technical behavior (e.g., cloud Auto Backup of Room DB, on-disk storage of solar coordinates in `hardware_rules_backups`, or telemetry files containing geolocatable BSSIDs).
