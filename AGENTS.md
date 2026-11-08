# WeMi Light Control — Architecture & Agent Guide (`AGENTS.md`)

**WeMi Light Control** (`com.example.wemi`, v6.0 / `d323be0`) is an Android & Android TV local-LAN controller for Belkin WeMo smart switches, dimmers, and mini plugs. It pairs a 2013-era skeuomorphic Belkin WeMo interface (`#73D41B` lime green header, brushed metallic rows, glowing power buttons, and custom hardware illustrations) with direct local UPnP / SSDP / SOAP networking and Room persistence.

Detailed agent instructions and protocol specs are also packaged as a repository skill in [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) and [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md).

---

## 1. Supported Belkin WeMo Hardware

The app is designed around four physical Belkin WeMo device types (`WeMoDeviceType`):

1. **In-Wall Light Switch (1st Gen)** (`IN_WALL_SWITCH` — `F7C030`, UPnP `LightSwitch`):
   - Single-pole in-wall paddle switch with green Wi-Fi/power LED indicator and binary relay (`0`/`1`).
2. **In-Wall 3-Way Light Switch (2nd Gen)** (`THREE_WAY_SWITCH` — `WLS040` / `WLS0403`, UPnP `LightSwitch`):
   - 3-way compatible in-wall rocker switch supporting multi-location traveler switching (`threeWayTravelerStatus`) and binary relay (`0`/`1`).
3. **In-Wall Wi-Fi Smart Dimmer** (`IN_WALL_DIMMER` — `F7C059` / `WDS060`, UPnP `Dimmer`):
   - In-wall touch dimmer with 7-segment green LED brightness bar, continuous `1–100%` dimming (`<brightness>`), and Night Mode (`20%` soft cap).
4. **WeMo Mini Smart Plug** (`MINI_PLUG` — `F7C063` / `WSP080`, UPnP `Socket` / `Mini`):
   - Compact horizontal pass-through smart outlet plug with side power toggle and front LED status indicator.

---

## 2. Architecture & Source Layout

### Package Structure (`app/src/main/java/com/example/`)

- **`MainActivity.kt`**:
  - Entry point (`WeMiMainScreen`). Automatically detects Android TV (`FEATURE_LEANBACK` or `UI_MODE_TYPE_TELEVISION`) and toggles between the 10-foot D-Pad dashboard (`WeMoTvDashboardScreen`) and the mobile/tablet `Scaffold`.
- **`data/` (Room v4 Persistence & Repository)**:
  - `WeMoEntities.kt`:
    - `WeMoDeviceType`: `IN_WALL_SWITCH`, `THREE_WAY_SWITCH`, `IN_WALL_DIMMER`, `MINI_PLUG`.
    - `WeMoConnectionQuality`: `EXCELLENT` (<= 28 ms, 4 bars), `GOOD` (<= 65 ms, 3 bars), `SLOW` (> 65 ms, 2 bars), `OFFLINE` (0 bars).
    - `WeMoHomeNetworkEntity` (`wemo_home_networks`): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
    - `WeMoDeviceEntity` (`wemo_devices`): Device state, network address (`ipAddress`, `port`, `networkKey`, `wifiSsid`), hardware telemetry (`serialNumber`, `firmwareVersion`, `macAddress`, `signalStrengthPercent`), and live connection status (`isReachable`, `latencyMs`, `lastCheckedTime`, `isOn`, `brightness`).
    - `WeMoRuleEntity` (`wemo_rules`): Schedules and timers (`TIME_SCHEDULE`, `SUNSET_SUNRISE`, `AUTO_OFF_TIMER`).
  - `WeMoDao.kt` & `WeMoDatabase.kt`: Room database (`wemo_control_database.db`, schema `version = 4`).
  - `WeMoRepository.kt`: Bridges Room DAO and `WeMoNetworkClient` for power/brightness control, latency pinging, multi-home switching, room batch control, SSDP discovery, and JSON backup/import.
- **`network/` (UPnP / SSDP / SOAP Client)**:
  - `WeMoNetworkClient.kt`:
    - `setBinaryState(ipAddress, port, isOn, brightness)`: SOAP `POST /upnp/control/basicevent1` (`SetBinaryState`) with automatic fallback port probing and RTT measurement (`latencyMs`).
    - `getBinaryState(ipAddress, port)`: SOAP `POST /upnp/control/basicevent1` (`GetBinaryState`) parsing `<BinaryState>` and `<brightness>`.
    - `performSsdpDiscoveryScan(...)`: UDP multicast `M-SEARCH` to `239.255.255.250:1900` followed by HTTP `GET /setup.xml` descriptor parsing.
