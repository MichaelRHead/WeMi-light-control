---
name: android-code-modularization
description: Android & Jetpack Compose modularization and readability rules (<500-line files). Use when auditing complexity or refactoring large classes.
---

# Android Code Modularization & Human Readability Skill

Use this skill whenever designing, reviewing, or refactoring Kotlin & Jetpack Compose code in Android / Android TV applications (including `MichaelRHead/WeMi-light-control`) so that every source file remains immediately understandable to a human reader.

---

## 1. Core Readability Principles

1. **The Human Screenful & File-Size Heuristic (`120–400` Lines Target)**:
   - A human engineer should be able to open any implementation file and understand its entire responsibility within 30 seconds.
   - **Target Size**: Keep standard UI components, dialogs, screens, data fixtures, and test files between **120 and 400 lines**.
   - **Hard Warning Threshold**: Any file exceeding **500 lines** must be audited for mixed responsibilities.
2. **The Subtlety Exception**:
   - A file should only get large or dense when there is **genuine underlying algorithmic, concurrency, or wire-protocol subtlety** that would become *harder* to reason about if scattered across multiple files.
   - *Examples of genuine subtlety*:
     - A 9-port TCP/HTTP state prober that distinguishes ARP/SYN connect timeouts from `ECONNREFUSED` and wedged HTTP read timeouts while verifying UPnP `<serialNumber>` identity.
     - A concurrency coordinator that serializes per-device SOAP mutations with a `Mutex`, bounds LAN-wide RTOS socket concurrency with `Semaphore(3)`, suppresses GENA push echoes for `1200ms`, and runs a 2-stage self-healing reconnect with optimistic UI rollback.
   - **Rule**: Even when a module has genuine subtlety, **strip out all non-subtle neighbors** (seed/demo fixtures, static XML regex helpers, crypto utilities, unrelated features) into small helper files so the subtle state machine stands alone and crystal-clear.
3. **Top-Down Newspaper Ordering Within Every File**:
   - Place the primary public entry point (`@Composable fun FooScreen(...)` or `class FooCoordinator(...)`) at the **top** of the file.
   - Follow with private orchestration helpers in call order, then leaf UI subcomponents or pure data mappers at the bottom.
   - Document *why* non-obvious protocol quirks or concurrency guards exist (e.g., why `Connection: close` is required on RTOS `WLS0403` switches, or why `<BinaryState>Error</BinaryState>` means idempotent success).

---

## 2. Layer-by-Layer Modularization Patterns

### 2.1 Repository & Domain Coordination (`data/`)
Avoid letting a `Repository` class grow into a 1,500+ line God object that mixes seed data, network discovery, device state transitions, and hardware provisioning:
- **Extract Seed & Demo Fixtures (`*DemoFixtures.kt` & `DemoHardwareRulesStore.kt`)**:
  - Move static sample entities (`defaultHomeNetworks()`, `defaultClassicDevices()`) into a dedicated file (`DemoFixtures.kt`), and keep in-memory Demo Showroom on-device rules CRUD/backup simulation isolated in `DemoHardwareRulesStore.kt`.
- **Unify Optimistic State-Transition Templates & Read `baseDevice` Inside `mutex.withLock`**:
  - Never copy-paste 70-line `mutex.withLock { optimisticWrite(); semaphore.withPermit { soapCall() }; twoStageReconnect(); commitOrRevert() }` blocks across `togglePower`, `setPowerExplicit`, and `updateBrightness`.
  - Extract a single higher-order helper (e.g., `executeOptimisticDeviceCommand(...)`) that reads the latest `baseDevice = dao.getDeviceById(device.id)` *inside* `mutexFor(device).withLock` (so rapid back-to-back toggles never invert against a stale caller snapshot and queued commands reuse freshly port-healed endpoints), encapsulates the optimistic Room update, 2-stage self-healing retry, SOAP log recording, and offline rollback in one place.
