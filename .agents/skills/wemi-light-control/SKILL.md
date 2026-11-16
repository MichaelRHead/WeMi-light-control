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
- **Unit, Protocol, Governance & E2E Compose UI Tests (`56/56` across 13 suites + Lint)**:
  ```bash
  ./gradlew testDebugUnitTest --rerun-tasks lintDebug
  ```
- **Debug APK Build**:
  ```bash
  ./gradlew assembleDebug
  ```
- **Package / Namespace**: `org.suppressingfire.wemi` (`applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `versionName = "27.0"`, `versionCode = 27`).

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

## 3. Codebase Map (`v27.0` — `org.suppressingfire.wemi`, `100%` of `77` `.kt` Files `< 500` Lines)

### Data & Scheduling Layer (`app/src/main/java/org/suppressingfire/wemi/data/`)
- **`DeviceEntities.kt`**:
  - `DeviceType` (`SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`), `RuleType` (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`), and `DeviceCategory` (`classifyDeviceCategory`: `CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`).
  - `DeviceEntity.matchesDiscovered`, `DeviceEntity.concurrencyKey` & `SsdpDiscoveredDevice.matchesEndpointOrSerial`: Shared 3-tier hardware identity matching (`serialNumber` with placeholder-serial fallthrough → `UDN`/`USN` → `(ipAddress, port)`/`friendlyName`) and placeholder-serial-safe per-device `Mutex` key (`serialNumber` when real, else `"$ipAddress:$port"`).
  - `normalizeDeviceNameForInference`, `inferRoomFromName` & `resolveDeviceRoomAssignment`: Normalizes CamelCase/delimiters, infers rooms in specificity order (`"Ensuite"` before `"Bedroom"`, `"bed"`, `"bedside"`, `"nightstand"`, `"girls"`/`"boys"`/`"kids"` → `"Bedroom"`, `"stair"`/`"stairway"` → `"Hallway & Landing"`, word-bounded `"den"`/`"hall"`, and `"Unassigned"` fallback instead of `"Living Room"`), and non-destructively migrates switches stuck in `"Living Room"`.
  - `ConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `HomeNetworkEntity` (`wemo_home_networks`), `DeviceEntity` (`wemo_devices`), and `RuleEntity` (`wemo_rules`).
- **`SwitchDao.kt` & `SwitchDatabase.kt`**: Room DB at `version = 5` (`wemi_switch_database`, `.fallbackToDestructiveMigration(true)`), with per-`Application` in-memory isolation and automatic `SQLiteDatabase.close()` on test transitions under Robolectric.
- **`SwitchRepository.kt`**, **`GenaAndHealthPollingCoordinator.kt`**, **`DiscoveryCoordinator.kt`**, **`HomeProfileSanitizer.kt`**, **`ProvisioningCoordinator.kt`**, **`DeviceCrudAndRulesCoordinator.kt`** & **`DemoFixtures.kt`**:
  - Coordinates `SwitchDao`, `NetworkClient`, `GenaSubscriptionManager`, and `ProvisionerClient` (`100%` of `data/` files `< 500` lines).
  - Defaults to **Real LAN Mode** (`isDemoShowroomMode = false`) on physical Android devices and **Demo Showroom Mode** (`true`) inside Robolectric unless overridden (`LanRuntimeUtils.isRunningInRobolectric()`), serializing `ensureSeeded` and Demo/LAN mode resets via `seedAndModeMutex`.
  - Enforces shared `Semaphore(3)` LAN concurrency + `concurrencyKey()`-isolated per-device `Mutex`, 3-tier hardware identity matching (`matchesDiscoveredDevice` & `findMatchingExistingDevice`), inside-lock fresh `dao.getDeviceById` snapshots across startup `GetBinaryState` detection (`detectStartupDeviceStates`), single-pass concurrent `reconnectAndRediscoverHome`, `reconnectDevice`, and `executeSsdpScan` (`DiscoveryCoordinator.kt`), monotonic home-scoped `sortOrder` after deletions (`HomeProfileSanitizer.kt`, `DeviceCrudAndRulesCoordinator.kt`, `ProvisioningCoordinator.kt`), parallel `Semaphore(3)` `pollActiveHomeStates` and `refreshGenaSubscriptionsForHome` + strict 3-tier `handleGenaNotification` (`serialNumber` → `"dev_${id}"` → blank-key `sourceIp` inside `mutexFor(matched)`) (`GenaAndHealthPollingCoordinator.kt`), manual IP 9-port hardware probing + home/room-scoped `moveDeviceOrder` (`DeviceCrudAndRulesCoordinator.kt`), concurrent `Semaphore(3)`-bounded `roomName.ifBlank { "Unassigned" }`-normalized room batch execution (`toggleRoomPower`, `updateRoomBrightness`), idempotent `setDevicePowerExplicit`, unified `executeOptimisticDeviceCommand` reading fresh `baseDevice = dao.getDeviceById(device.id)` *inside* `mutexFor(device).withLock` with 2-stage self-healing reconnect (9-port probe → targeted SSDP by `serialNumber`), offline state revert (`isReachable = false, latencyMs = 0`, clearing `lastSetStateTimestampMs`), and GENA/mutex/echo cleanup on `deleteDevice` and resets.
  - Filters out Belkin soft-AP SSIDs (`WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) from `metainfo1#GetMetaInfo` (`isWemoDeviceSetupSsid`), auto-sanitizes any previously polluted `homeName`/`ssidLabel` rows and re-infers legacy `"Living Room"` room assignments on startup (`HomeProfileSanitizer.kt`), and provides one-tap `resetLanHomesSwitchesAndRooms()`.
