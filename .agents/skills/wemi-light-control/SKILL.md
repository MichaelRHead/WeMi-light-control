---
name: wemi-light-control
description: "WeMi Light Control domain guide: local UPnP/SSDP/SOAP/GENA protocol, on-device rules.db inspection, Room v6 schema, and 10.22.22.1 setup for switches, dimmers, and plugs."
---

# WeMi Light Control Skill (`MichaelRHead/WeMi-light-control`)

Use this skill when working on the **WeMi Light Control** Android & Android TV application in `/home/antigravity/MichaelRHead/WeMi-light-control`.

For detailed UPnP/SSDP/SOAP/GENA/`rules1`/`timesync1` wire formats, device model identifiers, and XML schemas for Belkin's discontinued smart switch platform, read [`references/wemo-upnp-protocol.md`](references/wemo-upnp-protocol.md), [`../../../UX_DESIGN.md`](../../../UX_DESIGN.md) and [`../../../UX_DESIGN_AND_EXPERIENCE.md`](../../../UX_DESIGN_AND_EXPERIENCE.md) (AI Studio Agent-owned UX specifications), [`../../../DEBUGGING_AND_SUPPORT.md`](../../../DEBUGGING_AND_SUPPORT.md) (Antigravity Debugging & Support Agent-owned telemetry specification), [`../../../DISCOVERY_AND_ROOM_GROUPING.md`](../../../DISCOVERY_AND_ROOM_GROUPING.md) (Antigravity Device Discovery & Room Grouping Agent-owned specification), [`../../../LICENSE_GOVERNANCE.md`](../../../LICENSE_GOVERNANCE.md) (Antigravity License & Brand Governance Agent-owned specification), [`../../../IMPLEMENTED.md`](../../../IMPLEMENTED.md) (reverse-chronological Change Log), [`../../../UPCOMING_FEATURES.md`](../../../UPCOMING_FEATURES.md) (upcoming features roadmap), and [`../../../USER_GUIDE.md`](../../../USER_GUIDE.md) (plus [`../../../docs/user-guide/`](../../../docs/user-guide/)). For UX design, device discovery & room grouping, license & brand governance, human-readable code modularization, E2E testing standards, on-device screenshot/telemetry debugging, multi-agent Git synchronization, agent commit attribution, Change Log maintenance, and Stage 3 documentation audits, also consult [`../wemi-ux-design-and-experience/SKILL.md`](../wemi-ux-design-and-experience/SKILL.md), [`../wemi-discovery-and-room-grouping/SKILL.md`](../wemi-discovery-and-room-grouping/SKILL.md), [`../wemi-license-governance/SKILL.md`](../wemi-license-governance/SKILL.md), [`../android-code-modularization/SKILL.md`](../android-code-modularization/SKILL.md), [`../android-e2e-testing/SKILL.md`](../android-e2e-testing/SKILL.md), [`../wemi-device-telemetry-debug/SKILL.md`](../wemi-device-telemetry-debug/SKILL.md), [`../multi-agent-git-sync/SKILL.md`](../multi-agent-git-sync/SKILL.md), [`../agent-commit-attribution/SKILL.md`](../agent-commit-attribution/SKILL.md), [`../wemi-changelog-and-docs/SKILL.md`](../wemi-changelog-and-docs/SKILL.md), and [`../wemi-doc-audit-and-review/SKILL.md`](../wemi-doc-audit-and-review/SKILL.md). Always keep root `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `USER_GUIDE.md`, and `docs/user-guide/*.md` updated as features are added or modified (do not duplicate `ALL_CAPS.md` files inside `docs/`).

---

## 1. Build, Test & Environment

- **Toolchain**: Gradle `9.3.1`, AGP `9.1.1`, Kotlin `2.2.10`, KSP `2.3.5`, Compose BOM `2024.09.00`, Room `2.8.5` (`version = 6`, `MIGRATION_5_6`), Robolectric `4.18.1`, `compileSdk = 36.1`, `minSdk = 24`.
- **Unit, Protocol, Hardware-Rules, Widget, Telemetry, Governance, ChromeOS ARCVM & E2E Compose UI Tests (`127/127` across 25 suites + Lint)**:
  ```bash
  sh gradlew testDebugUnitTest --rerun-tasks lintDebug
  ```
- **Debug APK Build**:
  ```bash
  sh gradlew assembleDebug
  ```
- **Package / Namespace**: `org.suppressingfire.wemi` (`applicationId = "com.aistudio.wemiswitch.kxpqzr"`, `versionName = "34.0"`, `versionCode = 34`; `v35.0` ready for Stage 1).

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

