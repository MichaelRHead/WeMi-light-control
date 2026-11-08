# WeMi Light Control — Next-Iteration Implementation Plan (Phases 8–13)

**Target Repository**: `MichaelRHead/WeMi-light-control` (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`)  
**Current Baseline**: `v10.0` (Phases 1–7 Completed — see [`IMPLEMENTED.md`](../IMPLEMENTED.md) / [`docs/IMPLEMENTED.md`](IMPLEMENTED.md))  
**Target Platforms**: Android Phones, Tablets, Home Screen Widgets, Quick Settings, and Android TV (10-foot D-Pad Hub)

Related documents in this repository:
- [`IMPLEMENTED.md`](../IMPLEMENTED.md) / [`docs/IMPLEMENTED.md`](IMPLEMENTED.md) — Completed Phases 1–7 Architecture & Design Decisions (`v10.0`)
- [`AGENTS.md`](../AGENTS.md) — Architecture & Agent Guide
- [`.agents/skills/wemi-light-control/SKILL.md`](../.agents/skills/wemi-light-control/SKILL.md) — Agent Skill Definition
- [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](../.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md) — Complete `pywemo` & Home Assistant Wire Protocol Reference

---

## 1. Completed Foundation (`v10.0` — Phases 1–7)

All 7 foundational phases for local-LAN Belkin WeMo discovery, control, self-healing, GENA push events, scheduling, and zero-cloud `10.22.22.1` provisioning are complete and documented in [`IMPLEMENTED.md`](../IMPLEMENTED.md):

- **Phase 1**: `pywemo`-grade UPnP / SSDP (`MulticastLock` + 1s retransmit + unicast `/24` fallback) + 9-port smart probing (`49151..49159`) + SOAP control (`<BinaryState>Error</BinaryState>`, Dimmer `<brightness>`, `GetMetaInfo`, `GetSignalStrength`, `ChangeFriendlyName`, `ConfigureNightMode`).
- **Phase 2**: Real LAN Mode vs. Demo Showroom Mode, `Semaphore(3)` RTOS protection, per-device `Mutex`, 2-stage self-healing reconnect, optimistic UI with offline revert, and `serialNumber` deduplication.
- **Phase 3**: Embedded UPnP GENA HTTP `NOTIFY` push server (`SUBSCRIBE /upnp/event/basicevent1`, `TIMEOUT: Second-300`, HTTP 412 recovery) + 30s fallback poll.
- **Phase 4**: Zero-permission `/24` Wi-Fi subnet monitor (`WeMoNetworkMonitor`) + automatic `AccessPointSSID` sync from `GetMetaInfo`.
- **Phase 5**: App-driven `WeMoRuleScheduler` for daily `SCHEDULE` rules, live `MM:SS` `AUTO_OFF` countdown pills, and Dimmer `20%` Night Mode.
- **Phase 6**: Zero-cloud `10.22.22.1` WeMo Setup AP auto-detection banner and `WiFiSetup1` AES-128-CBC (Methods 1 & 2) provisioning wizard.
- **Phase 7**: 8 automated JUnit / Robolectric protocol & repository test suites (`WeMoProtocolTest.kt` & `ExampleRobolectricTest.kt`).

---

## 2. Next-Iteration Roadmap Overview (Phases 8–13)

With the core local UPnP/SSDP/SOAP/GENA engine complete, the next iteration transforms **WeMi Light Control** into a standalone, zero-cloud smart-home hub and OS-integrated controller:

| Phase | Feature Area | Key Deliverables |
| :--- | :--- | :--- |
| **Phase 8** | **On-Device Hardware Rules DB (`rules.db` SQLite over SOAP)** | Read, decode, and write the embedded SQLite `rules.db` (`urn:Belkin:service:rules:1#FetchRules` & `#StoreRules` Base64 ZIP) on OpenWrt switches (`F7C030`, `F7C059`, `F7C063`) so schedules & auto-off timers run inside the wall switch even when all phones are away from home. |
| **Phase 9** | **Android Quick Settings Tiles, Home Screen Widgets & Always-On TV Hub Service** | Android `TileService` quick toggles, Jetpack Glance / `AppWidgetProvider` skeuomorphic 1x1 & 4x1 room widgets, and an optional `BOOT_COMPLETED` foreground service for Android TV / wall tablets acting as a 24/7 LAN controller. |
| **Phase 10** | **Expanded WeMo Hardware Fleet (`Insight`, `Maker`, `Link`, Dimmer Long-Press & Faders)** | Real-time power/wattage/kWh monitoring for **WeMo Insight (`F7C029`)** via `insight1#GetInsightParams`, hardware fade ramps & `<LongPress>` scene triggers for **Dimmers (`F7C059`/`WDS060`)**, and **WeMo Maker (`F7C043`)** / **Link (`F7C031`)** support. |
| **Phase 11** | **One-Tap OS Wi-Fi Handoff (`WifiNetworkSpecifier`) for `10.22.22.1` Setup** | Android 10+ `WifiNetworkSpecifier` in-app connection to `WeMo.Light.*` / `WeMo.Mini.*` APs without leaving the app, plus `pywemo` Method 3 (`binaryOption`) password encryption and `basicevent1#ReSetup` factory reset control. |
| **Phase 12** | **Offline Astronomical Solar Calculator & GENA Motion-Sensor Automation Chains** | Offline NOAA latitude/longitude solar calculator for exact daily Sunrise/Sunset times (`Sunset - 20m`, `Sunrise + 15m`) and sub-second GENA `Motion` (`F7C028`) → multi-switch trigger chains with configurable hold timers. |
| **Phase 13** | **Household Profile Sync (JSON / QR Export-Import) & Local Webhook / Home Assistant Bridge** | Export/import remembered Wi-Fi Homes, room names, custom ordering, and rules via JSON file or on-screen QR code (ideal for syncing a phone setup onto an Android TV), plus an optional local REST/Webhook endpoint on Android TV. |

---

## 3. Detailed Specifications for Next Iteration

### Phase 8: On-Device Hardware Rules DB (`WeMoHardwareRulesManager.kt`)
Currently, `WeMoRuleScheduler` executes schedules and auto-off timers from the Android device over the LAN. However, 1st-Gen OpenWrt Belkin WeMo devices (`F7C030`, `F7C059`, `F7C063`) also run a local SQLite rule engine (`/tmp/Belkin_settings/rules.db`) inside the switch itself:
1. **`FetchRules` Inspection (`urn:Belkin:service:rules:1#FetchRules`)**:
   - Query `<ruleDbPath>` (typically `http://<ip>:<port>/rules.db`) and `<ruleDbVersion>`.
   - Download the ZIP archive from `<ruleDbPath>`, extract the inner `rules.db` SQLite file into a temporary cache file, and read `RULES`, `RULEDEVICES`, and `DEVICECOMBINATION` using Android's built-in `SQLiteDatabase.openDatabase`.
2. **Two-Way Hardware Rule Sync (`StoreRules`)**:
   - Package modified `rules.db` into a deflated ZIP archive, Base64-encode it into `<ruleDbBody>`, increment `<ruleDbVersion>`, and push via `urn:Belkin:service:rules:1#StoreRules` (`<processDb>1</processDb>`).
   - Gracefully fall back to app-driven `WeMoRuleScheduler` for 2nd-Gen RTOS devices (`WLS0403`, `WDS060`, `WSP080`) that return HTTP 404 on `/upnp/control/rules1`.
3. **Hardware Time Sync (`timesync1#TimeSync`)**:
   - Because Belkin's NTP servers were retired alongside the cloud shutdown, add `urn:Belkin:service:timesync:1#TimeSync` to push the phone's current UTC epoch, timezone offset, and DST flags directly to each switch so hardware schedules fire at the right local clock time.

---

### Phase 9: Android OS Widgets, Quick Settings & Always-On TV Hub Mode
1. **Skeuomorphic Home Screen Widgets (`WeMoAppWidgetProvider`)**:
   - **Single Switch 1x1 Widget**: Classic brushed-aluminum WeMo tile with glowing green/amber power ring and live latency dot.
   - **Room Group 4x1 Bar Widget**: Shows active Wi-Fi Home, room master toggle, and up to 4 pinned switches/dimmers.
2. **Android Quick Settings Tile (`WeMoQuickSettingsTileService`)**:
   - Allows pulling down the Android notification shade and toggling a favorite switch or whole room (e.g., `"Living Room Lights"`) with a single tap—even from the lock screen.
3. **Always-On Android TV / Tablet LAN Hub Service (`WeMoHubForegroundService`)**:
   - Optional toggle in **More** (`"Run as Always-On Local Automation Hub"`), automatically enabled on Android TV (`UI_MODE_TYPE_TELEVISION`) and started on `BOOT_COMPLETED`.
   - Keeps `WeMoGenaSubscriptionManager` and `WeMoRuleScheduler` alive in the background so scheduled rules, motion triggers, and `timesync1` clock updates run 24/7 on an Android TV box.

---

### Phase 10: Expanded Belkin WeMo Hardware Support
1. **WeMo Insight Energy Monitoring Plug (`F7C029` — `urn:Belkin:service:insight:1`)**:
   - Parse pipe-delimited `<InsightParams>` (`State|LastChangeTS|OnForSec|OnTodaySec|OnTotalSec|TimeWindow|AvgPowerW|CurrentPowerMw|TodayMwMin|TotalMwMin|StandbyThresholdMw`).
   - Distinguish `State = 1` (**ON / Drawing Load**) vs. `State = 8` (**Standby** — plugged-in appliance is idle below `StandbyThresholdMw`).
   - Render live Wattage (`W`) and daily energy (`Wh`/`kWh`) telemetry pills in [`WeMoDeviceRow`](../app/src/main/java/com/example/ui/components/WeMoDeviceRow.kt).
2. **Dimmer (`F7C059` / `WDS060`) Fader Ramps & Physical `<LongPress>` Scene Triggers**:
   - Support `<fader>` SOAP parameter (`<fader>300:1770000000:1:0:100</fader>`) for smooth sleep/wake fade-out and fade-in transitions.
   - Listen for `<LongPress>1</LongPress>` in `WeMoGenaSubscriptionManager` so long-pressing a wall dimmer can trigger a multi-device WeMi Room Scene (e.g. turning off all switches in the house).
3. **WeMo Maker (`F7C043`) & WeMo Link (`F7C031`)**:
   - Support momentary relay mode (garage door / gate opener) and dry-contact sensor status on WeMo Maker.
   - Support `urn:Belkin:service:bridge:1#GetEndDevices` and `#SetDeviceStatus` for WeMo Link Zigbee smart bulbs.

---

### Phase 11: Frictionless `10.22.22.1` Setup with `WifiNetworkSpecifier`
1. **In-App Setup AP Join (`Android 10+ WifiNetworkSpecifier`)**:
   - Add a **"Find & Join WeMo.xxx Setup AP"** button inside `ProvisionWeMoApDialog` using `WifiNetworkSpecifier.Builder().setSsidPattern(PatternMatcher("WeMo.", PatternMatcher.PATTERN_PREFIX)).build()`.
   - Bind the provisioning OkHttp client specifically to the `Network` returned by `ConnectivityManager.requestNetwork(...)` so `10.22.22.1` SOAP calls route over the WeMo Setup AP even while mobile data remains active.
2. **Method 3 (`binaryOption`) Password Encryption & Hardware Reset (`ReSetup`)**:
   - Add `pywemo` Method 3 (`len + iv + ciphertext` prefix) alongside Methods 1 and 2 in `WeMoProvisionerClient`.
   - Expose a confirmation-protected **"Factory Reset Wi-Fi (`ReSetup`)"** action in the device edit dialog (`urn:Belkin:service:basicevent:1#ReSetup`) so users migrating to a new router can put an existing switch back into `10.22.22.1` setup mode without unscrewing the wall faceplate.

---

### Phase 12: Offline Astronomical Solar Calculator & GENA Motion Chains
1. **Offline Sunrise/Sunset Calculator (`WeMoSolarCalculator.kt`)**:
   - Compute exact daily local Sunrise, Sunset, and Civil Twilight times from an optional user-configured city/latitude-longitude (zero location permission required if selected from a city preset or manual coordinates).
   - Automatically update `SUNSET` / `SUNRISE` rules each day (with custom offsets like `Sunset - 15m` or `Sunrise + 30m`).
2. **Real-Time Motion Sensor (`F7C028`) Automation Chains**:
   - When `WeMoGenaSubscriptionManager` receives `<BinaryState>1</BinaryState>` from a `MOTION_SENSOR` (`urn:Belkin:device:sensor:1` / `urn:Belkin:device:NetCamSensor:1`), immediately evaluate all enabled `MOTION_TRIGGER` rules and turn ON the target room/switches, starting an automatic re-triggerable hold timer (`autoOffMinutes`).

---

### Phase 13: Household Configuration Sync (JSON / QR) & Android TV Local Bridge
1. **Zero-Cloud Household Sync (`WeMoBackupManager.kt`)**:
   - Export all `wemo_home_networks`, `wemo_devices` (including custom names, rooms, and sort order), and `wemo_rules` to a compact JSON file or multi-frame / compressed QR code.
   - On Android TV, display a **"Pair from Phone"** local HTTP receiver (`http://<tv-ip>:8990/sync`) so the user can push their entire phone configuration to their living room TV in one tap over the home LAN.
2. **Optional Local REST / Webhook Endpoint**:
   - Allow local scripts, Tasker, or Home Assistant on the LAN to query or trigger WeMi scenes via simple local HTTP calls when Android TV Hub Mode is active.
