# WeMi Light Control — Architecture & Agent Guide (`AGENTS.md`)

**WeMi Light Control** (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`, v10.0) is an Android & Android TV local-LAN controller for Belkin WeMo smart switches, dimmers, and mini plugs. It pairs a 2013-era skeuomorphic Belkin WeMo interface (`#73D41B` lime green header, brushed metallic rows, glowing power buttons, and custom hardware illustrations) with `pywemo`-grade local UPnP / SSDP / SOAP / GENA networking, zero-cloud `10.22.22.1` provisioning, and Room v5 persistence.

Detailed agent instructions, user guides, protocol specs, completed phases, and the next-iteration roadmap are packaged in:
- [`USER_GUIDE.md`](USER_GUIDE.md) / [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`IMPLEMENTED.md`](IMPLEMENTED.md) / [`docs/IMPLEMENTED.md`](docs/IMPLEMENTED.md) — Completed Phases 1–7 Architecture & Design Decisions (`v10.0`)
- [`docs/IMPLEMENTATION_PLAN.md`](docs/IMPLEMENTATION_PLAN.md) — Next-Iteration Roadmap & Future Engineering Ideas (Phases 8–13)
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Agent Skill Definition
- [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md) — Complete `pywemo` & Home Assistant UPnP/SSDP/SOAP/GENA Protocol Reference

---

## 1. Supported Belkin WeMo Hardware

The app supports four physical Belkin WeMo hardware families:

1. **In-Wall Light Switch (1st Gen)** (`F7C030`, UPnP `LightSwitch` / `uuid:Lightswitch-1_0-...`):
   - Single-pole in-wall paddle switch with green Wi-Fi/power LED indicator and binary relay (`0`/`1`).
2. **In-Wall 3-Way Light Switch (2nd Gen RTOS)** (`WLS040` / `WLS0403`, UPnP `LightSwitch` / `uuid:Lightswitch-2_0-...` / `3_0`):
   - 3-way compatible in-wall rocker switch supporting multi-location traveler switching and binary relay (`0`/`1`), protected by `Semaphore(3)` LAN concurrency limiting.
3. **In-Wall Wi-Fi Smart Dimmer** (`F7C059` OpenWrt & `WDS060` RTOS, UPnP `Dimmer`):
   - In-wall touch dimmer with 7-segment green LED brightness bar, continuous `1–100%` dimming (`<brightness>`), and hardware Night Mode (`20%` soft cap via `ConfigureNightMode`).
4. **WeMo Mini Smart Plug** (`F7C063` & `WSP080`, UPnP `Socket` / `Mini`):
   - Compact horizontal pass-through smart outlet plug with side power toggle and front LED status indicator.

---

## 2. Architecture & Source Layout

### Package Structure (`app/src/main/java/com/example/`)

- **`MainActivity.kt`**:
  - Entry point (`WeMiSwitchApp`). Automatically detects Android TV (`UI_MODE_TYPE_TELEVISION`), renders the auto-detected `10.22.22.x` **WeMo Setup AP Banner**, and hosts the mobile/tablet `Scaffold` and dialogs (`ProvisionWeMoApDialog`, `AddOrEditWeMoDeviceDialog`, `WeMoSsdpScannerModal`).
- **`data/` (Room v5 Persistence, Self-Healing Repository & Scheduler)**:
  - `WeMoEntities.kt`:
    - `WeMoDeviceType`: `SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`.
    - `WeMoDeviceCategory` (`classifyDeviceCategory`): `CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`.
    - `WeMoConnectionQuality`: `EXCELLENT` (<= 28 ms, 4 bars), `GOOD` (<= 65 ms, 3 bars), `SLOW` (> 65 ms, 2 bars), `OFFLINE` (0 bars).
    - `WeMoHomeNetworkEntity` (`wemo_home_networks`): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
    - `WeMoDeviceEntity` (`wemo_devices`): Device state, network address (`ipAddress`, `port`, `networkKey`, `wifiSsid`), hardware telemetry (`serialNumber`, `firmwareVersion`, `hardwareModel`, `macAddress`, `udn`, `signalStrengthPercent`, `nightModeEnabled`), and live connection status (`isReachable`, `latencyMs`, `lastCheckedTime`, `isOn`, `brightness`, `autoOffMinutes`).
    - `WeMoRuleEntity` (`wemo_rules`): Local schedules and timers (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`).
  - `WeMoDao.kt` & `WeMoDatabase.kt`: Room database (`wemo_control_database.db`, schema `version = 5`).
  - `WeMoRepository.kt`: Bridges Room DAO, `WeMoNetworkClient`, `WeMoGenaSubscriptionManager`, and `WeMoProvisionerClient` with `Semaphore(3)` RTOS protection, per-device `Mutex`, 2-stage self-healing reconnect, startup `GetBinaryState` detection (`detectStartupDeviceStates`), manual IP 9-port hardware probing, optimistic UI with offline revert, and **Real LAN Mode** vs. **Demo Showroom Mode**.
  - `WeMoRuleScheduler.kt`: Coroutine engine for per-device/rule Auto-Off countdowns (`activeCountdownsSec`) and daily `SCHEDULE` / Sunset / Sunrise rule execution.
- **`network/` (`pywemo`-Grade UPnP / SSDP / SOAP / GENA & Provisioning Engine)**:
  - `WeMoNetworkClient.kt`:
    - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. read timeout separation and `<serialNumber>`/`<UDN>` verification.
    - `setBinaryState` (with `<BinaryState>Error</BinaryState>` handling), `getBinaryState`, `getMetaInfo`, `getSignalStrength`, `changeFriendlyName`, `configureNightMode`, `scanSubnetUnicast`, and `performSsdpDiscoveryScan` (`MulticastLock` + 1s retransmit).
  - `WeMoGenaSubscriptionManager.kt`: Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery) for `<100ms` physical switch updates.
  - `WeMoNetworkMonitor.kt`: Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector with `createWifiBoundSocketFactory`.
  - `WeMoProvisionerClient.kt`: `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` and `pywemo`-exact OpenSSL EVP_BytesToKey MD5 AES-128-CBC (Methods 1, 2 & 3) double-send `ConnectHomeNetwork` + `CloseSetup` + `SetSetupDoneStatus` provisioner.
- **`ui/` (Jetpack Compose Skeuomorphic UI)**:
  - `WeMoViewModel.kt`, `components/WeMoWifiHomeBar.kt`, `components/WeMoDeviceRow.kt`, `components/WeMoRoomGroupCard.kt`, `components/WeMoSsdpScannerModal.kt`, `components/WeMoDialogs.kt`, `components/WeMoTvScreen.kt`, `components/WeMoHardwareIllustrations.kt`, and `screens/WeMoRulesAndMoreScreens.kt`.

---

## 3. Build & Verification Commands (Mandatory Pre-Push Gate)

> **CRITICAL RULE**: **Only push code to GitHub (`origin/main` or `origin/agent-docs`) after verifying that the full build and all automated tests pass with zero errors.** Never push unverified or non-compiling code.

```bash
# Run Robolectric unit, protocol & UI integration tests (ExampleRobolectricTest + WeMoProtocolTest)
./gradlew testDebugUnitTest

# Assemble debug APK
./gradlew assembleDebug
```

- In remote VM environments where `HTTPS_PROXY` is active, ensure `~/.gradle/gradle.properties` configures `http.proxyHost`/`https.proxyHost` and `javax.net.ssl.trustStore` with the local proxy CA, and `ANDROID_HOME` points to the Android SDK (`platforms;android-36`, `build-tools;36.0.0`).
- **Keep Documentation Updated**: Whenever new features are implemented or existing features are adjusted, always update:
  - [`IMPLEMENTED.md`](IMPLEMENTED.md) and [`docs/IMPLEMENTED.md`](docs/IMPLEMENTED.md)
  - [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md), and the relevant feature guides under [`docs/user-guide/`](docs/user-guide/).



