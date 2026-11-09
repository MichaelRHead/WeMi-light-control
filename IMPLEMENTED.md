# WeMi Light Control — Implemented Features & Change Log (`IMPLEMENTED.md`)

**Repository**: `MichaelRHead/WeMi-light-control` (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`)  
**Current Version**: `v19.0` (`versionCode = 19`)  
**Target Platforms**: Android Phones, Tablets, and Android TV (`UI_MODE_TYPE_TELEVISION` 10-foot D-Pad interface)

Related documentation:
- [`UX_DESIGN.md`](UX_DESIGN.md) — Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows *(Owned Exclusively by the Google AI Studio Agent)*
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`USER_GUIDE.md`](USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Human-Readable Code Modularization, Implementation Validation & End-to-End (E2E) Testing Plan
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
- [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) — UX Design, Behavioral Modeling & CUJ Skill *(Owned Exclusively by the Google AI Studio Agent)*
- [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) — On-Device Screenshot & Recorded Telemetry File Debugging Skill *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
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
| **2026-11-09 10:46 PST** | `v19.0` (`HEAD`) | **Unified 9-Port Self-Healing (`executeWithPortHealing`) & Wi-Fi Socket Binding Fix, UI/Data Modularization (`WeMoProvisionDebugPanel`, `sanitizeAndResolveInitialLanHome`), and Expanded Full-Stack Hardware CUJ E2E Test Suite (`WeMoHardwareCujEndToEndTest`, `26/26` Passing)** | [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt), [`WeMoProvisionDebugPanel.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionDebugPanel.kt), [`WeMoHardwareCujEndToEndTest.kt`](app/src/test/java/com/example/WeMoHardwareCujEndToEndTest.kt), [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt) |
| **2026-11-09 10:36 PST** | `v19.0` (`ba78cbf`) | **Antigravity Debugging & Support Agent-Owned Architecture Spec (`DEBUGGING_AND_SUPPORT.md`), User Guide (`docs/user-guide/telemetry-and-troubleshooting.md`), Skill (`wemi-device-telemetry-debug`) & Play Store `v19.0` (`versionCode = 19`) Release** | [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`AGENTS.md`](AGENTS.md), [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/README.md`](docs/README.md), [`app/build.gradle.kts`](app/build.gradle.kts) |
| **2026-11-09 09:18 PST** | `v18.0` (`a866b8c`) | **AI Studio Agent-Owned Android UX Design Guidelines (`UX_DESIGN.md`), Behavioral Modeling (`UX_DESIGN_AND_EXPERIENCE.md`), Skill (`wemi-ux-design-and-experience`) & Single-Source Root `ALL_CAPS.md` Documentation Layout** | [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md), [`docs/README.md`](docs/README.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md), [`AGENTS.md`](AGENTS.md) |
| **2026-11-09 00:05 PST** | `v17.0` (`5c081f3`..`510e692`) | **Full-Stack E2E CUJ Test (`Reset` → `SSDP UDP Discovery` → `Adopt Switch & Dimmer` → `Toggle Switch` → `Toggle & Scale Dimmer Brightness`), Loopback UDP `M-SEARCH` Responder, Top-First Dimmer Drawer & Pass-Through Status Toast (`25/25` Passing)** | [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt), [`WeMoSsdpDiscoveryEngine.kt`](app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoDeviceDiagnosticsDrawer.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceDiagnosticsDrawer.kt), [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt) |
| **2026-11-09 00:02 PST** | `v17.0` (`a8f7f38`) | **Expanded Room Inference (`"bed"`, `"stair"`, `"girls"`/`"kids"`), `"Unassigned"` Fallback Instead of `"Living Room"`, and Automatic Migration of Switches Stuck in `"Living Room"`** | [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoDemoFixtures.kt`](app/src/main/java/com/example/data/WeMoDemoFixtures.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt), [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt) |
| **2026-11-08 23:42 PST** | `v17.0` (`db7a65f`) | **Fresh-Device `10.22.22.1` `WiFiSetup1` Provisioning Bug Fixes, On-Screen Screenshot Debug Panel (`WeMoProvisionDebugPanel`), Telemetry File Exporter (`WeMoTelemetryFileExporter`) & `wemi-device-telemetry-debug` Agent Skill** | [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt), [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt), [`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt), [`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoGenaProvisioningAndRulesTest.kt`](app/src/test/java/com/example/WeMoGenaProvisioningAndRulesTest.kt), [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt) |
| **2026-11-08 22:30 PST** | `v16.0` (`d52b359`) | **Expanded `v13.0–v16.0` Unit, Repository & E2E Compose UI Test Coverage (`22/22` Passing) & Synchronous Modal Dismissal across All CRUD Dialogs** | [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt), [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt) |
| **2026-11-08 21:57 PST** | `v16.0` (`754e9fb`) | **Compact Wi-Fi Home Selector & SSDP Banner, Filter Out Belkin WeMo Soft-AP SSIDs (`Wemo.LS3W.xxx`, `WeMo.Switch.xxx`) from Naming Home, and One-Tap Reset for Homes, Switches & Rooms** | [`WeMoWifiHomeBar.kt`](app/src/main/java/com/example/ui/components/WeMoWifiHomeBar.kt), [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt), [`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt), [`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt) |
| **2026-11-08 18:15–20:28 PST** | `v13.0–v15.0` (`dcba5a0`, `f297cb6`, `853bd53`) | **Uniform Room Group Card Height, Dark-Gray IEC Power-Off & Disconnected Icons, Full-Width Reactive Switch Name & Redundant Room Chip Removal** | [`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt), [`WeMoDeviceStatusBadges.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceStatusBadges.kt), [`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoTvCardsAndScenes.kt`](app/src/main/java/com/example/ui/components/WeMoTvCardsAndScenes.kt) |
| **2026-11-08 16:12 PST** | `v12.0` (`77bf3a6`..`df22cc8`) | **Phase M4 Test Suite Modularization, `FakeWeMoUpnpServer` Extraction, Loopback Socket/DB/Dialog Fixes & E2E Compose UI Tests (`20/20` Passing)** | [`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt), [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt), [`WeMoGenaProvisioningAndRulesTest.kt`](app/src/test/java/com/example/WeMoGenaProvisioningAndRulesTest.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt), [`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoDatabase.kt`](app/src/main/java/com/example/data/WeMoDatabase.kt) |
| **2026-11-08 15:00 PST** | `v12.0` (`cce6bf4`) | **Phase M3 UI Layer Modularization: Illustrations, Dialogs, DeviceRow, TvScreen, Screens & `MainActivity`** | [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDevicesTabScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoDevicesTabScreen.kt), [`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt) |
| **2026-11-08 14:48 PST** | `v12.0` (`f073177`) | **Phase M2 Network Layer Modularization: `WeMoXmlAndSkuParser`, `WeMoSsdpDiscoveryEngine` & `WeMoPasswordCrypto`** | [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt), [`WeMoSsdpDiscoveryEngine.kt`](app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt), [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoPasswordCrypto.kt`](app/src/main/java/com/example/network/WeMoPasswordCrypto.kt) |
| **2026-11-08 14:30 PST** | `v12.0` (`bace660`) | **Phase M1 Data Layer Modularization: `WeMoDemoFixtures`, `WeMoDiscoveryCoordinator`, `WeMoProvisioningCoordinator` & Unified `executeOptimisticDeviceCommand`** | [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoProvisioningCoordinator.kt`](app/src/main/java/com/example/data/WeMoProvisioningCoordinator.kt), [`WeMoDemoFixtures.kt`](app/src/main/java/com/example/data/WeMoDemoFixtures.kt) |
| **2026-11-08 07:42 PST** | `v12.0` (`ac7cd7b`) | **Expanded Semantic Category Keywords (`garland`, `wreath`, `vent`, `pendant`), Empty State Onboarding Card Layout & E2E Test** | [`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), [`ExampleRobolectricTest.kt`](app/src/test/java/com/example/ExampleRobolectricTest.kt) |
| **2026-11-08 07:41 PST** | `v12.0` (`2f97020`) | **Multi-Agent Git Sync & Anti-Stomp Integration Skill (`multi-agent-git-sync`)** | [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md), [`AGENTS.md`](AGENTS.md), [`IMPLEMENTED.md`](IMPLEMENTED.md) |
| **2026-11-08 07:35 PST** | `v12.0` (`c5ca7c4`) | **Architecture & Protocol Review Sign-Off (Section 7 of Modularization Plan)** | [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) |
| **2026-11-08 07:25 PST** | `v12.0` (`2cfe70d`..`de14b85`) | **Reverse-Chronological Change Log (`IMPLEMENTED.md`), `UPCOMING_FEATURES.md` & `wemi-changelog-and-docs` Skill** | [`IMPLEMENTED.md`](IMPLEMENTED.md), [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md), [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) |
| **2026-11-08 07:20–07:28 PST** | `v12.0` (`627ab38`, `a308d24`) | **Code Modularization & E2E Testing Plan + `android-code-modularization` & `android-e2e-testing` Skills** | [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md), [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md), [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) |
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

