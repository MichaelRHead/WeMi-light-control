# WeMi Light Control — Architecture & Agent Guide (`AGENTS.md`)

**WeMi Light Control** (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`, `v20.0`) is an Android & Android TV local-LAN controller for Belkin WeMo smart switches, dimmers, and mini plugs. It pairs a 2013-era skeuomorphic Belkin WeMo interface (`#73D41B` lime green header, brushed metallic rows, glowing power buttons, and custom hardware illustrations) with `pywemo`-grade local UPnP / SSDP / SOAP / GENA networking, zero-cloud `10.22.22.1` provisioning, and Room v5 persistence.

Detailed agent instructions, UX design architecture, debugging & support architecture, device discovery & room grouping architecture, user guides, protocol specs, reverse-chronological change log, modularization plan, and upcoming features roadmap are packaged in:
- [`UX_DESIGN.md`](UX_DESIGN.md) — **Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows** (**Owned Exclusively by the Google AI Studio Agent** — read-only for all other agents)
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — **Android & Android TV UX Design, Behavioral Model & Critical User Journeys** (**Owned Exclusively by the Google AI Studio Agent** — read-only for all other agents)
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — **On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture** (**Owned Exclusively by the Antigravity Debugging & Support Agent** — read-only for all other agents)
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — **Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture** (**Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent** — read-only for all other agents)
- [`USER_GUIDE.md`](USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — **4-Agent Autonomous Round-Robin Release Review Process & Live Sign-Off State Machine** (`ai_studio_ux_cuj` → `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing` → `ai_studio_ux_cuj`)
- [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) — High-Level User-Facing Google Play Store Release Notes (`v1.0` → `v20.0`)
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Feature & Improvement Change Log (`v1.0` → `v20.0`)
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Human-Readable Code Modularization, Implementation Validation & End-to-End (E2E) Testing Plan
- [`.agents/skills/wemi-release-coordinator/SKILL.md`](.agents/skills/wemi-release-coordinator/SKILL.md) — **4-Agent Autonomous Round-Robin Release Review & Sign-Off Coordinator Skill**
- [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) — **UX Design, Behavioral Modeling & CUJ Skill** (**Owned Exclusively by the Google AI Studio Agent** — read-only for all other agents)
- [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) — **On-Device Screenshot & Recorded Telemetry File Debugging Skill** (**Owned Exclusively by the Antigravity Debugging & Support Agent** — read-only for all other agents)
- [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) — **Device Discovery Logic, 3-Tier Identity Resolution & Automatic Room Grouping Skill** (**Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent** — read-only for all other agents)
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Core Domain & Protocol Agent Skill
- [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) — Change Log & Documentation Maintenance Skill
- [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md) — Multi-Agent Git Sync, Anti-Stomp Audit & Conflict-Free Integration Skill
- [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md) — Android Code Modularization & Human Readability Skill
- [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) — Android End-to-End (E2E) Testing & Validation Skill
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
    - `normalizeDeviceNameForInference`, `inferRoomFromName` & `resolveDeviceRoomAssignment`: Normalizes CamelCase/delimiters, infers rooms in specificity order (`"Ensuite"` before `"Bedroom"`, `"bed"`/`"bedside"`/`"nightstand"`/`"girls"`/`"boys"`/`"kids"` → `"Bedroom"`, `"stair"`/`"stairway"` → `"Hallway & Landing"`, word-bounded `"den"`/`"hall"`, and `"Unassigned"` fallback instead of `"Living Room"`), and non-destructively migrates switches stuck in `"Living Room"`.
    - `WeMoConnectionQuality`: `EXCELLENT` (<= 28 ms, 4 bars), `GOOD` (<= 65 ms, 3 bars), `SLOW` (> 65 ms, 2 bars), `OFFLINE` (0 bars).
    - `WeMoHomeNetworkEntity` (`wemo_home_networks`): Persistent multi-home Wi-Fi profiles (`networkKey`, `homeName`, `ssidLabel`, `subnetPrefix`, `gatewayIp`, `lastConnectedTime`, `autoReconnectOnArrival`, `sortOrder`).
    - `WeMoDeviceEntity` (`wemo_devices`): Device state, network address (`ipAddress`, `port`, `networkKey`, `wifiSsid`), hardware telemetry (`serialNumber`, `firmwareVersion`, `hardwareModel`, `macAddress`, `udn`, `signalStrengthPercent`, `nightModeEnabled`), and live connection status (`isReachable`, `latencyMs`, `lastCheckedTime`, `isOn`, `brightness`, `autoOffMinutes`).
    - `WeMoRuleEntity` (`wemo_rules`): Local schedules and timers (`SCHEDULE`, `MOTION_TRIGGER`, `AUTO_OFF`).
  - `WeMoDao.kt` & `WeMoDatabase.kt`: Room database (`wemo_control_database.db`, schema `version = 5`, per-`Application` in-memory isolation under Robolectric).
  - `WeMoRepository.kt`: Public repository facade holding `Semaphore(3)` RTOS protection, per-device `Mutex`, unified `executeOptimisticDeviceCommand` (optimistic UI write, `lastSetStateTimestampMs` GENA echo suppression, 2-stage reconnect, and offline revert), startup `ensureSeeded` delegation, one-tap `resetLanHomesSwitchesAndRooms()`, UPnP GENA push handling, and Room CRUD.
  - `WeMoDiscoveryCoordinator.kt`: 3-tier hardware identity matcher (`matchesDiscoveredDevice` & `findMatchingExistingDevice`), startup `GetBinaryState` detection (`detectStartupDeviceStates`), startup soft-AP SSID sanitization + legacy `"Living Room"` room re-inference (`sanitizeAndResolveInitialLanHome`), 2-stage self-healing reconnect (`attemptTwoStageReconnect`), SSDP scan & auto-adoption (`executeSsdpScan`, `adoptDiscoveredDevice`), and zero-permission subnet/SSID home sync with `isWemoDeviceSetupSsid` filtering.
  - `WeMoProvisioningCoordinator.kt`: `10.22.22.1` Setup AP probing (`probeSetupApDevice`), 2.4 GHz AP scanning (`scanSetupApNetworks`), `WiFiSetup1#ConnectHomeNetwork` provisioning (`provisionSetupApDevice`), Demo Showroom AP simulation, and post-provisioning Room registration.
  - `WeMoDemoFixtures.kt`: Showcase Demo Showroom fixtures (`DEFAULT_ROOMS`, `defaultHomeNetworks`, `defaultClassicDevices`, `defaultClassicRules`).
  - `WeMoRuleScheduler.kt`: Coroutine engine for per-device/rule Auto-Off countdowns (`activeCountdownsSec`) and daily `SCHEDULE` / Sunset / Sunrise rule execution.
- **`network/` (`pywemo`-Grade UPnP / SSDP / SOAP / GENA & Provisioning Engine)**:
  - `WeMoNetworkClient.kt`:
    - 9-port `probeWemoPort` (`49153, 49152, 49154, 49151, 49155..49159`) with TCP connect timeout vs. `ECONNREFUSED` vs. read timeout separation and `<serialNumber>`/`<UDN>` verification.
    - Unified `executeWithPortHealing` (with Wi-Fi bound `wifiSocketFactory` candidate fallback), `setBinaryState` (with `<BinaryState>Error</BinaryState>` handling), `getBinaryState`, `getMetaInfo`, `getSignalStrength`, `changeFriendlyName`, `configureNightMode`, and companion delegations to `WeMoXmlAndSkuParser` and `WeMoSsdpDiscoveryEngine`.
  - `WeMoXmlAndSkuParser.kt`: Stateless UPnP/SSDP/SOAP parsers (`buildSsdpMSearchPacket`, `parseSsdpHeaders`, `classifyHardwareSku`, `parseSetupXmlDescriptor`, `parseMetaInfoSoapResponse` with Belkin soft-AP `WeMo.*` / `Wemo.LS3W.*` SSID filtering, `parseSignalStrengthSoapResponse`, `parseApListXml`).
  - `WeMoSsdpDiscoveryEngine.kt`: UDP `239.255.255.250:1900` `M-SEARCH` scanner (`performSsdpDiscoveryScan` with `MulticastLock` + 1s retransmit) and `/24` `Semaphore(24)` unicast sweep (`scanSubnetUnicast`).
  - `WeMoGenaSubscriptionManager.kt`: Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery) for `<100ms` physical switch updates.
  - `WeMoNetworkMonitor.kt`: Zero-Location-permission `ConnectivityManager.NetworkCallback` `/24` subnet & `10.22.22.x` Setup AP detector with `createWifiBoundSocketFactory` (caching unvalidated `NetworkCallback` Wi-Fi handles + loopback `127.0.0.1` & Robolectric aware).
  - `WeMoProvisionerClient.kt` & `WeMoSetupDiagnostics.kt`: `10.22.22.1` zero-cloud `WiFiSetup1#GetApList` / `GetNetworkList` (`8500ms` site-survey timeout), OkHttp-framed `probeSetupDevice`, and double-send `ConnectHomeNetwork` + 20s `GetNetworkStatus` poll + `CloseSetup` + `SetSetupDoneStatus` provisioner with `WeMoSetupDebugSnapshot` screenshot diagnostics and `WeMoTelemetryFileExporter` (`wemi-telemetry-latest.txt`).
  - `WeMoPasswordCrypto.kt`: Stateless `pywemo`-exact OpenSSL `EVP_BytesToKey` MD5 key/IV derivation and AES-128-CBC Wi-Fi password encryption/decryption (Methods 1, 2 & 3 × `addPasswordLengths`).
- **`ui/` (Jetpack Compose Skeuomorphic UI)**:
  - `WeMoViewModel.kt`: UI state holder, `setupDebugSnapshot`, background health polling, synchronous modal dismissal, and coroutine dispatchers.
  - `screens/WeMoDevicesTabScreen.kt` (with `EmptyDevicesState`), `screens/WeMoRulesScreen.kt`, and `screens/WeMoMoreScreen.kt`.
  - `components/WeMoWifiHomeBar.kt`, `components/WeMoRoomGroupCard.kt`, `components/WeMoSsdpScannerModal.kt`, and `components/WeMoHeaderAndNav.kt`.
  - `components/WeMoDeviceRow.kt` (with `AutoResizingDeviceNameText`), `components/WeMoDeviceStatusBadges.kt` (with `WeMoPowerStatePill`), and `components/WeMoDeviceDiagnosticsDrawer.kt`.
  - `components/WeMoDeviceEditDialog.kt`, `components/WeMoRuleDialog.kt`, `components/WeMoProvisionApDialog.kt`, and `components/WeMoProvisionDebugPanel.kt`.
  - `components/WeMoTvScreen.kt` and `components/WeMoTvCardsAndScenes.kt`.
  - `components/WeMoHardwareIllustrations.kt` (`drawIecPowerSymbol`, `drawDisconnectedSymbol`, `WeMoPowerRingButton`, `WeMoDisconnectedRingButton`), `components/WeMoSwitchAndPlugIllustration.kt`, `components/WeMoLightAndSensorIllustrations.kt`, `components/WeMoChristmasTreeIllustration.kt`, and `components/WeMoFanIllustration.kt`.
- **`test/` (Loopback UPnP/SOAP Hardware Simulator, Protocol Unit Tests & Compose E2E Tests — `app/src/test/java/com/example/`)**:
  - `FakeWeMoUpnpServer.kt`: Reusable `AutoCloseable` loopback Belkin WeMo HTTP/UPnP/SOAP/GENA/`WiFiSetup1` + UDP SSDP `M-SEARCH` responder server on `127.0.0.1` (`ServerSocket(0)` & `DatagramSocket(0)`).
  - `WeMoSkuAndCryptoTest.kt`: Hardware SKU classification (`F7C030`, `WLS0403`, `F7C059`, `WDS060`, `F7C063`, `WSP080`), `/setup.xml` parsing, `GetMetaInfo` soft-AP SSID filtering (`isWemoDeviceSetupSsid`), semantic device categorization, and `pywemo` AES-128-CBC encryption/decryption (Methods 1, 2 & 3).
  - `WeMoSoapAndSelfHealingTest.kt`: 9-port `probeWemoPort` port-shift healing, `<BinaryState>Error</BinaryState>` quirk, `GetMetaInfo`, `ChangeFriendlyName`, offline rollback, manual IP probing, soft-AP SSID startup DB sanitization, `resetLanHomesSwitchesAndRooms`, and multi-device startup state detection.
  - `WeMoGenaProvisioningAndRulesTest.kt`: UPnP GENA `SUBSCRIBE`, HTTP 412 recovery, embedded `NOTIFY` callback server, `WeMoRuleScheduler`, and full loopback `10.22.22.1` `WiFiSetup1` provisioning (including `NetworkStatus=0` failure & `CloseSetup` hold and `WeMoSetupDebugSnapshot` formatting).
  - `ExampleRobolectricTest.kt`, `WeMoEndToEndUiTest.kt` & `WeMoHardwareCujEndToEndTest.kt`: Full-stack Jetpack Compose + Robolectric + `FakeWeMoUpnpServer` End-to-End UI tests (`26/26` total tests passing), including the complete `Reset` -> `SSDP Discovery` -> `Adopt Switch & Dimmer` -> `Toggle Switch` -> `Toggle & Scale Dimmer Brightness` CUJ, 9-port Disconnected ring self-healing + GENA push UI transition + Live SOAP Log file export CUJ, and zero-cloud `10.22.22.1` Setup AP hardware scan + AES Method 2 provisioning + screenshot telemetry export CUJ.

---

## 3. Build & Verification Commands (Mandatory Pre-Push Gate)

> **CRITICAL RULE**: **Only push code to GitHub (`origin/main` or `origin/agent-docs`) after verifying that the full build and all automated tests pass with zero errors.** Never push unverified or non-compiling code.

```bash
# Force-execute all 26 Robolectric unit, protocol & E2E Compose UI tests
./gradlew testDebugUnitTest --rerun-tasks

# Assemble debug APK
./gradlew assembleDebug
```

- In remote VM environments where `HTTPS_PROXY` is active, ensure `~/.gradle/gradle.properties` configures `http.proxyHost`/`https.proxyHost` and `javax.net.ssl.trustStore` with the local proxy CA, and `ANDROID_HOME` points to the Android SDK (`platforms;android-36`, `build-tools;36.0.0`).
- **AI Studio Agent Exclusive UX Ownership ([`wemi-ux-design-and-experience`](.agents/skills/wemi-ux-design-and-experience/SKILL.md))**: [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), and [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) are **owned exclusively by the Google AI Studio Agent**. All other agents must read and follow them when implementing UI/UX features or tests, but **must not modify or overwrite them**.
- **Antigravity Debugging & Support Agent Exclusive Telemetry Ownership ([`wemi-device-telemetry-debug`](.agents/skills/wemi-device-telemetry-debug/SKILL.md))**: [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), and [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md) are **owned exclusively by the Antigravity Debugging & Support Agent**. All other agents must read and follow them when modifying provisioning, diagnostics, or telemetry UI/network flows, but **must not modify, overwrite, or delete them**.
- **Antigravity Device Discovery & Room Grouping Agent Exclusive Ownership ([`wemi-discovery-and-room-grouping`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md))**: [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) and [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) are **owned exclusively by the Antigravity Device Discovery & Room Grouping Agent**. All other agents must read and follow them when modifying SSDP/unicast discovery, 3-tier identity matching (`matchesDiscoveredDevice`), or automatic room inference/migration (`inferRoomFromName` & `resolveDeviceRoomAssignment`), but **must not modify, overwrite, or delete them**.
- **Multi-Agent Git Sync & Anti-Stomp Audit ([`multi-agent-git-sync`](.agents/skills/multi-agent-git-sync/SKILL.md))**: Before starting work or pushing commits, always run `git fetch --all`, inspect incoming commits (`git log -p HEAD..origin/main`), verify that none of your prior changes were accidentally overwritten or broken by concurrent agent commits, and rebase additively (`git pull --rebase origin main`). Keep all `ALL_CAPS.md` documentation files at the repository root and human-focused user guides in `docs/user-guide/`.
- **Keep Change Log, Upcoming Features & User Guide Updated ([`wemi-changelog-and-docs`](.agents/skills/wemi-changelog-and-docs/SKILL.md))**: Whenever new features are implemented or existing features are adjusted, always:
  - Prepend a new timestamped entry at the **top** (reverse chronological order) of [`IMPLEMENTED.md`](IMPLEMENTED.md).
  - Remove completed items from [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md).
  - Update [`USER_GUIDE.md`](USER_GUIDE.md) and the relevant feature guides under [`docs/user-guide/`](docs/user-guide/).
- **4-Agent Autonomous Round-Robin Release Review ([`wemi-release-coordinator`](.agents/skills/wemi-release-coordinator/SKILL.md), [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md), [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md))**:
  - Releases follow a 5-stage round-robin ring synchronized via [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) on `origin/main`:
    1. **Stage 1 (Initiate)**: `ai_studio_ux_cuj` (Google AI Studio Agent) initiates the release, signs off UX/CUJs, and sets `CURRENT_TURN: discovery_and_grouping`.
    2. **Stage 2 (Review 1)**: `discovery_and_grouping` (Antigravity Discovery & Room Grouping Agent) audits discovery/identity/room bundling, runs tests, signs off, and advances `CURRENT_TURN: telemetry_and_debugging` via `bash scripts/advance_release_turn.sh sign_off discovery_and_grouping "<summary>"`.
    3. **Stage 3 (Review 2)**: `telemetry_and_debugging` (Antigravity Telemetry & Debugging Agent) audits `10.22.22.1` setup telemetry & SOAP log export, runs tests, signs off, and advances `CURRENT_TURN: code_quality_and_testing` via `bash scripts/advance_release_turn.sh sign_off telemetry_and_debugging "<summary>"`.
    4. **Stage 4 (Review 3)**: `code_quality_and_testing` (Antigravity Code Quality & E2E Testing Agent) audits modularity/lifecycle/executable `gradlew` (`100755`), runs `./gradlew testDebugUnitTest --rerun-tasks assembleDebug`, signs off, and advances `CURRENT_TURN: ai_studio_ux_cuj` (`RELEASE_STATUS: AWAITING_FINAL_CONCLUSION`) via `bash scripts/advance_release_turn.sh sign_off code_quality_and_testing "<summary>"`.
    5. **Stage 5 (Conclude)**: `ai_studio_ux_cuj` (Google AI Studio Agent) verifies all 4 agents signed off and concludes the release (`RELEASE_STATUS: APPROVED_FOR_PLAY_STORE`, `CURRENT_TURN: NONE`).
  - Each Antigravity agent keeps `bash scripts/await_release_turn.sh <role_id>` running as an async background task (`IsDaemon: false`, `WaitMsBeforeAsync: 1000`) so Antigravity's Reactive Wakeup automatically resumes the agent when its turn arrives on `origin/main`.




