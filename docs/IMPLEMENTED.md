# WeMi Light Control — Implemented Features & Change Log (`IMPLEMENTED.md`)

**Repository**: `MichaelRHead/WeMi-light-control` (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`)  
**Current Version**: `v12.0` (`versionCode = 12`)  
**Target Platforms**: Android Phones, Tablets, and Android TV (`UI_MODE_TYPE_TELEVISION` 10-foot D-Pad interface)

Related documentation:
- [`USER_GUIDE.md`](USER_GUIDE.md) / [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) / [`docs/UPCOMING_FEATURES.md`](docs/UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Human-Readable Code Modularization, Implementation Validation & End-to-End (E2E) Testing Plan
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Core Domain & Protocol Agent Skill
- [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) — Change Log & Documentation Maintenance Skill
- [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md) — Multi-Agent Git Sync, Anti-Stomp Audit & Conflict-Free Integration Skill
- [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md) — Android Code Modularization & Human Readability Skill
- [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) — Android End-to-End (E2E) Testing & Validation Skill

> [!IMPORTANT]
> **Change Log Ordering Rule**: All implemented features, bug fixes, and architectural improvements are recorded below in **reverse chronological order** (newest at the top, oldest at the bottom) with the date/time landed, version/commit reference, and affected source files.

---

## 1. Reverse-Chronological Summary Table

| Landed (PST) | Version / Commit | Feature or Improvement | Primary Files |
| :--- | :--- | :--- | :--- |
| **2026-11-08 07:41 PST** | `v12.0` (`HEAD`) | **Multi-Agent Git Sync & Anti-Stomp Integration Skill (`multi-agent-git-sync`)** | [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md), [`AGENTS.md`](AGENTS.md), [`IMPLEMENTED.md`](IMPLEMENTED.md) |
| **2026-11-08 07:25 PST** | `v12.0` (`2cfe70d`..`de14b85`) | **Reverse-Chronological Change Log (`IMPLEMENTED.md`), `UPCOMING_FEATURES.md` & `wemi-changelog-and-docs` Skill** | [`IMPLEMENTED.md`](IMPLEMENTED.md), [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md), [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) |
| **2026-11-08 07:20–07:28 PST** | `v12.0` (`627ab38`, `a308d24`) | **Code Modularization & E2E Testing Plan + `android-code-modularization` & `android-e2e-testing` Skills** | [`docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md), [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md), [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) |
| **2026-11-08 07:19 PST** | `v12.0` (`1237ecc`) | **Provisioning Status Box Error Styling, Empty State Category Showcase & `v12.0` Bump** | [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), [`app/build.gradle.kts`](app/build.gradle.kts) |
| **2026-11-08 07:08 PST** | `v11.0` (`cca33cd`) | **Modular User Guide Index & 8 Detailed Feature Guides** | [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/user-guide/`](docs/user-guide/) |
| **2026-11-08 01:49 PST** | `v11.0` (`fa7e744`) | **Fresh-Device `WiFiSetup1` Provisioning Fixes, `pywemo` OpenSSL AES-128-CBC (Methods 1/2/3), Android Wi-Fi Socket Binding & Manual IP Probing** | [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt) |
| **2026-11-08 00:03–00:55 PST** | `v10.0–v11.0` (`62bef69`, `47b7a59`, `171f936`) | **Expanded Loopback Protocol/UI Test Suite & Mandatory Pre-Push Gradle Gate** | [`WeMoProtocolTest.kt`](app/src/test/java/com/example/WeMoProtocolTest.kt), [`ExampleRobolectricTest.kt`](app/src/test/java/com/example/ExampleRobolectricTest.kt), [`AGENTS.md`](AGENTS.md) |
| **2026-11-07 23:47 PST** | `v10.0` (`1a7a411`, `4649008`) | **Semantic Switch-Name Classification & Custom 3-State Icons (Christmas Trees, Fans, Lights/Lamps)** | [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt), [`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt) |
| **2026-11-07 23:16 PST** | `v9.0` (`0670690`, `8a06f6a`) | **Startup `GetBinaryState` Detection & Distinct `ON` vs. `OFF (Accessible Standby)` vs. `Disconnected` Icons** | [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt) |
| **2026-11-07 19:33–21:11 PST** | `v7.0–v8.0` (`29fa58c`, `edb9fcb`, `e1677c1`) | **UPnP GENA Push Server (`<100ms`), Zero-Permission Subnet/SSID Monitor, Rule Scheduler & Initial `10.22.22.1` Provisioner (Phases 3–7)** | [`WeMoGenaSubscriptionManager.kt`](app/src/main/java/com/example/network/WeMoGenaSubscriptionManager.kt), [`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoRuleScheduler.kt`](app/src/main/java/com/example/data/WeMoRuleScheduler.kt) |
| **2026-11-07 17:43 PST** | `v6.0–v7.0` (`787cd9d`) | **`pywemo`-Grade UPnP / SSDP / SOAP Engine, 9-Port Smart Probing, Self-Healing Reconnect & Real LAN vs. Demo Mode (Phases 1–2)** | [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt) |
| **2026-11-07 16:17–17:22 PST** | `v6.0` (`503b2d5`..`0027337`) | **`AGENTS.md`, `wemi-light-control` Agent Skill & Belkin WeMo Wire Protocol Reference** | [`AGENTS.md`](AGENTS.md), [`.agents/skills/wemi-light-control/`](.agents/skills/wemi-light-control/) |
| **2026-11-07 15:53 PST** | `v5.0` (`1d0a993`, `d1620ab`) | **Persistent Multi-Home Wi-Fi Network Profiles (`WeMoWifiHomeBar`)** | [`WeMoWifiHomeBar.kt`](app/src/main/java/com/example/ui/components/WeMoWifiHomeBar.kt), [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt) |
| **2026-11-07 15:38 PST** | `v4.0` (`b0abf66`) | **Real-Time Device Latency Tracking & 4-Bar Connection Quality Badges** | [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt) |
| **2026-11-07 15:09 PST** | `v3.0–v4.0` (`bdf1bfc`, `a9e2429`) | **10-Foot Android TV Remote (D-Pad) Dashboard (`wemi TV`)** | [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt) |
| **2026-11-07 14:50–14:56 PST** | `v2.0` (`f7186a6`, `1415109`) | **Room Categorization, Collapsible Room Group Cards & Master Room Power/Dimmer Controls** | [`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt) |
| **2026-11-07 13:55–14:28 PST** | `v1.5` (`25be5e1`, `2687233`, `c243120`) | **Interactive SSDP Discovery Scanner Modal, Pull-to-Refresh & Play Store Assets** | [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt) |
| **2026-11-07 13:18–13:33 PST** | `v1.0` (`0e607e8`..`b6c1cf6`) | **Initial WeMi Light Controller, Skeuomorphic UI & Room Persistence** | [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDatabase.kt`](app/src/main/java/com/example/data/WeMoDatabase.kt) |

