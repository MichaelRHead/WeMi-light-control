---
name: wemi-light-control
description: Architecture, Belkin WeMo UPnP/SSDP/SOAP protocol reference, Room v4 schema, and mock-to-real implementation guide for the WeMi Light Control Android & Android TV app (MichaelRHead/WeMi-light-control). Use whenever inspecting, modifying, testing, or implementing real LAN discovery and control for WeMo In-Wall Switches (1st Gen & 2nd Gen 3-Way), In-Wall Dimmers, and WeMo Mini Smart Plugs.
---

# WeMi Light Control Skill (`MichaelRHead/WeMi-light-control`)

Use this skill when working on the **WeMi Light Control** Android & Android TV application in `/home/antigravity/MichaelRHead/WeMi-light-control`.

For detailed UPnP/SSDP/SOAP wire formats, device model identifiers, and XML schemas for Belkin WeMo switches, dimmers, and mini plugs, read [`references/wemo-upnp-protocol.md`](references/wemo-upnp-protocol.md).

---

## 1. Build, Test & Environment

- **Toolchain**: AGP `9.1.0`, Kotlin `2.3.20`, KSP `2.3.6`, Compose BOM `2026.03.00`, Room `2.8.4`, Robolectric `4.16.1`, `compileSdk = 36`, `minSdk = 26`.
- **Unit / Robolectric Tests**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Debug APK Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Package / Namespace**: `com.example.wemi` (source code lives under `app/src/main/java/com/example/`).

---

## 2. Target Hardware Inventory

The user's real hardware fleet consists of four Belkin WeMo device families (modeled in `WeMoDeviceType`):

| `WeMoDeviceType` | Real Hardware Models | UPnP `<modelName>` | Capabilities |
| :--- | :--- | :--- | :--- |
| `IN_WALL_SWITCH` | 1st-Gen WeMo Light Switch (`F7C030`) | `LightSwitch` | Binary Relay (`0`/`1`), Green Wi-Fi/Power LED |
| `THREE_WAY_SWITCH` | 2nd-Gen 3-Way WeMo Light Switch (`WLS040`, `WLS0403`) | `LightSwitch` / `WLS040` / `WLS0403` | 3-way traveler status, Binary Relay (`0`/`1`), White/Amber status bar LED |
| `IN_WALL_DIMMER` | WeMo Wi-Fi Smart Dimmer (`F7C059`, `WDS060`) | `Dimmer` / `WDS060` | Binary state + Brightness (`1..100%`), Night Mode (20% cap), 7-segment green LED level bar |
| `MINI_PLUG` | WeMo Mini Smart Plug (`F7C063`, `WSP080`) | `Socket` / `Mini` / `WSP080` | Compact outlet relay (`0`/`1`), side power button & status LED |

---

## 3. Codebase Map (v6.0 / Commit `d323be0`)

