---
name: wemi-ux-design-and-experience
description: "EXCLUSIVE TO THE GOOGLE AI STUDIO AGENT (Read-Only for All Other Agents). Governs the UX design, skeuomorphic visual language, 3-state hardware mental model, layout invariants, and Critical User Journeys (CUJs) for WeMi Light Control on Android & Android TV."
---

# WeMi Light Control — UX Design, Behavioral Modeling & CUJ Skill (`wemi-ux-design-and-experience`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP RULE**:
> - **Owner**: **Google AI Studio Agent (`AI Studio Build`) — EXCLUSIVE OWNER.**
> - **Read-Only for Other Agents**: All other agents (CLI agents, GitHub sync agents, test runners, and external coding assistants) **MUST read and strictly enforce** the UX rules, layout invariants, and Critical User Journeys (CUJs) defined in this skill, [`UX_DESIGN.md`](../../../UX_DESIGN.md), and [`UX_DESIGN_AND_EXPERIENCE.md`](../../../UX_DESIGN_AND_EXPERIENCE.md), **but MUST NOT edit, overwrite, or modify this `SKILL.md` file, `UX_DESIGN.md`, or `UX_DESIGN_AND_EXPERIENCE.md`.**

---

## 1. Core UX Mental Models to Enforce (`org.suppressingfire.wemi`)

1. **Three-State Hardware Truth (`ON` vs. `OFF Accessible Standby` vs. `Disconnected`)**:
   - **Accessible & ON (`isReachable && isOn`)**: Illuminated appliance illustration + glowing emerald corner badge + green `ON` (`ON • XX%`) status pill + glowing cyan-teal (`#00838F`) `PowerRingButton` with white IEC 60417-5009 `"I/O"` power glyph (`drawIecPowerSymbol`).
   - **Accessible & OFF Standby (`isReachable && !isOn`)**: Crisp dark-slate appliance housing + **very dark charcoal gray (`#23272F`)** corner badge with white IEC `"I/O"` glyph + neutral `OFF` (`OFF • XX%`) pill + **very dark charcoal gray (`#23272F` ring / `#181B20` glyph)** `PowerRingButton` with the **same IEC `"I/O"` power symbol**. Never use an amber bullseye or offline icon for a reachable OFF switch.
   - **Disconnected / Unavailable (`!isReachable`)**: Muted silhouette + dashed red border + red (`#DC2626`) corner badge with broken-link slash (`drawDisconnectedSymbol`) + `"Not Detected"` pill + `DisconnectedRingButton` (`reconnect_button_${id}`) that triggers a 2-stage self-healing reconnect on tap.

2. **Five Semantic Appliance Categories (`DeviceCategory`)**:
   - `classifyDeviceCategory(name, deviceType)` in `DeviceEntities.kt` maps switch names to bespoke Jetpack Compose `Canvas` illustrations (`HardwareIllustrations.kt`, `ChristmasTreeIllustration.kt`, `FanIllustration.kt`, `LightAndSensorIllustrations.kt`, `SwitchAndPlugIllustration.kt`):
     - `CHRISTMAS_TREE` (`Tree Lights`)
     - `FAN` (`Fan`)
     - `LIGHT` (`Light`)
     - `MOTION_SENSOR` (`Motion`)
     - `SWITCH_PLUG` (`Switch`)

3. **Spatial Room Mental Model & `"Unassigned"` Fallback (`inferRoomFromName`)**:
   - Recognizes natural family naming (`"bed"`, `"bedside"`, `"nightstand"`, `"girls"`, `"boys"`, `"kids"` → `"Bedroom"`; `"stair"`, `"stairway"`, `"upstairs"`, `"downstairs"`, `"landing"`, word-bounded `"hall"` → `"Hallway & Landing"`).
   - Unrecognized names default to **`"Unassigned"`** (sorted at the bottom after named rooms), never `"Living Room"`.
   - On startup/SSDP scan in Real LAN Mode (`resolveDeviceRoomAssignment`), switches previously stuck in `"Living Room"` are automatically migrated to their inferred rooms while preserving custom user room assignments.

