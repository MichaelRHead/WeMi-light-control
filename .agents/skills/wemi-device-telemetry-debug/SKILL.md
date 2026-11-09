---
name: wemi-device-telemetry-debug
description: Capture, parse, and diagnose on-device Belkin WeMo setup, discovery, and UPnP/SOAP telemetry from the user's physical Android phone or Android TV via screenshots (Setup Debug Details panel, SOAP Packet Log, Inline Diagnostics Drawer) or recorded telemetry files (wemi-telemetry-latest.txt / Copy / Save-Share). EXCLUSIVELY OWNED by the Antigravity Debugging & Support Agent (other agents MUST read and follow this skill when modifying provisioning/telemetry flows, but MUST NOT edit this skill, DEBUGGING_AND_SUPPORT.md, or docs/user-guide/telemetry-and-troubleshooting.md).
---

# WeMi Device Telemetry & Screenshot Debugging Skill (`wemi-device-telemetry-debug`)

> [!CAUTION]
> **EXCLUSIVE OWNERSHIP & READ-ONLY RULE FOR PEER AGENTS**:
> - **Owner**: **Antigravity Debugging & Support Agent — SOLE AUTHOR & MAINTAINER**.
> - **Policy**: **ONLY the Antigravity Debugging & Support Agent may modify [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](SKILL.md), [`DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md), or [`docs/user-guide/telemetry-and-troubleshooting.md`](../../docs/user-guide/telemetry-and-troubleshooting.md).**
> - **All other agents** (including the Google AI Studio Agent, CLI agents, and external coding assistants) **MUST read, understand, and obey** the diagnostic invariants, non-destructive `10.22.22.1` provisioning rules, and telemetry `testTag` contracts documented here, **but MUST NOT edit, overwrite, or delete these files.**

For the full debugging & support architecture and invariants, read [`../../DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md) and the end-user guide in [`../../docs/user-guide/telemetry-and-troubleshooting.md`](../../docs/user-guide/telemetry-and-troubleshooting.md).

Use this skill whenever a user reports an issue with physical Belkin WeMo hardware (`F7C030`, `WLS040`/`WLS0403`, `F7C059`/`WDS060`, `F7C063`/`WSP080`) on their Android phone, tablet, or Android TV — especially during **`10.22.22.1` zero-cloud Wi-Fi setup**, **LAN SSDP discovery**, or **UPnP/SOAP control**.

Because physical Belkin WeMo switches sit on the user's private local Wi-Fi (`192.168.x.x/24`) or isolated soft-AP (`10.22.22.1/24`), the fastest way to close the diagnostic loop is to inspect **structured on-screen screenshots** or **exported telemetry files (`wemi-telemetry-latest.txt`)** captured directly on the user's device.

---

## 1. Two Ways the User Can Provide Device Telemetry

### Method A: On-Screen Screenshot (Fastest 1-Step Workflow)
Ask the user to take a screenshot of one of the three built-in high-contrast diagnostic views in **WeMi Light Control** and attach/upload it in chat:

1. **During `10.22.22.1` Device Setup (`ProvisionWeMoApDialog` → `SETUP DEBUG DETAILS (SCREENSHOT)`)**:
   - Located at the bottom of the **Provision New WeMo AP (`10.22.22.1`)** dialog (`Modifier.testTag("provision_debug_panel")`).
   - Automatically stays open whenever setup fails (`NetworkStatus=0`, `NetworkStatus=2`, or unreachable).
   - If the user wants to screenshot a *successful* run as well, ask them to toggle the **`Keep Dialog Open on Success`** chip (`keep_provision_dialog_open_chip`) ON before tapping **`Provision Switch`**.
2. **On the `More` Tab (`UPnP / SOAP Packet Log`)**:
   - Shows the most recent SOAP actions (`GetBinaryState`, `SetBinaryState`, `GetMetaInfo`, `WiFiSetup1#GetApList`, `WiFiSetup1#ConnectHomeNetwork`, `SSDP+UnicastScan`, `GENA#NOTIFY`), target endpoints, HTTP status codes, and round-trip latency in milliseconds.
3. **On Any Switch Row (`Inline Device Diagnostics Drawer`)**:
   - Tap the info (`ⓘ`) button on any device row to expand the hardware SKU, firmware string, MAC, Serial Number, UDN, IP:port, and **`Ping Switch (RTT)`** button.

When the user provides a screenshot:
- Always call `view_file` on the uploaded image path (e.g., `/home/antigravity/upload/...` or transcript media URI) to inspect every line of the screenshot directly.

### Method B: Recorded Telemetry File or Clipboard Export (`wemi-telemetry-latest.txt`)
When a trace is longer than one screen or the user prefers sharing a text file:

1. **Inside `ProvisionWeMoApDialog` (`SETUP DEBUG DETAILS` header)**:
   - Tap **`Save/Share`** (`share_provision_debug_button`) to write `/data/data/com.aistudio.wemiswitch.kxpqzr/files/wemi-telemetry-latest.txt`, copy the full report to the clipboard, and open Android's share sheet (to save to Google Drive / Files or paste directly into chat).
   - Or tap **`Copy`** (`copy_provision_debug_button`) to copy the `=== WeMi Setup AP Diagnostic Report ===` block to the clipboard and save `wemi-telemetry-latest.txt`.
