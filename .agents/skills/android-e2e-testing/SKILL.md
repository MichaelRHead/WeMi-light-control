---
name: android-e2e-testing
description: End-to-end (E2E) testing, loopback hardware simulation, and multi-layer validation methodology for Android & Android TV apps using Robolectric, Jetpack Compose UI testing, and embedded UPnP/SOAP/GENA servers. Use when designing, writing, splitting, or validating unit, protocol, and full-stack UI tests.
---

# Android End-to-End (E2E) Testing & Implementation Validation Skill

Use this skill when designing, writing, refactoring, or executing unit, protocol, and end-to-end (E2E) UI tests for Android and Android TV applications (including `MichaelRHead/WeMi-light-control`).

---

## 1. Testing Philosophy: Hermetic, Fast, Full-Stack Validation

For a local-LAN hardware controller app, mocking `SwitchRepository` or `NetworkClient` with synthetic stubs hides the bugs that matter most in production (socket timeouts, UPnP port shifts, XML quirks, concurrency deadlocks, and optimistic UI rollback).

Instead, follow a **3-Tier Hermetic Testing Pyramid** that runs deterministically on the JVM via JUnit 4 + Robolectric + Jetpack Compose UI Test (`./gradlew testDebugUnitTest`) in seconds without requiring physical switches or an external emulator:

```mermaid
flowchart TD
    subgraph Tier3["Tier 3: Full-Stack E2E Compose + Loopback Hardware Flows"]
        UI["createAndroidComposeRule&lt;MainActivity&gt;()\nPhone, Tablet & Android TV D-Pad Flows"]
    end
    subgraph Tier2["Tier 2: Repository, Concurrency, GENA & Scheduler Integration"]
        Repo["SwitchRepository + Room In-Memory DB + RuleScheduler\nSemaphore(3), Per-Device Mutex, 2-Stage Reconnect, Offline Revert"]
    end
    subgraph Tier1["Tier 1: Wire Protocol, Crypto & Loopback Hardware Simulators"]
        Net["NetworkClient + ProvisionerClient + GenaSubscriptionManager\nEmbedded Loopback HTTP/SOAP/GENA/WiFiSetup1 ServerSockets"]
    end
    UI --> Repo --> Net
```

---

## 2. Modular Test Suite Organization (`app/src/test/java/org/suppressingfire/wemi/` — All Files `< 500` Lines)

Never let a single test file exceed `500` lines. Organize tests by architectural responsibility (targeting **150–480 lines per test file**) with shared loopback hardware fixtures:

1. **Shared Loopback Hardware & Compose Polling Fixtures (`FakeUpnpServer.kt` & `ComposeLoopbackTestUtils.kt`)**:
   - `FakeUpnpServer.kt` (`480L`) encapsulates a lightweight `ServerSocket(0)` and `DatagramSocket(0)` bound to `127.0.0.1` that simulates a real Belkin WeMo switch, dimmer, motion sensor, or `10.22.22.1` setup AP (`GetBinaryState`, `SetBinaryState` with `<BinaryState>Error</BinaryState>` quirk mode, `GetMetaInfo`, `GetSignalStrength`, `ConfigureNightMode`, `ChangeFriendlyName`, `SUBSCRIBE /upnp/event/basicevent1` with HTTP 412 expiry, UDP SSDP `M-SEARCH`, and `WiFiSetup1`), including simulated **port shifting** and **DHCP IP swap hazards**.
   - `ComposeLoopbackTestUtils.kt` (`42L`) provides shared `awaitLoopbackIo(timeoutMs)` wall-clock pumping and `hasTestTagPrefix(prefix)` semantic matchers across all Compose E2E suites.