- **`ui/` (Jetpack Compose Skeuomorphic UI)**:
  - `WeMoViewModel.kt`: State holder (`WeMoUiState`) combining Room flows, active Wi-Fi home filtering (`activeHomeKey`), and room grouping (`WeMoRoomGroup`).
  - `components/WeMoWifiHomeBar.kt`: Remembered Wi-Fi Homes switcher bar + `AddWifiHomeNetworkDialog`.
  - `components/WeMoDeviceRow.kt`: Brushed metallic device card, `WeMoConnectionStatusBadge` (LED + 4-bar signal indicator + `ms` RTT + inline `Retry`), `WeMoCircularPowerButton`, `WeMoBrightnessSlider`, and expandable hardware drawer (`Ping RTT`, `Reconnect`, `Edit`, `Delete`).
  - `components/WeMoRoomGroupCard.kt`: Room header with master ON/OFF button and room-wide dimmer slider.
  - `components/WeMoSsdpScannerModal.kt`: Live SSDP UDP discovery modal with packet console and device adoption.
  - `components/WeMoTvScreen.kt`: Android TV D-Pad 10-foot interface (`TV Mode`).
  - `components/WeMoHardwareIllustrations.kt`: Custom `Canvas` vector illustrations for all 4 Belkin WeMo hardware models.
  - `screens/WeMoRulesAndMoreScreens.kt`: Rules & Schedules screen and More/Settings screen (backup/restore, LAN diagnostics, factory reset).

---

## 3. Build & Verification Commands

```bash
# Run Robolectric unit & UI integration tests
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

---

## 4. Roadmap: Converting AI Studio Skeleton Mocks to Real LAN Execution

The AI Studio skeleton (`v5.0` / commit `d1620ab`) includes simulated fallbacks so the UI can be previewed without physical hardware. To run against real Belkin WeMo switches, dimmers, and mini plugs on the user's LAN:

1. **Honor Real SOAP Reachability in `WeMoRepository.kt`**:
   - Stop forcing `isReachable = true` in `toggleDevicePower`, `updateDeviceBrightness`, `reconnectDevice`, `pingDeviceLatency`, and `reconnectAndRediscoverHome` when `SoapCallResult.reachableOnLan == false`.
   - Reflect genuine offline/unreachable states (`isReachable = false`, `latencyMs = 0`) when a device fails to respond on any candidate UPnP port (`49151..49156`).
2. **Real SSDP Multicast Discovery & Live State Probing**:
   - Acquire Android `WifiManager.MulticastLock` during `performSsdpDiscoveryScan` so the Wi-Fi stack receives UDP unicast replies from WeMo devices.
   - Remove the hardcoded `"Patio String Lights"` (`$subnetPrefix.118`) injection and synthetic `knownLocalDevices` loop in `WeMoNetworkClient.performSsdpDiscoveryScan`.
   - After fetching `/setup.xml` for a discovered device, immediately query `getBinaryState` (and `GetSignalStrength` if supported) so initial `isOn`, `brightness`, and `latencyMs` reflect real hardware state instead of hardcoded `isOn = true, brightness = 100`.
   - Add an optional fast unicast port scan fallback (`49151..49156` across the `/24` subnet) when router AP isolation or IGMP snooping blocks UDP multicast.
3. **Accurate Hardware Model Classification & Serial Deduplication**:
   - Refine `parseSetupXmlDescriptor` to distinguish 1st-Gen `IN_WALL_SWITCH` (`F7C030`), 2nd-Gen 3-Way `THREE_WAY_SWITCH` (`WLS040` / `WLS0403`), `IN_WALL_DIMMER` (`F7C059` / `WDS060`), and `MINI_PLUG` (`F7C063` / `WSP080`).
   - Match discovered devices by `serialNumber` in `adoptDiscoveredDevice` so DHCP IP or port changes update existing devices in place without creating duplicates.
4. **Live Wi-Fi Subnet Detection & Optional Demo Mode**:
   - Auto-detect the phone/TV's current LAN subnet (`resolveLocalIpv4Address()`) to select or create the corresponding `WeMoHomeNetworkEntity`, and allow clearing or disabling the pre-seeded demo devices (`ensureSeeded()`).
