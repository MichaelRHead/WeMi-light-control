# Troubleshooting, Screenshots & Sharing Telemetry Logs

[← Back to User Guide Index](../../USER_GUIDE.md) • [Architecture & Debugging Spec (`DEBUGGING_AND_SUPPORT.md`)](../../DEBUGGING_AND_SUPPORT.md)

> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR OTHER AGENTS)**: This guide (`docs/user-guide/telemetry-and-troubleshooting.md`), [`DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md), and [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](../../.agents/skills/wemi-device-telemetry-debug/SKILL.md) are **maintained exclusively by the Antigravity Debugging & Support Agent**. Other agents must read and respect these specifications without overwriting or deleting them.

Because switches from Belkin's discontinued smart switch platform operate entirely on your private home Wi-Fi (`192.168.x.x`) or on a factory-reset switch's isolated setup hotspot (`10.22.22.1`), **WeMi Light Control** includes built-in **on-screen diagnostic panels** and a **one-tap telemetry file exporter (`wemi-telemetry-latest.txt`)**.

Whenever you need help troubleshooting `10.22.22.1` Wi-Fi setup, switch discovery, Home Screen widgets, or UPnP/SOAP commands, you can share diagnostic details in **two ways**:
1. **Take a screenshot** of the on-screen debug panels and attach the image.
2. **Export or copy the recorded telemetry file (`wemi-telemetry-latest.txt`)** with a single tap on **`Save/Share`** or **`Copy`**.

> [!NOTE]
> **Privacy & Local-Only Storage Protection**: The Wi-Fi password input in the `10.22.22.1` setup wizard is masked by default (with an eye icon to show/hide it), and neither the on-screen debug panel nor the exported `wemi-telemetry-latest.txt` file ever displays or records your plaintext or encrypted Wi-Fi password (only `Method 1/2/3`, `addLengths`, and `plainLen` / `encLen` are logged). Any `<password>` or raw base64 `<ruleDbBody>` XML payloads in SOAP log summaries are automatically redacted before export, and optional Sunrise/Sunset Solar Location coordinates are never written to telemetry exports (only `solarLoc=set` or `solarLoc=unset`). In addition, both `wemi-telemetry-latest.txt` and local pre-write switch rule backups (`hardware_rules_backups`) are explicitly excluded from Android cloud Auto-Backup and device-to-device transfer so LAN metadata and switch backups stay strictly on your device.

---

## 1. Method A: Providing an On-Screen Diagnostic Screenshot

You can take a standard Android screenshot (**Power + Volume Down**) of any of the three diagnostic views below and share the image directly (or use **`Copy`** / **`Save/Share`** for full text traces):

### A1. During `10.22.22.1` Switch Setup (`SETUP DIAGNOSTICS`)
1. Open the **Provision New Switch AP (`10.22.22.1`)** dialog (by tapping the Deep Teal **Smart Switch Setup AP Detected (`10.22.22.x`)** banner at the top of the **Devices** tab on Android phones/tablets, or directly via **`More` → `Provision New Switch AP (10.22.22.1)`** — on Chromebooks, Android runs in a virtual `100.115.92.x` network so open it via **`More`** after connecting ChromeOS Wi-Fi to the switch's setup hotspot).
2. Look for the **`SETUP DIAGNOSTICS`** dark terminal card inside the dialog:
   - **Compact While Idle, Auto-Expands on Errors or Traces**: While waiting for input, the card stays compact — tap **`Details`** to inspect the live `[NET]` / `[PROBE]` lines or **`Hide`** to collapse them. Whenever provisioning starts or fails (`NetworkStatus = 0` or `2`, or unreachable), the card automatically expands so the full trace is immediately visible.
   - **Automatic Hold on Failure**: If the switch fails to join your home Wi-Fi (`NetworkStatus = 0` or `2`) or cannot be reached on `10.22.22.1`, the app **automatically keeps the switch's `10.22.22.1` setup AP open** (`[STEP 6 HOLD] ... Skipping CloseSetup so 10.22.22.1 AP stays open for retry`) and keeps the dialog on screen so you can tap **`Copy`** / **`Save/Share`** or retry another AES option immediately.
   - **Keeping the Dialog Open on Success**: If you also want to capture diagnostics after a *successful* provisioning run (`NetworkStatus = 1` or `3`), tap **`Details`** inside `SETUP DIAGNOSTICS` and toggle the **`Keep Dialog Open on Success`** chip ON before tapping **`Provision Switch`**.
3. Tap **`Copy`** or **`Save/Share`** inside **`SETUP DIAGNOSTICS`** (recommended), or take a screenshot showing the **`SETUP DIAGNOSTICS`** terminal box and status banner above it (your Wi-Fi password stays masked unless you tap the show-password icon).

### A2. Live UPnP / SOAP Packet Log (`More` Tab)
1. Tap the **`More`** tab in the bottom navigation bar.
2. Scroll down to the **`UPnP / SOAP Packet Log`** card at the bottom of the screen.
3. Each entry displays the timestamp (`h:mm:ss a`), switch or widget name, SOAP/HTTP action (e.g., `SetBinaryState`, `GetBinaryState`, `GetMetaInfo`, `WiFiSetup1#ConnectHomeNetwork`, `GET rulesservice.xml`, `rules1#FetchRules`, `GET /rules.db`, `timesync1#TimeSync`, `rules1#StoreRules(v<n>)`, `rules1#RestoreBackup`, `GENA PropChange`, `GENA#SUBSCRIBE (Skipped)` on ChromeOS VMs using `15s` SOAP polling, or headless Home Screen `Widget#Toggle`), target `http://<ip>:<port>/upnp/control/...` URL, and HTTP status / exact offline failure reason / round-trip latency in milliseconds.
4. Take a screenshot of the packet log card to share recent network traffic (including background Home Screen widget taps and live on-device rule reads, writes, clock syncs, and backup restores).

### A3. Per-Switch Hardware, Latency & On-Device Rules Drawer (`Devices` Tab)
1. On the **`Devices`** tab, tap the body of any reachable switch row (outside the right-side power ring) to expand its inline diagnostics drawer.
2. Tap **`Ping RTT`** to run a live `GetBinaryState` + `GetMetaInfo` check.
3. For switches, dimmers, and plugs, check the **`On-Device Rules`** section at the bottom of the drawer (`HardwareRuleRowItems`) and tap **`Inspect Rules`** (or **`Refresh`** once inspected) to preview the switch's stored database version (`DB v...`), any **`Switch clock needs sync`** alert with a one-tap **`Sync Clock`** chip, quick **`Add Rule`** and **`Manage Rules`** shortcuts, and up to 4 decoded `trigger → action` rule cards (with **`• Paused`** badges and a `+N more rule(s) in Rules tab` overflow link).
4. Take a screenshot showing the switch's `Endpoint (IP:Port)`, `SN`, `Firmware`, live SOAP round-trip latency badge, and **`On-Device Rules`** card.

---

## 2. Method B: Exporting or Copying a Telemetry File (`wemi-telemetry-latest.txt`)

When a setup, discovery, on-device rules, or Home Screen widget trace is longer than a single screen, you can export a complete text report (`wemi-telemetry-latest.txt`) in one tap:

### B1. Exporting from the `10.22.22.1` Setup Dialog (`Save/Share` or `Copy`)
Inside the **`SETUP DIAGNOSTICS`** header bar in the **Provision New Switch AP (`10.22.22.1`)** dialog (available whether the panel is collapsed or expanded):
- **Tap `Save/Share`**:
  1. Writes the diagnostic report to `files/wemi-telemetry-latest.txt` in the app's private storage.
  2. Copies the entire report to your Android clipboard.
  3. Opens Android's **Share Sheet (`Save or Share WeMi Telemetry`)** so you can save `wemi-telemetry-latest.txt` to **Files / Google Drive**, email it, or send it directly to chat.
- **Tap `Copy`**:
  1. Copies the version-stamped `=== WeMi Setup AP Diagnostic Report (v35.0 [35] • ...) ===` block to your clipboard and updates `files/wemi-telemetry-latest.txt`.
  2. Paste the copied text directly into your chat message or issue report.

### B2. Exporting from the `More` Tab (`Save/Share Log`)
In the header of the **`UPnP / SOAP Packet Log`** card on the **`More`** tab:
- **Tap `Save/Share Log`**:
  1. Generates a comprehensive **5-section** report (`wemi-telemetry-latest.txt`, stamped with `App Version: v35.0 (versionCode=35, com.aistudio.wemiswitch.kxpqzr)`) containing:
     - **`--- [1. SETUP AP & PROVISIONING SNAPSHOT] ---`**: All hardware probe, encryption flag, AP scan, `[TARGET AP]` match/warning, and `[STEP 1..6]` provisioning traces from your most recent setup attempt (keeping `[NET]` subnet/Wi-Fi-binding/ChromeOS VM flags consistent with the captured session).
     - **`--- [2. REMEMBERED WI-FI HOMES] ---`**: All remembered Wi-Fi Home profiles (`networkKey`, `homeName`, `ssid`, `/24` subnet — or `subnet=unset gw=none` on a fresh Chromebook before the first switch is discovered — and gateway IP).
     - **`--- [3. REGISTERED SWITCHES & DEVICES] ---`**: Every switch in your current inventory with its normalized room, Wi-Fi Home (`home=<networkKey>`), IP:port, state (`ON`, `OFF_STANDBY`, `OFFLINE`), dimmer brightness/Night Mode (`dim=<n>%`, `night=1`), latency (`ms`), hardware SKU, firmware, serial number, MAC, canonical UPnP `UDN`, cached on-device rules capability (`rulesCap=...`, optional `lp=1` and `rulesFw=...`), and — when inspected — a privacy-safe `  rules:` summary (`dbV=...`, rule count, SQLite integrity, clock drift, `needsSync`, local backup count, and `solarLoc=set/unset` without exact coordinates).
     - **`--- [4. UPNP / SOAP PACKET LOG] ---`**: Recent UPnP/SOAP/GENA/SSDP log entries — including ChromeOS VM discovery/swept subnets, `GENA#SUBSCRIBE (Skipped)` polling notices, on-device rule inspections, live writes, clock syncs, and backup restores (`GET rulesservice.xml`, `rules1#FetchRules`, `GET /rules.db`, `timesync1#TimeSync`, `rules1#StoreRules`, `rules1#RestoreBackup`), Top-Header **`All Off`**, Room Master toggles, Android TV D-Pad switch/dimmer commands, and **headless Home Screen widget taps/timeouts** — with timestamps, endpoints, HTTP statuses, exact offline failure causes, and redacted single-line `xml:` payload summaries.
     - **`--- [5. HOME SCREEN WIDGETS] ---`**: Every configured Home Screen light or room widget with its `appWidgetId`, bound serial/UDN/room/Wi-Fi Home, and whether it resolves to active switches or is `ORPHANED` (needs reconfiguring).
  2. Copies the full report to your clipboard and opens the Android **Share Sheet**.

### B3. Pulling `wemi-telemetry-latest.txt` via USB (`adb`)
If your Android phone, Chromebook (ARCVM), or Android TV is connected to a computer with USB debugging enabled, you can also pull the latest recorded file directly from the terminal after tapping **`Copy`**, **`Save/Share`**, or **`Save/Share Log`**:

```bash
adb exec-out run-as com.aistudio.wemiswitch.kxpqzr cat files/wemi-telemetry-latest.txt > wemi-telemetry-latest.txt
```

---

## 3. Quick Reference: What the Telemetry Lines Mean

| Line Prefix | Example | What It Tells You |
| :--- | :--- | :--- |
| **`[NET]`** | `mode=REAL-LAN ip=10.22.22.2 (wlan0) gw=10.22.22.1 setupSubnet=true wifiBound=true` *(or `ip=100.115.92.6 (eth1) ... arcVmNat=true dns=192.168.86.1` on ChromeOS)* | Confirms whether the app is in `REAL-LAN` mode, connected to the switch's `10.22.22.x` Wi-Fi AP (`setupSubnet=true`), binding sockets directly to Wi-Fi/Ethernet (`wifiBound=true`), or running inside ChromeOS ARCVM (`arcVmNat=true`) with its detected IPv4 `dns=` hint. |
| **`[PROBE]`** | `host=10.22.22.1 -> :49152/setup.xml=HTTP_200(14ms,1420B)` | Shows per-port probe diagnostics across all 9 UPnP ports (`49151..49159`) and which descriptor path (`/setup.xml` or `/Lightsetup.xml`) responded with `HTTP_200`. |
| **`[XML]`** | `/setup.xml :49152 name="Smart Switch" model=LightSwitch sku=... fw=WeMo_WW_...` | Hardware model name, default friendly name, SKU, and firmware version read from `/setup.xml`. |
| **`[FLAGS]`** | `rtos=0 iot=0 binaryOption=0 new_algo=0 -> autoMethod=1 autoLen=true` | Hardware encryption flags from `/setup.xml` and which OpenSSL AES-128-CBC method (`1`, `2`, or `3`) and length suffix (`autoLen`) were auto-selected. |
| **`[ID]`** | `mac=EC1A59... sn=221517... (src=GetMetaInfo) meta="..."` | The switch's MAC address and Serial Number (required to salt the AES-128-CBC key) and which SOAP/XML source supplied them (`GetMetaInfo`, `GetMetaInfo+setup.xml`, `setup.xml`, or `basicevent1#GetMacAddr`). |
| **`[SCAN]`** | `GetApList=HTTP_200(1240ms,count=4)` or `Skipped (Setup AP unreachable)` | Results of the switch's `WiFiSetup1#GetApList` (or `GetNetworkList`) 2.4 GHz site survey (automatically skipped if `[PROBE]` timed out so you don't wait an extra `8.5s`). |
| **`[TARGET AP]`** | `"MyHome24" found in ApList: Ch=6 Auth=WPA2PSK/AES Signal=88%` | The exact 2.4 GHz channel, authentication mode, and cipher that will be sent in `ConnectHomeNetwork`. |
| **`[STEP 4 CRYPTO]`** | `Method=1 addLengths=true plainLen=12 encLen=68 suffix=0c40` | Confirms the password encryption parameters (`Method 1/2/3` and whether the 4-hex-char `xxyy` length suffix was appended) without exposing your password. |
| **`[STEP 5 SEND #1]`** | `ConnectHomeNetwork -> HTTP_200 (18ms) PairingStatus=1` | Confirms the switch accepted the `ConnectHomeNetwork` SOAP command. |
| **`[POLL #n]`** | `GetNetworkStatus -> Status=1 (14ms)` | Status codes from the switch while joining your router: **`1` = Connected**, **`3` = Connected (Handshake Complete)**, **`0` = Still Connecting / Handshake Failed**, **`2` = Bad Password / Auth Rejected** (reports the exact `Method` and `lengths` tried on `>=8`-char passwords so you know which encryption chip to toggle on retry). |
| **`[STEP 6 CLOSE/HOLD]`** | `CloseSetup -> success` or `Skipping CloseSetup so 10.22.22.1 AP stays open for retry` | Shows whether the setup AP was closed after a successful connection (`1` or `3`) or intentionally kept open (`kept_open_for_retry`) so you can retry immediately. |
| **`[HOME]`** | `key=wifi_lan_chromeos_default name="My Home" ssid="Local LAN" subnet=unset gw=none` | Lists each saved Wi-Fi Home profile and its physical `/24` subnet (`subnet=unset` on a fresh Chromebook until the first switch is discovered or a manual subnet is scanned). |
| **`  rules:` (Section 3)** | `dbV=7 rules=2 integrity=true driftSec=0 needsSync=false backups=2 solarLoc=set` | Privacy-safe on-device rules health readout for inspected switches (DB version, rule count, SQLite integrity, clock drift, retained local backup count, and whether Solar Location is set — without exposing coordinates). |
| **`rules1#StoreRules` / `timesync1#TimeSync` / `rules1#RestoreBackup`** | `Verified DB v8` or `Blocked by anti-brick validator` | Shows live on-device rule saves, clock syncs, and raw-ZIP backup restores (with `<ruleDbBody>` blobs automatically redacted). If blocked or rejected, check the `xml:` line or restore a saved backup from the **Rules** tab. |
| **`GENA#SUBSCRIBE (Skipped)`** | `Skipped in ChromeOS VM (NAT guest IP 100.115.92.6); using 15s SOAP state polling` | Confirms ChromeOS ARCVM intentionally skipped unreachable GENA push callbacks (protecting switch sockets) and switched to `15s` SOAP polling instead. |
| **`[WIDGET #id]`** | `type=DEVICE name="Patio" devId=7 sn=... udn=... home=... -> resolved=#7 "Patio" (ON)` *(or `Widget#Toggle(ROOM)` -> `Updated 2/2 switch(es)`)* | Confirms each placed Home Screen widget's bound target (serial, optional canonical `udn`, room, and Wi-Fi Home), whether it resolves or is `ORPHANED`, and how many switches responded on Room widget taps (`0/N` logs `[ERR]`). |

