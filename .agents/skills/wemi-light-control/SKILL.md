---
name: wemi-light-control
description: "WeMi Light Control domain guide: local UPnP/SSDP/SOAP/GENA protocol, Room v5 schema, and 10.22.22.1 setup for switches, dimmers, and plugs."
---

# WeMi Light Control Skill (`MichaelRHead/WeMi-light-control`)

Use this skill when working on the **WeMi Light Control** Android & Android TV application in `/home/antigravity/MichaelRHead/WeMi-light-control`.

For detailed UPnP/SSDP/SOAP/GENA wire formats, device model identifiers, and XML schemas for Belkin's discontinued smart switch platform, read [`references/wemo-upnp-protocol.md`](references/wemo-upnp-protocol.md), [`../../UX_DESIGN.md`](../../UX_DESIGN.md) and [`../../UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md) (AI Studio Agent-owned UX specifications), [`../../DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md) (Antigravity Debugging & Support Agent-owned telemetry specification), [`../../DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md) (Antigravity Device Discovery & Room Grouping Agent-owned specification), [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) (Antigravity License & Brand Governance Agent-owned specification), [`../../IMPLEMENTED.md`](../../IMPLEMENTED.md) (reverse-chronological Change Log), [`../../UPCOMING_FEATURES.md`](../../UPCOMING_FEATURES.md) (upcoming features roadmap), and [`../../USER_GUIDE.md`](../../USER_GUIDE.md) (plus [`../../docs/user-guide/`](../../docs/user-guide/)). For UX design, device discovery & room grouping, license & brand governance, human-readable code modularization, E2E testing standards, on-device screenshot/telemetry debugging, multi-agent Git synchronization, agent commit attribution, and Change Log maintenance, also consult [`../wemi-ux-design-and-experience/SKILL.md`](../wemi-ux-design-and-experience/SKILL.md), [`../wemi-discovery-and-room-grouping/SKILL.md`](../wemi-discovery-and-room-grouping/SKILL.md), [`../wemi-license-governance/SKILL.md`](../wemi-license-governance/SKILL.md), [`../android-code-modularization/SKILL.md`](../android-code-modularization/SKILL.md), [`../android-e2e-testing/SKILL.md`](../android-e2e-testing/SKILL.md), [`../wemi-device-telemetry-debug/SKILL.md`](../wemi-device-telemetry-debug/SKILL.md), [`../multi-agent-git-sync/SKILL.md`](../multi-agent-git-sync/SKILL.md), [`../agent-commit-attribution/SKILL.md`](../agent-commit-attribution/SKILL.md), and [`../wemi-changelog-and-docs/SKILL.md`](../wemi-changelog-and-docs/SKILL.md). Always keep root `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, and `docs/user-guide/*.md` updated as features are added or modified (do not duplicate `ALL_CAPS.md` files inside `docs/`).

---

## 1. Build, Test & Environment

- **Toolchain**: Gradle `9.3.1`, AGP `9.1.1`, Kotlin `2.2.10`, KSP `2.3.5`, Compose BOM `2024.09.00`, Room `2.7.0` (`version = 5`), Robolectric `4.16.1`, `compileSdk = 36`, `minSdk = 24`.
- **Unit, Protocol, Widget, Telemetry, Governance & E2E Compose UI Tests (`102/102` across 20 suites + Lint)**:
  ```bash
  sh gradlew testDebugUnitTest --rerun-tasks lintDebug
  ```
- **Debug APK Build**:
  ```bash
  sh gradlew assembleDebug
  ```
