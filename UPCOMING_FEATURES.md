# WeMi Light Control — Upcoming Features & Next-Iteration Roadmap (`UPCOMING_FEATURES.md`)

**Target Repository**: `MichaelRHead/WeMi-light-control` (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`)  
**Current Baseline**: `v37.0` (See [`IMPLEMENTED.md`](IMPLEMENTED.md) for the reverse-chronological Change Log of completed features)  
**Target Platforms**: Android Phones, Tablets, Chromebooks (ChromeOS ARCVM), Home Screen Widgets, Quick Settings, and Android TV (10-foot D-Pad Hub)

Related documents in this repository:
- [`UX_DESIGN.md`](UX_DESIGN.md) — Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows *(Owned Exclusively by the Google AI Studio Agent)*
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`SECURITY_AND_PRIVACY.md`](SECURITY_AND_PRIVACY.md) — Security Threat Model, Privacy Lifecycle Invariants & Stage 4 Hardening Backlog *(Owned Exclusively by the Antigravity Security & Privacy Agent)*
- [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) — Open-Source MIT License Parity, Minimal Permissive Dependency Policy (`Apache-2.0` AndroidX/Kotlin Only; Zero External HTTP/Font Bloat) & Brand/Trademark Governance *(Owned Exclusively by the Antigravity License & Brand Governance Agent)*
- [`CHROMEOS_ARCVM_DISCOVERY_DESIGN.md`](CHROMEOS_ARCVM_DISCOVERY_DESIGN.md) — ChromeOS VM (ARCVM) Local-LAN SSDP Discovery, Physical-Subnet Stage C Fallback, Home Profile Hygiene & GENA Safety Architecture *(Implemented)*
- [`DEPENDENCY_UPGRADE_PLAN.md`](DEPENDENCY_UPGRADE_PLAN.md) — Phased Gradle, AGP, Kotlin, KSP, Compose BOM, Room, Coroutines & Robolectric Upgrade Plan *(Wave 1 Verified in AI Studio; Wave 2 Deferred Post-v35.0; Wave 3 Deferred)*
- [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) — User-Facing Google Play Store Release Notes (`v1.0` → `v37.0`)
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Feature Change Log (`v1.0` → `v37.0`)
- [`USER_GUIDE.md`](USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`AGENTS.md`](AGENTS.md) & [`ARCHITECTURE.md`](ARCHITECTURE.md) — Concise Agent Guide & On-Demand Architecture/Test Catalog
- [`.agents/skills/wemi-release-coordinator/SKILL.md`](.agents/skills/wemi-release-coordinator/SKILL.md) — 7-Agent / 8-Stage Autonomous Release Review, Sign-Off & Play Store Promotion Coordinator Skill
- [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) — On-Device Screenshot & Recorded Telemetry File Debugging Skill *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) — Device Discovery Logic, 3-Tier Identity Resolution & Automatic Room Grouping Skill *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`.agents/skills/wemi-security-and-privacy/SKILL.md`](.agents/skills/wemi-security-and-privacy/SKILL.md) — Stage 4 Security & Privacy Review Invariants Skill *(Owned Exclusively by the Antigravity Security & Privacy Agent)*
- [`.agents/skills/security-and-privacy/SKILL.md`](.agents/skills/security-and-privacy/SKILL.md) — General Application Security, Privacy Engineering & Threat Modeling Skill
- [`.agents/skills/wemi-license-governance/SKILL.md`](.agents/skills/wemi-license-governance/SKILL.md) — Open-Source MIT License Parity, Minimal Permissive Dependency Policy & Trademark Governance Skill *(Owned Exclusively by the Antigravity License & Brand Governance Agent)*
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Core Domain & Protocol Agent Skill
- [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) — Change Log & Documentation Maintenance Skill
- [`.agents/skills/wemi-doc-audit-and-review/SKILL.md`](.agents/skills/wemi-doc-audit-and-review/SKILL.md) — Stage 3 (`telemetry_and_debugging`) Documentation Audit & User-Guide Review Skill
- [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md) — Multi-Agent Git Sync, Anti-Stomp Audit & Conflict-Free Integration Skill
- [`.agents/skills/agent-commit-attribution/SKILL.md`](.agents/skills/agent-commit-attribution/SKILL.md) — Mandatory Agent Commit Description Attribution Skill
- [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md) — Android Code Modularization & Human Readability Skill
- [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) — Android End-to-End (E2E) Testing & Validation Skill

---

## 1. Upcoming Features Overview

With the core local UPnP/SSDP/SOAP/GENA engine (`LanHttpClient.kt`, `LanEndpointPolicy.kt`, immediate Real-LAN `<100ms` authenticated GENA subscriptions, parallel `Semaphore(3)` health polling, and ChromeOS ARCVM network-bound `MulticastSocket` discovery + physical-subnet fallback), full Step 1 & Step 2 on-device hardware rules management (zero-RTT Tier-1 + firmware-cached Tier-2 capability detection, live in-place `rules.db` editing for **Long Press**, **Auto-Off Delay**, **Sunrise/Sunset Schedules**, and **Timed Actions**, automatic `timesync1#TimeSync` clock sync, offline `LOCATIONINFO` solar coordinates, rotating raw-ZIP backup restore, anti-brick pre-store validator + `HardwareRulesSchemaGuard` + `FetchRules` verification, and non-destructive Room v7 `MIGRATION_6_7` retiring legacy phone-only app rules), zero-cloud **Household Config Sync** (SAF JSON export/import + PIN-encrypted local-LAN Phone-to-TV / Phone-to-Device transfer with 3-tier merge & preview), 3-state semantic category icons (`Christmas Tree`, `Fan`, `Light`, `Motion`, `Switch`), zero-cloud `10.22.22.1` provisioning (`Methods 1, 2 & 3` with `minSdk 24` compatibility, ephemeral password handling, and deterministic crypto zeroization), compact single-row `TopHeader` (`48.dp` accessible controls + `"All Off"` + in-app `ReleaseHistoryDialog`), zero-dependency Home Screen Light & Room Toggle Widgets (`WeMiSwitchWidgetProvider`, non-exported `WeMiWidgetActionReceiver` with single-use pin nonces & serial/UDN resolution), 3-column room-grouped Android TV dashboard (`wemi TV` with focusable inline dimmer sliders, Room Bar dimming, conditional `Holiday ON`, and **Pair from Phone `Sync`**), dark-gray IEC Power-Off / red Disconnected icons, Belkin soft-AP SSID filtering (`WeMo.*` / `Wemo.LS3W.*`), redacted-by-default diagnostics (`TelemetryRedactor`) & strict local-only cloud-backup exclusions, confirmation-protected full LAN reset, `org.suppressingfire.wemi` package architecture, MIT license & brand governance, `100%` `< 500`-line modularization across all `134+` production and test `.kt` files, and `147+` unit, protocol, hardware-rules, household-sync, security, widget, telemetry, governance, ChromeOS ARCVM, and full-stack E2E tests across `29+` suites (plus `28/28` Python Play Store tests) complete, the upcoming features roadmap focuses on turning **WeMi Light Control** into an OS-integrated smart-home controller and optional local hub:

| Track | Upcoming Feature Area | Key Deliverables |
| :--- | :--- | :--- |
| **Track 1** | **Android Quick Settings Tiles, Multi-Switch 4x1 Widget Bar & Always-On TV Hub Service** | Android `TileService` quick toggles, a multi-switch 4x1 room bar widget companion to the shipped single-light/room Home Screen widget, and an optional `BOOT_COMPLETED` foreground service for Android TV / wall tablets acting as a 24/7 LAN controller & periodic `timesync1` clock keeper. |
| **Track 2** | **Expanded Hardware Fleet (`Insight`, `Maker`, `Link`, Dimmer Long-Press & Faders)** | Real-time power/wattage/kWh monitoring for **Insight (`F7C029`)** via `insight1#GetInsightParams`, hardware fade ramps & `<LongPress>` scene triggers for **Dimmers (`F7C059`/`WDS060`)**, and **Maker (`F7C043`)** / **Link (`F7C031`)** support. |
| **Track 3** | **One-Tap OS Wi-Fi Handoff (`WifiNetworkSpecifier`) & Hardware `ReSetup`** | Android 10+ `WifiNetworkSpecifier` in-app connection to `WeMo.Light.*` / `WeMo.Mini.*` APs without leaving the app to open Android Settings, plus a confirmation-protected `basicevent1#ReSetup` factory reset action. |
| **Track 4** | **On-Device Motion/Sensor & Away-Mode Rule Builders + Periodic Clock Maintenance** | Native `rules.db` editors for `Motion Controlled` (`DEVICECOMBINATION`) and `Away Mode` rules (building on the shipped Step 2 mutator), plus optional background `WorkManager` clock-drift checks for switches after household power outages. |
| **Track 5** | **Optional Local REST / Webhook Bridge (Android TV / Wall Tablet)** | *(Zero-Cloud Household JSON Export/Import & PIN-Encrypted Phone-to-TV Local LAN Pairing Sync shipped in `v37.0`)* Remaining: optional opt-in token-authenticated local REST/Webhook endpoint on Android TV when Hub Mode is active. |
| **Track 6** | **Phased Dependency & Toolchain Modernization ([`DEPENDENCY_UPGRADE_PLAN.md`](DEPENDENCY_UPGRADE_PLAN.md))** | *(Wave 1 Verified in AI Studio: `lifecycle 2.9.2`, `room 2.8.5`, `coroutines 1.11.0`, `androidx.test 1.7.0`, `robolectric 4.18.1`; Wave 2 Deferred Post-`v35.0`)* Next: Wave 2 upgrades Compose BOM + `activity-compose` + `lifecycle 2.10.0` (co-reviewed by `ai_studio_ux_cuj`) within AI Studio's `Gradle 9.3.1` / `AGP 9.1.1` / `Kotlin 2.2.10` / `compileSdk 36.1` ceiling; Wave 3 toolchain bump deferred until AI Studio upgrades its container Gradle binary. |

---

## 2. Detailed Specifications for Upcoming Features

### Track 1: Android Quick Settings Tiles, Multi-Switch 4x1 Widget Bar & Always-On TV Hub Mode
1. **Multi-Switch Room Group 4x1 Bar Widget**:
   - Builds on the shipped one-tap single-light and room-toggle Home Screen widget (`WeMiSwitchWidgetProvider`, `v29.0–v31.0`) by adding a wider 4x1 bar displaying the active Wi-Fi Home, room master toggle, and up to 4 individual pinned switches/dimmers.
2. **Android Quick Settings Tile (`QuickSettingsTileService`)**:
   - Allows pulling down the Android notification shade and toggling a favorite switch or whole room (e.g., `"Living Room Lights"`) with a single tap — even from the lock screen.
3. **Always-On Android TV / Tablet LAN Hub Service (`HubForegroundService`)**:
   - Optional toggle in **More** (`"Run as Always-On Local Automation Hub"`), automatically enabled on Android TV (`UI_MODE_TYPE_TELEVISION`) and started on `BOOT_COMPLETED`.
   - Keeps `GenaSubscriptionManager` and periodic `timesync1#TimeSync` clock maintenance alive in the background on an always-on Android TV box or wall tablet.

---

### Track 2: Expanded Smart Switch Hardware Support
1. **Insight Energy Monitoring Plug (`F7C029` — `urn:Belkin:service:insight:1`)**:
   - Parse pipe-delimited `<InsightParams>` (`State|LastChangeTS|OnForSec|OnTodaySec|OnTotalSec|TimeWindow|AvgPowerW|CurrentPowerMw|TodayMwMin|TotalMwMin|StandbyThresholdMw`).
   - Distinguish `State = 1` (**ON / Drawing Load**) vs. `State = 8` (**Standby** — plugged-in appliance is idle below `StandbyThresholdMw`).
   - Render live Wattage (`W`) and daily energy (`Wh`/`kWh`) telemetry pills in [`DeviceRow.kt`](app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceRow.kt).
2. **Dimmer (`F7C059` / `WDS060`) Fader Ramps & Physical `<LongPress>` Scene Triggers**:
   - Support `<fader>300:1770000000:1:0:100</fader>` SOAP parameter for smooth sleep/wake fade-out and fade-in transitions.
   - Listen for `<LongPress>1</LongPress>` in `GenaSubscriptionManager` so long-pressing a wall dimmer can trigger a multi-device WeMi Room Scene (e.g., turning off all switches in the house).
3. **Maker (`F7C043`) & Link (`F7C031`)**:
   - Support momentary relay mode (garage door / gate opener) and dry-contact sensor status on Maker.
   - Support `urn:Belkin:service:bridge:1#GetEndDevices` and `#SetDeviceStatus` for Link Zigbee smart bulbs.

---

### Track 3: Frictionless `10.22.22.1` Setup with `WifiNetworkSpecifier` & Hardware `ReSetup`
1. **In-App Setup AP Join (`Android 10+ WifiNetworkSpecifier`)**:
   - Add a **"Find & Join Switch Setup AP"** button inside `ProvisionApDialog` using `WifiNetworkSpecifier.Builder().setSsidPattern(PatternMatcher("WeMo.", PatternMatcher.PATTERN_PREFIX)).build()`.
2. **Hardware Wi-Fi Reset (`urn:Belkin:service:basicevent:1#ReSetup`)**:
   - Expose a confirmation-protected **"Reset Switch Wi-Fi (`ReSetup`)"** action in the device edit dialog so users migrating to a new router can put an existing switch back into `10.22.22.1` setup mode without unscrewing the wall faceplate.

---

### Track 4: On-Device Motion/Sensor & Away-Mode Rule Builders + Periodic Clock Maintenance
1. **Native `Motion Controlled` & `Away Mode` On-Device Rule Editors**:
   - Extend `HardwareRulesDbMutator` and `HardwareRuleEditorDialog` beyond Long Press, Auto-Off Delay, Schedules, and Timed Actions to author native `DEVICECOMBINATION` sensor-triggered rules and `Away Mode` randomized occupancy windows directly inside compatible switches' `rules.db`.
2. **Multi-Switch Rule Cloning & Periodic Post-Outage Clock Sync**:
   - Allow copying a schedule or Solar Location across multiple switches in one tap and optionally schedule background `timesync1#TimeSync` checks after router/power outages.

---

### Track 5: Optional Local REST / Webhook Bridge (Android TV / Wall Tablet)
*(Note: Zero-cloud Household Configuration JSON Export/Import and one-tap PIN-encrypted Phone-to-TV / Phone-to-Device local LAN sync have shipped.)*
1. **Optional Local REST / Webhook Endpoint**:
   - Allow local scripts, Tasker, or Home Assistant on the LAN to query or trigger WeMi rooms and scenes via token-authenticated local HTTP calls when an opt-in Android TV / wall-tablet Hub Mode is active.