2. **Tier 1 — Pure Wire Helpers, SKU Classification, Crypto & Client Probing (`ExampleUnitTest.kt`, `SkuAndCryptoTest.kt` & `SoapAndSelfHealingTest.kt`)**:
   - `ExampleUnitTest.kt` (`214L`): Pure JVM unit tests for `XmlAndSkuParser.buildSoapEnvelope` / `escapeXml` / `parseBinaryStatePayload`, `LanRuntimeUtils.isPlaceholderSerial`, `DeviceEntity.matchesDiscovered` (3-tier identity + placeholder-serial fallthrough), `DeviceEntity.concurrencyKey()`, `RuleType`, `TvScene`, Play Console R8 `EdgeToEdge*` / `SystemBarStyle*` keep rules, cloud Auto-Backup telemetry exclusions (`backup_rules.xml` & `data_extraction_rules.xml`), and optional `android.hardware.wifi` manifest declarations.
   - `SkuAndCryptoTest.kt` (`329L`): Validates `classifyHardwareSku` (`F7C030`, `WLS040`/`WLS0403`, `F7C059`/`WDS060`, `F7C063`/`WSP080`), `parseSetupXmlDescriptor`, `isWemoDeviceSetupSsid`, `classifyDeviceCategory`, `inferRoomFromName` / `resolveDeviceRoomAssignment`, and `minSdk 24`-safe (`kotlin.io.encoding.Base64`) OpenSSL `EVP_BytesToKey` AES-128-CBC round-trip `encryptWifiPassword` / `decryptWifiPassword` (Methods 1, 2, and 3).
   - `SoapAndSelfHealingTest.kt` (`241L`): Validates `LanHttpClient` HTTP/1.1 framing (`Content-Length`, `Transfer-Encoding: chunked`, and missing-`Content-Length` + delayed-`FIN` recovery), `NetworkClient.probeWemoPort` 9-port port-shift healing + `<serialNumber>` mismatch guard, `<BinaryState>Error</BinaryState>` handling, `GetMetaInfo`, `ChangeFriendlyName`, `ConfigureNightMode`, and `GetSignalStrength`.
3. **Tier 2 — Repository Concurrency, Self-Healing, Batch Fan-Out, Provisioning & License Governance (`RepositorySelfHealingTest.kt`, `BatchAndCrudIntegrationTest.kt`, `GenaProvisioningAndRulesTest.kt` & `LicenseAndBrandGovernanceTest.kt`)**:
   - **Startup State Detection & Self-Healing (`RepositorySelfHealingTest.kt`, `477L`)**: Parallel `detectStartupDeviceStates` (`ON`, `OFF Accessible Standby`, `Disconnected`), manual-IP `saveDevice` 9-port probing, startup soft-AP SSID sanitization, `resetLanHomesSwitchesAndRooms`, Real LAN Mode offline revert, `toggleDimmerNightMode` 9-port self-healing + offline rollback, and `pollActiveHomeStates` `2000ms` echo suppression.
   - **Concurrent `Semaphore(3)` Batch Operations, Parallel Health Polling, Immediate Real-LAN GENA & Demo `saveDevice` Fast-Path (`BatchAndCrudIntegrationTest.kt`, `292L`)**: Validates concurrent `toggleRoomPower` and idempotent `turnAllSwitches` across 4 loopback `FakeUpnpServer` switches (`maxActive <= 3`), parallel `pollActiveHomeStates` and single-pass `reconnectAndRediscoverHome`, immediate Real-LAN `SUBSCRIBE /upnp/event/basicevent1` registration on `SwitchViewModel.adoptSsdpDevice` + `statusBannerJob` timer reset, and instant Demo Showroom `saveDevice` completion (`< 500ms`) without live TCP probes on synthetic `192.168.1.x` IPs.
   - **UPnP GENA Push, Multi-Byte UTF-8 Byte-Framing, Non-Resetting `RuleScheduler` & Zero-Cloud `WiFiSetup1` Provisioning (`GenaProvisioningAndRulesTest.kt`, `485L`)**: Verifies `SUBSCRIBE`, HTTP 412 recovery, byte-exact `Content-Length` framing on multi-byte UTF-8 `<e:propertyset>` `NOTIFY` payloads (`<100ms`), `RuleScheduler` countdown preservation across health polls, and full loopback `probeSetupDevice` → `GetApList`/`GetNetworkList` → double `ConnectHomeNetwork` → `GetNetworkStatus` multi-poll → `CloseSetup` + `SetSetupDoneStatus` → automatic Room device insertion + `BuildConfig` telemetry version stamping.
   - **MIT License, Dependency Footprint & Brand Hygiene (`LicenseAndBrandGovernanceTest.kt`, `230L`)**: Verifies root `LICENSE` parity with `MoreScreen.kt`, minimal `Apache-2.0` dependency footprint (zero external HTTP/font/JNI `.so` bloat), `org.suppressingfire.wemi` package/filename hygiene, and Play Store trademark boundaries.