2. **Inside the `More` Tab (`UPnP / SOAP Packet Log` header)**:
   - Tap **`Save/Share Log`** (`export_telemetry_file_button`) to generate a complete 4-section report (`WeMoTelemetryFileExporter.buildFullTelemetryReport` in [`WeMoSetupDiagnostics.kt`](../../app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt)):
     - `--- [1. SETUP AP & PROVISIONING SNAPSHOT] ---`
     - `--- [2. REMEMBERED WI-FI HOMES] ---`
     - `--- [3. REGISTERED SWITCHES & DEVICES] ---`
     - `--- [4. UPNP / SOAP PACKET LOG] ---`
3. **Via `adb` (If User Has USB/Wireless Debugging Connected)**:
   ```bash
   adb shell run-as com.aistudio.wemiswitch.kxpqzr cat files/wemi-telemetry-latest.txt > wemi-telemetry-latest.txt
   ```

---

## 2. Line-by-Line Diagnostic Decoder (`WeMoSetupDebugSnapshot`)

Every `SETUP DEBUG DETAILS (SCREENSHOT)` panel and exported telemetry file uses deterministic line prefixes generated by [`WeMoSetupDiagnostics.kt`](../../app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt):

### 2.1 `[NET]` — Phone Network & Android Wi-Fi Socket Binding
Format:
```text
[NET] mode=REAL-LAN ip=10.22.22.2 (wlan0) gw=10.22.22.1 setupSubnet=true wifiBound=true
```
- **`mode=DEMO` vs `mode=REAL-LAN`**:
  - If `mode=DEMO`, the app is in **Demo Showroom Mode** and simulating provisioning! Instruct the user to open the **`More`** tab and switch to **Real LAN Mode** (or tap **Reset**).
- **`ip=...` & `setupSubnet=true/false`**:
  - Must be `10.22.22.x` (`setupSubnet=true`) when connected to a factory-reset Belkin WeMo soft-AP (`WeMo.Switch.xxx`, `WeMo.Light.xxx`, `Wemo.LS3W.xxx`, `WeMo.Mini.xxx`).
  - If `ip=192.168.x.x` (`setupSubnet=false`), the phone is still on the home router Wi-Fi (or auto-switched away from the WeMo AP because it had no internet).
- **`wifiBound=true/false`**:
  - Must be `true` on real Android hardware so OkHttp/raw sockets bind via `wifiNetwork.bindSocket(socket)` (`WeMoNetworkMonitor.createWifiBoundSocketFactory`) and bypass Android cellular data routing.
  - If `wifiBound=false` while `ip=10.22.22.x`, ask the user to temporarily toggle Mobile Data OFF or tap *"Stay connected"* on Android's *"This network has no internet access"* prompt.

### 2.2 `[PROBE]` & `[XML]` — `10.22.22.1` Port & Descriptor Probe
Format:
```text
[PROBE] host=10.22.22.1 -> 49153:refused, 49152:/setup.xml(HTTP 200, 1420B)
[XML] /setup.xml :49152 name="WeMo Light Switch" model=LightSwitch sku=Belkin WeMo Light Switch (1st Gen F7C030) fw=WeMo_WW_2.00.11408.PVT-OWRT-LS
```
- **If `[XML/ID] No WeMo setup descriptor read from 10.22.22.1`**:
  - Inspect `[PROBE]`:
    - All `timeout`: packets are not reaching `10.22.22.1` (check `ip=10.22.22.x` and `wifiBound=true`, or check if the switch uses `192.168.49.1` on rare OEM firmwares).
    - All `refused`: the switch's UPnP daemon is still booting after power-on/reset; wait 10 seconds and tap **`Scan APs`** again.

### 2.3 `[FLAGS]` & `[ID]` — Password Encryption Method & Key Material
Format:
```text
[FLAGS] rtos=0 iot=0 binaryOption=0 new_algo=0 -> autoMethod=1 autoLen=true
[ID] mac=EC1A59XXXXXX sn=221517K01018AB (src=GetMetaInfo) meta="EC1A59XXXXXX|221517K01018AB|0|0|WeMo.Switch.8AB|..."
```
- **Verify `src=GetMetaInfo` or `src=setup.xml` or `src=GetMacAddr/GetSerialNo`**:
  - Belkin's OpenSSL AES-128-CBC key derivation (`WeMoPasswordCrypto.kt`) derives its key and IV from the switch's exact 12-hex-character `macAddress` and `serialNumber`.
  - If `src=MISSING` or `mac=` is blank/wrong length (`!= 12` hex chars), the switch will fail to decrypt the Wi-Fi password (`NetworkStatus=2` or `0`).
