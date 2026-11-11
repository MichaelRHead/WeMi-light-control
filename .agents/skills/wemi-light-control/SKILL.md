---
name: wemi-light-control
description: Architecture, Belkin WeMo UPnP/SSDP/SOAP/GENA protocol reference, Room v5 schema, and production implementation guide for the WeMi Light Control Android & Android TV app (MichaelRHead/WeMi-light-control). Use whenever inspecting, modifying, testing, or extending local LAN discovery, GENA push events, rules, and 10.22.22.1 zero-cloud provisioning for WeMo In-Wall Switches (1st Gen & 2nd Gen 3-Way), In-Wall Dimmers, and WeMo Mini Smart Plugs.
---

# WeMi Light Control Skill (`MichaelRHead/WeMi-light-control`)

Use this skill when working on the **WeMi Light Control** Android & Android TV application in `/home/antigravity/MichaelRHead/WeMi-light-control`.

For detailed UPnP/SSDP/SOAP/GENA wire formats, device model identifiers, and XML schemas for Belkin's discontinued smart switch platform, read [`references/wemo-upnp-protocol.md`](references/wemo-upnp-protocol.md), [`../../UX_DESIGN.md`](../../UX_DESIGN.md) and [`../../UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md) (AI Studio Agent-owned UX specifications), [`../../DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md) (Antigravity Debugging & Support Agent-owned telemetry specification), [`../../DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md) (Antigravity Device Discovery & Room Grouping Agent-owned specification), [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) (Antigravity License & Brand Governance Agent-owned specification), [`../../IMPLEMENTED.md`](../../IMPLEMENTED.md) (reverse-chronological Change Log), [`../../UPCOMING_FEATURES.md`](../../UPCOMING_FEATURES.md) (upcoming features roadmap), and [`../../USER_GUIDE.md`](../../USER_GUIDE.md) (plus [`../../docs/user-guide/`](../../docs/user-guide/)). For UX design, device discovery & room grouping, license & brand governance, human-readable code modularization, E2E testing standards, on-device screenshot/telemetry debugging, multi-agent Git synchronization, agent commit attribution, and Change Log maintenance, also consult [`../wemi-ux-design-and-experience/SKILL.md`](../wemi-ux-design-and-experience/SKILL.md), [`../wemi-discovery-and-room-grouping/SKILL.md`](../wemi-discovery-and-room-grouping/SKILL.md), [`../wemi-license-governance/SKILL.md`](../wemi-license-governance/SKILL.md), [`../android-code-modularization/SKILL.md`](../android-code-modularization/SKILL.md), [`../android-e2e-testing/SKILL.md`](../android-e2e-testing/SKILL.md), [`../wemi-device-telemetry-debug/SKILL.md`](../wemi-device-telemetry-debug/SKILL.md), [`../multi-agent-git-sync/SKILL.md`](../multi-agent-git-sync/SKILL.md), [`../agent-commit-attribution/SKILL.md`](../agent-commit-attribution/SKILL.md), and [`../wemi-changelog-and-docs/SKILL.md`](../wemi-changelog-and-docs/SKILL.md). Always keep root `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, and `docs/user-guide/*.md` updated as features are added or modified (do not duplicate `ALL_CAPS.md` files inside `docs/`).

---

## 1. Build, Test & Environment

- **Toolchain**: AGP `9.1.0`, Kotlin `2.3.20`, KSP `2.3.6`, Compose BOM `2026.03.00`, Room `2.8.4` (`version = 5`), Robolectric `4.16.1`, `compileSdk = 36`, `minSdk = 24`.
- **Unit, Protocol, Governance & E2E Compose UI Tests (`30/30` across 8 suites)**:
  ```bash
  ./gradlew testDebugUnitTest --rerun-tasks
  ```