## 3. Codebase Map (Post-`v34.0` / `v35.0`-Ready — `org.suppressingfire.wemi`, `100%` of `108` `.kt` Files `< 500` Lines)

### Data & Scheduling Layer (`app/src/main/java/org/suppressingfire/wemi/data/`)
- **`DeviceEntities.kt` & `RoomAndCategoryRules.kt`**:
  - `UNASSIGNED_ROOM`, `normalizeRoomName`, `groupDevicesByCanonicalRoom`, `DeviceEntity.normalizedRoomName`, `DeviceType` (`SWITCH_PLUG`, `MOTION_SENSOR`, `LIGHTBULB_DIMMER`), `RuleType` (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`), and declarative word-bounded `RoomAndCategoryRules` powering `classifyDeviceCategory` (`CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`) and `isHolidayLightDevice`.
  - `DeviceEntity.matchesDiscovered`, `DeviceEntity.concurrencyKey` & `SsdpDiscoveredDevice.matchesEndpointOrSerial`: Shared 3-tier hardware identity matching (`serialNumber` with placeholder-serial fallthrough → canonical `UDN`/`USN` → `(ipAddress, port)`/`friendlyName`) and placeholder-serial-safe per-device `Mutex` key (`serialNumber` → canonical `udn` → `"$ipAddress:$port"`).
  - `normalizeDeviceNameForInference`, `inferRoomFromName` & `resolveDeviceRoomAssignment`: Normalizes CamelCase/delimiters, infers rooms in specificity order (`"Ensuite"` before `"Bedroom"`, `"bed"`, `"bedside"`, `"nightstand"`, `"girls"`/`"boys"`/`"kids"` → `"Bedroom"`, `\bstair` with low-priority `"upstairs"`/`"downstairs"` floor fallback, word-bounded `"den"`/`"hall"`/`"shed"`/`"pier"`/`"wc"`, and `"Unassigned"` fallback instead of `"Living Room"`), and non-destructively migrates switches stuck in `"Living Room"`.
  - `ConnectionQuality`: `EXCELLENT` (<= 28ms, 4 bars), `GOOD` (<= 65ms, 3 bars), `SLOW` (> 65ms, 2 bars), `OFFLINE` (0 bars).
  - `HomeNetworkEntity` (`wemo_home_networks`), `DeviceEntity` (`wemo_devices`, including Room v6 firmware-cached on-device rules capability columns), and `RuleEntity` (`wemo_rules`).
- **`SwitchDao.kt` & `SwitchDatabase.kt`**: Room DB at `version = 6` (`wemi_switch_database`, non-destructive `MIGRATION_5_6` preserving user homes/devices/rules), with per-`Application` in-memory isolation and automatic `SQLiteDatabase.close()` on test transitions under Robolectric.
- **`SwitchRepository.kt`**, **`GenaAndHealthPollingCoordinator.kt`**, **`DiscoveryCoordinator.kt`**, **`HardwareRulesCoordinator.kt`**, **`HomeProfileSanitizer.kt`**, **`ProvisioningCoordinator.kt`**, **`DeviceCrudAndRulesCoordinator.kt`** & **`DemoFixtures.kt`**:
  - Coordinates `SwitchDao`, `NetworkClient`, `HardwareRulesClient`, `GenaSubscriptionManager`, and `ProvisionerClient` (`100%` of `data/` files `< 500` lines).
  - Defaults to **Real LAN Mode** (`isDemoShowroomMode = false`) on physical Android devices and **Demo Showroom Mode** (`true`) inside Robolectric unless overridden (`LanRuntimeUtils.isRunningInRobolectric()`), serializing `ensureSeeded` and Demo/LAN mode resets via `seedAndModeMutex` + cold-start `awaitDemoSeedIfPending()`.
  - Enforces shared `Semaphore(3)` LAN concurrency + `concurrencyKey()`-isolated per-device `Mutex`, symmetric 4-pass hardware identity matching (`matchesDiscoveredDevice`, `findMatchingExistingDevice` & `findMatchingDiscoveredDevice` with non-loopback blank-serial port-hop dedup), inside-lock fresh `dao.getDeviceById` snapshots across startup/reconnect/SSDP updates, monotonic home-scoped `sortOrder` after deletions, parallel `Semaphore(3)` `pollActiveHomeStates` and `refreshGenaSubscriptionsForHome` (with deduplicated ChromeOS CGNAT GENA-skip logging) + strict 3-tier `handleGenaNotification` (`GenaAndHealthPollingCoordinator.kt`), UDN-guarded manual IP 9-port hardware probing + home/room-scoped `moveDeviceOrder` + unified Wi-Fi Home-isolated `executeRuleAction` (`DeviceCrudAndRulesCoordinator.kt`), firmware-cached Tier-2 hardware rules verification & read-only `rules.db` snapshots (`HardwareRulesCoordinator.kt`), concurrent `Semaphore(3)`-bounded room batch execution, idempotent `setDevicePowerExplicit`, mutex-serialized `stepDeviceBrightness`, unified `executeOptimisticDeviceCommand`, soft-AP SSID & ChromeOS CGNAT startup DB sanitization + physical subnet backfill (`HomeProfileSanitizer.kt`), and confirmation-protected `resetLanHomesSwitchesAndRooms()`.
- **`RuleScheduler.kt`**:
  - Manages live per-device Auto-Off countdown timers (`activeCountdownsSec`, preserving in-flight countdowns across health polls and isolating same-named devices across homes via `activeDeviceIds`) and evaluates daily `SCHEDULE` / Sunset / Sunrise rules.

### Network Layer (`app/src/main/java/org/suppressingfire/wemi/network/`)
- **`LanHttpClient.kt`**, **`LanRuntimeUtils.kt`**, **`LanSubnetHintResolver.kt`**, **`NetworkModels.kt`**, **`NetworkClient.kt`**, **`DeviceDescriptorFetcher.kt`**, **`XmlAndSkuParser.kt`** & **`SsdpDiscoveryEngine.kt`**:
  - Zero-dependency `LanHttpClient` built directly on `javax.net.SocketFactory` (`GET`, binary `getBytes` `<= 2 MiB`, `POST`, `SUBSCRIBE`, `UNSUBSCRIBE` with `tcpNoDelay = true`, byte-exact `Content-Length` / chunked framing, and delayed-`FIN` recovery).
  - RFC 6598 CGNAT (`100.64.0.0/10`), ChromeOS `arc0` (`100.115.92.2`), RFC 1918 & ARCVM classifiers (`LanRuntimeUtils.kt`) and 5-tier physical `/24` Stage C resolver (`LanSubnetHintResolver.kt`, keeping direct live-IP `/24` on Phones/TVs while resolving physical home subnets inside ChromeOS ARCVM).
  - Centralized SOAP XML builders, pre-compiled regexes, zero-RTT Tier-1 `/setup.xml` rules capability hints (`inferRulesCapabilityFromSetupXml`), and `escapeXml`/`unescapeXml` entity handling (`XmlAndSkuParser.kt`).
  - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout separation and strict `<serialNumber>`/`<UDN>` identity verification (`NetworkClient.kt`, `DeviceDescriptorFetcher.kt`).
  - `SsdpDiscoveryEngine.kt` (`MulticastLock`, network-bound `MulticastSocket(null)`, `ensureActive()` cancellation check, `NTS: ssdp:byebye` & `ST`/`NT` guards, `Semaphore(3)` Stage A/B enrichment & multicast-zero/manual-override Stage C `/24` RFC 1918 unicast sweep).
- **`HardwareRulesModels.kt`**, **`HardwareRulesProtocol.kt`**, **`HardwareRulesClient.kt`**, **`HardwareRulesDbCodec.kt`** & **`HardwareRulesSafetyValidator.kt`**:
  - Lazy Tier-2 `/rulesservice.xml` + `rules1#FetchRules` capability verification, `timesync1#GetDeviceTime` / `#TimeSync` clock drift checks, schema-tolerant read-only `rules.db` SQLite-in-ZIP extraction (`Long Press`, `Auto-Off Delay`, `Schedule / Sunrise-Sunset`, `Away Mode`), rotating raw-ZIP backups, and the 10-point anti-brick pre-store validator (`validateAndPackToBase64Zip` + armored `StoreRules` contract).
- **`GenaSubscriptionManager.kt`**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, `SupervisorJob`-isolated per-client socket handling, RFC 6598 CGNAT callback guard skipping unreachable ChromeOS ARCVM `SUBSCRIBE` calls while preserving Phone/TV & loopback subscriptions, HTTP 412 recovery with superseded-SID & placeholder eviction, `deviceKeyFor`, and `unsubscribeDevice`) for `<100ms` physical switch updates.
- **`NetworkMonitor.kt`**:
  - Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet, IPv4 DNS & `10.22.22.x` Setup AP detector with `ActiveLanBinding`, VPN & ChromeOS `arc0` exclusion, active physical uplink priority, and `createWifiBoundSocketFactory`.