- **Package / Namespace**: `org.suppressingfire.wemi` (`applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `versionName = "31.0"`, `versionCode = 31`).

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

## 3. Codebase Map (`v31.0` — `org.suppressingfire.wemi`, `100%` of `90` `.kt` Files `< 500` Lines)

### Data & Scheduling Layer (`app/src/main/java/org/suppressingfire/wemi/data/`)
- **`DeviceEntities.kt`**:
  - `UNASSIGNED_ROOM`, `normalizeRoomName`, `DeviceEntity.normalizedRoomName`, `DeviceType` (`SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`), `RuleType` (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`), and `DeviceCategory` (`classifyDeviceCategory`: `CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`).
  - `DeviceEntity.matchesDiscovered`, `DeviceEntity.concurrencyKey` & `SsdpDiscoveredDevice.matchesEndpointOrSerial`: Shared 3-tier hardware identity matching (`serialNumber` with placeholder-serial fallthrough → canonical `UDN`/`USN` → `(ipAddress, port)`/`friendlyName`) and placeholder-serial-safe per-device `Mutex` key (`serialNumber` → canonical `udn` → `"$ipAddress:$port"`).
  - `normalizeDeviceNameForInference`, `inferRoomFromName` & `resolveDeviceRoomAssignment`: Normalizes CamelCase/delimiters, infers rooms in specificity order (`"Ensuite"` before `"Bedroom"`, `"bed"`, `"bedside"`, `"nightstand"`, `"girls"`/`"boys"`/`"kids"` → `"Bedroom"`, `"stair"`/`"stairway"` → `"Hallway & Landing"`, word-bounded `"den"`/`"hall"`, and `"Unassigned"` fallback instead of `"Living Room"`), and non-destructively migrates switches stuck in `"Living Room"`.
  - `ConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `HomeNetworkEntity` (`wemo_home_networks`), `DeviceEntity` (`wemo_devices`), and `RuleEntity` (`wemo_rules`).
- **`SwitchDao.kt` & `SwitchDatabase.kt`**: Room DB at `version = 5` (`wemi_switch_database`, `.fallbackToDestructiveMigration(true)`), with per-`Application` in-memory isolation and automatic `SQLiteDatabase.close()` on test transitions under Robolectric.
- **`SwitchRepository.kt`**, **`GenaAndHealthPollingCoordinator.kt`**, **`DiscoveryCoordinator.kt`**, **`HomeProfileSanitizer.kt`**, **`ProvisioningCoordinator.kt`**, **`DeviceCrudAndRulesCoordinator.kt`** & **`DemoFixtures.kt`**:
  - Coordinates `SwitchDao`, `NetworkClient`, `GenaSubscriptionManager`, and `ProvisionerClient` (`100%` of `data/` files `< 500` lines).
  - Defaults to **Real LAN Mode** (`isDemoShowroomMode = false`) on physical Android devices and **Demo Showroom Mode** (`true`) inside Robolectric unless overridden (`LanRuntimeUtils.isRunningInRobolectric()`), serializing `ensureSeeded` and Demo/LAN mode resets via `seedAndModeMutex` + cold-start `awaitDemoSeedIfPending()`.
  - Enforces shared `Semaphore(3)` LAN concurrency + `concurrencyKey()`-isolated per-device `Mutex`, 4-pass hardware identity matching (`matchesDiscoveredDevice` & `findMatchingExistingDevice`), inside-lock fresh `dao.getDeviceById` snapshots across startup/reconnect/SSDP updates, monotonic home-scoped `sortOrder` after deletions, parallel `Semaphore(3)` `pollActiveHomeStates` and `refreshGenaSubscriptionsForHome` + strict 3-tier `handleGenaNotification` (`GenaAndHealthPollingCoordinator.kt`), manual IP 9-port hardware probing + home/room-scoped `moveDeviceOrder` + unified Wi-Fi Home-isolated `executeRuleAction` (`DeviceCrudAndRulesCoordinator.kt`), concurrent `Semaphore(3)`-bounded room batch execution, idempotent `setDevicePowerExplicit`, mutex-serialized `stepDeviceBrightness`, unified `executeOptimisticDeviceCommand`, soft-AP SSID startup DB sanitization (`HomeProfileSanitizer.kt`), and one-tap `resetLanHomesSwitchesAndRooms()`.
- **`RuleScheduler.kt`**:
  - Manages live per-device Auto-Off countdown timers (`activeCountdownsSec`, preserving in-flight countdowns across 30s health polls and isolating same-named devices across homes via `activeDeviceIds`) and evaluates daily `SCHEDULE` / Sunset / Sunrise rules.

### Network Layer (`app/src/main/java/org/suppressingfire/wemi/network/`)
- **`LanHttpClient.kt`**, **`LanRuntimeUtils.kt`**, **`NetworkModels.kt`**, **`NetworkClient.kt`**, **`DeviceDescriptorFetcher.kt`**, **`XmlAndSkuParser.kt`** & **`SsdpDiscoveryEngine.kt`**:
  - Zero-dependency `LanHttpClient` built directly on `javax.net.SocketFactory` (`GET`, `POST`, `SUBSCRIBE`, `UNSUBSCRIBE` with `tcpNoDelay = true`, byte-exact `Content-Length` / chunked framing, and delayed-`FIN` recovery).
  - Centralized SOAP XML builders, pre-compiled regexes, and `escapeXml`/`unescapeXml` entity handling (`XmlAndSkuParser.kt`).
  - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation and strict `<serialNumber>`/`<UDN>` identity verification (`NetworkClient.kt`, `DeviceDescriptorFetcher.kt`).
  - `SsdpDiscoveryEngine.kt` (`MulticastLock`, `ensureActive()` cancellation check, `Semaphore(3)` Stage A/B enrichment & multicast-zero Stage C `/24` unicast sweep).
- **`GenaSubscriptionManager.kt`**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, `SupervisorJob`-isolated per-client socket handling, HTTP 412 recovery with superseded-SID & placeholder eviction, `deviceKeyFor`, and `unsubscribeDevice`) for `<100ms` physical switch updates.
- **`NetworkMonitor.kt`**:
  - Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector with `createWifiBoundSocketFactory`.
- **`ProvisionerClient.kt`**, **`SetupApProber.kt`**, **`SetupDiagnostics.kt`** & **`PasswordCrypto.kt`**:
  - `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` (`8500ms` site-survey timeout), `LanHttpClient`-framed `probeSetupDevice`, `SharedSoapLogStore` widget-to-app log ring buffer, `BuildConfig`-stamped `SetupDebugSnapshot`, 5-section `TelemetryFileExporter` (`wemi-telemetry-latest.txt`), and `minSdk 24`-safe OpenSSL `EVP_BytesToKey` MD5 AES-128-CBC (Methods 1, 2 & 3) `ConnectHomeNetwork` provisioning.

### UI, Widget & Test Layer (`ui/`, `widget/` & `app/src/test/java/org/suppressingfire/wemi/`)
- **`SwitchViewModel.kt`**, **`DeviceActionsUiController.kt`**, **`ProvisionApUiController.kt`**, **`HomeAndRulesUiController.kt`**, **`MainActivity.kt`**, **`ui/screens/*`** & **`ui/components/*`**:
  - All UI, ViewModel, and controller files sit strictly under `500` lines (`TvHeadersAndScenes.kt` `456L` with deduplicated `TvSceneChip`, `SwitchViewModel.kt` `496L` with IO-offloaded device-only widget refresh, lock-synchronized `ssdpScanJob`/`reconnectJob`/`scanJob` lifecycles, `RuleDialog` defaulting `AUTO_OFF` to OFF, compact `48.dp` single-row `TopHeader`, 3-column room-grouped `wemi TV`, 3-state hardware icons, `ProvisionDebugPanel`, and `open_source_licenses_card` in `MoreScreen`).
- **`widget/` (`WeMiSwitchWidgetProvider.kt`, `WidgetActionExecutor.kt`, `WidgetConfigStore.kt`, `WidgetRemoteViewsBuilder.kt`, `SwitchWidgetConfigureActivity.kt`, `WidgetPickerCards.kt`)**:
  - Zero-dependency Android Home Screen Light & Room Toggle Widgets with one-tap `requestPinAppWidget` pinning, launcher configuration picker, serial-first anti-hijack target resolution, multi-home Room isolation, per-widget `Mutex` double-tap serialization + delete/restore eviction, `SharedSoapLogStore` telemetry bridging, and non-positive ID guards.
- **Test Suites (`20` suites, `102/102` Kotlin tests + `21/21` Python script tests — see [`ARCHITECTURE.md`](../../ARCHITECTURE.md) & [`android-e2e-testing`](../android-e2e-testing/SKILL.md))**:
  - `FakeUpnpServer.kt`, `ComposeLoopbackTestUtils.kt`, `ExampleUnitTest.kt`, `SkuAndCryptoTest.kt`, `RuleAndTransportTest.kt`, `SoapAndSelfHealingTest.kt`, `RepositorySelfHealingTest.kt`, `BatchAndCrudIntegrationTest.kt`, `DiscoveryAndGroupingIntegrationTest.kt`, `LifecycleAndConcurrencyTest.kt`, `GenaProvisioningAndRulesTest.kt`, `TelemetryDiagnosticsTest.kt`, `LicenseAndBrandGovernanceTest.kt`, `ExampleRobolectricTest.kt`, `EndToEndUiTest.kt`, `HardwareCujEndToEndTest.kt`, `ProvisioningAndGenaCujE2ETest.kt`, `TvAndHeaderLayoutE2ETest.kt`, `TvNavigationAndScenesE2ETest.kt`, `HomeScreenWidgetTest.kt`, `HomeScreenWidgetUiE2ETest.kt`, and `AppFlowsAndManagementE2ETest.kt` (`100%` `< 500` lines).