---

## 2. Detailed Change Log (Newest to Oldest)

### [2026-11-08 07:41 PST] — Multi-Agent Git Sync & Anti-Stomp Integration Skill (`multi-agent-git-sync`, `v12.0`)
- **Added [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md)**:
  - Defines the 5-step multi-agent Git synchronization, anti-stomp audit (`git diff <your-commit>..origin/main`), additive conflict resolution, root/`docs/` mirror verification, and `.agents/skills/` ↔ persistent skill registry synchronization workflow so parallel agent conversations and AI Studio code syncs never overwrite each other's changes.

---

### [2026-11-08 07:25 PST] — Reverse-Chronological Change Log (`IMPLEMENTED.md`), `UPCOMING_FEATURES.md` & `wemi-changelog-and-docs` Skill (`2cfe70d`..`de14b85`, `v12.0`)
- **Reframed [`IMPLEMENTED.md`](IMPLEMENTED.md) & [`docs/IMPLEMENTED.md`](docs/IMPLEMENTED.md) as a Reverse-Chronological Change Log**:
  - Restructured the implementation document so every feature, bug fix, and architectural milestone is chronicled in reverse chronological order with timestamps, commit hashes, version numbers, and affected files.
- **Renamed Implementation Plan to [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) & [`docs/UPCOMING_FEATURES.md`](docs/UPCOMING_FEATURES.md)**:
  - Replaced `docs/IMPLEMENTATION_PLAN.md` with `UPCOMING_FEATURES.md` (and `docs/UPCOMING_FEATURES.md`) focused strictly on upcoming roadmap items.