- **Decompose by Domain Workflow**:
  - Keep `SwitchRepository` (`< 500` lines) as a concise facade that delegates to focused internal collaborators:
    - `DiscoveryCoordinator` + `HomeProfileSanitizer`: SSDP scan orchestration, `<serialNumber>`/`<UDN>` 3-tier matching (`DeviceEntity.matchesDiscovered`) across DHCP IP changes, inside-lock fresh `dao.getDeviceById` snapshots across startup/reconnect/SSDP updates, monotonic home-scoped `sortOrder` on adoption, soft-AP SSID / room migration sanitization, and `/24` subnet home resolution.
    - `GenaAndHealthPollingCoordinator`: `Semaphore(3)`-bounded concurrent UPnP GENA subscription renewals (`refreshGenaSubscriptionsForHome`), concurrent 30s health polling (`pollActiveHomeStates`), strict 3-tier `<100ms` GENA `NOTIFY` routing (`serialNumber` → `"dev_${id}"` → blank-key `sourceIp` inside `mutexFor(matched)`), and `toggleDimmerNightMode`.
    - `ProvisioningCoordinator`: Zero-cloud `10.22.22.1` setup AP probing, AP scanning, `ConnectHomeNetwork` provisioning, and monotonic-`sortOrder` automatic Room registration.
    - `DeviceCrudAndRulesCoordinator`: Manual-IP `saveDevice` 9-port probing + `ChangeFriendlyName` hardware sync, home/room-scoped `moveDeviceOrder`, and room batch power/brightness fan-out.
    - `HardwareRulesCoordinator` + `DemoHardwareRulesStore`: `Semaphore(3)`-bounded + per-device `Mutex` lazy Tier-2 rules capability verification, live Step 2 `rules.db` inspection/CRUD/clock-sync/solar-location/backup-restore, Room v7 firmware-scoped capability caching (`MIGRATION_5_6`, `MIGRATION_6_7`), and `try / finally` inspecting-state cleanup.
    - `HouseholdSyncCoordinator`: Identity-safe 3-tier Room v7 `MERGE` / `REPLACE` household bundle import and export without crossing conflicting hardware serials/UDNs.
    - **Placeholder-Safe Concurrency & GENA Keying (`DeviceEntity.concurrencyKey()` & `GenaSubscriptionManager.deviceKeyFor`)**: Key per-device `Mutex` maps and GENA subscription slots by `!isPlaceholderSerial(serialNumber)` (`"$ipAddress:$port"` or `"dev_${device.id}"` fallback) so multiple manually added switches never contend on a single lock or overwrite each other's GENA callback slot.

### 2.2 Network & Wire Protocol Layer (`network/` — All Files `< 500` Lines)
Separate **stateless wire parsing, DTOs, runtime checks, and cryptography** from **stateful socket & HTTP I/O**:
- **Shared Wire DTOs & Runtime Guards (`NetworkModels.kt`, `LanRuntimeUtils.kt` & `LanEndpointPolicy.kt`)**:
  - Keep wire data classes (`SoapCallResult`, `SwitchMetaInfo`, `SsdpScanReport`, `ScannedAp`, `SetupDeviceDescriptor`, `ProvisionResult`, `ParsedBinaryState`) in `NetworkModels.kt`, cross-layer runtime helpers (`PLACEHOLDER_SERIALS`, `isPlaceholderSerial`, null-safe `isRunningInRobolectric()`, `resolvePreferredLanInterfaceIpv4`) in `LanRuntimeUtils.kt`, and strict RFC 1918 / CRLF / SSRF URL validation in `LanEndpointPolicy.kt`.
- **Stateless Parsers, SOAP Envelopes & Classifiers (`XmlAndSkuParser.kt` & `StrictJsonCodec.kt`)**:
  - Keep pure functions (`buildSoapEnvelope`, `soapActionHeader`, `escapeXml`, `parseBinaryStatePayload`, `parseSsdpHeaders`, `parseSetupXmlDescriptor`, `classifyHardwareSku`, `buildSsdpMSearchPacket`) and bounded RFC 8259 JSON parsing (`StrictJsonCodec.kt`, `<= 512 KiB`, `<= 16_384` nodes, depth `<= 12`) in stateless, zero-Android-dependency files that can be unit-tested in milliseconds.