### [2026-11-09 10:46 PST] — Unified 9-Port Self-Healing (`executeWithPortHealing`) & Wi-Fi Socket Binding Fix, UI/Data Modularization (`WeMoProvisionDebugPanel`, `sanitizeAndResolveInitialLanHome`), and Expanded Full-Stack Hardware CUJ E2E Test Suite (`WeMoHardwareCujEndToEndTest`, `26/26` Passing) (`v19.0`)
- **Unified 9-Port Self-Healing (`executeWithPortHealing`) & Wi-Fi Socket Binding Bug Fix ([`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt))**:
  - Consolidated duplicated 9-port `probeWemoPort` + candidate-port fallback loops across `setBinaryState`, `getBinaryState`, `changeFriendlyName`, and `configureNightMode` into a single private suspending helper `executeWithPortHealing(device, executeOnPort)`.
  - Fixed a bug in the candidate-port fallback loop where bare `Socket()` was previously used instead of `wifiSocketFactory.createSocket()`, ensuring multi-network Android devices (cellular + Wi-Fi active simultaneously) remain bound to the Wi-Fi interface during port recovery.
  - Added `registerLoopbackCandidatePort` / `unregisterLoopbackCandidatePort` / `currentLoopbackCandidatePorts` on `WeMoNetworkClient.Companion` (integrated into `probeWemoPort` and `WeMoProvisionerClient.probeSetupDevice`) and `WeMoTelemetryFileExporter.latestTelemetryFile(context)` so full-stack Compose UI E2E tests can exercise 9-port self-healing and `10.22.22.1` setup AP provisioning against ephemeral loopback `FakeWeMoUpnpServer` ports.
- **UI & Data Layer Modularization ([`WeMoProvisionDebugPanel.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionDebugPanel.kt), [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt))**:
  - Extracted the 184-line `WeMoProvisionDebugPanel` screenshot diagnostic & telemetry file export composable out of `WeMoProvisionApDialog.kt` (`675` → `527` lines) into its own dedicated file `WeMoProvisionDebugPanel.kt`.
  - Extracted the 70-line Real LAN startup home/device soft-AP SSID sanitization (`isWemoDeviceSetupSsid`) and legacy `"Living Room"` room migration block out of `WeMoRepository.ensureSeeded()` into `WeMoDiscoveryCoordinator.sanitizeAndResolveInitialLanHome(localIp)` (`WeMoRepository.kt` `898` → `832` lines).
- **Test Suite Modularization & Expanded Full-Stack Hardware CUJ E2E Coverage ([`WeMoHardwareCujEndToEndTest.kt`](app/src/test/java/com/example/WeMoHardwareCujEndToEndTest.kt), [`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt))**:
  - Split `WeMoEndToEndUiTest.kt` (`651` lines) into `WeMoEndToEndUiTest.kt` (`353` lines, 4 core UI/CRUD/Diagnostics/Reset tests) and `WeMoHardwareCujEndToEndTest.kt` (`588` lines, 3 full-stack multi-step loopback hardware CUJ E2E tests).
  - Enhanced `FakeWeMoUpnpServer` with `registerAsCandidatePort` / `setCandidatePortDiscoverable` and `lastGenaCallbackPort` capture from `SUBSCRIBE` `CALLBACK:` headers.
  - Added two new full-stack Compose UI + Robolectric + `FakeWeMoUpnpServer` E2E tests (`26/26` total tests passing):
    1. `verify E2E Disconnected switch 9-port self-healing reconnect ring, GENA push notification UI update, and Live SOAP Log export`: Verifies a switch stored with a stale port (`49159`) recovers via the Disconnected ring (`reconnect_button_${id}`) onto `FakeWeMoUpnpServer.port`, toggles ON via `power_button_${id}`, transitions automatically from `ON` to `OFF` in Compose UI when `FakeWeMoUpnpServer.pushGenaNotify` pushes `<BinaryState>0</BinaryState>` to `WeMoGenaSubscriptionManager`, and exports `wemi-telemetry-latest.txt` via `export_telemetry_file_button` on the `More` tab.
    2. `verify E2E zero-cloud 10_22_22_1 Setup AP hardware scan, AES Method 2 provisioning, and screenshot telemetry export over loopback server`: Verifies opening `ProvisionWeMoApDialog`, scanning a live loopback `FakeWeMoUpnpServer` (`WLS0403` RTOS), selecting `HeadFamily-2.4G (Ch 6 • 91%)`, provisioning `Stairway 3-Way Switch` with `"Keep Dialog Open on Success"` enabled (`ChangeFriendlyName`, double `ConnectHomeNetwork`, `GetNetworkStatus` polls, `CloseSetup`, `SetSetupDoneStatus`), exporting screenshot telemetry via `copy_provision_debug_button`, and verifying the new switch appears in the Compose `Devices` list under `Hallway & Landing`.

---

### [2026-11-09 10:36 PST] — Antigravity Debugging & Support Agent-Owned Architecture Spec (`DEBUGGING_AND_SUPPORT.md`), User Guide (`docs/user-guide/telemetry-and-troubleshooting.md`), Skill (`wemi-device-telemetry-debug`) & Play Store `v19.0` Release (`v18.0–v19.0`)
- **Exclusive Antigravity Debugging & Support Agent Ownership (`DEBUGGING_AND_SUPPORT.md`, `wemi-device-telemetry-debug`, `telemetry-and-troubleshooting.md`) ([`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`AGENTS.md`](AGENTS.md), [`docs/README.md`](docs/README.md), [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md), [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md))**:
  - Created [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) at the repository root and established explicit governance that [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), and [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md) are **owned exclusively by the Antigravity Debugging & Support Agent** (read-only for all other agents).
  - Documented the 5 non-negotiable debugging & `10.22.22.1` provisioning invariants (`CloseSetup` skipped on `netStatus == 0` or `2`, non-dismissing `ProvisionWeMoApDialog` on failure or `keep_provision_dialog_open_chip`, zero plaintext password logging, diagnostic `Modifier.testTag` preservation, and OkHttp `Content-Length` + `8500ms` site-survey timeout).