- **Added [`wemi-changelog-and-docs`](.agents/skills/wemi-changelog-and-docs/SKILL.md) Agent Skill**:
  - Created a dedicated workspace and registered skill instructing all agents working in this repository to prepend new entries to `IMPLEMENTED.md` in reverse chronological order, keep `USER_GUIDE.md` / `docs/user-guide/` in sync, and update `UPCOMING_FEATURES.md` whenever features land.

---

### [2026-11-08 07:20 PST] — Code Modularization & E2E Validation Plan + Modularization & Testing Skills (`627ab38`, `v12.0`)
- **Added [`docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md)**:
  - Defined a 4-phase plan (Phases M1–M4) to decompose large Repository, Network, UI, and Test files into focused single-responsibility Kotlin modules while preserving all package imports and `pywemo` protocol guarantees.
- **Added [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md) & [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md)**:
  - Packaged reusable engineering skills for file-size/cohesion boundaries and loopback E2E Robolectric/Compose validation.

---

### [2026-11-08 07:19 PST] — Provisioning Status Box Error Styling, Empty State Category Showcase & `v12.0` Bump (`1237ecc`, `v12.0`)
- **Provisioning Status Box Error Highlighting ([`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt))**:
  - Added warning/error styling (`#FEF2F2` background and `WemoErrorRed` text) to `ProvisionWeMoApDialog` when `10.22.22.1` is unreachable or rejects a request, plus preset room selection chips.
