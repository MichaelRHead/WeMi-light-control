# WeMi Light Control — Architecture & Agent Guide (`AGENTS.md`)

**WeMi Light Control** (`com.example.wemi`, v6.0 / `d323be0`) is an Android & Android TV local-LAN controller for Belkin WeMo smart switches, dimmers, and mini plugs. It pairs a 2013-era skeuomorphic Belkin WeMo interface (`#73D41B` lime green header, brushed metallic rows, glowing power buttons, and custom hardware illustrations) with direct local UPnP / SSDP / SOAP networking and Room persistence.

Detailed agent instructions, protocol specs, and the `pywemo`-to-Kotlin implementation plan are packaged in:
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Agent Skill Definition
- [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md) — Complete `pywemo` & Home Assistant UPnP/SSDP/SOAP/GENA Protocol Reference
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — Phased Kotlin Implementation Plan

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

## 4. Production Implementation Plan (`docs/IMPLEMENTATION_PLAN.md`)

The AI Studio skeleton (`v6.0` / commit `d323be0`) includes simulated fallbacks so the UI can be previewed without physical hardware. The full 7-phase engineering plan to make this a production-ready app is documented in [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md):

1. **Phase 1 — `pywemo`-Grade UPnP / SSDP / SOAP Engine (`WeMoNetworkClient.kt`)**:
   - Real SSDP multicast discovery (`WifiManager.MulticastLock`, 1-second `M-SEARCH` retransmit, exact `LOCATION` fetching for `/setup.xml` & `/Lightsetup.xml`, `<manufacturer>Belkin` validation) + unicast `/24` port-sweep fallback when multicast is blocked.
   - Smart 9-port probing (`49153, 49152, 49154, 49151, 49155..49159`) with `<UDN>`/`<serialNumber>` verification and TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation.
   - Live state & telemetry queries (`GetBinaryState`, `metainfo1#GetMetaInfo` for exact SKU & Wi-Fi SSID, `GetSignalStrength`, `ConfigureNightMode`, `ChangeFriendlyName`) with `Connection: close` and `<BinaryState>Error</BinaryState>` handling.
2. **Phase 2 — Real Reachability, Concurrency & Self-Healing Repository (`WeMoRepository.kt`)**:
   - Remove all forced `isReachable = true` mock fallbacks; reflect genuine `OFFLINE` states (`isReachable = false`, `latencyMs = 0`).
   - Protect 2nd-Gen RTOS switches (`WLS040` / `WLS0403` / `WDS060`) with a LAN-wide `Semaphore(3)` and per-device `Mutex`.
   - Implement two-stage auto-reconnect (port re-probe on known IP → targeted SSDP scan by `serialNumber`/`UDN`) and serial-based deduplication + "Adopt All Discovered".
3. **Phase 3 — Real-Time Local Push Events via UPnP GENA (`WeMoGenaSubscriptionManager.kt`)**:
   - Embedded coroutine HTTP `NOTIFY` callback server (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, `HTTP 412` recovery) so physical wall-switch and 3-way traveler flips update the UI immediately.
4. **Phase 4 — Live Wi-Fi Subnet Detection & Explicit Demo Mode (`WeMoNetworkMonitor.kt`)**:
   - `ConnectivityManager.NetworkCallback` live subnet detection, auto-matching/creating `WeMoHomeNetworkEntity` profiles, clean startup on real LANs, and moving the 13 seeded sample devices behind an explicit Demo Mode toggle.
5. **Phase 5 — Working Rules, Schedules & Auto-Off Timers (`WeMoRuleScheduler.kt`)**:
   - Real countdown execution for `autoOffMinutes` / `AUTO_OFF_TIMER` and scheduled execution for `TIME_SCHEDULE` / `SUNSET_SUNRISE`.
6. **Phase 6 — Zero-Cloud Wi-Fi Provisioning (`10.22.22.1`) & Hardware Management (`WeMoProvisionerClient.kt`)**:
   - Provision factory-reset WeMo APs (`WeMo.Light.xxx` / `WeMo.Mini.xxx` at `10.22.22.1:49152`) via `WiFiSetup1` (`GetApList`, AES-128-CBC Methods 1/2/3, double-send `ConnectHomeNetwork`).
7. **Phase 7 — Loopback UPnP/SOAP/GENA & Robolectric Test Suite (`WeMoProtocolTest.kt`, `ExampleRobolectricTest.kt`)**:
   - Automated tests simulating all 4 hardware types (`F7C030`, `WLS0403`, `F7C059`/`WDS060`, `F7C063`/`WSP080`), port shifts, and GENA push notifications.
