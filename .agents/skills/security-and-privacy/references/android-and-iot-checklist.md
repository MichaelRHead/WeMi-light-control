# Android & Local-LAN / IoT Security Checklist

Reference guide for the `security-and-privacy` skill. Aligned with OWASP MASVS v2 / MASTG and OWASP IoT Top 10.

---

## 1. Android Manifest, IPC & Component Exposure (MASVS-PLATFORM)
- **`android:exported`**: Inspect every `<activity>`, `<service>`, `<receiver>`, and `<provider>`. Any component with an `<intent-filter>` must explicitly justify `exported="true"` and validate all incoming `Intent` actions, extras, data URIs, and widget/app IDs.
- **Broadcast Receivers & Widgets**: Verify widget `AppWidgetProvider` receivers handle custom action strings safely — can a malicious co-resident app broadcast `ACTION_TOGGLE` with forged extras (`deviceId`, `roomName`, `appWidgetId`) to toggle physical switches or DoS the LAN? Check whether custom internal actions are guarded by `android:exported="false"` (via a separate non-exported receiver) or signature permissions, or validated strictly against locally stored widget config.
- **`PendingIntent` Hygiene**: Require `PendingIntent.FLAG_IMMUTABLE` (unless inline reply/bubbles require `FLAG_MUTABLE` with explicit `ComponentName`). Always use explicit `Intent(context, TargetClass::class.java)`.
- **`FileProvider` Scope**: Check `res/xml/*file_paths*.xml`. Ensure only a dedicated export directory (e.g., `files-path path="exports/"` or `cache-path path="telemetry/"`) is exposed — never `path="."` across all of `filesDir` or `cacheDir` (which could expose databases, backups, or other internal files if a URI path is guessed or reused). Check `FLAG_GRANT_READ_URI_PERMISSION` (never `WRITE` unless required).
- **Task & UI Security**: Check `FLAG_SECURE` on screens displaying or collecting Wi-Fi passwords/secrets, `android:allowTaskReparenting`, deep link / URI scheme validation, and `rememberSaveable` / `onSaveInstanceState` for sensitive text fields.

## 2. Network Security & Custom HTTP/UPnP/SSDP/GENA Clients (MASVS-NETWORK)
- **Cleartext Traffic Scoping**: Prefer an explicit `android:networkSecurityConfig` restricting cleartext HTTP strictly to private LAN/RFC 1918 or link-local IP ranges (or explicit local targets) rather than blanket `android:usesCleartextTraffic="true"` (which allows accidental plaintext HTTP to arbitrary internet domains). Note Android's `network_security_config` XML limitations (it supports `<domain-config>` by domain/IP literals rather than CIDR blocks, so app-layer host/IP validation in the HTTP client is essential as the primary enforcer!).
- **SSRF & Arbitrary Host Redirection**:
  - Whenever a URL is parsed from untrusted LAN data (SSDP `LOCATION` header, UPnP `/setup.xml`, `rules1#FetchRules` `<ruleDbPath>` URL, HTTP `Location` redirect), validate:
    1. Scheme is strictly `http` (or `https`).
    2. Host matches the expected peer IP or a validated private LAN IPv4 address (block public internet IPs, cloud metadata `169.254.169.254`, unexpected `127.0.0.1` loopback in production unless test-injected, IPv6 mapped tricks, and userinfo `user:pass@host`).
    3. Port is in a valid range (`1..65535`, ideally constrained to expected hardware ports or peer endpoint).
  - Never allow CRLF (`\r`, `\n`) in host, path, SOAPAction, or header values (HTTP request smuggling / header injection).
- **Custom HTTP/1.1 Framing & Resource Bounds**:
  - Enforce hard upper bounds on: status/header line lengths, total header bytes, header count, `Content-Length`, chunked transfer total bytes, and uncompressed payload bytes.
  - Enforce both `connectTimeout`, `soTimeout`, **and** an overall wall-clock read deadline (so a malicious LAN peer trickling 1 byte every `soTimeout - 100ms` cannot stall threads forever — slowloris).
  - Verify `Content-Length` integer overflow / negative value handling and `Transfer-Encoding: chunked` hex length bounds.
- **Embedded Local HTTP Servers (e.g., UPnP GENA Callback Server)**:
  - Check bind address and port exposure.
  - Bound concurrent connections (`Semaphore` / thread pool cap) and per-connection header/body size and read timeouts so a rogue LAN host cannot exhaust threads or heap.
  - Authenticate or tightly scope incoming `NOTIFY` requests: verify `SID` matches an active subscription, verify source peer IP matches the subscribed device IP, and ignore or rate-limit unknown SIDs.