4. **Tier 3 — End-to-End Compose UI & User Journey Tests (`ExampleRobolectricTest.kt`, `EndToEndUiTest.kt`, `HardwareCujEndToEndTest.kt`, `ProvisioningAndGenaCujE2ETest.kt`, `TvAndHeaderLayoutE2ETest.kt` & `TvNavigationAndScenesE2ETest.kt`)**:
   - Uses `createAndroidComposeRule<MainActivity>()` to validate complete human user journeys (`46/46` total tests across `13` suites):
     - **Journey A & B (`ExampleRobolectricTest.kt` `345L` & `EndToEndUiTest.kt` `340L`)**: Multi-Home & Room Master control, 3-state visual semantics (`ON`, `OFF Accessible Standby`, `Disconnected`), semantic category badges, edit/reorder/manual-add dialogs, and Rules CRUD.
     - **Journey C & E (`ProvisioningAndGenaCujE2ETest.kt` `277L`)**: Zero-cloud `10.22.22.1` Setup AP hardware scan + AES Method 2 provisioning + screenshot telemetry export (`copy_provision_debug_button`), and Disconnected 9-port self-healing reconnect ring + GENA `NOTIFY` push UI transition + Live SOAP Log export (`export_telemetry_file_button`).
     - **Journey D (`HardwareCujEndToEndTest.kt` `297L`)**: Full-stack `Reset` → loopback UDP `SSDP Discovery` → `Adopt Switch & Dimmer` → `Toggle Switch` → `Toggle & Scale Dimmer Brightness` (`75%`, `21%`, `Slider`, `Night Mode 20%`, Room Master slider).
     - **Journey F & G (`TvAndHeaderLayoutE2ETest.kt` `345L` & `TvNavigationAndScenesE2ETest.kt` `209L`)**: `>= 48.dp` accessible single-row `TopHeader` (`all_off_devices_button`, `Role.Button`, `ContentDescription`), plus native `@Config(sdk = [36], qualifiers = "television")` 3-column room-grouped D-Pad navigation, focusable inline Dimmer Slider (`tv_card_dim_mode_${id}` + `Key.Back`), multi-row column memory (`Col 2` preserved across rows/room headers), empty-home initial focus on `tv_scan_button`, Room Header `Left`/`Right` `-10%`/`+10%` room dimmer stepping, and TV Scenes (`Movie 20%`, `Holiday ON`, `All OFF`).

---

## 3. Compose Semantic `testTag` Conventions

Every interactive or status-bearing Compose element must expose a deterministic `Modifier.testTag(...)` so E2E tests never rely on fragile layout hierarchy indexing:
- Top Header single-row controls (`>= 48.dp` touch height + `Role.Button`): `"brand_wordmark_text"`, `"all_off_devices_button"`, `"refresh_devices_button"`, `"edit_devices_button"`, `"add_device_button"`
- Device Row container & name: `"device_row_${device.id}"`, `"device_name_${device.id}"`
- Power Ring button: `"power_button_${device.id}"`
- Reconnect / Retry button: `"reconnect_button_${device.id}"`
- Inline Diagnostics & Edit Mode: `"ping_device_${device.id}"`, `"edit_device_${device.id}"`, `"delete_device_${device.id}"`
- Status Badges (use `useUnmergedTree = true` when inside clickable rows):
  - `"connection_status_badge_${device.id}"`
  - `"power_state_badge_${device.id}"`
  - `"category_badge_${device.id}"`
- Room Group header & toggle: `"room_header_$roomName"`, `"room_power_toggle_$roomName"`
- Android TV 10-foot Dashboard (`wemi TV`): `"tv_dashboard_screen"`, `"tv_device_grid"`, `"tv_room_header_$roomName"`, `"tv_room_dim_minus_$roomName"`, `"tv_room_dim_plus_$roomName"`, `"tv_device_card_${device.id}"`, `"tv_card_dim_mode_${device.id}"`, `"tv_dimmer_mode_toggle"`, `"tv_remote_dpad_ok"`, `"tv_scene_movie"`, `"tv_scene_holiday"`, `"tv_scene_bedtime"`, `"tv_scan_button"`
- Empty-Home Onboarding Card (`EmptyDevicesState`): `"empty_devices_state_card"`, `"empty_state_scan_ssdp_button"`, `"empty_state_provision_ap_button"`, `"empty_state_add_manual_button"`, `"empty_state_restore_classic_button"`
- Navigation & Modals: `"nav_tab_devices"`, `"nav_tab_rules"`, `"nav_tab_more"`, `"save_device_button"`, `"open_provision_ap_wizard_button"`, `"provision_ap_status_box"`, `"provision_status_box"`, `"confirm_provision_ap_button"`, `"add_rule_button"`, `"save_rule_button"`