- **`RuleScheduler.kt`**:
  - Manages live per-device Auto-Off countdown timers (`activeCountdownsSec`, preserving in-flight countdowns across 30s health polls unless `autoOffMinutes` changed) and evaluates daily `SCHEDULE` / Sunset / Sunrise rules.

### Network Layer (`app/src/main/java/org/suppressingfire/wemi/network/`)
- **`LanHttpClient.kt`**, **`LanRuntimeUtils.kt`**, **`NetworkModels.kt`**, **`NetworkClient.kt`**, **`DeviceDescriptorFetcher.kt`**, **`XmlAndSkuParser.kt`** & **`SsdpDiscoveryEngine.kt`**:
  - Zero-dependency `LanHttpClient` built directly on `javax.net.SocketFactory` (`GET`, `POST`, `SUBSCRIBE`, `UNSUBSCRIBE` with `tcpNoDelay = true`, byte-exact `Content-Length` / chunked framing, and delayed-`FIN` recovery in `readUntilEof`).
  - Shared wire DTOs (`NetworkModels.kt`), placeholder-serial/Robolectric/LAN IPv4 helpers (`LanRuntimeUtils.kt`), and centralized SOAP XML builders & `<BinaryState>` pipe-token parser (`XmlAndSkuParser.buildSoapEnvelope`, `soapActionHeader`, `parseBinaryStatePayload`).
  - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation and `<serialNumber>`/`<UDN>` verification.
  - Unified `executeWithPortHealing` & `executeBasicEventSoapCall`, `setBinaryState` (with `<BinaryState>Error</BinaryState>` quirk handling), `getBinaryState`, `changeFriendlyName`, `configureNightMode`, delegated `getMetaInfo` / `getSignalStrength` / `/setup.xml` enrichment (`DeviceDescriptorFetcher.kt`), `scanSubnetUnicast`, and `performSsdpDiscoveryScan` (`MulticastLock` + 1s retransmit + deterministic loopback UDP registration order).
- **`GenaSubscriptionManager.kt`**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery with superseded-SID eviction, placeholder-safe `deviceKeyFor` (`serialNumber` when real else `"dev_${id}"`), `unsubscribeDevice`, and byte-exact multi-byte UTF-8 `Content-Length` framing) bound to `wifiSocketFactory` for `<100ms` physical switch updates.
- **`NetworkMonitor.kt`**:
  - Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector with `createWifiBoundSocketFactory` (caching unvalidated `NetworkCallback` Wi-Fi handles + loopback `127.0.0.1` & Robolectric aware).
- **`ProvisionerClient.kt`**, **`SetupApProber.kt`**, **`SetupDiagnostics.kt`** & **`PasswordCrypto.kt`**:
  - `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` (`8500ms` site-survey timeout) and `LanHttpClient`-framed `probeSetupDevice` (`SetupApProber.kt`), `BuildConfig`-stamped `SetupDebugSnapshot` screenshot diagnostics, `TelemetryFileExporter` (`wemi-telemetry-latest.txt`, excluded from cloud Auto-Backup via `backup_rules.xml` & `data_extraction_rules.xml`), and `minSdk 24`-safe (`kotlin.io.encoding.Base64`) OpenSSL `EVP_BytesToKey` MD5 AES-128-CBC (Methods 1, 2 & 3 × `addPasswordLengths`) double-send `ConnectHomeNetwork` + 20s `GetNetworkStatus` poll + `CloseSetup` + `SetSetupDoneStatus` provisioner (`ProvisionerClient.kt`).