- **XML / SOAP Injection & Parser Safety**:
  - Escape XML special characters (`&`, `<`, `>`, `"`, `'`) in **every** interpolated value (friendly names, SSIDs, passwords, rule bodies, device UDNs, coordinates).
  - If using `XmlPullParser` / `DocumentBuilderFactory` / `SAXParser`, disable DTDs and external entities (`FEATURE_PROCESS_DOCDECL = false`, `disallow-doctype-decl = true`).
  - If using regexes on untrusted XML/SSDP/HTTP strings, audit for nested quantifiers / catastrophic backtracking (ReDoS) and oversized input truncation before matching.

## 3. Untrusted ZIP & SQLite Processing (MASVS-CODE / MASVS-STORAGE)
- **ZIP Unpacking**:
  - Reject any entry whose name contains `..`, starts with `/` or `\`, contains `:` (drive letter), or resolves outside the canonical target directory (`dest.canonicalPath.startsWith(root.canonicalPath + File.separator)`).
  - Enforce a strict **decompressed byte counter** while streaming from `ZipInputStream` (never trust `entry.size`, which can be `-1` or spoofed in the ZIP header) and cap total entry count.
- **Untrusted SQLite Databases (e.g., IoT `rules.db` fetched over LAN)**:
  - Before invoking native `SQLiteDatabase.openDatabase`: check file magic (`SQLite format 3\u0000`), size cap, and open in a private temp directory.
  - **SQLite Trigger / View / Virtual Table / Schema Trap**: If an app executes `INSERT`, `UPDATE`, or `DELETE` against an untrusted SQLite DB received over LAN, a malicious peer can embed SQLite `TRIGGER`s, `GENERATED` columns, `CHECK` constraints, or `VIEW`s that execute arbitrary SQL statements (corrupting other tables, causing infinite recursion/DoS, or exploiting SQLite functions). Audit whether the app inspects `sqlite_master` (`type IN ('trigger', 'view')` or virtual tables) before running mutations or `PRAGMA integrity_check`!
  - Ensure all SQL queries use bound `?` parameters (`rawQuery(sql, args)`, `execSQL(sql, args)`). Never concatenate strings from untrusted DB rows or UI fields into SQL.
  - If writing a DB back to embedded C firmware that runs unparameterized `snprintf` SQL queries, enforce strict allowlists/validation on string fields written to the DB.
  - Clean up temporary `.db`, `-journal`, `-wal`, `-shm` files in `finally` blocks.

## 4. Cryptography & Wi-Fi Provisioning (MASVS-CRYPTO)
- **Legacy IoT Crypto Containment**: Legacy devices often mandate broken schemes (e.g., OpenSSL MD5 `EVP_BytesToKey` + AES-128-CBC with zero/derived IV and no MAC, keyed from public MAC/serial!). Verify:
  1. The legacy primitive is isolated strictly to the hardware wire protocol and **never reused** for local app storage, auth tokens, or exports.
  2. **CRITICAL**: Because the key & IV are derived purely from the device's **public MAC address and serial number** (both visible over the open setup AP and recorded in telemetry!), any logged AES ciphertext from `ConnectHomeNetwork` is **trivially decryptable back to the user's plaintext home Wi-Fi password** if the MAC and serial are also present! Therefore, ciphertext **must** be redacted in all logs, buffers, and exports.
- **Randomness & Comparisons**: Use `java.security.SecureRandom` for nonces, tokens, or IDs requiring unpredictability; use `MessageDigest.isEqual` for secret/MAC comparisons.

## 5. Build, Packaging, Logging & Supply Chain (MASVS-RESILIENCE)
- **Release Logging**: Audit `android.util.Log.d/v/i/w/e` and `println` calls across production code. Check `proguard-rules.pro` for `-assumenosideeffects` stripping `Log.v`/`Log.d` in release builds, and ensure secrets/PII are never passed to `Log.*` even in debug builds.
- **R8 & Debuggable**: Verify release builds have `isMinifyEnabled = true`, `isDebuggable = false` (default), and no test-only backdoors reachable in release APKs.
- **CI/CD & Scripts**: Inspect `.github/workflows/*.yml` and `scripts/*.{sh,py,java}` for unpinned actions, workflow expression injection (`${{ ... }}`), overly broad `GITHUB_TOKEN` scopes, insecure temp files, and secret handling.