- **`ProvisionerClient.kt`**, **`SetupApProber.kt`**, **`SetupDiagnostics.kt`** & **`PasswordCrypto.kt`**:
  - `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` (`8500ms` site-survey timeout), `LanHttpClient`-framed `probeSetupDevice`, `SharedSoapLogStore` widget-to-app log ring buffer, `BuildConfig`-stamped `SetupDebugSnapshot` (with ChromeOS `arcVmNat=true` / `dns=` telemetry), 5-section `TelemetryFileExporter` (`wemi-telemetry-latest.txt` with `subnet=unset` formatting & canonical widget `udn=`), and `minSdk 24`-safe OpenSSL `EVP_BytesToKey` MD5 AES-128-CBC (Methods 1, 2 & 3) `ConnectHomeNetwork` provisioning.

### UI, Widget & Test Layer (`ui/`, `widget/` & `app/src/test/java/org/suppressingfire/wemi/`)
- **`SwitchViewModel.kt`**, **`DeviceActionsUiController.kt`**, **`ProvisionApUiController.kt`**, **`HomeAndRulesUiController.kt`**, **`MainActivity.kt`**, **`ui/screens/*`** & **`ui/components/*`**:
  - All UI, ViewModel, and controller files sit strictly under `500` lines (`TvHeadersAndScenes.kt` `462L` with deduplicated `TvSceneChip` & conditional `Holiday ON`, `SwitchViewModel.kt` with IO-offloaded device-only widget refresh & `15s` ARCVM / `30s` Phone-TV foreground polling, lock-synchronized `ssdpScanJob`/`reconnectJob`/`scanJob` lifecycles, `HardwareRulesSection.kt` & `DeviceDiagnosticsDrawer.kt` read-only on-device switch rules readout, `ArcVmManualSubnetFallbackCard` in `SsdpDiscoveryBanner.kt` / `SsdpScannerModal.kt`, CGNAT-safe `WifiHomeBar.kt` / `AddWifiHomeNetworkDialog`, `RuleDialog` defaulting `AUTO_OFF` to OFF, compact `48.dp` single-row `TopHeader` with static `wemi` wordmark & subtle `app_version_button` opening `ReleaseHistoryDialog` / `ReleaseHistoryCatalog`, 3-column room-grouped `wemi TV` with dynamic `Holiday ON` focus recovery, 3-state hardware icons, collapsible `ProvisionDebugPanel` with auto-expand recovery, and `open_source_licenses_card` in `MoreScreen`).