4. **Native Hardware Form-Factor Routing (Phone/Tablet vs. Android TV)**:
   - Phone/tablet (`Scaffold` with compact single-row `TopHeader` in `HeaderAndNav.kt` and `BottomNavigationBar` in `BottomNavBar.kt`) and Android TV (`TvDashboardScreen` under `Configuration.UI_MODE_TYPE_TELEVISION`) are strictly native to their hardware form factor and interaction model. Do not add manual Phone/TV mode switch buttons to either layout.
   - On Android TV (`wemi TV`), use a single compact top header row paired with a full-height **3-Column Room-Grouped Switch Grid (`GridCells.Fixed(3)`)** with full-span `TvRoomSectionHeader` headers (`tv_room_header_<Room>` in `TvRoomSectionHeader.kt`) that are deterministically selected when moving Up or Down from any column (including Column 1) and support `Left / Right` (`-10%` / `+10%`) room dimmer stepping, unmistakable 10-foot cursor focus (`3.5.dp` `#22D3EE` border, `#133B66` surface, `FOCUSED • OK` badge, with empty-home initial focus on `tv_scan_button`), and a focusable **Dimmer Slider (`tv_card_dim_mode_<id>` in `TvDimmerControls.kt`)** below the dimmer's On/Off Toggle so Google TV remotes without a Play/Menu button can navigate `Down` from the toggle into the slider, press `Left / Right` (`-10%` / `+10%`) to adjust brightness, press `Up` or system `Back` (`BackHandler`) to return to the toggle, and press `Down` to move to the widget below.

5. **Zero-Launch Home Screen Light & Room Toggle Widgets (`widget/` — CUJ 8)**:
   - Supports one-tap in-app pinning (`room_pin_widget_button_<Room>` in `RoomGroupCard.kt`, `pin_device_widget_<id>` in `DeviceDiagnosticsDrawer.kt` via API 26+ `requestPinAppWidget`) and launcher drawer configuration (`SwitchWidgetConfigureActivity.kt` + `WidgetPickerCards.kt` with Rooms/Lights tabs, Wi-Fi Home filters, and `>= 48.dp` touch targets).
   - Mirrors WeMi's 3-state hardware language in `RemoteViews` (`widget_bg_on` Ocean Teal, `widget_bg_off` dark charcoal standby, `widget_bg_offline` red dashed) and toggles lights/rooms directly over local-LAN UPnP SOAP via `WidgetActionExecutor` without launching `MainActivity`.

---

## 2. Five Mandatory Layout & Interaction Invariants

1. **Dedicated Full-Width Title Line (`AutoResizingDeviceNameText` / `AutoShrinkSwitchName`)**:
   - In `DeviceRow.kt`, `device.name` must occupy **Line 1** across the full card width (`Modifier.fillMaxWidth().testTag("device_name_${device.id}")`).
   - All secondary badges (`connection_status_badge`, `power_state_badge`, IP/Auto-Off, `category_badge`) must sit in a wrapping `FlowRow` on **Line 2** alongside the hardware icon and 64dp Power/Disconnected ring.
   - Omit redundant room badges inside `DeviceRow` and `TvDeviceCard`.

2. **Uniform Room Group Header Card Height**:
   - In `RoomGroupCard.kt`, `roomName` sits on **Line 1** (`singleLineOnly = true`), and the `"$activeCount/$totalControllable ON"` pill (`maxLines = 1, softWrap = false`) + active/latency subtitle (`maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`) sit on **Line 2**.
   - Every collapsed `RoomGroupHeaderCard` must render at the **exact same height** regardless of room name length (`abs(heightA - heightB) <= 2f`).