---

## 4. Avoiding E2E Flakiness, Modal Scrim Traps & Socket Starvation

1. **Wall-Clock Loopback Pumping vs. `composeTestRule.waitUntil` (`awaitLoopbackIo` Pattern)**:
   - Under Robolectric, `composeTestRule.waitUntil(timeoutMillis)` spins the virtual `TestMainClock` in a tight CPU loop without sleeping real wall-clock time, starving background `Dispatchers.IO` and loopback `ServerSocket` threads (`FakeUpnpServer`).
   - Always use `awaitLoopbackIo` (which interleaves `composeTestRule.waitForIdle()` with `Thread.sleep(20)`) when waiting for background `Dispatchers.IO` / Room / loopback HTTP updates to propagate back to `Dispatchers.Main` and Compose UI.
2. **Synchronous Modal Dismissal Before Background Network Probes**:
   - Whenever an E2E test clicks **Save** in a modal dialog (`save_device_button`, `save_rule_button`) and then interacts with the underlying screen (`edit_devices_button`, `devices_list`), the ViewModel **must** dismiss the modal synchronously on the main thread (`_showAddDeviceDialog.value = false`, `_editingDevice.value = null`) *before* launching `viewModelScope.launch` / `Dispatchers.IO` probes (`repository.saveDevice`).
   - If modal dismissal happens inside the coroutine after `repository.saveDevice`, any probe against a non-loopback demo IP (`192.168.1.x`) will keep the modal scrim open on screen and swallow subsequent test clicks.
3. **Loopback `SocketFactory` & Per-Application Room DB Isolation**:
   - Ensure `NetworkMonitor.createWifiBoundSocketFactory` overrides `0`-arg `createSocket()` with a wrapper that skips `wifiNet.bindSocket(this)` when `inetAddr.isLoopbackAddress` (`127.0.0.1`) or under Robolectric.
   - Ensure `SwitchDatabase.getDatabase` tracks `lastAppContext` and uses `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()` under Robolectric so consecutive `@Test` methods in a suite never share stale SQLite connections or dirty tables.
4. **Flag Suspiciously Slow Individual UI Tests (`> 3s` Red Flag)**:
   - Inspect per-test durations in `app/build/test-results/testDebugUnitTest/TEST-*.xml`. Every loopback UI test (after initial Robolectric classloading on the first test) should complete in **`0.5s – 1.5s`**.
   - Any test taking `> 3s` indicates a background coroutine is hitting a real TCP connect timeout on a non-loopback IP while holding a lock or blocking a UI transition.
5. **Single-SDK (`sdk = [36]`) Robolectric Classloader Convention**:
   - Always use `@Config(sdk = [36])` (or `@Config(sdk = [36], qualifiers = "television")` when testing Android TV qualifier overrides) across all Robolectric test classes and methods. Mixing multiple SDK levels (e.g., `sdk = [34]` alongside `sdk = [36]`) in the same test JVM forces Robolectric to load a second `android-all` JAR into memory, causing dual-SDK `ZipFileSystemProvider` collisions and heap exhaustion.

---

## 5. Mandatory Pre-Push Verification Gate (`--rerun-tasks`)

Never rely on a cached `UP-TO-DATE` Gradle test run after refactoring or syncing remote commits. Before pushing any code or test changes to GitHub:
```bash
# 1. Force-execute all unit, loopback protocol, and Robolectric Compose E2E tests
./gradlew testDebugUnitTest --rerun-tasks

# 2. Assemble the debug APK to verify resource linking, manifest merging, and KSP Room codegen
./gradlew assembleDebug
```
Both commands must exit with code `0`, zero test failures, and verified `TEST-*.xml` execution timestamps.