- **Polished Empty Home State ([`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt))**:
  - Added a 4-icon category illustration showcase (`SWITCH_PLUG`, `LIGHT`, `CHRISTMAS_TREE`, `FAN`) and full-width action buttons to `EmptyDevicesState`.

---

### [2026-11-08 07:08 PST] — Modular User Guide Index & 8 Detailed Feature Guides (`cca33cd`, `v11.0`)
- **Created Top-Level User Guide Index ([`USER_GUIDE.md`](USER_GUIDE.md) & [`docs/USER_GUIDE.md`](docs/USER_GUIDE.md))**:
  - Summarizes every major user-facing feature in a quick-reference table and links to 8 dedicated feature guides under [`docs/user-guide/`](docs/user-guide/):
    1. [`docs/user-guide/main-screen.md`](docs/user-guide/main-screen.md) — Main Screen & Device Controls (3-state startup icons, semantic switch-name icons, latency badges, inline dimming, Night Mode, GENA push sync)
    2. [`docs/user-guide/switch-discovery.md`](docs/user-guide/switch-discovery.md) — Switch Discovery & Network Scanning (SSDP multicast, unicast `/24` fallback, auto-adoption & `Adopt All`, self-healing reconnect)
    3. [`docs/user-guide/fresh-device-setup.md`](docs/user-guide/fresh-device-setup.md) — Fresh Device Setup & Zero-Cloud Wi-Fi Provisioning (`10.22.22.1` `WiFiSetup1`)
    4. [`docs/user-guide/room-grouping.md`](docs/user-guide/room-grouping.md) — Room Grouping & Master Room Controls (`Grouped by Room` vs. `Flat List`, room filter chips, Master Room Power Ring, Room Master Brightness Drawer)
    5. [`docs/user-guide/editing-and-managing-devices.md`](docs/user-guide/editing-and-managing-devices.md) — Editing, Renaming, Reordering & Adding Switches (`Edit Mode`, live icon preview, `Sync Name to WeMo Hardware`, automatic 9-port IP probing)
    6. [`docs/user-guide/wifi-homes.md`](docs/user-guide/wifi-homes.md) — Multi-Home Wi-Fi Profiles & Switching (zero-permission subnet detection, `GetMetaInfo` SSID sync, 1-tap `Reconnect`)
    7. [`docs/user-guide/rules-and-timers.md`](docs/user-guide/rules-and-timers.md) — Rules, Schedules & Auto-Off Timers (daily schedules, Sunset/Sunrise, live `Auto-Off MM:SS` countdowns, motion triggers)
    8. [`docs/user-guide/more-and-tv-mode.md`](docs/user-guide/more-and-tv-mode.md) — Master Controls, Diagnostics, Demo Mode & Android TV (`All Switches ON/OFF`, Real LAN vs. Demo Showroom Mode, SOAP Packet Log, `wemi TV` 10-foot D-Pad interface)

---

### [2026-11-08 01:49 PST] — Fresh-Device `WiFiSetup1` Provisioning Fixes, `pywemo` OpenSSL AES-128-CBC, Wi-Fi Socket Binding & Manual IP Probing (`fa7e744`, `v11.0`)
- **Byte-for-Byte `pywemo` OpenSSL AES-128-CBC Password Encryption ([`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt))**:
  - Replaced PBKDF2-SHA1 key derivation with Belkin's exact OpenSSL EVP_BytesToKey MD5 derivation (`salt = keydata[0..8]`, `derivedKey = MD5(keydata + salt)[0..16]`, `iv = keydata[0..16]`) matching `pywemo/ouimeaux_device/api/wifi_setup.py` across all three hardware encryption methods:
    - **Method 1 (Gen 1 OpenWrt `F7C030` / `F7C059` / `F7C063`)**: `keydata = mac[0..6] + serial + mac[6..12]` with `addLengths = true` (`Base64(ciphertext) + "%02x%02x".format(ciphertext.size, plainPassword.length)`).
    - **Method 2 (Gen 2 RTOS `WLS040` / `WLS0403` / `WDS060`)**: `keydata = mac[0..6] + serial + mac[6..12] + "b3{8t;80dIN{ra83eC1s?M70?683@2Yf"` with `addLengths = false` (pure Base64 without hex length suffix).
    - **Method 3 (`<binaryOption>1</binaryOption>` Symmetric Firmware)**: `keydata = mac[0..3] + mac[9..12] + serial + "bVduaWFyZkllaWVAb3RjbHAkcm9uT2Jh" + mac[6..9] + mac[3..6]` with `addLengths = true`.
  - Added `decryptWifiPassword(...)` for round-trip encryption/decryption verification across all 3 methods.
- **9-Port Setup AP Probing & Auto-Detected Encryption Method (`probeSetupDevice`)**:
  - Probes all 9 candidate WeMo ports (`49152..49159`) on `10.22.22.1` checking both `/setup.xml` and `/Lightsetup.xml` with fast `650ms` connect-timeout abort, parses `<rtos>`, `<iot>`, and `<binaryOption>`, and automatically selects `recommendedEncryptionMethod`.
  - Added fallback from `WiFiSetup1#GetApList` to `WiFiSetup1#GetNetworkList` for legacy firmware compatibility.