### UI & Test Layer (`app/src/main/java/org/suppressingfire/wemi/ui/` & `app/src/test/java/org/suppressingfire/wemi/`)
- **`SwitchViewModel.kt`**, **`DeviceActionsUiController.kt`**, **`ProvisionApUiController.kt`**, **`HomeAndRulesUiController.kt`**, **`MainActivity.kt`**, **`ui/screens/*`** & **`ui/components/*`**:
  - All UI, ViewModel, and controller files sit strictly under `500` lines (`max = 477L` in `SwitchViewModel.kt`; `DeviceActionsUiController.kt` handles cancellation-safe `try / finally` device/room/SSDP actions with `roomName.ifBlank { "Unassigned" }` Room Master normalization, home/room-scoped `moveDeviceOrder`, and immediate Real-LAN `refreshGenaSubscriptionsForHome` on scan/adoption/save/reconnect; `SwitchViewModel.kt` tracks/cancels `statusBannerJob` on repeated banners; `DevicesTabUiState` & `DevicesTabActions` in `DevicesTabScreen.kt`, `WeMiModalDialogs.kt`, `BottomNavBar.kt`, `MoreScreen.kt` + `MoreControlAndLogCards.kt`, `ProvisionApDialog.kt` + `ProvisionApFormSections.kt`, `SsdpScannerModal.kt` + `SsdpDiscoveryBanner.kt`).
  - Compact single-row `TopHeader` (`HeaderAndNav.kt` with `>= 48.dp` touch targets, `all_off_devices_button`, and `Role.Button` / `contentDescription` semantics) and 3-column room-grouped Android TV dashboard (`TvDashboardScreen` in `TvScreen.kt`, `TvDimmerControls.kt`, `TvRoomSectionHeader.kt`, `TvHeadersAndScenes.kt` & `TvCardsAndScenes.kt` with `rememberUpdatedState` `onKeyEvent` closures across all cards, dimmer strips, room bars, and top-bar scene/scan chips, deterministic `FocusRequester` room-bar routing, `Left`/`Right` room dimming, empty-home `Rescan` focus, and focusable inline dimmer sliders).
  - Full-width `AutoResizingDeviceNameText` on Line 1 of `DeviceRow` (with inline `Auto-Off M:SS` countdown badge `auto_off_countdown_${id}` and edit-mode `move_up_${id}` / `move_down_${id}`), `TvDeviceCard`, `RoomGroupHeaderCard`, and `WifiHomeBar` so long names (`"Hallway & Landing"`, `"My Home"`) never squeeze adjacent pills into vertical character columns.
  - Dark-gray IEC `"I/O"` power-off icon (`drawIecPowerSymbol` in `#23272F`/`#181B20`), red `DisconnectedRingButton` (`drawDisconnectedSymbol`), screenshot-ready `ProvisionDebugPanel` (`provision_debug_panel`, `provision_debug_text`) inside `ProvisionApDialog`, and `open_source_licenses_card` (`mit_license_text_surface` & `third_party_notices_surface`) in `MoreScreen`.
  - One-tap `Reset` chip (`reset_lan_data_chip`) in `WifiHomeBar` and `more_reset_lan_homes_switches_button` on `MoreControlAndLogCards`.
- **`FakeUpnpServer.kt`**, **`ComposeLoopbackTestUtils.kt`**, **`ExampleUnitTest.kt`**, **`SkuAndCryptoTest.kt`**, **`SoapAndSelfHealingTest.kt`**, **`RepositorySelfHealingTest.kt`**, **`BatchAndCrudIntegrationTest.kt`**, **`GenaProvisioningAndRulesTest.kt`**, **`LicenseAndBrandGovernanceTest.kt`**, **`ExampleRobolectricTest.kt`**, **`EndToEndUiTest.kt`**, **`HardwareCujEndToEndTest.kt`**, **`ProvisioningAndGenaCujE2ETest.kt`**, **`TvAndHeaderLayoutE2ETest.kt`** & **`TvNavigationAndScenesE2ETest.kt`**:
  - 55 automated pure JVM, loopback UPnP/SOAP/GENA protocol, repository self-healing & batch concurrency, license governance, and full-stack Compose + Android TV D-Pad E2E tests across 13 suites (`100%` of test files `< 500` lines).
