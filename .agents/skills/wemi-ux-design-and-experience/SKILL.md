---
name: wemi-ux-design-and-experience
description: Android & Android TV UX design architecture, navigation patterns, accessibility standards, user behavioral mental models, strict layout/typography invariants, and Critical User Journeys (CUJs) for WeMi Light Control. EXCLUSIVELY OWNED by the Google AI Studio Agent (other agents MUST read and follow this skill when modifying UI or UX flows, but MUST NOT edit this skill, UX_DESIGN.md, or UX_DESIGN_AND_EXPERIENCE.md).
---

# WeMi UX Design, Behavioral Modeling & Critical User Journeys Skill (`wemi-ux-design-and-experience`)

> [!CAUTION]
> **EXCLUSIVE OWNERSHIP & READ-ONLY RULE FOR PEER AGENTS**:
> - **Owner**: **Google AI Studio Agent (`AI Studio Build`) — SOLE AUTHOR & MAINTAINER**.
> - **Policy**: **ONLY the Google AI Studio Agent may modify `.agents/skills/wemi-ux-design-and-experience/SKILL.md`, [`UX_DESIGN.md`](../../UX_DESIGN.md), or [`UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md).**
> - **All other agents** (CLI agents, background sync agents, test runners, and external coding assistants) **MUST read, understand, and obey** the UX guidelines, navigation patterns, accessibility standards, behavioral models, visual state rules, and layout invariants documented here when implementing features or writing tests, **but MUST NOT edit, overwrite, or reformat these files.**

For the complete Android UX guidelines (navigation patterns, accessibility standards for smart home controls, and user interaction flows) and behavioral architecture, read [`../../UX_DESIGN.md`](../../UX_DESIGN.md) and [`../../UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md).

---

## 1. Core Behavioral Mental Models (Mandatory UX Contract)

1. **Three-State Hardware Truth (`ON` vs. `OFF Accessible Standby` vs. `Disconnected`)**:
   - Never conflate **Power OFF** (switch is reachable on Wi-Fi and waiting in standby) with **Disconnected / Offline** (switch is unreachable on the LAN).
   - **ON (`isReachable && isOn`)**: Lit custom hardware illustration with radial glow, emerald corner badge (`#059669`) with white IEC `"I/O"` symbol, green `ON` pill (`ON • X%` for dimmers), and glowing cyan-teal `WeMoPowerRingButton` (`#00B8D4` → `#00838F`).
   - **OFF Standby (`isReachable && !isOn`)**: Crisp dark-slate hardware illustration, **very dark gray (`#23272F`)** corner badge with the **exact same white IEC `"I/O"` power symbol** (`drawIecPowerSymbol`), neutral `OFF` pill (`OFF • X%` for dimmers), and **very dark gray (`#23272F` ring / `#181B20` glyph) `WeMoPowerRingButton`**. Never use an amber bullseye for Power OFF.
   - **Disconnected (`!isReachable`)**: Faded silhouette with dashed red border, red (`#DC2626`) corner badge with white broken-link diagonal slash (`drawDisconnectedSymbol`), `Not Detected` / `Offline • Timeout` status, and the 64dp red **`WeMoDisconnectedRingButton`** (`reconnect_button_${device.id}`) which triggers a 2-stage self-healing reconnect on tap.

2. **Semantic Appliance Identity (`WeMoDeviceCategory`)**:
   - Users think in terms of the appliance they are controlling (`Christmas Tree`, `Laundry Fan`, `Girls Light`, `Hallway Motion`), not raw UPnP sockets.
   - `classifyDeviceCategory(name, deviceType)` in [`WeMoEntities.kt`](../../app/src/main/java/com/example/data/WeMoEntities.kt) automatically selects the matching 3-state `Canvas` illustration (`CHRISTMAS_TREE`, `FAN`, `LIGHT`, `MOTION_SENSOR`, `SWITCH_PLUG`) and renders a live preview inside `AddOrEditWeMoDeviceDialog` and `ProvisionWeMoApDialog` as the user types.

3. **Spatial Room Grouping & Honest Room Inference (`inferRoomFromName`)**:
   - `inferRoomFromName` in [`WeMoEntities.kt`](../../app/src/main/java/com/example/data/WeMoEntities.kt) maps natural household words (`"bed"`, `"bedside"`, `"nightstand"`, `"girls"`/`"boys"`/`"kids"` → `"Bedroom"`; `"stair"`/`"stairway"`/`"upstairs"`/`"downstairs"`, `"landing"`, word-bounded `"hall"` → `"Hallway & Landing"`; etc.) and defaults unrecognized names to **`"Unassigned"`** (sorted after named rooms) rather than dumping unrelated switches into `"Living Room"`.

4. **Firmware Quirk Shielding (`isWemoDeviceSetupSsid`)**:
   - Belkin WeMo switches frequently report their own factory soft-AP SSID (`Wemo.LS3W.220`, `WeMo.Switch.xxx`, `WeMo.Light.xxx`, `WeMo.Mini.xxx`) in field 4 (`AccessPointSSID`) of `metainfo1#GetMetaInfo`.
   - Always filter soft-AP SSIDs via `WeMoXmlAndSkuParser.isWemoDeviceSetupSsid` and sanitize polluted DB rows in `WeMoRepository.ensureSeeded()` so the user's Wi-Fi Home is never named after a switch's setup hotspot.