3. **Compact Single-Line Top Bars**:
   - `WifiHomeBar` and `SsdpDiscoveryBanner` must use single-line titles/subtitles (`maxLines = 1`) and compact `28.dp–32.dp` action chips/buttons so switches remain above the fold.

4. **Top-First Primary Controls in `ExpandedDrawer`**:
   - For dimmable lights (`LIGHTBULB_DIMMER`), `DeviceDiagnosticsDrawer.kt` must render the **Brightness Level Slider (`1–100%`)** and quick preset chips (`1%`, `21%`, `25%`, `50%`, `75%`, `100%`, `Night Mode (20%)`) at the **very top** of `ExpandedDrawer`, above RTT ping and UPnP endpoint metadata.

5. **Non-Blocking Status Toasts & Synchronous Dialog Dismissal**:
   - `status_feedback_banner` in `MainActivity.kt` must remain non-clickable so touches pass through to underlying controls.
   - ViewModel save/confirm handlers must close modal dialogs synchronously on the main thread before launching background `Dispatchers.IO` work.

---

## 3. Mandatory Stage 1 Release Audit: Obsolete UI & UX Hygiene Checklist

Before initiating any release review (`Stage 1: ai_studio_ux_cuj` in [`RELEASE_PROCESS.md`](../../../RELEASE_PROCESS.md)), audit all Compose screens, dialogs, drawers, widgets, and Android TV views and clean up any regressions across these six checks:
1. **No Dead or No-Op Controls**: Every switch, button, and chip must trigger real, persisted, user-meaningful behavior (no legacy toggles whose branches render identical UI).
2. **No Internal Dev / Asset Showcase Cards**: Keep promotional, build-check, or Store-asset preview cards out of end-user screens and delete unused bundled drawable assets.
3. **Strict Demo vs. Real LAN Separation**: Gate demo/preset actions (such as `Reset Preset` or simulated sensor triggers) behind `isDemoShowroomMode`, and ensure Real LAN labels accurately reflect live hardware actions (`"Refresh Motion"` vs. `"Simulate Motion"`).
4. **No Redundant Chrome or Unprotected Destructive Actions**: Hide empty-state or discovery banners once populated (`SsdpDiscoveryBanner`), avoid exposing one-tap destructive `Reset` chips on everyday control bars, and guard destructive resets behind a confirmation dialog on the `More` tab.
5. **Progressive Disclosure & Clean User-Facing Copy**: Collapse technical diagnostic panels (`ProvisionDebugPanel`) while idle with an explicit `Details`/`Hide` toggle (auto-expanding on traces or errors) and strip developer jargon like `(SCREENSHOT)`.
6. **Context-Relevant Conditional Controls**: Only surface specialized scene/feature controls when matching hardware exists on the active home (e.g., show Android TV `Holiday ON` only when `devices.any { isHolidayLightDevice(it) }` and target only matching holiday switches).

---

## 4. Automated E2E CUJ Verification (`ExampleRobolectricTest.kt`, `EndToEndUiTest.kt`, `HardwareCujEndToEndTest.kt`, `ProvisioningAndGenaCujE2ETest.kt`, `TvAndHeaderLayoutE2ETest.kt`, `TvNavigationAndScenesE2ETest.kt`, `HomeScreenWidgetTest.kt`, `HomeScreenWidgetUiE2ETest.kt`, `AppFlowsAndManagementE2ETest.kt` & `TelemetryDiagnosticsTest.kt` — `102/102` Tests Across `20` Suites)

Whenever UI components, widgets, ViewModel flows, or repository methods are touched, verify all 8 Critical User Journeys documented in [`UX_DESIGN.md`](../../../UX_DESIGN.md) and [`UX_DESIGN_AND_EXPERIENCE.md`](../../../UX_DESIGN_AND_EXPERIENCE.md) by running:

```bash
sh gradlew testDebugUnitTest --rerun-tasks
```