- **Verify Encryption Method (`autoMethod` & `autoLen`)**:
  - `Method 1` (`OpenWrt`, `rtos=0`): `key = MAC[0:6] + Serial + MAC[6:12]`.
  - `Method 2` (`RTOS`, `rtos=1`, e.g. `WLS040` / `WLS0403` / `WDS060` / `WSP080`): `key = MAC[0:6] + MAC[6:12] + Serial` (transposed) with `iot` shifts.
  - `Method 3` (`binaryOption=1` symmetric): fixed 24-char key from `GetMetaInfo`.
  - Some transitional firmwares (`new_algo=1` or early RTOS builds) require toggling **Password Length Suffix (`xxyy` hex)** between **`Append xxyy`** (`lenSuffix=true`) and **`No Suffix`** (`lenSuffix=false`), or overriding **Password Encryption Method** (`Gen 1 (OpenWrt)`, `Gen 2 (RTOS)`, `Method 3 (Sym)`). All 6 combinations are selectable via chips in `ProvisionWeMoApDialog`.

### 2.4 `[SCAN]` & `[TARGET AP]` — 2.4 GHz Site Survey Match
Format:
```text
[SCAN] 4 APs: MyHome24(Ch6,WPA2PSK/AES,88%), Neighbor(Ch11,WPA2PSK/AES,42%)
[TARGET AP] "MyHome24" found in ApList: Ch=6 Auth=WPA2PSK/AES Signal=88%
```
- **If `[TARGET AP] WARN: "..." NOT in ApList!`**:
  - Belkin WeMo switches are **2.4 GHz only** (channels 1–11 in North America). If the user typed a **5 GHz-only** or **6 GHz** SSID, or a band-steered SSID whose 2.4 GHz beacon wasn't seen, the switch cannot join it.
  - If the router uses **WPA3-Only (`SAE`)** or **WPA2/WPA3 Mixed Mode with PMF Required**, older WeMo firmware may report `Auth=WPAPSK` or fail handshake (`NetworkStatus=0`). Recommend enabling a 2.4 GHz `WPA2-PSK (AES)` network/guest SSID or selecting the exact `Auth Mode` / `Encrypt` reported by `[SCAN]`.

### 2.5 `[STEP 1..5]` — Live `ConnectHomeNetwork` & `GetNetworkStatus` Trace
Format:
```text
[STEP 1] Probe 10.22.22.1 -> port=49152 sku=Belkin WeMo 3-Way Light Switch (2nd Gen WLS040/WLS0403) mac=EC1A59XXXXXX sn=221517K01018AB (src=GetMetaInfo)
[STEP 2] FriendlyName="Hallway 3-Way" -> ok=true
[STEP 3] Matched "MyHome24" in GetApList -> Ch=6 Auth=WPA2PSK/AES
[STEP 4] Encrypt password -> method=2 (auto=2) lenSuffix=true cipherHexLen=44
[STEP 5] ConnectHomeNetwork -> HTTP=true PairingStatus=1 NetStatus=1 CloseSetup=closed Poll=[poll#1=0, poll#2=1]
```
- **`NetStatus=1` or `NetStatus=3`**:
  - **Success!** The switch associated with the home router and obtained a DHCP lease (`CloseSetup=closed`).
- **`NetStatus=2` (`Bad Password / Handshake Rejected`)**:
  - The switch attempted to join the AP on the specified channel, but the 4-way WPA2 handshake failed. Either:
    1. The Wi-Fi password has a typo, OR
    2. The firmware expects a different encryption permutation — ask the user to try toggling **`Append xxyy`** ↔ **`No Suffix`** (`provision_len_true_chip` / `provision_len_false_chip`) or switching between **`Gen 1 (OpenWrt)`** and **`Gen 2 (RTOS)`** while the `10.22.22.1` AP is still open (`CloseSetup=kept_open_for_retry`).
- **`NetStatus=0` after all 18 polls (`Poll=[poll#1=0, ..., poll#18=0]`)**:
  - The switch never completed association on the target channel. Common causes:
    1. Target SSID is 5 GHz-only or out of 2.4 GHz range.
    2. Router `AuthMode`/`EncryptionType` mismatch (check `[TARGET AP]` vs the **2.4G Ch / Auth Mode / Encrypt** fields).
    3. Router MAC filtering or WPA3-PMF rejection.

---

## 3. Expanding Telemetry When New Hardware Quirks Are Discovered

Whenever a screenshot or exported telemetry file reveals a new firmware flag, SOAP tag, or hardware SKU quirk:
1. Add the parsed field to `WeMoSetupDeviceDescriptor` in [`WeMoProvisionerClient.kt`](../../app/src/main/java/com/example/network/WeMoProvisionerClient.kt) and surface it in `WeMoSetupDebugSnapshot.toDisplayLines` in [`WeMoSetupDiagnostics.kt`](../../app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt).
2. Add a deterministic loopback regression test in [`WeMoGenaProvisioningAndRulesTest.kt`](../../app/src/test/java/com/example/WeMoGenaProvisioningAndRulesTest.kt) using [`FakeWeMoUpnpServer.kt`](../../app/src/test/java/com/example/FakeWeMoUpnpServer.kt).
3. Run `./gradlew testDebugUnitTest --rerun-tasks assembleDebug` before pushing.