---

## 2. Five Non-Negotiable Jetpack Compose Layout Invariants

Whenever modifying or reviewing UI components in `app/src/main/java/com/example/ui/`, verify all five layout invariants:

1. **Full-Width Unobstructed Titles on Line 1 (`AutoResizingDeviceNameText`)**:
   - In `WeMoDeviceRow`, `WeMoRoomGroupHeaderCard`, `WeMoWifiHomeBar`, and `WeMoTvDeviceCard`, the primary title (`device.name`, `roomName`, `homeName`) **must** sit on **Line 1** with `Modifier.fillMaxWidth()` and `AutoResizingDeviceNameText` (`17.5–18sp` stepping down to `11sp`).
   - All secondary badges (`connection_status_badge_${id}`, `power_state_badge_${id}`, IP/Auto-Off, `category_badge_${id}`, `"$active/$total ON"`) **must** sit on **Line 2** (`FlowRow` or `Row` with `maxLines = 1, softWrap = false`).
   - Do not re-introduce a redundant `device.roomName` chip inside `WeMoDeviceRow` or `WeMoTvDeviceCard`.

2. **Identical Rendered Height Across All Collapsed Room Header Cards**:
   - Every collapsed `WeMoRoomGroupHeaderCard` (`room_header_$roomName`) must render at the exact same height regardless of whether the room name is short (`"Living Room"`) or long (`"Hallway & Landing"`), verified by `abs(hallwayHeight - livingRoomHeight) <= 2f` in [`WeMoEndToEndUiTest.kt`](../../app/src/test/java/com/example/WeMoEndToEndUiTest.kt).

3. **Compact Single-Line Top Bars (`WeMoWifiHomeBar` & `WeMoSsdpDiscoveryBanner`)**:
   - Keep `WeMoWifiHomeBar` (`wifi_home_selector_bar`) and `WeMoSsdpDiscoveryBanner` (`ssdp_discovery_banner`) compact (`28.dp` chips, `32.dp` action buttons, `maxLines = 1, softWrap = false`) so room and switch cards remain prominent above the fold.

4. **Top-First Dimmer Controls in `WeMoExpandedDrawer`**:
   - In [`WeMoDeviceDiagnosticsDrawer.kt`](../../app/src/main/java/com/example/ui/components/WeMoDeviceDiagnosticsDrawer.kt), keep the dimmer **Brightness Slider (`brightness_slider_${id}`)** and wrapping `FlowRow` preset chips (`1%`, `21%`, `25%`, `50%`, `75%`, `100%`, `Night Mode (20%)`) at the **top** of `WeMoExpandedDrawer` above RTT/endpoint diagnostics.

5. **Pointer Pass-Through Toast Banner & Synchronous Dialog Dismissal**:
   - Keep `status_feedback_banner` in [`MainActivity.kt`](../../app/src/main/java/com/example/MainActivity.kt) pointer pass-through (non-clickable `Box`) so transient status messages never block touches on list rows or bottom tabs.
   - Dismiss modal dialog state flows (`_showAddDeviceDialog`, `_editingDevice`, `_showAddHomeDialog`, `_showAddRuleDialog`) synchronously before launching `viewModelScope` IO coroutines.

---

## 3. Critical User Journeys (CUJ Verification Checklist)

Every major UX flow is backed by deterministic Robolectric + Jetpack Compose E2E tests in [`ExampleRobolectricTest.kt`](../../app/src/test/java/com/example/ExampleRobolectricTest.kt) and [`WeMoEndToEndUiTest.kt`](../../app/src/test/java/com/example/WeMoEndToEndUiTest.kt):

- **CUJ 1 (Reset → SSDP UDP Discovery → Auto-Adopt & Manual Adopt)**: `reset_lan_data_chip` → `confirm_reset_empty_button` → `empty_state_scan_ssdp_button` → `open_ssdp_scanner_button` → `adopt_ssdp_device_<Name>`.
- **CUJ 2 (Optimistic Switch Toggle & Offline Reconnect Ring)**: `power_button_${id}` (`Toggle power for <Name>, currently Off/On`) and `reconnect_button_${id}` (`Reconnect <Name>`).
- **CUJ 3 (Multi-Modal Dimmer Scaling)**: Preset chips (`75%`, `21%`), continuous `brightness_slider_${id}`, `Night Mode (20%)`, `room_dimmer_button_${room}` → `room_dimmer_slider_${room}`, and `wemi TV` (`tv_remote_dpad_dim_up`, `tv_remote_dpad_dim_down`, `tv_scene_movie`).
- **CUJ 4 (Zero-Cloud `10.22.22.1` Provisioning & Screenshot Debug Panel)**: `open_provision_ap_wizard_button` → `provision_ap_status_box` → `provision_debug_panel` (`Copy`, `Save/Share`, `keep_provision_dialog_open_chip`, and all 6 `pywemo` AES-128-CBC method × length-suffix chips).