- **Pure Cryptographic Helpers (`PasswordCrypto.kt`, `HouseholdSyncCrypto.kt` & `HouseholdSyncPake.kt`)**:
  - Isolate key derivation and cipher routines (`EVP_BytesToKey` `AES-128-CBC` in `PasswordCrypto.kt`; `AES-256-GCM` with sender-bound AAD and buffer zeroization in `HouseholdSyncCrypto.kt`; pure-Kotlin RFC 9382 SPAKE2 over RFC 3526 Group 14 in `HouseholdSyncPake.kt`) into focused crypto files with round-trip tests.
- **Discovery, Descriptor Enrichment, Hardware Rules, Household Sync & Setup Probing Separation**:
  - Separate UDP multicast `M-SEARCH` + `/24` unicast subnet sweeping (`SsdpDiscoveryEngine.kt`) and secondary metadata/`/setup.xml` enrichment (`DeviceDescriptorFetcher.kt`) from point-to-point `basicevent1` HTTP SOAP control (`NetworkClient.kt`), separate on-device `rules1`/`timesync1` models, XML protocol, SQLite-in-ZIP codec, schema guard, parameterized in-place mutator, anti-brick validation, `noBackupFilesDir` backup store, armored write orchestration, and LAN I/O across focused modules (`HardwareRulesModels.kt`, `HardwareRulesProtocol.kt`, `HardwareRulesDbCodec.kt`, `HardwareRulesSchemaGuard.kt`, `HardwareRulesDbMutator.kt`, `HardwareRulesSafetyValidator.kt`, `HardwareRulesBackupStore.kt`, `HardwareRulesWriteEngine.kt`, `HardwareRulesClient.kt`), separate zero-cloud Household Sync codec, HTTP framing + UDP beacon, LAN receiver, and LAN client (`HouseholdSyncCodec.kt`, `HouseholdSyncHttpFraming.kt`, `HouseholdSyncLanServer.kt`, `HouseholdSyncLanClient.kt`), separate telemetry redaction (`TelemetryRedactor.kt`), and separate `10.22.22.1` 9-port setup probing & AP site-survey scanning (`SetupApProber.kt`) from `ConnectHomeNetwork` provisioning (`ProvisionerClient.kt`).

### 2.3 Jetpack Compose UI Layer (`ui/` — All Files `< 500` Lines)
1. **One Screen, Dialog, or Card Group Per File (`< 500` Lines Across All UI Files)**:
   - Never bundle unrelated modal dialogs (`AddOrEditDeviceDialog`, `HardwareRuleEditorDialog`, `HardwareLocationDialog`, `HardwareRulesBackupDialog`, `ProvisionApDialog`, `HouseholdSyncSendDialog`, `HouseholdSyncReceiveDialog`, `HouseholdImportConfirmDialog`) into a single `Dialogs.kt` monolith. Give each dialog or bar its own file (`HeaderAndNav.kt` + `BottomNavBar.kt`, `DeviceEditDialog.kt`, `HardwareRuleEditorDialog.kt` + `HardwareRuleEditorFields.kt`, `HardwareRulesAuxDialogs.kt`, `HouseholdSyncDialogs.kt` + `HouseholdSyncReceiveDialogs.kt` + `TvHouseholdSyncDialog.kt`, `ProvisionApDialog.kt` + `ProvisionApFormSections.kt`, `SsdpScannerModal.kt` + `SsdpDiscoveryBanner.kt`), and split multi-card tabs cleanly (`MoreScreen.kt` for license notices + `MoreControlAndLogCards.kt` for operational/telemetry cards + `HouseholdSyncCard.kt` for zero-cloud export/import/LAN sync, and `RulesScreen.kt` + `HardwareRulesSection.kt` + `HardwareRuleRowItems.kt` + `DeviceDiagnosticsDrawer.kt` for the dedicated **On-Device Switch Rules (`On-Switch`)** surfaces).
