---
name: wemi-light-control
description: Architecture, Belkin WeMo UPnP/SSDP/SOAP/GENA protocol reference, Room v5 schema, and production implementation guide for the WeMi Light Control Android & Android TV app (MichaelRHead/WeMi-light-control). Use whenever inspecting, modifying, testing, or extending local LAN discovery, GENA push events, rules, and 10.22.22.1 zero-cloud provisioning for WeMo In-Wall Switches (1st Gen & 2nd Gen 3-Way), In-Wall Dimmers, and WeMo Mini Smart Plugs.
---

# WeMi Light Control Skill (`MichaelRHead/WeMi-light-control`)

Use this skill when working on the **WeMi Light Control** Android & Android TV application in `/home/antigravity/MichaelRHead/WeMi-light-control`.

For detailed UPnP/SSDP/SOAP/GENA wire formats, device model identifiers, and XML schemas for Belkin WeMo switches, dimmers, and mini plugs, read [`references/wemo-upnp-protocol.md`](references/wemo-upnp-protocol.md), [`../../IMPLEMENTED.md`](../../IMPLEMENTED.md), [`../../USER_GUIDE.md`](../../USER_GUIDE.md) (and [`../../docs/user-guide/`](../../docs/user-guide/)), [`../../docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](../../docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md), and [`../../docs/IMPLEMENTATION_PLAN.md`](../../docs/IMPLEMENTATION_PLAN.md). For human-readable code modularization and E2E testing standards, also consult [`../android-code-modularization/SKILL.md`](../android-code-modularization/SKILL.md) and [`../android-e2e-testing/SKILL.md`](../android-e2e-testing/SKILL.md). Always keep `IMPLEMENTED.md`, `USER_GUIDE.md`, and `docs/user-guide/*.md` updated as features are added or modified.

---

## 1. Build, Test & Environment

- **Toolchain**: AGP `9.1.0`, Kotlin `2.3.20`, KSP `2.3.6`, Compose BOM `2026.03.00`, Room `2.8.4` (`version = 5`), Robolectric `4.16.1`, `compileSdk = 36`, `minSdk = 24`.
- **Unit & Protocol Tests (`ExampleRobolectricTest` + `WeMoProtocolTest`)**:
  ```bash
  ./gradlew testDebugUnitTest
  ```
- **Debug APK Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Package / Namespace**: `com.example` (`applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `versionName = "12.0"`).

---

## 2. Target Hardware Inventory

The user's real hardware fleet consists of four Belkin WeMo device families:

| Hardware Family | Real Hardware SKUs | UPnP `<modelName>` / `<UDN>` | Capabilities |
| :--- | :--- | :--- | :--- |
| **1st-Gen In-Wall Switch** | `F7C030` (OpenWrt) | `LightSwitch` / `uuid:Lightswitch-1_0-...` | Binary Relay (`0`/`1`), Green Wi-Fi/Power LED |
| **2nd-Gen 3-Way Switch** | `WLS040`, `WLS0403` (RTOS) | `LightSwitch` / `WLS040` / `WLS0403` (`<rtos>1</rtos>`) | 3-way traveler switching, Binary Relay (`0`/`1`), protected by `Semaphore(3)` LAN concurrency cap |
| **In-Wall Smart Dimmer** | `F7C059` (OpenWrt), `WDS060` (RTOS) | `Dimmer` / `WDS060` | Binary state + Brightness (`1..100%`), Hardware Night Mode (`20%` cap via `ConfigureNightMode`) |
| **WeMo Mini Smart Plug** | `F7C063`, `WSP080` | `Socket` / `Mini` / `WSP080` | Compact pass-through outlet relay (`0`/`1`), side power button & status LED |

---

## 3. Codebase Map (v12.0 / Completed Phases 1–7)

### Data & Scheduling Layer (`app/src/main/java/com/example/data/`)
- **`WeMoEntities.kt`**:
  - `WeMoDeviceType`: `SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`.
  - `WeMoConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `WeMoHomeNetworkEntity` (`wemo_home_networks`): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
  - `WeMoDeviceEntity` (`wemo_devices`): Persisted device state (`id`, `name`, `roomName`, `networkKey`, `wifiSsid`, `deviceType`, `ipAddress`, `port`, `isOn`, `isReachable`, `brightness`, `latencyMs`, `lastCheckedTime`, `lastMotionTime`, `serialNumber`, `firmwareVersion`, `hardwareModel`, `macAddress`, `udn`, `signalStrengthPercent`, `nightModeEnabled`, `sortOrder`, `autoOffMinutes`).
  - `WeMoRuleEntity` (`wemo_rules`): Local automation rules (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`).
- **`WeMoDao.kt` & `WeMoDatabase.kt`**: Room DB at `version = 5` (`wemo_control_database.db`, `.fallbackToDestructiveMigration(true)`).
- **`WeMoRepository.kt`**:
  - Coordinates `WeMoDao`, `WeMoNetworkClient`, `WeMoGenaSubscriptionManager`, and `WeMoProvisionerClient`.
  - Defaults to **Real LAN Mode** (`isDemoShowroomMode = false`) on physical Android devices and **Demo Showroom Mode** (`true`) inside Robolectric unless overridden.
  - Enforces `Semaphore(3)` LAN concurrency + per-device `Mutex`, optimistic UI with 2-stage self-healing reconnect (9-port probe → targeted SSDP by `serialNumber`), and offline state revert (`isReachable = false, latencyMs = 0`).
  - Auto-populates `WeMoHomeNetworkEntity.ssidLabel` from `metainfo1#GetMetaInfo` (`AccessPointSSID`).
- **`WeMoRuleScheduler.kt`**:
  - Manages live per-device Auto-Off countdown timers (`activeCountdownsSec`) and evaluates daily `SCHEDULE` / Sunset / Sunrise rules.

### Network Layer (`app/src/main/java/com/example/network/`)
- **`WeMoNetworkClient.kt`**:
  - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation and `<serialNumber>`/`<UDN>` verification.
  - `setBinaryState` (with `<BinaryState>Error</BinaryState>` quirk handling), `getBinaryState`, `getMetaInfo`, `getSignalStrength`, `changeFriendlyName`, `configureNightMode`, `scanSubnetUnicast`, and `performSsdpDiscoveryScan` (`MulticastLock` + 1s retransmit).
- **`WeMoGenaSubscriptionManager.kt`**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery) for `<100ms` physical switch updates.
- **`WeMoNetworkMonitor.kt`**:
  - Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector.
- **`WeMoProvisionerClient.kt`**:
  - `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` and AES-128-CBC (Methods 1 & 2) double-send `ConnectHomeNetwork` + `CloseSetup` provisioner.

### UI & Test Layer
- **`WeMoViewModel.kt`** & **`MainActivity.kt`**: Combines Room flows, live `lanNetworkState` (`10.22.22.x` Setup AP banner), `activeAutoOffCountdownsSec`, GENA subscription + 30s fallback poll loop, and Android TV 10-foot UI.
- **`WeMoProtocolTest.kt` & `ExampleRobolectricTest.kt`**: Loopback UPnP/SOAP/GENA protocol tests and Compose UI integration tests.