- **Dedicated Human Troubleshooting & Telemetry Export Guide ([`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md), [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/README.md`](docs/README.md), [`docs/user-guide/fresh-device-setup.md`](docs/user-guide/fresh-device-setup.md), [`docs/user-guide/more-and-tv-mode.md`](docs/user-guide/more-and-tv-mode.md))**:
  - Added Feature Guide #9 (`docs/user-guide/telemetry-and-troubleshooting.md`) explaining both workflows for sharing on-device diagnostic telemetry:
    1. **Method A (On-Screen Screenshots)**: How to capture screenshots of **`SETUP DEBUG DETAILS (SCREENSHOT)`** inside `ProvisionWeMoApDialog` (including automatic `kept_open_for_retry` hold on failure and the **`Keep Dialog Open on Success`** chip), the **`UPnP / SOAP Packet Log`** on the `More` tab, and the **Inline Switch Diagnostics Drawer (`ⓘ`)**.
    2. **Method B (Recorded Telemetry File `wemi-telemetry-latest.txt`)**: How to export or copy the complete 3-section diagnostic report via **`Save/Share`**, **`Copy`**, **`Save/Share Log`**, or USB `adb`.
    3. **Quick Reference Decoder & Privacy Guarantee**: Explains every `[NET]`, `[PROBE]`, `[FLAGS]`, `[ID]`, `[SCAN]`, `[TARGET AP]`, and `[STEP 1..5]` line and confirms that plaintext Wi-Fi passwords are never displayed or logged.

---

### [2026-11-09 09:18 PST] — AI Studio Agent-Owned Android UX Design Guidelines (`UX_DESIGN.md`), Behavioral Modeling (`UX_DESIGN_AND_EXPERIENCE.md`), Skill (`wemi-ux-design-and-experience`) & Single-Source Root `ALL_CAPS.md` Documentation Layout (`v18.0`)
- **Exclusive AI Studio Agent UX Guidelines (`UX_DESIGN.md`), Behavioral Architecture (`UX_DESIGN_AND_EXPERIENCE.md`) & Skill ([`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md), [`AGENTS.md`](AGENTS.md), [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md))**:
  - Created `UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`, and the companion `.agents/skills/wemi-ux-design-and-experience/SKILL.md` skill, establishing an explicit governance rule that **only the Google AI Studio Agent may modify these files** while all other agents must read and respect them as the canonical UX specification.
  - Documented navigation patterns (3-tab `NavigationBar`, `Grouped by Room` vs. `Flat List`, inline `WeMoExpandedDrawer` vs. modal dialogs, `BackHandler` priority stack, and 10-foot Android TV `UI_MODE_TYPE_TELEVISION` D-Pad ergonomics), accessibility standards for smart home controls (`48dp`–`64dp` touch targets, `TalkBack` `Role.Switch`/`Role.Button` semantics, 3-state color/icon/border redundancy, and `AutoResizingDeviceNameText`), and end-to-end user interaction flows.
- **Consolidated Single-Source Documentation Layout (`ALL_CAPS.md` at Root, Human User Guides in `docs/user-guide/`) ([`docs/README.md`](docs/README.md), [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md), [`AGENTS.md`](AGENTS.md), [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md), [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md))**:
  - Eliminated duplicate `ALL_CAPS.md` mirrors from `docs/` (`docs/IMPLEMENTED.md`, `docs/UPCOMING_FEATURES.md`, `docs/USER_GUIDE.md`, `docs/UX_DESIGN.md`, `docs/UX_DESIGN_AND_EXPERIENCE.md`) and moved `CODE_QUALITY_AND_MODULARIZATION_PLAN.md` to the repository root (`/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`).
  - Added [`docs/README.md`](docs/README.md) and updated all agent skills so all agents know that top-level `ALL_CAPS.md` files live exclusively at the repository root (`/`) while `docs/` is dedicated exclusively to the human-focused user guides under `docs/user-guide/`.

---

### [2026-11-09 00:05 PST] — Full-Stack E2E CUJ Test (`Reset` → `SSDP UDP Discovery` → `Adopt Switch & Dimmer` → `Toggle Switch` → `Toggle & Scale Dimmer Brightness`), Loopback UDP `M-SEARCH` Responder, Top-First Dimmer Drawer & Pass-Through Status Toast (`25/25` Passing) (`v17.0`)
- **Loopback UDP SSDP `M-SEARCH` Hardware Simulator & Multi-Device Loopback Discovery ([`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt), [`WeMoSsdpDiscoveryEngine.kt`](app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt), [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt))**:
  - Upgraded `FakeWeMoUpnpServer` with an embedded loopback UDP `DatagramSocket(0, 127.0.0.1)` SSDP responder (`enableSsdpResponder` / `setSsdpDiscoverable(Boolean)`) that parses incoming `M-SEARCH` datagrams and replies with `HTTP/1.1 200 OK` (`LOCATION: http://127.0.0.1:<port>/setup.xml`, `ST`, `USN`), plus `<brightness>` in `/setup.xml`.
  - Added `registerLoopbackSsdpPort` / `unregisterLoopbackSsdpPort` in `WeMoSsdpDiscoveryEngine` & `WeMoNetworkClient` so `performSsdpDiscoveryScan` mirrors UDP `M-SEARCH` bursts to registered loopback `FakeWeMoUpnpServer` ports and deduplicates multiple loopback devices by `(ipAddress, port)` / `serialNumber`.
  - Serialized `adoptDiscoveredDevice` in `WeMoDiscoveryCoordinator` with `adoptMutex` and refined device matching so multiple loopback hardware simulators on `127.0.0.1` with distinct ports/serials never overwrite each other.