- **Debug APK Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Package / Namespace**: `org.suppressingfire.wemi` (`applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `versionName = "22.0"`, `versionCode = 22`).

---

## 2. Target Hardware Inventory

The user's real hardware fleet (`192.168.86.0/24`, 17–18 switches across 9 rooms) consists of four device families from Belkin's discontinued smart switch platform:

| Hardware Family | Real Hardware SKUs | UPnP `<modelName>` / `<UDN>` / Setup AP | Capabilities |
| :--- | :--- | :--- | :--- |
| **1st-Gen In-Wall Switch** | `F7C030` (OpenWrt) | `LightSwitch` / `uuid:Lightswitch-1_0-...` / `WeMo.Light.xxx`, `WeMo.Switch.xxx` | Binary Relay (`0`/`1`), Green Wi-Fi/Power LED |
| **2nd-Gen 3-Way Switch** | `WLS040`, `WLS0403` (RTOS) | `LightSwitch` / `WLS040` / `WLS0403` (`<rtos>1</rtos>`) / `Wemo.LS3W.xxx` | 3-way traveler switching, Binary Relay (`0`/`1`), protected by `Semaphore(3)` LAN concurrency cap |
| **In-Wall Smart Dimmer** | `F7C059` (OpenWrt), `WDS060` (RTOS) | `Dimmer` / `WDS060` / `WeMo.Dimmer.xxx` | Binary state + Brightness (`1..100%`), Hardware Night Mode (`20%` cap via `ConfigureNightMode`) |
| **Mini Smart Plug** | `F7C063`, `WSP080` | `Socket` / `Mini` / `WSP080` / `WeMo.Mini.xxx` | Compact pass-through outlet relay (`0`/`1`), side power button & status LED |

---

## 3. Codebase Map (`v22.0` — `org.suppressingfire.wemi`)

### Data & Scheduling Layer (`app/src/main/java/org/suppressingfire/wemi/data/`)
- **`DeviceEntities.kt`**:
  - `DeviceType`: `SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`.
  - `DeviceCategory` (`classifyDeviceCategory`): `CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`.
  - `normalizeDeviceNameForInference`, `inferRoomFromName` & `resolveDeviceRoomAssignment`: Normalizes CamelCase/delimiters, infers rooms in specificity order (`"Ensuite"` before `"Bedroom"`, `"bed"`, `"bedside"`, `"nightstand"`, `"girls"`/`"boys"`/`"kids"` → `"Bedroom"`, `"stair"`/`"stairway"` → `"Hallway & Landing"`, word-bounded `"den"`/`"hall"`, and `"Unassigned"` fallback instead of `"Living Room"`), and non-destructively migrates switches stuck in `"Living Room"`.
  - `ConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `HomeNetworkEntity` (`wemo_home_networks`): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
  - `DeviceEntity` (`wemo_devices`): Persisted device state (`id`, `name`, `roomName`, `networkKey`, `wifiSsid`, `deviceType`, `ipAddress`, `port`, `isOn`, `isReachable`, `brightness`, `latencyMs`, `lastCheckedTime`, `lastMotionTime`, `serialNumber`, `firmwareVersion`, `hardwareModel`, `macAddress`, `udn`, `signalStrengthPercent`, `nightModeEnabled`, `sortOrder`, `autoOffMinutes`).
  - `RuleEntity` (`wemo_rules`): Local automation rules (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`).
- **`SwitchDao.kt` & `SwitchDatabase.kt`**: Room DB at `version = 5` (`wemo_control_database.db`, `.fallbackToDestructiveMigration(true)`), with per-`Application` in-memory isolation and automatic `SQLiteDatabase.close()` on test transitions under Robolectric.
- **`SwitchRepository.kt`**, **`DiscoveryCoordinator.kt`**, **`ProvisioningCoordinator.kt`** & **`DemoFixtures.kt`**:
  - Coordinates `SwitchDao`, `NetworkClient`, `GenaSubscriptionManager`, and `ProvisionerClient`.
  - Defaults to **Real LAN Mode** (`isDemoShowroomMode = false`) on physical Android devices and **Demo Showroom Mode** (`true`) inside Robolectric unless overridden.
  - Enforces shared `Semaphore(3)` LAN concurrency + per-device `Mutex`, 3-tier hardware identity matching (`matchesDiscoveredDevice` & `findMatchingExistingDevice`), startup `GetBinaryState` detection (`detectStartupDeviceStates`), manual IP 9-port hardware probing (`saveDevice`), unified `executeOptimisticDeviceCommand` with 2-stage self-healing reconnect (9-port probe → targeted SSDP by `serialNumber`), and offline state revert (`isReachable = false, latencyMs = 0`, clearing `lastSetStateTimestampMs`).
  - Filters out Belkin soft-AP SSIDs (`WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) from `metainfo1#GetMetaInfo` (`isWemoDeviceSetupSsid`), auto-sanitizes any previously polluted `homeName`/`ssidLabel` rows and re-infers legacy `"Living Room"` room assignments on startup (`sanitizeAndResolveInitialLanHome`), and provides one-tap `resetLanHomesSwitchesAndRooms()`.
- **`RuleScheduler.kt`**:
  - Manages live per-device Auto-Off countdown timers (`activeCountdownsSec`) and evaluates daily `SCHEDULE` / Sunset / Sunrise rules.

### Network Layer (`app/src/main/java/org/suppressingfire/wemi/network/`)
- **`LanHttpClient.kt`**, **`NetworkClient.kt`**, **`XmlAndSkuParser.kt`** & **`SsdpDiscoveryEngine.kt`**:
  - Zero-dependency `LanHttpClient` built directly on `javax.net.SocketFactory` (`GET`, `POST`, `SUBSCRIBE`, `UNSUBSCRIBE` with byte-exact `Content-Length` / chunked framing).
  - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation and `<serialNumber>`/`<UDN>` verification.
  - Unified `executeWithPortHealing`, `setBinaryState` (with `<BinaryState>Error</BinaryState>` quirk handling), `getBinaryState`, `getMetaInfo` (filtering Belkin soft-AP SSIDs in `parseMetaInfoSoapResponse`), `getSignalStrength`, `changeFriendlyName`, `configureNightMode`, `scanSubnetUnicast`, and `performSsdpDiscoveryScan` (`MulticastLock` + 1s retransmit).
- **`GenaSubscriptionManager.kt`**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery) bound to `wifiSocketFactory` for `<100ms` physical switch updates.
- **`NetworkMonitor.kt`**:
  - Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector with `createWifiBoundSocketFactory` (caching unvalidated `NetworkCallback` Wi-Fi handles + loopback `127.0.0.1` & Robolectric aware).