### Data Layer (`app/src/main/java/com/example/data/`)
- **`WeMoEntities.kt`**:
  - `WeMoDeviceType`: `IN_WALL_SWITCH`, `THREE_WAY_SWITCH`, `IN_WALL_DIMMER`, `MINI_PLUG`.
  - `WeMoConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `WeMoHomeNetworkEntity` (`wemo_home_networks` table): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
  - `WeMoDeviceEntity` (`wemo_devices` table): Persisted device state (`id`, `name`, `roomName`, `deviceType`, `ipAddress`, `port`, `serialNumber`, `firmwareVersion`, `macAddress`, `signalStrengthPercent`, `isOn`, `brightness`, `isReachable`, `latencyMs`, `lastCheckedTime`, `nightModeEnabled`, `threeWayTravelerStatus`, `autoOffMinutes`, `lastToggledTime`, `sortOrder`, `networkKey`, `wifiSsid`).
  - `WeMoRuleEntity` (`wemo_rules` table): Local automation schedules (`TIME_SCHEDULE`, `SUNSET_SUNRISE`, `AUTO_OFF_TIMER`).
- **`WeMoDao.kt` & `WeMoDatabase.kt`**:
  - Room DB at `version = 4` (`wemo_control_database.db`, `.fallbackToDestructiveMigration()`).
  - Reactive `Flow` queries for devices, rules, and remembered Wi-Fi home networks.
- **`WeMoRepository.kt`**:
  - Coordinates `WeMoDao` and `WeMoNetworkClient`.
  - Contains `ensureSeeded()` (seeds 3 demo homes, 13 demo devices, and 4 rules when DB is empty).
  - Handles power toggles, dimmer brightness updates, latency pinging (`pingDeviceLatency`), home switching (`switchHomeNetwork`, `reconnectAndRediscoverHome`, `saveNewHomeNetwork`), batch room controls (`setRoomPowerState`, `setRoomBrightness`), SSDP discovery (`scanLocalNetworkForDevices`, `adoptDiscoveredDevice`), and JSON backup/restore.

### Network Layer (`app/src/main/java/com/example/network/`)
- **`WeMoNetworkClient.kt`**:
  - `setBinaryState(ipAddress, port, isOn, brightness)`: Sends SOAP `POST /upnp/control/basicevent1` with `SOAPAction: "urn:Belkin:service:basicevent:1#SetBinaryState"`. Scans fallback ports `[49153, 49152, 49154, 49155]` if the saved port fails.
  - `getBinaryState(ipAddress, port)`: Sends SOAP `GetBinaryState` and parses `<BinaryState>` (`0`/`1` or pipe-delimited `1|...`) and `<brightness>`.
  - `performSsdpDiscoveryScan(...)`: Broadcasts UDP `M-SEARCH` to `239.255.255.250:1900` (`ST: upnp:rootdevice` and `ST: urn:Belkin:service:basicevent:1`), parses `LOCATION:` headers, and fetches `/setup.xml`.

### UI Layer (`app/src/main/java/com/example/ui/`)
- **`WeMoViewModel.kt`**: Holds `activeHomeKey`, combines Room flows into `WeMoUiState` (filtering `devices` by `activeHomeKey` and grouping into `roomGroups: List<WeMoRoomGroup>`), and exposes actions to Compose.
- **`MainActivity.kt`**: Detects Android TV (`PackageManager.FEATURE_LEANBACK` or `UI_MODE_TYPE_TELEVISION`) and routes either to `WeMoTvDashboardScreen` or the mobile/tablet `Scaffold`.
- **`components/WeMoWifiHomeBar.kt`**: Top bar displaying the active Wi-Fi Home (`homeName`, `ssidLabel`, `subnetPrefix.x`, online/total pill, `Re-Scan` button, expandable remembered homes list, and `AddWifiHomeNetworkDialog`).
- **`components/WeMoDeviceRow.kt`**: Skeuomorphic silver-gradient device card with hardware illustration, `WeMoConnectionStatusBadge` (`WeMoSignalBarsIndicator` + `latencyMs` RTT badge + inline `Retry` button), `WeMoCircularPowerButton`, dimmer brightness slider (`WeMoBrightnessSlider`), and expandable hardware drawer (`Ping RTT`, `Reconnect`, `Edit`, `Delete`).
- **`components/WeMoRoomGroupCard.kt`**: Room group header with master power toggle, room-wide brightness slider, and collapsible device list.
- **`components/WeMoSsdpScannerModal.kt`**: Live SSDP radar scanner modal with packet log console and one-tap device adoption.
- **`components/WeMoTvScreen.kt`**: 10-foot Android TV D-Pad interface with room filter chips and focus-highlighted cards.

---

## 4. Crucial Gap Analysis: AI Studio Mocks vs. Real Hardware

The AI Studio skeleton includes several simulated fallbacks so the UI can be previewed without physical WeMo devices. When implementing real hardware support, address these exact locations:

1. **Fake SOAP Fallbacks in `WeMoRepository.kt` & `WeMoNetworkClient.kt`**:
   - In `WeMoNetworkClient.setBinaryState` and `getBinaryState`, when all candidate ports fail to connect, it returns `SoapCallResult(success = true/false, reachableOnLan = false, latencyMs = 12/14, ...)`.
   - In `WeMoRepository.toggleDevicePower`, `updateDeviceBrightness`, `reconnectDevice`, `pingDeviceLatency`, and `reconnectAndRediscoverHome`, the repository **ignores `result.reachableOnLan`** and unconditionally writes `isReachable = true` (and `isOn = nextOn`) to Room!
   - **Real Behavior Needed**: Mark `isReachable = result.reachableOnLan`. Only update `isOn`/`brightness` on real HTTP 200 SOAP responses (or revert optimistic UI state and surface an error/offline state when `reachableOnLan == false`).
2. **Fake SSDP Discovery Injections in `WeMoNetworkClient.performSsdpDiscoveryScan`**:
   - Currently appends all `knownLocalDevices.filter { it.isReachable }` as synthetic SSDP results (`isLiveUdpResponse = false`) and injects a fake `"Patio String Lights"` at `$subnetPrefix.118:49153` (`isLiveUdpResponse = true`) whenever no live device at `.118` responds.
   - Also, `parseSetupXmlDescriptor` hardcodes `isOn = true, brightness = 100` instead of querying `getBinaryState` after fetching `/setup.xml`.
   - **Real Behavior Needed**: Remove the synthetic `"Patio String Lights"` and fake cached-device loop; after parsing `/setup.xml`, call `getBinaryState` on the discovered `ip:port` to populate real initial `isOn` and `brightness` state.
3. **Expanded Port Range & Unicast Subnet Sweep Fallback**:
   - Belkin WeMo firmware uses ports `49151` through `49156` (often jumping ports after reboot or firmware update). Expand `candidatePorts` to `[49153, 49152, 49154, 49155, 49151, 49156]`.
   - On Android Wi-Fi, acquire `WifiManager.MulticastLock` before UDP SSDP `M-SEARCH` so multicast responses are not dropped by the Wi-Fi chipset, and optionally support fast TCP port probing across the `/24` subnet if multicast is blocked by the router.
4. **Device Identity & IP/Port Healing by Serial Number / UDN**:
   - `WeMoRepository.adoptDiscoveredDevice` currently matches existing devices only by `ipAddress` or `name`. Match by `serialNumber` first so that if a WeMo switch gets a new DHCP IP or shifts UPnP port, its existing Room row (`id`, `roomName`, `networkKey`) is updated in place.
5. **Hardware Model Classification in `parseSetupXmlDescriptor`**:
   - Distinguish 1st-Gen `IN_WALL_SWITCH` (`F7C030`, `LightSwitch`) from 2nd-Gen 3-Way `THREE_WAY_SWITCH` (`WLS040`, `WLS0403`), `IN_WALL_DIMMER` (`F7C059`, `WDS060`, `Dimmer`), and `MINI_PLUG` (`F7C063`, `WSP080`, `Socket`, `Mini`) using `<modelName>`, `<modelDescription>`, and `<serialNumber>` prefixes.
6. **Live Wi-Fi Network / Subnet Detection**:
   - Detect the actual Android LAN IPv4 subnet (`resolveLocalIpv4Address()` / `ConnectivityManager`) on startup and network change to automatically match or prompt for the active `WeMoHomeNetworkEntity` instead of defaulting hardcoded `activeHomeKey = "home_main"` and seeded mock devices.