- **`widget/` (`WeMiSwitchWidgetProvider.kt`, `WidgetActionExecutor.kt`, `WidgetConfigStore.kt`, `WidgetRemoteViewsBuilder.kt`, `SwitchWidgetConfigureActivity.kt`, `WidgetPickerCards.kt`)**:
  - Zero-dependency Android Home Screen Light & Room Toggle Widgets with one-tap `requestPinAppWidget` pinning, launcher configuration picker, Tier-1 serial / Tier-2 canonical UDN anti-hijack target resolution, multi-home Room isolation, per-widget `Mutex` double-tap serialization + delete/restore eviction, `SharedSoapLogStore` telemetry bridging, and non-positive ID guards.
- **Test Suites (`25` suites, `127/127` Kotlin tests + `23/23` Python script tests — see [`ARCHITECTURE.md`](../../../ARCHITECTURE.md) & [`android-e2e-testing`](../android-e2e-testing/SKILL.md))**:
  - `FakeUpnpServer.kt`, `FakeHardwareRulesServer.kt`, `FakeChromeOsPatchpanelBridge.kt`, `ComposeLoopbackTestUtils.kt`, `ExampleUnitTest.kt`, `SkuAndCryptoTest.kt`, `RuleAndTransportTest.kt`, `SoapAndSelfHealingTest.kt`, `RepositorySelfHealingTest.kt`, `BatchAndCrudIntegrationTest.kt`, `DiscoveryAndGroupingIntegrationTest.kt`, `LifecycleAndConcurrencyTest.kt`, `GenaProvisioningAndRulesTest.kt`, `HardwareRulesTest.kt`, `TelemetryDiagnosticsTest.kt`, `ChromeOsArcVmDiscoveryTest.kt`, `LicenseAndBrandGovernanceTest.kt`, `ExampleRobolectricTest.kt`, `EndToEndUiTest.kt`, `HardwareCujEndToEndTest.kt`, `HardwareRulesE2ETest.kt`, `ProvisioningAndGenaCujE2ETest.kt`, `HeaderAndReleaseHistoryE2ETest.kt`, `TvAndHeaderLayoutE2ETest.kt`, `TvNavigationAndScenesE2ETest.kt`, `HomeScreenWidgetTest.kt`, `HomeScreenWidgetUiE2ETest.kt`, `AppFlowsAndManagementE2ETest.kt`, and `ChromeOsArcVmE2ETest.kt` (`100%` `< 500` lines).
