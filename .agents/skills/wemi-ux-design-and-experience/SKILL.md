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
   - Phone/tablet (`Scaffold` with compact single-row `TopHeader` and `BottomNavigationBar`) and Android TV (`TvDashboardScreen` under `Configuration.UI_MODE_TYPE_TELEVISION`) are strictly native to their hardware form factor and interaction model. Do not add manual Phone/TV mode switch buttons to either layout.
   - On Android TV (`wemi TV`), use a single compact top header row paired with a full-height **3-Column Room-Grouped Switch Grid (`GridCells.Fixed(3)`)** with full-span `TvRoomSectionHeader` headers (`tv_room_header_<Room>`), unmistakable 10-foot cursor focus (`3.5.dp` `#22D3EE` border, `#133B66` surface, `FOCUSED • OK` badge), and a dedicated **Dimmer Adjust Mode (`DIM ◀/▶`)** so `D-Pad Left / Right` never gets trapped on dimmer cards during normal 3-column navigation.

---

## 2. Five Mandatory Layout & Interaction Invariants

1. **Dedicated Full-Width Title Line (`AutoShrinkSwitchName`)**:
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

## 3. Automated E2E CUJ Verification (`EndToEndUiTest.kt`, `HardwareCujEndToEndTest.kt` & `ExampleRobolectricTest.kt`)

Whenever UI components, ViewModel flows, or repository methods are touched, verify all 7 Critical User Journeys documented in [`UX_DESIGN.md`](../../../UX_DESIGN.md) and [`UX_DESIGN_AND_EXPERIENCE.md`](../../../UX_DESIGN_AND_EXPERIENCE.md) by running:

```bash
./gradlew testDebugUnitTest --rerun-tasks
```
