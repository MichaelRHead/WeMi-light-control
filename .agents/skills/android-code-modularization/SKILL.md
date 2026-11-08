---
name: android-code-modularization
description: Guidelines and refactoring patterns for keeping Android & Jetpack Compose codebases exceptionally readable for humans. Use when auditing file complexity, decomposing large Repository/Network/ViewModel/Compose files into single-responsibility modules, eliminating duplicated state-transition boilerplate, or enforcing file-size and cohesion standards without breaking public APIs or package imports.
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
- **Extract Seed & Demo Fixtures (`*DemoFixtures.kt`)**:
  - Move static sample entities (`defaultHomeNetworks()`, `defaultClassicDevices()`, `defaultClassicRules()`) into a dedicated file (`WeMoDemoFixtures.kt`).
- **Unify Optimistic State-Transition Templates**:
  - Never copy-paste 70-line `mutex.withLock { optimisticWrite(); semaphore.withPermit { soapCall() }; twoStageReconnect(); commitOrRevert() }` blocks across `togglePower`, `setPowerExplicit`, and `updateBrightness`.
  - Extract a single higher-order helper (e.g., `executeOptimisticDeviceCommand(...)`) that encapsulates the concurrency lock, optimistic Room update, 2-stage self-healing retry, SOAP log recording, and offline rollback in one place.
- **Decompose by Domain Workflow**:
  - Keep `Repository` as a concise facade (~300–450 lines) that delegates to focused internal collaborators:
    - `*DiscoveryAndAdoptionManager`: SSDP scan orchestration, `<serialNumber>` matching across DHCP IP changes, startup parallel state probing, and `/24` subnet home resolution.
    - `*ProvisioningCoordinator`: Zero-cloud `10.22.22.1` setup AP probing, AP scanning, `ConnectHomeNetwork` provisioning, and automatic Room registration.

### 2.2 Network & Wire Protocol Layer (`network/`)
Separate **stateless wire parsing / cryptography** from **stateful socket & HTTP I/O**:
- **Stateless Parsers & Classifiers (`*XmlAndSkuParser.kt`)**:
  - Keep pure functions (`parseSsdpHeaders`, `parseSetupXmlDescriptor`, `classifyHardwareSku`, `buildSsdpMSearchPacket`) in a stateless, zero-Android-dependency file that can be unit-tested in milliseconds.
- **Pure Cryptographic Helpers (`*PasswordCrypto.kt`)**:
  - Isolate key derivation (`EVP_BytesToKey` MD5 / PBKDF2) and cipher routines (`AES/CBC/PKCS5Padding` Methods 1, 2, and 3) into a dedicated crypto utility file with round-trip `encrypt`/`decrypt` functions.
- **Discovery vs. Control Separation**:
  - Separate UDP multicast `M-SEARCH` + `/24` unicast subnet sweeping (`*SsdpDiscoveryEngine.kt`) from point-to-point HTTP SOAP control (`*NetworkClient.kt`).

### 2.3 Jetpack Compose UI Layer (`ui/`)
1. **One Screen or Dialog Per File**:
   - Never bundle unrelated modal dialogs (`AddOrEditDeviceDialog`, `AddRuleDialog`, `ProvisionApDialog`) into a single `Dialogs.kt` monolith. Give each dialog its own file named after its workflow (`WeMoDeviceEditDialog.kt`, `WeMoRuleDialog.kt`, `WeMoProvisionApDialog.kt`).
2. **Separate Custom Canvas Illustrations by Category (`ui/components/illustrations/`)**:
   - Custom `Canvas` / vector artwork is line-heavy (~150–250 lines per 3-state illustration). Group illustrations into dedicated files (e.g., `WeMoSwitchAndPlugIllustration.kt`, `WeMoLightAndSensorIllustrations.kt`, `WeMoChristmasTreeIllustration.kt`, `WeMoFanIllustration.kt`, and `WeMoIllustrationCommon.kt` for the category router and shared badges).
3. **Keep `MainActivity.kt` as a Thin App Shell (`< 300` Lines)**:
   - `MainActivity` / `WeMiSwitchApp` should only handle edge-to-edge system bars, TV vs. Mobile mode routing, top/bottom scaffold bars, `BackHandler`, and modal dialog hosting.
   - Extract each bottom-navigation tab's body into `ui/screens/` (e.g., `WeMoDevicesTabScreen.kt`, `WeMoRulesScreen.kt`, `WeMoMoreScreen.kt`, `WeMoTvDashboardScreen.kt`).
4. **Cohesive UI State Slices in ViewModels**:
   - Avoid exposing 25–35 independent `StateFlow` properties that force the root composable to write 35 `.collectAsStateWithLifecycle()` lines. Group related UI state into immutable data classes (e.g., `ProvisioningUiState`, `DeviceListUiState`) or cohesive screen state holders.

---

## 3. Safe Refactoring Checklist (Zero-Regression Protocol)

When modularizing an existing Android codebase:
1. **Preserve Package & Public Signatures First**:
   - Keep extracted top-level functions or internal delegates in the same package (or provide typealiases / delegating methods) so existing callers, Compose previews, and tests compile without churn.
2. **Verify Line Counts & Responsibility Boundaries**:
   - Run `wc -l $(find app/src -name "*.kt" | sort)` before and after refactoring to confirm no file exceeds its complexity budget without justification.
3. **Mandatory Build & Test Gate**:
   - Run `./gradlew testDebugUnitTest assembleDebug` after every modularization step and verify 100% test pass rate and zero compiler warnings before committing.