2. **Separate Custom Canvas Illustrations by Category (`ui/components/`)**:
   - Custom `Canvas` / vector artwork is line-heavy (~150–250 lines per 3-state illustration). Group illustrations into dedicated files (`SwitchAndPlugIllustration.kt`, `LightAndSensorIllustrations.kt`, `ChristmasTreeIllustration.kt`, `FanIllustration.kt`, and `HardwareIllustrations.kt` for the category router and shared badges).
3. **Keep `MainActivity.kt` & `TvScreen.kt` as Thin Orchestration Shells (`< 500` Lines) & Bundle Tab State (`DevicesTabUiState`)**:
   - `MainActivity` / `WeMiSwitchApp` should only handle edge-to-edge system bars, TV vs. Mobile mode routing, top/bottom scaffold bars, `BackHandler`, and modal dialog hosting (`WeMiModalDialogs.kt`).
   - Bundle read-only tab state and callbacks into cohesive parameter objects (`DevicesTabUiState` & `DevicesTabActions` in `DevicesTabScreen.kt`; `HouseholdSyncUiState` & `PendingHouseholdImport` in `HouseholdSyncUiState.kt`) instead of prop-drilling 40+ flat parameters, and split the Android TV 10-foot dashboard across `TvScreen.kt` (focus/grid orchestration), `TvDimmerControls.kt` (`TvDeviceCardDimmerStrip`), `TvRoomSectionHeader.kt` (focusable room bar with `OK` master toggle + `Left`/`Right` room dimmer stepping), `TvHeadersAndScenes.kt` (`TvTopHeaderBar`, `TvSceneChip`), `TvHouseholdSyncDialog.kt` (10-foot D-Pad pairing overlay with Up/Down focus containment), and `TvCardsAndScenes.kt` (`TvDeviceCard` 3-state D-Pad card). Wrap all long-lived `Modifier.onKeyEvent` action and directional navigation callbacks in `rememberUpdatedState(...)` across cards, dimmer strips, room bars, and top-bar chips so D-Pad presses never invoke closures capturing stale `device.isOn`, `device.brightness`, or empty-grid `groupedByRoom` snapshots.
4. **Cohesive UI State Controllers, `try / finally` Spinner Cleanup & Bounded Concurrent Batch Execution (`DeviceActionsUiController`, `HardwareRulesUiController`, `HouseholdSyncUiController`, `ProvisionApUiController` & `HomeAndRulesUiController` Pattern)**:
   - Avoid letting a single `AndroidViewModel` accumulate 1,000+ lines by mixing core state exposures with device/room/SSDP action workflows, on-device hardware rules dialog/mutation workflows, household sync flows, multi-step wizard state machines (`10.22.22.1` provisioning), or secondary modal CRUD workflows (Wi-Fi Home profiles, device edit modal, TV scenes).
   - Extract self-contained state flows and coroutine workflows into internal ViewModel-scoped controllers (`DeviceActionsUiController.kt`, `HardwareRulesUiController.kt`, `HouseholdSyncUiController.kt` + `HouseholdSyncUiState.kt`, `ProvisionApUiController.kt`, `HomeAndRulesUiController.kt`) while delegating public `StateFlow` properties and action methods on `SwitchViewModel` (`< 500` lines) so external callers and E2E tests experience zero API churn.
   - Always wrap loading spinner mutations (`_togglingRooms`, `_togglingDeviceIds`, `_isRefreshing`, `_isReconnectingHome`, `_isScanningSetupAp`, `_isProvisioningAp`, `isDiscoveringPeers`, `isPushingToPeer`, `isStartingReceiveSession`) in job-identity-checked `try { ... } finally { ... }` so coroutine cancellation or superseded jobs never leave stuck spinners, and fan out multi-switch batch operations (`toggleRoomPower`, `updateRoomBrightness`, `turnAllSwitches`, `applyTvScene`) via `coroutineScope { map { async { ... } }.awaitAll() }` over idempotent `setDevicePowerExplicit(device, targetOn)` bounded by `Semaphore(3)`.

---

## 3. Critical Anti-Breakage Invariants During Refactoring

Whenever refactoring ViewModels, Compose dialogs, network clients, or Room persistence, enforce these three invariants to prevent subtle UI hangs and E2E test breakages:

1. **Never Block Synchronous Modal/Dialog Dismissal on Background `Dispatchers.IO` Network Probes (`SwitchViewModel.saveDevice` & `HardwareRulesUiController` Pattern)**:
   - When a user taps **Save** or **Confirm** in a standard CRUD dialog (`AddOrEditDeviceDialog`, `HardwareRuleEditorDialog`, `HardwareLocationDialog`, `HardwareRulesBackupDialog`, `DeleteHardwareRuleConfirmDialog`), always update the dialog visibility state synchronously (`_showAddDeviceDialog.value = false`, `_editingDevice.value = null`, `_editorRequest.value = null`, `_locationDialogDevice.value = null`, `_backupDialogDevice.value = null`, `_pendingDeleteRule.value = null`) **before** launching `viewModelScope.launch` / `Dispatchers.IO` background work (`repository.saveDevice` 9-port `probeWemoPort`, `ChangeFriendlyName`, or armored `rules.db` `StoreRules` writes).
   - Keeping modal dismissal inside `viewModelScope.launch` *after* a suspending network call leaves the `Dialog` covering the screen while TCP connect timeouts run against unreachable or demo IPs (`192.168.1.x`). This makes the **Save** button feel frozen in production and causes Compose UI tests (`createAndroidComposeRule`) to click into the modal scrim instead of the underlying screen.
   - *Exception*: Multi-step interactive wizards with an explicit in-dialog progress state machine (`ProvisionApDialog`) intentionally remain open while showing step-by-step progress and only auto-dismiss upon completion or Cancel.
2. **Preserve `0`-Arg `SocketFactory.createSocket()` Loopback & Test Guards (`NetworkMonitor.createWifiBoundSocketFactory` Pattern)**:
   - `LanHttpClient` invokes the **zero-argument** `SocketFactory.createSocket()` and subsequently calls `socket.connect(endpoint, timeout)`.
   - Never bind a socket to a Wi-Fi `Network` unconditionally inside `createSocket()`. Wrap `Socket` and override `connect(endpoint, timeout)` so `wifiNet.bindSocket(this)` is **skipped** when `inetAddr.isLoopbackAddress` (`127.0.0.1` loopback test servers) or when running under Robolectric (`Build.FINGERPRINT == "robolectric"`).
3. **Scope Singleton Room Databases Per `Application` Instance Under Robolectric (`SwitchDatabase.getDatabase` Pattern)**:
   - Robolectric creates a fresh `Application` instance for every `@Test` method while static singletons (`SwitchDatabase.INSTANCE`) persist across tests in the same JVM process.
   - Always track `lastAppContext` in `SwitchDatabase.getDatabase(context)` and rebuild with `Room.inMemoryDatabaseBuilder(...).allowMainThreadQueries()` under Robolectric whenever `context.applicationContext !== lastAppContext`, preventing stale SQLite connection errors and cross-test state leakage.

---

## 4. Safe Refactoring Checklist (Zero-Regression Protocol)

When modularizing an existing Android codebase:
1. **Preserve Package & Public Signatures First**:
   - Keep extracted top-level functions or internal delegates in the same package (or provide typealiases / delegating methods) so existing callers, Compose previews, and tests compile without churn.
2. **Verify Line Counts & Responsibility Boundaries**:
   - Run `wc -l $(find app/src -name "*.kt" | sort)` before and after refactoring to confirm no file exceeds its complexity budget without justification.
3. **Audit Synchronous vs. Asynchronous UI State Transitions**:
   - Verify that extracting or reorganizing ViewModel / Repository methods never moves synchronous UI state updates (`_show*Dialog`, `_editing*`) behind suspending network I/O.
4. **Mandatory Non-Cached Build & Test Gate (`--rerun-tasks`)**:
   - Run `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` after every modularization stage and after syncing remote commits, and inspect `app/build/test-results/testDebugUnitTest/TEST-*.xml` to verify 100% test pass rate, zero lint/compiler warnings, and sub-second loopback E2E test durations before committing.