- **Full-Stack E2E CUJ Test for Reset, Discovery, Adoption, Switch Toggling & Dimmer Scaling ([`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt))**:
  - Added `verify E2E Reset to SSDP Discovery, Adopt Switch and Dimmer, Toggle Switch, and Scale Dimmer Brightness CUJ` exercising the complete user journey against two live `FakeWeMoUpnpServer` instances (`Kitchen Island Switch` `WLS0403` and `Kitchen Under-Cabinet Dimmer` `F7C059`):
    1. Resetting all homes, switches, and rooms via `reset_lan_data_chip` -> `confirm_reset_empty_button` (`My Home (0)` + `EmptyDevicesState`).
    2. Discovering and auto-adopting `Kitchen Island Switch` via `empty_state_scan_ssdp_button` over UDP `M-SEARCH` + `/setup.xml` + `GetBinaryState` + `GetMetaInfo` (while verifying soft-AP SSID `Wemo.LS3W.220` does not rename `My Home`).
    3. Bringing `Kitchen Under-Cabinet Dimmer` online (`35%` standby), scanning via `open_ssdp_scanner_button`, and adopting it via `adopt_ssdp_device_Kitchen Under-Cabinet Dimmer` (`My Home (2)`).
    4. Toggling `Kitchen Island Switch` `OFF` -> `ON` -> `OFF` and verifying `SetBinaryState` on `switchServer`.
    5. Toggling `Kitchen Under-Cabinet Dimmer` `OFF • 35%` -> `ON • 35%`, expanding its drawer, scaling brightness via preset chips (`75%`, `21%`), continuous `Slider` touch gesture (`~88%`), hardware `Night Mode (20%)` (`ConfigureNightMode` SOAP), `Kitchen` Room Master Brightness slider (`room_dimmer_slider_Kitchen`), and 10-foot Android TV remote mode (`+10%`, `-10%`, `Movie Mode (20%)`), then toggling `OFF • 20%`.
- **UI & Test Isolation Refinements ([`WeMoDeviceDiagnosticsDrawer.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceDiagnosticsDrawer.kt), [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt), [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDatabase.kt`](app/src/main/java/com/example/data/WeMoDatabase.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt))**:
  - Placed the dimmer brightness slider and wrapping `FlowRow` preset chips at the top of `WeMoExpandedDrawer` so dimmer controls are immediately visible on compact screens.
  - Unified `WeMoSsdpScannerModal` into a single `LazyColumn` (`ssdp_discovered_list`) and made `status_feedback_banner` in `MainActivity.kt` pointer pass-through (`Box`) so transient status toasts never intercept touches on underlying controls.
  - Updated `WeMoDatabase.getDatabase` to create a fresh in-memory database per Robolectric test and guarded `_activeNetworkKey` initialization in `WeMoViewModel.init` (`25/25` tests passing).

---

### [2026-11-09 00:02 PST] — Expanded Room Inference (`"bed"`, `"stair"`, `"girls"`/`"kids"`), `"Unassigned"` Fallback Instead of `"Living Room"`, and Automatic Migration of Switches Stuck in `"Living Room"` (`a8f7f38`, `v17.0`)
- **Expanded `inferRoomFromName` Keyword Matching & `"Unassigned"` Fallback ([`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt), [`WeMoDemoFixtures.kt`](app/src/main/java/com/example/data/WeMoDemoFixtures.kt), [`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt))**:
  - Previously, `inferRoomFromName` only checked `"bedroom"` (missing standalone `"bed"` or `"bedside"`/`"nightstand"` in names like `"Shprese Bed Lamp"` and `"Mike Bed Light"`), only checked plural `"stairs"` (missing `"stair"` / `"stairway"` / `"staircase"` in `"Stairway Light"`), had no match for `"girls"` / `"boys"` / `"kids"` (`"Girls Light"`), used un-bounded `"den"` / `"hall"` substrings, and defaulted every unrecognized switch name to `else -> "Living Room"`.
  - Expanded `inferRoomFromName` to match standalone `"bed"`, `"bedside"`, `"nightstand"`, `"bunk"`, `"primary"`, `"girls"`/`"girl"`, `"boys"`/`"boy"`, `"kids"`/`"kid"` → `"Bedroom"`; `"stair"` (covering `"stair"`, `"stairs"`, `"stairway"`, `"staircase"`, `"upstairs"`, `"downstairs"`), `"mudroom"`, `"corridor"`, and word-bounded `"hall"` → `"Hallway & Landing"`; word-bounded `"den"` → `"Living Room"`; and changed the unrecognized fallback from `"Living Room"` to `"Unassigned"`.
  - Updated `MainActivity` so `"Unassigned"` always sorts after named rooms in both the room filter chip bar and the grouped room cards list.
- **Automatic Startup & SSDP Migration for Real LAN Switches Stuck in `"Living Room"` ([`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt))**:
  - Added automatic room re-inference in `WeMoRepository.ensureSeeded()`, `WeMoDiscoveryCoordinator.executeSsdpScan()`, and `WeMoDiscoveryCoordinator.adoptDiscoveredDevice()` when running in Real LAN Mode (`!isDemoShowroomMode`): any existing switch whose stored `roomName` is `"Living Room"` (despite `inferRoomFromName(name)` not matching `"Living Room"`), `"Unassigned"`, or blank is automatically migrated to its newly inferred room while preserving custom user-assigned room names.

---

### [2026-11-08 23:42 PST] — Fresh-Device `10.22.22.1` `WiFiSetup1` Provisioning Bug Fixes, On-Screen Screenshot Debug Panel (`WeMoProvisionDebugPanel`), Telemetry File Exporter (`WeMoTelemetryFileExporter`) & `wemi-device-telemetry-debug` Agent Skill (`db7a65f`, `v17.0`)
- **`wemi-device-telemetry-debug` Agent Skill & `WeMoTelemetryFileExporter` ([`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt), [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt), [`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt))**:
  - Created the `wemi-device-telemetry-debug` skill (in `.agents/skills/wemi-device-telemetry-debug/SKILL.md` and registered in the persistent skill registry) documenting how to capture, parse, and diagnose on-device telemetry via either **screenshots** (`SETUP DEBUG DETAILS (SCREENSHOT)`, `UPnP / SOAP Packet Log`, `Inline Device Diagnostics Drawer`) or **recorded telemetry files** (`wemi-telemetry-latest.txt`).
  - Added `WeMoTelemetryFileExporter` (`buildFullTelemetryReport`, `saveTelemetryReportToFile`, `copyAndShareTelemetryReport`) and wired **`Save/Share`** (`share_provision_debug_button`) inside `ProvisionWeMoApDialog` and **`Save/Share Log`** (`export_telemetry_file_button`) on the `More` tab so users can export `wemi-telemetry-latest.txt` with one tap.
