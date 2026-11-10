# Troubleshooting, Screenshots & Sharing Telemetry Logs

[← Back to User Guide Index](../../USER_GUIDE.md) • [Architecture & Debugging Spec (`DEBUGGING_AND_SUPPORT.md`)](../../DEBUGGING_AND_SUPPORT.md)

> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR OTHER AGENTS)**: This guide (`docs/user-guide/telemetry-and-troubleshooting.md`), [`DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md), and [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](../../.agents/skills/wemi-device-telemetry-debug/SKILL.md) are **maintained exclusively by the Antigravity Debugging & Support Agent**. Other agents must read and respect these specifications without overwriting or deleting them.

Because switches from Belkin's discontinued smart switch platform operate entirely on your private home Wi-Fi (`192.168.x.x`) or on a factory-reset switch's isolated setup hotspot (`10.22.22.1`), **WeMi Light Control** includes built-in **on-screen diagnostic panels** and a **one-tap telemetry file exporter (`wemi-telemetry-latest.txt`)**.

Whenever you need help troubleshooting `10.22.22.1` Wi-Fi setup, switch discovery, or UPnP/SOAP commands, you can share diagnostic details in **two ways**:
1. **Take a screenshot** of the on-screen debug panels and attach the image.
2. **Export or copy the recorded telemetry file (`wemi-telemetry-latest.txt`)** with a single tap on **`Save/Share`** or **`Copy`**.

> [!NOTE]
> **Privacy & Wi-Fi Password Protection**: Neither the on-screen debug panel nor the exported `wemi-telemetry-latest.txt` file ever displays or records your plaintext Wi-Fi password. Only the encryption method (`Method 1/2/3`), length-suffix flag (`addLengths`), and plaintext/encrypted lengths (`plainLen` / `encLen`) are logged.

---

## 1. Method A: Providing an On-Screen Diagnostic Screenshot

You can take a standard Android screenshot (**Power + Volume Down**) of any of the three diagnostic views below and share the image directly:

### A1. During `10.22.22.1` Switch Setup (`SETUP DEBUG DETAILS (SCREENSHOT)`)
1. Open the **Provision New Switch AP (`10.22.22.1`)** dialog (by tapping the green **Switch Setup AP Detected (`10.22.22.1`)** banner at the top of the **Devices** tab, or via **`More` → `Provision New Switch AP (10.22.22.1)`**).
2. Scroll to the **`SETUP DEBUG DETAILS (SCREENSHOT)`** terminal card inside the dialog:
   - **Automatic Hold on Failure**: If the switch fails to join your home Wi-Fi (`NetworkStatus = 0` or `2`) or cannot be reached on `10.22.22.1`, the app **automatically keeps the switch's `10.22.22.1` setup AP open** (`[STEP 6 HOLD] ... Skipping CloseSetup so 10.22.22.1 AP stays open for retry`) and keeps the dialog on screen so you can screenshot the full trace immediately.
   - **Keeping the Dialog Open on Success**: If you also want to capture a screenshot after a *successful* provisioning run (`NetworkStatus = 1` or `3`), tap the **`Keep Dialog Open on Success`** chip inside the debug panel before tapping **`Provision Switch`**.
3. Take a screenshot showing the **`SETUP DEBUG DETAILS (SCREENSHOT)`** terminal box (and the status banner above it) and attach it to your message.

### A2. Live UPnP / SOAP Packet Log (`More` Tab)
1. Tap the **`More`** tab in the bottom navigation bar.
2. Scroll down to the **`UPnP / SOAP Packet Log`** card at the bottom of the screen.
3. Each entry displays the timestamp (`HH:mm:ss.SSS`), direction (`TX→RX`, `GENA`, `SCAN`, `SETUP`), switch name, SOAP action (e.g., `SetBinaryState`, `GetBinaryState`, `GetMetaInfo`, `WiFiSetup1#ConnectHomeNetwork`), target `http://<ip>:<port>/upnp/control/...` URL, and HTTP status / round-trip latency in milliseconds.
4. Take a screenshot of the packet log card to share recent network traffic.

### A3. Per-Switch Hardware & Latency Drawer (`Devices` Tab)
1. On the **`Devices`** tab, tap the info icon (`ⓘ`) or body of any switch row to expand its inline diagnostics drawer.
2. Tap **`Ping RTT`** to run a live `GetBinaryState` + `GetMetaInfo` check.
3. Take a screenshot showing the switch's `Endpoint (IP:Port)`, `SN`, `Firmware`, and live SOAP round-trip latency badge.

---

## 2. Method B: Exporting or Copying a Telemetry File (`wemi-telemetry-latest.txt`)

When a setup or discovery trace is longer than a single screen, you can export a complete text report (`wemi-telemetry-latest.txt`) in one tap:

### B1. Exporting from the `10.22.22.1` Setup Dialog (`Save/Share` or `Copy`)
Inside the **`SETUP DEBUG DETAILS (SCREENSHOT)`** header bar in the **Provision New Switch AP (`10.22.22.1`)** dialog:
- **Tap `Save/Share`**:
  1. Writes the diagnostic report to `files/wemi-telemetry-latest.txt` in the app's private storage.
  2. Copies the entire report to your Android clipboard.
  3. Opens Android's **Share Sheet (`Save or Share WeMi Telemetry`)** so you can save `wemi-telemetry-latest.txt` to **Files / Google Drive**, email it, or send it directly to chat.
- **Tap `Copy`**:
  1. Copies the `=== WeMi Setup AP Diagnostic Report ===` block to your clipboard and updates `files/wemi-telemetry-latest.txt`.
  2. Paste the copied text directly into your chat message or issue report.

### B2. Exporting from the `More` Tab (`Save/Share Log`)
In the header of the **`UPnP / SOAP Packet Log`** card on the **`More`** tab:
- **Tap `Save/Share Log`**:
  1. Generates a comprehensive 4-section report (`wemi-telemetry-latest.txt`) containing:
     - **`--- [1. SETUP AP & PROVISIONING SNAPSHOT] ---`**: All hardware probe, encryption flag, AP scan, and `[STEP 1..6]` provisioning traces from your most recent setup attempt.
     - **`--- [2. REMEMBERED WI-FI HOMES] ---`**: All remembered Wi-Fi Home profiles (`networkKey`, `homeName`, `ssid`, `/24` subnet, and gateway IP).
     - **`--- [3. REGISTERED SWITCHES & DEVICES] ---`**: Every switch in your current inventory with its room, IP:port, state (`ON`, `OFF_STANDBY`, `OFFLINE`), latency (`ms`), hardware SKU, firmware, serial number, and MAC.
     - **`--- [4. UPNP / SOAP PACKET LOG] ---`**: Recent UPnP/SOAP/GENA/SSDP log entries with timestamps, endpoints, HTTP statuses, and XML payload summaries.
  2. Copies the full report to your clipboard and opens the Android **Share Sheet**.

### B3. Pulling `wemi-telemetry-latest.txt` via USB (`adb`)
If your Android phone or Android TV is connected to a computer with USB debugging enabled, you can also pull the latest recorded file directly from the terminal after tapping **`Copy`**, **`Save/Share`**, or **`Save/Share Log`**:

```bash
adb exec-out run-as com.aistudio.wemiswitch.kxpqzr cat files/wemi-telemetry-latest.txt > wemi-telemetry-latest.txt
```

---

## 3. Quick Reference: What the Setup Debug Lines Mean

| Line Prefix | Example | What It Tells You |
| :--- | :--- | :--- |
| **`[NET]`** | `mode=REAL-LAN ip=10.22.22.2 (wlan0) gw=10.22.22.1 setupSubnet=true wifiBound=true` | Confirms whether the app is in `REAL-LAN` mode, connected to the switch's `10.22.22.x` Wi-Fi AP (`setupSubnet=true`), and binding sockets directly to `wlan0` (`wifiBound=true`) bypassing mobile data. |
| **`[PROBE]`** | `host=10.22.22.1 -> :49152/setup.xml=HTTP_200(14ms,1420B)` | Shows per-port probe diagnostics across all 9 UPnP ports (`49151..49159`) and which descriptor path (`/setup.xml` or `/Lightsetup.xml`) responded with `HTTP_200`. |
| **`[XML]`** | `/setup.xml :49152 name="Smart Switch" model=LightSwitch sku=... fw=WeMo_WW_...` | Hardware model name, default friendly name, SKU, and firmware version read from `/setup.xml`. |
| **`[FLAGS]`** | `rtos=0 iot=0 binaryOption=0 new_algo=0 -> autoMethod=1 autoLen=true` | Hardware encryption flags from `/setup.xml` and which OpenSSL AES-128-CBC method (`1`, `2`, or `3`) and length suffix (`autoLen`) were auto-selected. |
| **`[ID]`** | `mac=EC1A59... sn=221517... (src=GetMetaInfo) meta="..."` | The switch's MAC address and Serial Number (required to salt the AES-128-CBC key) and which SOAP call supplied them (`GetMetaInfo`, `setup.xml`, or `basicevent1#GetMacAddr`). |
| **`[SCAN]`** | `GetApList=HTTP_200(1240ms,count=4)` | Results of the switch's `WiFiSetup1#GetApList` (or `GetNetworkList`) 2.4 GHz site survey. |
| **`[TARGET AP]`** | `"MyHome24" found in ApList: Ch=6 Auth=WPA2PSK/AES Signal=88%` | The exact 2.4 GHz channel, authentication mode, and cipher that will be sent in `ConnectHomeNetwork`. |
| **`[STEP 4 CRYPTO]`** | `Method=1 addLengths=true plainLen=12 encLen=68 suffix=0c40` | Confirms the password encryption parameters (`Method 1/2/3` and whether the 4-hex-char `xxyy` length suffix was appended) without exposing your password. |
| **`[STEP 5 SEND #1]`** | `ConnectHomeNetwork -> HTTP_200 (18ms) PairingStatus=1` | Confirms the switch accepted the `ConnectHomeNetwork` SOAP command. |
| **`[POLL #n]`** | `GetNetworkStatus -> Status=1 (14ms)` | Status codes from the switch while joining your router: **`1` = Connected**, **`3` = Connected (Handshake Complete)**, **`0` = Still Connecting / Handshake Failed**, **`2` = Bad Password / Auth Rejected**. |
| **`[STEP 6 CLOSE/HOLD]`** | `CloseSetup -> success` or `Skipping CloseSetup so 10.22.22.1 AP stays open for retry` | Shows whether the setup AP was closed after a successful connection (`1` or `3`) or intentionally kept open (`kept_open_for_retry`) so you can retry immediately. |
