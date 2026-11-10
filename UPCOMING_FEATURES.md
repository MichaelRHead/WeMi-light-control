# WeMi Light Control — Upcoming Features & Next-Iteration Roadmap (`UPCOMING_FEATURES.md`)

**Target Repository**: `MichaelRHead/WeMi-light-control` (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`)  
**Current Baseline**: `v21.0` (See [`IMPLEMENTED.md`](IMPLEMENTED.md) for the reverse-chronological Change Log of completed features)  
**Target Platforms**: Android Phones, Tablets, Home Screen Widgets, Quick Settings, and Android TV (10-foot D-Pad Hub)

Related documents in this repository:
- [`UX_DESIGN.md`](UX_DESIGN.md) — Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows *(Owned Exclusively by the Google AI Studio Agent)*
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) — User-Facing Google Play Store Release Notes (`v1.0` → `v21.0`)
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Feature Change Log (`v1.0` → `v21.0`)
- [`USER_GUIDE.md`](USER_GUIDE.md) — User Guide Index & Detailed Feature Guides ([`docs/user-guide/`](docs/user-guide/))
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
- [`.agents/skills/wemi-release-coordinator/SKILL.md`](.agents/skills/wemi-release-coordinator/SKILL.md) — 4-Agent Autonomous Round-Robin Release Review & Sign-Off Coordinator Skill
- [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md) — On-Device Screenshot & Recorded Telemetry File Debugging Skill *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) — Device Discovery Logic, 3-Tier Identity Resolution & Automatic Room Grouping Skill *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md) — Core Domain & Protocol Agent Skill
- [`.agents/skills/wemi-changelog-and-docs/SKILL.md`](.agents/skills/wemi-changelog-and-docs/SKILL.md) — Change Log & Documentation Maintenance Skill
- [`.agents/skills/multi-agent-git-sync/SKILL.md`](.agents/skills/multi-agent-git-sync/SKILL.md) — Multi-Agent Git Sync, Anti-Stomp Audit & Conflict-Free Integration Skill
- [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md) — Android Code Modularization & Human Readability Skill
- [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md) — Android End-to-End (E2E) Testing & Validation Skill

---

## 1. Upcoming Features Overview

With the core local UPnP/SSDP/SOAP/GENA engine, 3-state semantic category icons (`Christmas Tree`, `Fan`, `Light`, `Motion`, `Switch`), zero-cloud `10.22.22.1` provisioning (`Methods 1, 2 & 3`), compact single-line-safe Room/Home cards, dark-gray IEC Power-Off / red Disconnected icons, Belkin soft-AP SSID filtering (`WeMo.*` / `Wemo.LS3W.*`), one-tap LAN reset, `org.suppressingfire.wemi` package architecture, and 26 full-stack E2E tests complete in `v21.0`, the upcoming features roadmap focuses on turning **WeMi Light Control** into a standalone, zero-cloud smart-home hub and OS-integrated controller:

| Track | Upcoming Feature Area | Key Deliverables |
| :--- | :--- | :--- |
| **Track 1** | **On-Device Hardware Rules DB (`rules.db` SQLite over SOAP)** | Read, decode, and write the embedded SQLite `rules.db` (`urn:Belkin:service:rules:1#FetchRules` & `#StoreRules` Base64 ZIP) on OpenWrt switches (`F7C030`, `F7C059`, `F7C063`) plus `timesync1#TimeSync` so schedules & auto-off timers run inside the wall switch even when all phones are away from home. |
| **Track 2** | **Android Quick Settings Tiles, Home Screen Widgets & Always-On TV Hub Service** | Android `TileService` quick toggles, Jetpack Glance / `AppWidgetProvider` skeuomorphic 1x1 & 4x1 room widgets, and an optional `BOOT_COMPLETED` foreground service for Android TV / wall tablets acting as a 24/7 LAN controller. |
| **Track 3** | **Expanded Hardware Fleet (`Insight`, `Maker`, `Link`, Dimmer Long-Press & Faders)** | Real-time power/wattage/kWh monitoring for **Insight (`F7C029`)** via `insight1#GetInsightParams`, hardware fade ramps & `<LongPress>` scene triggers for **Dimmers (`F7C059`/`WDS060`)**, and **Maker (`F7C043`)** / **Link (`F7C031`)** support. |
| **Track 4** | **One-Tap OS Wi-Fi Handoff (`WifiNetworkSpecifier`) & Hardware `ReSetup`** | Android 10+ `WifiNetworkSpecifier` in-app connection to `WeMo.Light.*` / `WeMo.Mini.*` APs without leaving the app to open Android Settings, plus a confirmation-protected `basicevent1#ReSetup` factory reset action. |
| **Track 5** | **Offline Astronomical Solar Calculator & GENA Motion-Sensor Automation Chains** | Offline NOAA latitude/longitude solar calculator for exact daily Sunrise/Sunset times (`Sunset - 20m`, `Sunrise + 15m`) and sub-second GENA `Motion` (`F7C028`) → multi-switch trigger chains with configurable hold timers. |
| **Track 6** | **Household Profile Sync (JSON / QR Export-Import) & Local Webhook / Home Assistant Bridge** | Export/import remembered Wi-Fi Homes, room names, custom ordering, and rules via JSON file or on-screen QR code (ideal for syncing a phone setup onto an Android TV), plus an optional local REST/Webhook endpoint on Android TV. |

---

## 2. Detailed Specifications for Upcoming Features

### Track 1: On-Device Hardware Rules DB (`HardwareRulesManager.kt`)
Currently, `RuleScheduler` executes schedules and auto-off timers from the Android device over the LAN. However, 1st-Gen OpenWrt switches (`F7C030`, `F7C059`, `F7C063`) also run a local SQLite rule engine (`/tmp/Belkin_settings/rules.db`) inside the switch itself:
1. **`FetchRules` Inspection (`urn:Belkin:service:rules:1#FetchRules`)**:
   - Query `<ruleDbPath>` (typically `http://<ip>:<port>/rules.db`) and `<ruleDbVersion>`.
   - Download the ZIP archive from `<ruleDbPath>`, extract the inner `rules.db` SQLite file into a temporary cache file, and read `RULES`, `RULEDEVICES`, and `DEVICECOMBINATION` using Android's built-in `SQLiteDatabase.openDatabase`.
2. **Two-Way Hardware Rule Sync (`StoreRules`)**:
   - Package modified `rules.db` into a deflated ZIP archive, Base64-encode it into `<ruleDbBody>`, increment `<ruleDbVersion>`, and push via `urn:Belkin:service:rules:1#StoreRules` (`<processDb>1</processDb>`).
   - Gracefully fall back to app-driven `RuleScheduler` for 2nd-Gen RTOS devices (`WLS0403`, `WDS060`, `WSP080`) that return HTTP 404 on `/upnp/control/rules1`.
3. **Hardware Time Sync (`timesync1#TimeSync`)**:
   - Because legacy NTP servers were retired alongside the cloud shutdown, add `urn:Belkin:service:timesync:1#TimeSync` to push the phone's current UTC epoch, timezone offset, and DST flags directly to each switch so hardware schedules fire at the right local clock time.

---

### Track 2: Android OS Widgets, Quick Settings & Always-On TV Hub Mode
1. **Skeuomorphic Home Screen Widgets (`AppWidgetProvider`)**:
   - **Single Switch 1x1 Widget**: Classic brushed-aluminum tile with glowing green/amber power ring and live latency dot.
   - **Room Group 4x1 Bar Widget**: Shows active Wi-Fi Home, room master toggle, and up to 4 pinned switches/dimmers.
2. **Android Quick Settings Tile (`QuickSettingsTileService`)**:
   - Allows pulling down the Android notification shade and toggling a favorite switch or whole room (e.g., `"Living Room Lights"`) with a single tap — even from the lock screen.
3. **Always-On Android TV / Tablet LAN Hub Service (`HubForegroundService`)**:
   - Optional toggle in **More** (`"Run as Always-On Local Automation Hub"`), automatically enabled on Android TV (`UI_MODE_TYPE_TELEVISION`) and started on `BOOT_COMPLETED`.
   - Keeps `GenaSubscriptionManager` and `RuleScheduler` alive in the background so scheduled rules, motion triggers, and `timesync1` clock updates run 24/7 on an Android TV box.

---

### Track 3: Expanded Smart Switch Hardware Support
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

### Track 4: Frictionless `10.22.22.1` Setup with `WifiNetworkSpecifier` & Hardware `ReSetup`
1. **In-App Setup AP Join (`Android 10+ WifiNetworkSpecifier`)**:
   - Add a **"Find & Join Switch Setup AP"** button inside `ProvisionApDialog` using `WifiNetworkSpecifier.Builder().setSsidPattern(PatternMatcher("WeMo.", PatternMatcher.PATTERN_PREFIX)).build()`.
2. **Hardware Wi-Fi Reset (`urn:Belkin:service:basicevent:1#ReSetup`)**:
   - Expose a confirmation-protected **"Reset Switch Wi-Fi (`ReSetup`)"** action in the device edit dialog so users migrating to a new router can put an existing switch back into `10.22.22.1` setup mode without unscrewing the wall faceplate.

---

### Track 5: Offline Astronomical Solar Calculator & GENA Motion Chains
1. **Offline Sunrise/Sunset Calculator (`SolarCalculator.kt`)**:
   - Compute exact daily local Sunrise, Sunset, and Civil Twilight times from an optional user-configured city/latitude-longitude (zero location permission required if selected from a city preset or manual coordinates).
   - Automatically update `SUNSET` / `SUNRISE` rules each day (with custom offsets like `Sunset - 15m` or `Sunrise + 30m`).
2. **Real-Time Motion Sensor (`F7C028`) Automation Chains**:
   - When `GenaSubscriptionManager` receives `<BinaryState>1</BinaryState>` from a `MOTION_SENSOR` (`urn:Belkin:device:sensor:1` / `urn:Belkin:device:NetCamSensor:1`), immediately evaluate all enabled `MOTION_TRIGGER` rules and turn ON the target room/switches, starting an automatic re-triggerable hold timer (`autoOffMinutes`).

---

### Track 6: Household Configuration Sync (JSON / QR) & Android TV Local Bridge
1. **Zero-Cloud Household Sync (`BackupManager.kt`)**:
   - Export all `wemo_home_networks`, `wemo_devices` (including custom names, rooms, and sort order), and `wemo_rules` to a compact JSON file or multi-frame / compressed QR code.
   - On Android TV, display a **"Pair from Phone"** local HTTP receiver (`http://<tv-ip>:8990/sync`) so the user can push their entire phone configuration to their living room TV in one tap over the home LAN.
2. **Optional Local REST / Webhook Endpoint**:
   - Allow local scripts, Tasker, or Home Assistant on the LAN to query or trigger WeMi scenes via simple local HTTP calls when Android TV Hub Mode is active.