- **`ProvisionerClient.kt`**, **`SetupDiagnostics.kt`** & **`PasswordCrypto.kt`**:
  - `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` (`8500ms` site-survey timeout), `LanHttpClient`-framed `probeSetupDevice`, `SetupDebugSnapshot` screenshot diagnostics, `TelemetryFileExporter` (`wemi-telemetry-latest.txt`), and first-party OpenSSL `EVP_BytesToKey` MD5 AES-128-CBC (Methods 1, 2 & 3 × `addPasswordLengths`) double-send `ConnectHomeNetwork` + 20s `GetNetworkStatus` poll + `CloseSetup` + `SetSetupDoneStatus` provisioner.

### UI & Test Layer (`app/src/main/java/org/suppressingfire/wemi/ui/` & `app/src/test/java/org/suppressingfire/wemi/`)
- **`SwitchViewModel.kt`**, **`MainActivity.kt`**, **`ui/screens/*`** & **`ui/components/*`**:
  - Full-width `AutoShrinkSwitchName` on Line 1 of `DeviceRow`, `TvDeviceCard`, `RoomGroupHeaderCard`, and `WifiHomeBar` so long names (`"Hallway & Landing"`, `"My Home"`) never squeeze adjacent pills into vertical character columns.
  - Dark-gray IEC `"I/O"` power-off icon (`drawIecPowerSymbol` in `#23272F`/`#181B20`), red `DisconnectedRingButton` (`drawDisconnectedSymbol`), screenshot-ready `ProvisionDebugPanel` (`provision_debug_panel`, `provision_debug_text`) inside `ProvisionApDialog`, and `open_source_licenses_card` (`mit_license_text_surface` & `third_party_notices_surface`) in `MoreScreen`.
  - One-tap `Reset` chip (`reset_lan_data_chip`) in `WifiHomeBar` and `more_reset_lan_homes_switches_button` on `MoreScreen`.
- **`FakeUpnpServer.kt`**, **`SkuAndCryptoTest.kt`**, **`SoapAndSelfHealingTest.kt`**, **`GenaProvisioningAndRulesTest.kt`**, **`LicenseAndBrandGovernanceTest.kt`**, **`ExampleRobolectricTest.kt`**, **`EndToEndUiTest.kt`** & **`HardwareCujEndToEndTest.kt`**:
  - 30 automated loopback UPnP/SOAP/GENA protocol, license governance, and full-stack Compose E2E tests.