- **5 Root-Cause Fixes in `10.22.22.1` `WiFiSetup1` Provisioning ([`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt), [`WeMoNetworkMonitor.kt`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt), [`WeMoProvisioningCoordinator.kt`](app/src/main/java/com/example/data/WeMoProvisioningCoordinator.kt))**:
  1. **Replaced Raw Socket `BufferedReader.readText()` with `OkHttpClient` Framing in `probeSetupDevice`**: Previously, `probeSetupDevice` opened a raw TCP `Socket` with `soTimeout = 1000ms` and called `readText()`, which waits for TCP `FIN` (EOF). Embedded Belkin WeMo HTTP servers often keep the socket open for several seconds after sending `Content-Length` bytes, causing `readText()` to time out, discard `/setup.xml`, return `descriptor = null`, and fall back to encrypting the Wi-Fi password with hardcoded dummy MAC `"EC1A59001122"` and Serial `"221517K01018AB"`. `probeSetupDevice` now uses `httpClient.newCall(req).execute()` (honoring `Content-Length` immediately) and adds a `basicevent1#GetMacAddr` / `GetSerialNo` fallback.
  2. **Extended `httpClient` Read Timeout (`2200ms` → `8500ms`) for 11-Channel 2.4 GHz Site Surveys**: Physical WeMo switches take `3–6s` to scan 2.4 GHz channels 1–11 during `WiFiSetup1#GetApList` / `GetNetworkList`.
  3. **Fixed False Success & Premature `CloseSetup` on `NetworkStatus == 0`**: Previously, `overallSuccess = httpSuccess && netStatus != 2` falsely treated `NetworkStatus = 0` (still connecting / failed to join router) as `success = true`, which immediately closed `ProvisionWeMoApDialog`, inserted a phantom device row, and called `CloseSetup()` (shutting down the switch's `10.22.22.1` setup AP before the user could retry). Now `overallSuccess = httpSuccess && (netStatus == 1 || netStatus == 3)`, and `CloseSetup` is skipped whenever `netStatus == 0` or `2` (`kept_open_for_retry`).
  4. **Extended `GetNetworkStatus` Poll Window (`7.5s` → `~18–20s`) & Preserved Polling Across Transient Nulls**: Stopped breaking out of the poll loop on the first transient `null` when `netStatus == 0` and increased default polls from `10` to `18` (`~18–20s`, matching `pywemo`'s `20s` minimum wait while the switch hops to the home router's 2.4 GHz channel).
  5. **Auto-Matched `GetApList` Channel/Auth/Encrypt & Cached Unvalidated `NetworkCallback` Wi-Fi Handle**: `provisionDeviceToHomeWifi` now automatically looks up manually typed SSIDs in `knownScannedAps` so the switch's reported 2.4 GHz `channel`, `authMode`, and `encryptionType` are always sent, and `WeMoNetworkMonitor` caches `lastCallbackWifiNetwork` so `createWifiBoundSocketFactory` reliably binds to unvalidated `10.22.22.1` Setup APs on Android 11–16 even when mobile data is active.
- **Screenshot-Ready On-Screen Setup Debug Panel (`WeMoProvisionDebugPanel`) & All 6 `pywemo` Crypto Permutations ([`WeMoSetupDiagnostics.kt`](app/src/main/java/com/example/network/WeMoSetupDiagnostics.kt), [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt))**:
  - Added `WeMoSetupDebugSnapshot` and `WeMoProvisionDebugPanel` (`testTag("provision_debug_panel")`, `testTag("provision_debug_text")`) directly inside `ProvisionWeMoApDialog` in a high-contrast dark terminal card (`#0F172A` background, `#86EFAC` monospace text) designed for one-shot phone screenshots.
  - Displays live `[NET]` (`subnet`, `ip`, `iface`, `setupAp`, `wifiBound`), `[PROBE]` (`port`, `xmlPath`, per-port probe trace), `[XML]` (`model`, `name`, `fw`), `[FLAGS]` (`rtos`, `iot`, `binaryOption`, `new_algo`, `autoMethod`), `[ID]` (`mac`, `serial`, `src`, raw `metaInfo`), `[SCAN]` (`GetApList` count & summary), `[TARGET AP]` (`ssid`, `ch`, `auth/enc`), and live `[STEP 1..5]` trace lines (`ConnectHomeNetwork` `PairingStatus`, `GetNetworkStatus` polls, and `CloseSetup` status) — without ever printing the plaintext Wi-Fi password.
  - Added a **"Keep Dialog Open on Success"** toggle chip (`keep_provision_dialog_open_chip`), a **"Copy"** button (`copy_provision_debug_button`), editable **2.4G Ch / Auth Mode / Encrypt** fields, and **Password Length Suffix (`xxyy` hex)** override chips (`provision_len_auto_chip`, `provision_len_true_chip`, `provision_len_false_chip`) so all 6 `pywemo` encryption combinations (`Method 1/2/3` × `lenSuffix true/false`) can be tested directly from the UI.

---

### [2026-11-08 22:30 PST] — Expanded `v13.0–v16.0` Unit, Repository & E2E Compose UI Test Coverage (`22/22` Passing) (`v16.0`)
- **Centralized Soft-AP SSID Filtering & Expanded Protocol/Repository Tests ([`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt), [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt))**:
  - Centralized `isWemoDeviceSetupSsid(ssid: String)` in `WeMoXmlAndSkuParser` so both `parseMetaInfoSoapResponse` and `WeMoDiscoveryCoordinator` share one canonical implementation for detecting Belkin factory soft-AP SSIDs (`WeMo.Switch.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`, `Wemo.LS3W.xxx`, `WeMo_Insight`, `Belkin.Setup.xxx`).
  - Expanded `WeMoSkuAndCryptoTest` with direct `isWemoDeviceSetupSsid` and `parseMetaInfoSoapResponse` soft-AP vs. real home router SSID tests.
  - Added `verify ensureSeeded sanitizes polluted WeMo soft-AP home names and SSIDs and adoptDiscoveredDevice preserves My Home` in `WeMoSoapAndSelfHealingTest`, verifying startup database sanitization (`ensureSeeded`), `adoptDiscoveredDevice` with a loopback `FakeWeMoUpnpServer` returning `Wemo.LS3W.220` in `GetMetaInfo`, and `resetLanHomesSwitchesAndRooms()`.
- **Expanded Compose E2E UI Tests & Synchronous Dialog Dismissal ([`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt))**:
  - Updated `WeMoViewModel.addWifiHomeNetwork`, `deleteDevice`, and `addRule` to dismiss modal dialog `MutableStateFlow`s synchronously before `viewModelScope.launch`.
  - Updated `WeMoRepository.defaultDemoMode` so fresh Robolectric `WeMoRepository` instances without an explicit `initialDemoModeOverride` always start in Demo Showroom Mode regardless of prior tests' `SharedPreferences` writes.
  - Added full-stack E2E UI assertions in `WeMoEndToEndUiTest` for `AutoResizingDeviceNameText` (full-width switch name rendered above `connection_status_badge_$id` and `category_badge_$id`), uniform `WeMoRoomGroupHeaderCard` height (`Living Room` vs. `Hallway & Landing`), `WeMoDisconnectedRingButton` (`reconnect_button_5`), the `Reset Homes, Switches & Rooms?` confirmation dialog (`reset_lan_data_chip`, `confirm_reset_and_rescan_button`, `confirm_reset_empty_button`), and `more_reset_lan_homes_switches_button` (`22/22` tests passing).

---

### [2026-11-08 21:57 PST] — Compact Wi-Fi Home Selector & SSDP Banner, Filter Out Belkin WeMo Soft-AP SSIDs from Naming Home, and One-Tap Reset for Homes, Switches & Rooms (`754e9fb`, `v16.0`)
- **Fixed WeMo Soft-AP SSID Polluting Home Name & Auto-Repaired Existing Databases ([`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt), [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt), [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt))**:
  - Identified on physical 2nd-Gen 3-Way switches (`WLS040` / `WLS0403`, factory soft-AP `Wemo.LS3W.220`) and 1st-Gen switches (`WeMo.Switch.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) that Belkin WeMo firmware often returns the switch's own factory-setup soft-AP SSID in field 4 (`AccessPointSSID`) of `metainfo1#GetMetaInfo`. Previously, `syncHomeSsidFromDeviceSsid` treated that string as the user's router SSID and overwrote `homeName` with `"Wemo.LS3W.220 Home"`.
  - Updated `WeMoXmlAndSkuParser.parseMetaInfoSoapResponse` and `WeMoDiscoveryCoordinator.syncHomeSsidFromDeviceSsid` to filter out `wemo.*` / `wemo_*` / `wemo-*` / `belkin.*` soft-AP SSIDs and never overwrite the user's `homeName` with a device-reported SSID.
  - Added automatic startup sanitization in `WeMoRepository.ensureSeeded()` so any existing Room database row whose `homeName` or `ssidLabel` was previously set to `"WeMo.Switch..."` / `"Wemo.LS3W..."` or `"Home Wi-Fi (..."` is immediately repaired to `"My Home"` (`"LAN ${subnetPrefix}.0/24"`).
- **Compact `WeMoWifiHomeBar` & `WeMoSsdpDiscoveryBanner` (`maxLines = 1`, `softWrap = false`) ([`WeMoWifiHomeBar.kt`](app/src/main/java/com/example/ui/components/WeMoWifiHomeBar.kt), [`WeMoSsdpScannerModal.kt`](app/src/main/java/com/example/ui/components/WeMoSsdpScannerModal.kt))**:
  - Fixed a vertical text-wrapping bug in `WeMoWifiHomeBar` where a long `homeName` squeezed the adjacent `ssidLabel` pill into a `0dp`-wide column that wrapped 1 character per line vertically.
  - Replaced the two-item row with single-line `AutoResizingDeviceNameText` (`singleLineOnly = true`) on Line 1 and compact single-line status (`"$onlineCount/${homeDevices.size} Active • ${subnetPrefix}.0/24"`, `maxLines = 1, softWrap = false`) on Line 2, reducing padding and chip heights (`28.dp` / `32.dp`) across both `WeMoWifiHomeBar` and `WeMoSsdpDiscoveryBanner`.
- **One-Tap Reset for Remembered Homes, Discovered Switches & Rooms ([`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt), [`WeMoWifiHomeBar.kt`](app/src/main/java/com/example/ui/components/WeMoWifiHomeBar.kt), [`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt))**:
  - Added a `Reset` chip (`testTag("reset_lan_data_chip")`) on `WeMoWifiHomeBar` with a confirmation dialog offering **"Reset & Rescan LAN"** (`confirm_reset_and_rescan_button`) and **"Reset Empty"** (`confirm_reset_empty_button`), plus a **"Reset Homes, Discovered Switches & Rooms"** action (`more_reset_lan_homes_switches_button`) on the **More** tab.

---

### [2026-11-08 18:15–20:28 PST] — Uniform Room Group Card Height, Dark-Gray IEC Power-Off & Disconnected Icons, Full-Width Reactive Switch Name & Redundant Room Chip Removal (`dcba5a0`, `f297cb6`, `853bd53`, `v13.0–v15.0`)
- **Very Dark Gray IEC Power-Off Icon & Disconnected Ring Button ([`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt), [`WeMoDeviceStatusBadges.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceStatusBadges.kt), [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt))**:
  - Updated `WeMoPowerRingButton`, `WeMoPowerStatePill`, `drawDeviceStateCornerBadge`, and the hardware illustrations so Power OFF uses the same classic IEC power symbol (`drawIecPowerSymbol`: broken circle arc + top vertical stem) as Power ON, rendered in very dark gray (`#23272F` / `#181B20`) instead of an amber bullseye circle.
  - Added `WeMoDisconnectedRingButton` (`drawDisconnectedSymbol` with dashed red ring and broken-link slash icon) for unavailable/offline switches (`!device.isReachable`) on the right of `WeMoDeviceRow` and in the illustration corner badge.
- **Uniform Room Group Header Card Height ([`WeMoRoomGroupCard.kt`](app/src/main/java/com/example/ui/components/WeMoRoomGroupCard.kt))**:
  - Fixed a layout wrapping bug in `WeMoRoomGroupHeaderCard` where long room names like `"Hallway & Landing"` squeezed the adjacent `"0/1 ON"` pill into a 0-width, multi-line vertical text column that inflated the card height.
  - Placed `roomName` on its own dedicated single-line top row using `AutoResizingDeviceNameText(..., singleLineOnly = true)` (`testTag("room_title_$roomName")`) and moved the `"$activeCount/$totalControllable ON"` badge (`maxLines = 1, softWrap = false`) onto the second line alongside `"${onlineDevices.size}/${roomDevices.size} Active • Avg ${avgLatency}ms"`, ensuring `"Hallway & Landing"`, `"Living Room"`, and all other room cards render at the exact same compact height.
- **Full-Width Unobstructed Switch Title with Reactive Font Scaling ([`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoTvCardsAndScenes.kt`](app/src/main/java/com/example/ui/components/WeMoTvCardsAndScenes.kt))**:
  - Added `AutoResizingDeviceNameText` (`Modifier.fillMaxWidth()`, `testTag("device_name_${device.id}")`) at the top of both phone/tablet (`WeMoDeviceRow`) and Android TV (`WeMoTvDeviceCard`) switch cards so the switch name spans the full width of the card with zero competing widgets on its line and reactively steps down its font size (`18.sp` → `11.sp`) if a long name would otherwise overflow.
- **Secondary Status & Category Widgets Moved Below the Switch Name ([`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt))**:
  - Moved the category pill (`category_badge_${device.id}`), connection/latency badge (`connection_status_badge_${device.id}`), power state pill (`power_state_badge_${device.id}`), and IP / Auto-Off countdown into a `FlowRow` on the line below the switch name so badges never occlude or truncate the switch name.
- **Removed Redundant Room Name Chip Inside Switch Cards ([`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt), [`WeMoTvCardsAndScenes.kt`](app/src/main/java/com/example/ui/components/WeMoTvCardsAndScenes.kt))**:
  - Removed the redundant per-card `device.roomName` badge since switches are already organized under their collapsible room group headers (`WeMoRoomGroupCard`).

---

### [2026-11-08 16:12 PST] — Phase M4 Test Suite Modularization, `FakeWeMoUpnpServer` Extraction & End-to-End Compose + Loopback UI Tests (`77bf3a6`, `v12.0`)
- **Extracted Reusable Loopback Hardware Simulator ([`FakeWeMoUpnpServer.kt`](app/src/test/java/com/example/FakeWeMoUpnpServer.kt), `369` Lines)**:
  - Configurable `AutoCloseable` Belkin WeMo HTTP/UPnP/SOAP/GENA/`WiFiSetup1` server on `127.0.0.1` (`ServerSocket(0)`), handling `/setup.xml`, `/Lightsetup.xml`, `SetBinaryState` (with optional `<BinaryState>Error</BinaryState>` quirk), `GetBinaryState`, `GetMetaInfo`, `GetSignalStrength`, `ChangeFriendlyName`, `ConfigureNightMode`, `SUBSCRIBE` (with optional HTTP 412 simulation), `pushGenaNotify`, and `WiFiSetup1` (`GetApList`, `GetNetworkList`, `ConnectHomeNetwork`, `GetNetworkStatus`, `CloseSetup`, `SetSetupDoneStatus`).
- **Decomposed Monolithic `WeMoProtocolTest.kt` (`1,228` → `10` Lines) into 3 Focused Protocol Test Suites**:
  - [`WeMoSkuAndCryptoTest.kt`](app/src/test/java/com/example/WeMoSkuAndCryptoTest.kt) (`213` lines): Hardware SKU classification (`F7C030`, `WLS0403`, `F7C059`, `WDS060`, `F7C063`, `WSP080`), `/setup.xml` binaryState parsing, semantic device categorization, and `pywemo` OpenSSL AES-128-CBC encryption/decryption (Methods 1, 2 & 3).
  - [`WeMoSoapAndSelfHealingTest.kt`](app/src/test/java/com/example/WeMoSoapAndSelfHealingTest.kt) (`336` lines): 9-port `probeWemoPort` port-shift healing, DHCP serial verification, `<BinaryState>Error</BinaryState>` quirk, Real LAN Mode offline state revert, manual IP hardware probing (`saveDevice`), and multi-device startup state detection (`detectStartupDeviceStates`).
  - [`WeMoGenaProvisioningAndRulesTest.kt`](app/src/test/java/com/example/WeMoGenaProvisioningAndRulesTest.kt) (`208` lines): UPnP GENA `SUBSCRIBE`, HTTP 412 recovery, embedded `NOTIFY` callback server, `WeMoRuleScheduler`, and full loopback `10.22.22.1` `WiFiSetup1` provisioning.
- **Added Full-Stack End-to-End Compose + Loopback UI Test Suite ([`WeMoEndToEndUiTest.kt`](app/src/test/java/com/example/WeMoEndToEndUiTest.kt), `224` Lines) & Fixed 3 Subtle Loopback/UI Bugs**:
  - Added 3 full-stack E2E tests exercising `AddOrEditWeMoDeviceDialog` → `127.0.0.1` `FakeWeMoUpnpServer` `ChangeFriendlyName` & live `SetBinaryState` power ring toggle, inline `WeMoDeviceDiagnosticsDrawer` (`ping_device_1`) + Edit Mode device rename (`edit_devices_button`, `edit_device_1`), and `ProvisionWeMoApDialog` short-password validation + `AddWeMoRuleDialog` rule creation/execution (`20/20` total tests passing).
  - Fixed [`WeMoNetworkMonitor.createWifiBoundSocketFactory`](app/src/main/java/com/example/network/WeMoNetworkMonitor.kt) `0`-arg `createSocket()` so `OkHttpClient` never binds `127.0.0.1` loopback or Robolectric test sockets to a non-loopback `Network` interface, and scoped [`WeMoDatabase.getDatabase`](app/src/main/java/com/example/data/WeMoDatabase.kt) per `Application` instance under Robolectric using `Room.inMemoryDatabaseBuilder`.
  - Updated [`WeMoViewModel.saveDevice`](app/src/main/java/com/example/ui/WeMoViewModel.kt) to dismiss `AddOrEditWeMoDeviceDialog` (`_showAddDeviceDialog = false`, `_editingDevice = null`) immediately upon tapping **Save** rather than blocking the dialog on background `ChangeFriendlyName` / 9-port network probes.

---

### [2026-11-08 15:00 PST] — Phase M3 UI Layer Modularization (`MainActivity`, Illustrations, Dialogs, DeviceRow, TvScreen & Screens, `v12.0`)
- **Decomposed 6 Large Jetpack Compose UI Files into Focused, Human-Readable Modules**:
  - **Hardware & Category Illustrations**: Split [`WeMoHardwareIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoHardwareIllustrations.kt) (`1,374` → `379` lines) into [`WeMoSwitchAndPlugIllustration.kt`](app/src/main/java/com/example/ui/components/WeMoSwitchAndPlugIllustration.kt) (`252` lines), [`WeMoLightAndSensorIllustrations.kt`](app/src/main/java/com/example/ui/components/WeMoLightAndSensorIllustrations.kt) (`322` lines), [`WeMoChristmasTreeIllustration.kt`](app/src/main/java/com/example/ui/components/WeMoChristmasTreeIllustration.kt) (`286` lines), and [`WeMoFanIllustration.kt`](app/src/main/java/com/example/ui/components/WeMoFanIllustration.kt) (`192` lines).
  - **Dialogs**: Split [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt) (`906` lines) into [`WeMoDeviceEditDialog.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceEditDialog.kt) (`389` lines), [`WeMoRuleDialog.kt`](app/src/main/java/com/example/ui/components/WeMoRuleDialog.kt) (`209` lines), and [`WeMoProvisionApDialog.kt`](app/src/main/java/com/example/ui/components/WeMoProvisionApDialog.kt) (`398` lines), preserving all semantic `testTag`s (`provision_ap_status_box`, `provision_status_box`, etc.).
  - **Device Row & Telemetry**: Split [`WeMoDeviceRow.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt) (`813` → `387` lines) into [`WeMoDeviceStatusBadges.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceStatusBadges.kt) (`223` lines) and [`WeMoDeviceDiagnosticsDrawer.kt`](app/src/main/java/com/example/ui/components/WeMoDeviceDiagnosticsDrawer.kt) (`267` lines).
  - **Android TV 10-Foot UI**: Split [`WeMoTvScreen.kt`](app/src/main/java/com/example/ui/components/WeMoTvScreen.kt) (`1,011` → `647` lines) and extracted [`WeMoTvCardsAndScenes.kt`](app/src/main/java/com/example/ui/components/WeMoTvCardsAndScenes.kt) (`402` lines).
  - **Tab Screens & App Shell**: Split [`WeMoRulesAndMoreScreens.kt`](app/src/main/java/com/example/ui/screens/WeMoRulesAndMoreScreens.kt) (`584` lines) into [`WeMoRulesScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoRulesScreen.kt) (`196` lines) and [`WeMoMoreScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoMoreScreen.kt) (`414` lines), and extracted [`WeMoDevicesTabScreen.kt`](app/src/main/java/com/example/ui/screens/WeMoDevicesTabScreen.kt) (`388` lines) from [`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt) (`772` → `499` lines).

---

### [2026-11-08 14:48 PST] — Phase M2 Network Layer Modularization (`WeMoNetworkClient.kt` `1,268` → `856` Lines, `WeMoProvisionerClient.kt` `722` → `586` Lines, `v12.0`)
- **Decomposed [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt) and [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt) into Single-Responsibility Wire Protocol Modules**:
  - [`WeMoXmlAndSkuParser.kt`](app/src/main/java/com/example/network/WeMoXmlAndSkuParser.kt) (`239` lines): Extracted stateless UPnP/SSDP/SOAP parsers and SKU classifiers (`buildSsdpMSearchPacket`, `parseSsdpHeaders`, `classifyHardwareSku`, `parseSetupXmlDescriptor`, `parseMetaInfoSoapResponse`, `parseSignalStrengthSoapResponse`, `parseApListXml`).
  - [`WeMoSsdpDiscoveryEngine.kt`](app/src/main/java/com/example/network/WeMoSsdpDiscoveryEngine.kt) (`308` lines): Extracted UDP `239.255.255.250:1900` `M-SEARCH` discovery (`performSsdpDiscoveryScan` with `MulticastLock` + 1s retransmit + Demo Showroom fallback synthesis) and `Semaphore(24)` `/24` unicast sweep (`scanSubnetUnicast`).
  - [`WeMoPasswordCrypto.kt`](app/src/main/java/com/example/network/WeMoPasswordCrypto.kt) (`98` lines): Extracted `pywemo`-exact OpenSSL `EVP_BytesToKey` MD5 key/IV derivation and AES-128-CBC Wi-Fi password encryption/decryption across Methods 1 (OpenWrt), 2 (RTOS), and 3 (`binaryOption`).
  - Preserved all companion/method delegations on [`WeMoNetworkClient.kt`](app/src/main/java/com/example/network/WeMoNetworkClient.kt) (`856` lines) and [`WeMoProvisionerClient.kt`](app/src/main/java/com/example/network/WeMoProvisionerClient.kt) (`586` lines) for zero-regression compatibility.

---

### [2026-11-08 14:30 PST] — Phase M1 Data Layer Modularization (`WeMoRepository.kt` `1,898` → `629` Lines, `v12.0`)
- **Decomposed [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt) into 4 Single-Responsibility Modules**:
  - [`WeMoDemoFixtures.kt`](app/src/main/java/com/example/data/WeMoDemoFixtures.kt) (`351` lines): Extracted `DEFAULT_ROOMS`, `defaultHomeNetworks()`, `defaultClassicDevices()`, and `defaultClassicRules()` while preserving companion delegations on `WeMoRepository`.
  - [`WeMoDiscoveryCoordinator.kt`](app/src/main/java/com/example/data/WeMoDiscoveryCoordinator.kt) (`385` lines): Extracted startup `GetBinaryState` probing (`detectStartupDeviceStates`), 2-stage self-healing reconnect (`attemptTwoStageReconnect`), `reconnectDevice`, `reconnectAndRediscoverHome`, `executeSsdpScan`, `adoptDiscoveredDevice`, `adoptDiscoveredDevices`, and zero-permission subnet/SSID home sync, sharing the repository's single `Semaphore(3)`, `deviceMutexes`, and `lastSetStateTimestampMs` map.
  - [`WeMoProvisioningCoordinator.kt`](app/src/main/java/com/example/data/WeMoProvisioningCoordinator.kt) (`289` lines): Extracted `10.22.22.1` Setup AP probing (`probeSetupApDevice`), 2.4 GHz AP scanning (`scanSetupApNetworks`), `WiFiSetup1#ConnectHomeNetwork` provisioning (`provisionSetupApDevice`), Demo Showroom AP simulation, and post-provisioning Room registration.
  - [`WeMoRepository.kt`](app/src/main/java/com/example/data/WeMoRepository.kt) (`629` lines): Consolidated duplicated optimistic SOAP state-transition boilerplate across `toggleDevicePower`, `setDevicePowerExplicit`, and `updateDeviceBrightness` into a single `executeOptimisticDeviceCommand` pipeline.

---

### [2026-11-08 07:42 PST] — Expanded Semantic Category Keywords, Empty State Onboarding Card Layout & E2E Test (`ac7cd7b`, `v12.0`)
- **Expanded Semantic Category Classification ([`WeMoEntities.kt`](app/src/main/java/com/example/data/WeMoEntities.kt))**:
  - Extended `classifyDeviceCategory()` with additional holiday (`garland`, `wreath`, `tinsel`, `noel`, and `christmas` without light-specific nouns), airflow (`vent`), and lighting (`pendant`, `dimmer`, `led`) keywords.
- **Empty State & Provisioning Status Box Refinements ([`MainActivity.kt`](app/src/main/java/com/example/MainActivity.kt), [`WeMoDialogs.kt`](app/src/main/java/com/example/ui/components/WeMoDialogs.kt), [`WeMoViewModel.kt`](app/src/main/java/com/example/ui/WeMoViewModel.kt))**:
  - Compacted `EmptyDevicesState` category illustrations (`36×46 dp`) and placed `Add Switch Manually` and `Restore Classic Setup` side-by-side so all 4 onboarding buttons fit cleanly on screen.
  - Ensured `provision_ap_status_box` (`provision_status_box`) in `ProvisionWeMoApDialog` renders a default readiness banner when idle while preserving red/green warning/error styling.
- **Expanded E2E Robolectric Coverage ([`ExampleRobolectricTest.kt`](app/src/test/java/com/example/ExampleRobolectricTest.kt))**:
  - Added `verify EmptyDevicesState onboarding card on empty Wi-Fi Home and Restore Classic Setup action` and assertions for `Porch Christmas Garland`, `Utility Room Vent`, and `Kitchen Island Pendant`.

---

### [2026-11-08 07:41 PST] — Multi-Agent Git Sync & Anti-Stomp Integration Skill (`multi-agent-git-sync`, `2f97020`, `v12.0`)
- **Added [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md)**:
  - Defines the 5-step multi-agent Git synchronization, anti-stomp audit (`git diff <your-commit>..origin/main`), additive conflict resolution, root/`docs/` mirror verification, and `.agents/skills/` ↔ persistent skill registry synchronization workflow so parallel agent conversations and AI Studio code syncs never overwrite each other's changes.

---

### [2026-11-08 07:35 PST] — Architecture & Protocol Review Sign-Off on Modularization Plan (`c5ca7c4`, `v12.0`)
- **Added Section 7 to [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md)**:
  - Recorded architecture and protocol invariants (Phase M1 `WeMoDatabase.getDatabase()` Room v5 singleton preservation, Phase M2 Android `Network.socketFactory` Wi-Fi binding and `pywemo` OpenSSL EVP_BytesToKey MD5 AES-128-CBC encryption, Phase M3 semantic 3-state category icons and manual IP 9-port hardware probing, and Phase M4 test target splits).

---

### [2026-11-08 07:25 PST] — Reverse-Chronological Change Log (`IMPLEMENTED.md`), `UPCOMING_FEATURES.md` & `wemi-changelog-and-docs` Skill (`2cfe70d`..`de14b85`, `v12.0`)
- **Reframed [`IMPLEMENTED.md`](IMPLEMENTED.md) as a Reverse-Chronological Change Log**:
  - Restructured the implementation document so every feature, bug fix, and architectural milestone is chronicled in reverse chronological order with timestamps, commit hashes, version numbers, and affected files.
- **Renamed Implementation Plan to [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md)**:
  - Replaced `docs/IMPLEMENTATION_PLAN.md` with `UPCOMING_FEATURES.md` focused strictly on upcoming roadmap items.
- **Added [`wemi-changelog-and-docs`](.agents/skills/wemi-changelog-and-docs/SKILL.md) Agent Skill**:
  - Created a dedicated workspace and registered skill instructing all agents working in this repository to prepend new entries to `IMPLEMENTED.md` in reverse chronological order, keep `USER_GUIDE.md` / `docs/user-guide/` in sync, and update `UPCOMING_FEATURES.md` whenever features land.

---

### [2026-11-08 07:20 PST] — Code Modularization & E2E Validation Plan + Modularization & Testing Skills (`627ab38`, `v12.0`)
- **Added [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md)**:
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
- **Created Top-Level User Guide Index ([`USER_GUIDE.md`](USER_GUIDE.md))**:
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