- **Multi-Poll `GetNetworkStatus`, `CloseSetup`, `SetSetupDoneStatus` & Automatic Room Adoption ([`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt))**:
  - Pushes optional `basicevent1#ChangeFriendlyName` while on the setup AP, double-sends `WiFiSetup1#ConnectHomeNetwork` (`100ms` apart with XML entity escaping), polls `WiFiSetup1#GetNetworkStatus` across `2s + 1.5s` intervals (`0` = connecting, `3` = WPA2 handshake, `1` = connected, `2` = short password), always sends `WiFiSetup1#CloseSetup` and `basicevent1#SetSetupDoneStatus`, and automatically inserts/updates the newly provisioned switch in Room (`wemo_devices`).
- **Android No-Internet Setup AP Detection & Wi-Fi Socket Binding ([`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt))**:
  - Removed `NET_CAPABILITY_INTERNET` from `NetworkRequest` and prioritized active `TRANSPORT_WIFI` networks over cellular `cm.activeNetwork` so joining a no-Internet `WeMo.Light.xxx` AP (`10.22.22.x`) with mobile data active still routes all `10.22.22.1` and LAN HTTP/TCP sockets over Wi-Fi via `createWifiBoundSocketFactory(context)`.
- **Automatic Hardware Probe on Manual IP Entry ([`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt))**:
  - When a user adds a fresh device manually by IP (`saveDevice`), the app automatically probes all 9 WeMo ports at that IP (`probeAndFetchDeviceDescriptor`) and fetches `/setup.xml`, `GetBinaryState`, `GetMetaInfo`, and `GetSignalStrength`.
- **Upgraded Setup Wizard UI ([`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt))**:
  - Added **Provision New WeMo AP (`10.22.22.1`)** entry points in the `+` Add Device dialog, Empty Home state, and `More` tab, with auto-probing on open, detected hardware banner, live switch-name icon preview, room selector, and `Auto-Detect` encryption chip.

---

### [2026-11-08 00:03–00:55 PST] — Expanded Protocol/UI Test Suite & Mandatory Pre-Push Gradle Gate (`62bef69`, `47b7a59`, `171f936`, `v10.0–v11.0`)
- **16 Automated JUnit / Robolectric / Loopback Tests ([`WeMoProtocolTest.kt`](app/src/test/java/com/example/WeMoProtocolTest.kt), [`ExampleRobolectricTest.kt`](app/src/test/java/com/example/ExampleRobolectricTest.kt), [`ExampleUnitTest.kt`](app/src/test/java/com/example/ExampleUnitTest.kt))**:
  - Expanded automated verification to **16 test cases** (`10` in `WeMoProtocolTest`, `5` in `ExampleRobolectricTest`, `1` in `ExampleUnitTest`) covering SKU classification, loopback UPnP port shifting, multi-device startup `ON` / `OFF (Accessible Standby)` / `Disconnected` state detection, semantic switch-name icon classification, 3-state Compose UI rendering and interactive power toggling, fresh-device `ProvisionWeMoApDialog` workflow, manual IP 9-port hardware probing, DHCP serial mismatch protection, `<BinaryState>Error</BinaryState>` handling, `GetMetaInfo`, `GetSignalStrength`, `ConfigureNightMode`, `ChangeFriendlyName`, Real LAN Mode offline revert, GENA `SUBSCRIBE` / HTTP 412 / `NOTIFY` callbacks, `WeMoRuleScheduler` time parsing & Auto-Off countdowns, `WiFiSetup1` AP parsing & AES-128-CBC encryption across Methods 1/2/3, SSDP packet building, and multi-home room group controls.
- **Mandatory Pre-Push Build & Test Gate ([`AGENTS.md`](AGENTS.md))**:
  - Enforced the rule that code is only pushed to GitHub after `./gradlew testDebugUnitTest` and `./gradlew assembleDebug` pass with zero errors.

---

### [2026-11-07 23:47 PST] — Semantic Switch-Name Classification & Custom 3-State Icons for Christmas Trees, Fans, and Lights/Lamps (`1a7a411`, `4649008`, `v10.0`)
- **Automatic Switch-Name Classification (`classifyDeviceCategory` in [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt))**:
  - Every `WeMoDeviceEntity` and `SsdpDiscoveredDevice` exposes `visualCategory: WeMoDeviceCategory` (`CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`) inferred from the switch name and UPnP type without requiring a Room schema migration.
- **Custom Jetpack Compose `Canvas` Illustrations ([`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt))**:
  - **`WeMoChristmasTreeIllustration`**: 3-tiered evergreen pine tree on a wooden stand with a 5-point star topper, garland cord, and 9 multi-color fairy lights that glow with radial halos when `ON`, show unlit frosted bulbs + warm amber base standby LED when `OFF`, and render a dashed red outline when `Disconnected`.
  - **`WeMoFanIllustration`**: 4 aerodynamic fan blades inside a protective grille shroud that spin smoothly (`rememberInfiniteTransition`) with cyan airflow glow when `ON`, rest stationary with a warm amber center hub standby LED when `OFF`, and render a dashed red outline when `Disconnected`.
  - **`WeMoLightbulbIllustration`**: Smart E26 LED bulb with luminous globe scaled to dimmer `brightness` when `ON`, smoke-slate glass dome with visible unlit filament posts/coil + warm amber neck collar when `OFF`, and dashed red outline when `Disconnected`.
- **Integrated Across All Screens ([`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt), [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt), [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt))**:
  - Added category pill badges (`Tree Lights`, `Fan`, `Light`, `Motion`, `Switch`) on device rows and live as-you-type icon preview inside the Add/Edit Device dialog.

---

### [2026-11-07 23:16 PST] — Startup `GetBinaryState` Detection & Distinct Three-State Iconography (`0670690`, `8a06f6a`, `v9.0`)
- **Immediate Startup `GetBinaryState` Probe (`WeMoRepository.detectStartupDeviceStates` in [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt))**:
  - On app launch and home network switch, all remembered switches are immediately probed in parallel (bounded by `Semaphore(3)` for RTOS safety) via `basicevent1#GetBinaryState` before the SSDP discovery sweep begins, updating each device's live `isOn`, `brightness`, `isReachable`, `port`, and `latencyMs` in Room as soon as it responds.
  - Updated `WeMoNetworkClient.parseSetupXmlDescriptor` to parse `<binaryState>` and `<brightness>` directly from `/setup.xml` so newly discovered `OFF` switches never default to `ON`.
- **Three Unmistakable Visual States ([`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt))**:
  1. **Accessible & ON (`isReachable && isOn`)**: Glowing hardware illustration, emerald corner badge with IEC `"I"` power bar, green `WeMoPowerStatePill` (`"ON"`), and glowing cyan-green `WeMoPowerRingButton` (`"I"`).
  2. **Accessible & OFF (`isReachable && !isOn`)**: Full-contrast deep slate-navy housing with a **warm amber standby locator LED** (`#F59E0B`), dark-slate & amber corner badge with IEC `"O"` standby ring, `WeMoPowerStatePill` (`"OFF"`), and recessed slate `WeMoPowerRingButton` with top amber pilot dot and closed `"O"` symbol.
  3. **Disconnected / Offline (`!isReachable`)**: Desaturated silhouette with dashed red outline (`#DC2626`), red `"X"` corner badge, `Offline` connection badge, and inline `Retry` button.

---

### [2026-11-07 19:33–21:11 PST] — UPnP GENA Push Server (`<100ms`), Zero-Permission Subnet/SSID Monitor, Rule Scheduler & Initial `10.22.22.1` Provisioner (`29fa58c`, `edb9fcb`, `e1677c1`, `v7.0–v8.0`)
- **Phase 3 — Embedded UPnP GENA Push Server ([`WeMoGenaSubscriptionManager.kt`](app/src/main/java/com/example/network/WeMoGenaSubscriptionManager.kt))**:
  - Embedded HTTP `NOTIFY` listener (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, 240s renewal, automatic HTTP 412 Precondition Failed recovery) delivering `<100ms` real-time UI updates when physical wall switches or 3-way rockers are pressed, backed by a 30s fallback poll in [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt).
- **Phase 4 — Zero-Permission Wi-Fi Subnet Monitor & `GetMetaInfo` SSID Sync ([`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt))**:
  - Tracks active `/24` IPv4 subnet and `10.22.22.x` Setup AP connections via `ConnectivityManager.NetworkCallback` without requiring Android Location permissions, and populates the Wi-Fi Home's `ssidLabel` from connected switches' `metainfo1#GetMetaInfo` (`AccessPointSSID`).
- **Phase 5 — App-Driven Rule Scheduler, Live Auto-Off Countdowns & Dimmer Night Mode ([`WeMoRuleScheduler.kt`](app/src/main/java/com/example/data/WeMoRuleScheduler.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt))**:
  - Executes daily `SCHEDULE` rules (including `Sunset` and `Sunrise` keywords) and per-device/rule `AUTO_OFF` timers with live `Auto-Off MM:SS` countdown badges on the device row, plus a hardware **Night Mode (`20%`)** chip calling `ConfigureNightMode`.
- **Phase 6 & 7 — Initial `10.22.22.1` Provisioner & Loopback Protocol Tests ([`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoProtocolTest.kt`](app/src/test/java/com/example/WeMoProtocolTest.kt))**:
  - Added auto-detected `10.22.22.x` Setup AP banner in [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), `ProvisionWeMoApDialog` in [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), Room v5 schema migration ([`WeMoDatabase.kt`](app/src/main/java/com/example/data/WeMoDatabase.kt)), and loopback UPnP/SOAP/GENA unit tests.

---

### [2026-11-07 17:43 PST] — `pywemo`-Grade UPnP / SSDP / SOAP Engine, 9-Port Smart Probing, Self-Healing Reconnect & Real LAN vs. Demo Mode (`787cd9d`, `v6.0–v7.0`)
- **Phase 1 — `pywemo`-Grade UPnP / SSDP / SOAP Client ([`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt))**:
  - Implemented 9-port smart probing (`49153, 49152, 49154, 49151, 49155..49159`) separating TCP connect timeout vs. `ECONNREFUSED` vs. HTTP read timeout and verifying `<serialNumber>`/`<UDN>` in `/setup.xml`.
  - Added `WifiManager.MulticastLock` SSDP `M-SEARCH` with 1s retransmit, `isBelkinWeMoDescriptor` filtering, and `Semaphore(24)` unicast `/24` port-sweep fallback.
  - Added SOAP quirk handling (`<BinaryState>Error</BinaryState>` treated as idempotent success, `Connection: close`, dimmer `<brightness>`, `GetMetaInfo`, `GetSignalStrength`, `ChangeFriendlyName`, `ConfigureNightMode`).
- **Phase 2 — Self-Healing Repository, RTOS Concurrency & Real LAN vs. Demo Showroom Mode ([`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoRulesAndMoreScreens.kt`](app/src/main/java/com/example/ui/screens/WeMoRulesAndMoreScreens.kt))**:
  - Added `Semaphore(3)` LAN concurrency cap (preventing `WLS040` / `WLS0403` RTOS firmware lockups) and per-device `Mutex` serialization.
  - Implemented optimistic UI updates with 2-stage self-healing reconnect (9-port sweep → serial-matched SSDP scan) and accurate `OFFLINE` state revert in Real LAN Mode.
  - Added explicit **Demo Showroom Mode** toggle in the `More` tab while defaulting physical Android hardware to clean **Real LAN Mode**.

---

### [2026-11-07 16:17–17:22 PST] — `AGENTS.md`, `wemi-light-control` Skill & Belkin WeMo Wire Protocol Reference (`503b2d5`..`0027337`, `v6.0`)
- Created [`AGENTS.md`](AGENTS.md), [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md), and [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md) documenting `pywemo` and Home Assistant UPnP/SSDP/SOAP/GENA wire specifications, RTOS concurrency limits, and `WiFiSetup1` provisioning payloads.

---

### [2026-11-07 15:53 PST] — Persistent Multi-Home Wi-Fi Network Profiles (`1d0a993`, `d1620ab`, `v5.0`)
- Added `WeMoHomeNetworkEntity` (`wemo_home_networks`) in [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt) and [`WeMoWifiHomeBar.kt`](app/src/main/java/com/example/ui/components/WeMoWifiHomeBar.kt) so users can maintain separate switch lists and subnets across multiple locations (`Main Home`, `Lake Cabin`, `Studio Office`) with 1-tap `Reconnect`.

---

### [2026-11-07 15:38 PST] — Real-Time Device Latency Tracking & 4-Bar Connection Quality Badges (`b0abf66`, `v4.0`)
- Added `WeMoConnectionQuality` (`EXCELLENT` <= 28ms, `GOOD` <= 65ms, `SLOW` > 65ms, `OFFLINE`), animated pulsing connection dots, and 4-bar wireless signal meters (`WeMoConnectionStatusBadge` and `WeMoSignalBarsIndicator`) in [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt).

---

### [2026-11-07 15:09 PST] — 10-Foot Android TV Remote (D-Pad) Dashboard (`bdf1bfc`, `a9e2429`, `v3.0–v4.0`)
- Implemented [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt) (`WeMoTvDashboardScreen`) with automatic `UI_MODE_TYPE_TELEVISION` detection, D-Pad navigation, `MediaPlayPause` room toggle shortcut, 1-click TV lighting scenes (`Movie Night`, `Bright Room`, `Nightlight`, `All Off`), and an on-screen virtual remote pad.

---

### [2026-11-07 14:50–14:56 PST] — Room Categorization, Collapsible Room Group Cards & Master Room Power/Dimmer Controls (`f7186a6`, `1415109`, `v2.0`)
- Added [`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt) (`WeMoRoomFilterBar` and `WeMoRoomGroupHeaderCard`) supporting `Grouped by Room` vs. `Flat List` views, room filter chips, 1-tap Master Room Power Ring toggling, and the expandable Room Master Brightness Drawer.

---

### [2026-11-07 13:55–14:28 PST] — Interactive SSDP Discovery Scanner Modal, Pull-to-Refresh & Play Store Assets (`25be5e1`, `2687233`, `c243120`, `v1.5`)
- Added [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt) for interactive SSDP `M-SEARCH` scanning, pull-to-refresh on the main device list in [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), and Google Play Store listing graphics under `play_store_assets/`.

---

### [2026-11-07 13:18–13:33 PST] — Initial WeMi Light Controller, Skeuomorphic UI & Room Persistence (`0e607e8`..`b6c1cf6`, `v1.0`)
- Initial implementation of **WeMi Light Control** with Belkin WeMo skeuomorphic Compose UI, `Devices` / `Rules` / `More` tabs, inline dimmer drawers, SOAP packet log inspector, and local Room database persistence.

---

## 3. Current System Architecture Overview

```mermaid
flowchart TD
    subgraph UI["Jetpack Compose UI (Mobile, Tablet & Android TV)"]
        Main["MainActivity / WeMiSwitchApp\n(Auto Setup AP Banner + TV Mode)"]
        HomeBar["WeMoWifiHomeBar\n(Multi-Home Switcher + 1-Tap Reconnect)"]
        Scanner["WeMoSsdpScannerModal\n(Live UDP + Unicast Fallback + Adopt All)"]
        Provision["ProvisionWeMoApDialog\n(10.22.22.1 Zero-Cloud Wi-Fi Setup)"]
        Rooms["WeMoRoomGroupCard & WeMoDeviceRow\n(3-State Category Icons, Power Ring, Dimmer, Night Mode)"]
        RulesUI["WeMoRulesScreen & WeMoMoreScreen\n(Demo Showroom Toggle, Master Controls, SOAP Log)"]
    end

    subgraph Domain["State, Self-Healing & Scheduling (com.example.data)"]
        VM["WeMoViewModel"]
        Repo["WeMoRepository\n(Semaphore(3) + Per-Device Mutex + 2-Stage Reconnect)"]
        Sched["WeMoRuleScheduler\n(Live Auto-Off Countdowns & Daily Schedules)"]
        DB[("WeMoDatabase (Room v5)\nwemo_devices / wemo_home_networks / wemo_rules")]
    end

    subgraph Net["pywemo-Grade Local Protocol Engine (com.example.network)"]
        Client["WeMoNetworkClient\n(9-Port Prober, SSDP MulticastLock, SOAP Control)"]
        Gena["WeMoGenaSubscriptionManager\n(SUBSCRIBE / Embedded HTTP NOTIFY Server)"]
        NetMon["WeMoNetworkMonitor\n(Zero-Permission /24 Subnet & Wi-Fi Socket Binding)"]
        Prov["WeMoProvisionerClient\n(WiFiSetup1 GetApList & OpenSSL AES-128-CBC Methods 1, 2 & 3)"]
    end

    Main --> VM
    HomeBar & Scanner & Provision & Rooms & RulesUI --> VM
    VM --> Repo
    NetMon -->|Live LanNetworkState| VM
    Repo <--> DB
    Repo <--> Sched
    Repo --> Client & Prov
    Repo <--> Gena
```
