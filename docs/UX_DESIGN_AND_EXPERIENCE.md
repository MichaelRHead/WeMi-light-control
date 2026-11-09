# WeMi Light Control — Android & Android TV UX Design, Behavioral Model & Critical User Journeys (`UX_DESIGN_AND_EXPERIENCE.md`)

**Repository**: `MichaelRHead/WeMi-light-control` (`com.example` / `com.aistudio.wemiswitch.kxpqzr`, `v17.0`)  
**Document & Skill Owner**: **Google AI Studio Agent (`AI Studio Build`) — EXCLUSIVE OWNER**  
**Companion Agent Skill**: [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](../.agents/skills/wemi-ux-design-and-experience/SKILL.md)

> [!CAUTION]
> **EXCLUSIVE OWNERSHIP & READ-ONLY GOVERNANCE RULE FOR OTHER AGENTS**:
> - **This document (`UX_DESIGN_AND_EXPERIENCE.md` and `docs/UX_DESIGN_AND_EXPERIENCE.md`) and its companion skill (`.agents/skills/wemi-ux-design-and-experience/SKILL.md`) are owned exclusively by the Google AI Studio Agent.**
> - **ONLY the Google AI Studio Agent is permitted to author, edit, or modify these files.**
> - **All other agents** (CLI coding agents, background sync agents, test runners, and external assistants) **MUST read, understand, and strictly adhere to** the UX principles, layout invariants, behavioral models, and Critical User Journeys (CUJs) defined here when writing code or tests, **but MUST NOT modify, overwrite, or reformat these files.**

Related documentation:
- [`AGENTS.md`](../AGENTS.md) — Architecture & Agent Guide
- [`IMPLEMENTED.md`](../IMPLEMENTED.md) / [`docs/IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Change Log (`v1.0` → `v17.0`)
- [`UPCOMING_FEATURES.md`](../UPCOMING_FEATURES.md) / [`docs/UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`USER_GUIDE.md`](../USER_GUIDE.md) / [`docs/USER_GUIDE.md`](USER_GUIDE.md) — Modular User Guide Index & Feature Guides ([`docs/user-guide/`](user-guide/))
- [`docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Code Modularization & E2E Validation Plan

---

## 1. UX Vision: Tactile Physicality Meets Zero-Latency Local Trust

Most smart-home apps fail their users in two ways:
1. **Cloud Indirection & Ambiguous State**: They treat a wall switch 15 feet away as an abstract cloud record, hiding whether a switch is actually reachable on the local Wi-Fi versus powered OFF, and leaving users staring at spinners when a router reassigns a DHCP address.
2. **Sterile, Cramped Card Layouts**: They crowd device names, room labels, signal icons, and toggle controls onto a single horizontal row—causing multi-word switch names (*"Hallway & Landing"*, *"Upstairs Master Bedroom Chandelier"*) to truncate to 2 characters or wrap vertically into broken 1-character-per-line columns.

**WeMi Light Control** is engineered around the opposite premise: **a smart-home controller should feel as immediate, legible, and trustworthy as a physical wall paddle.** Every visual surface, state transition, icon, and layout constraint is designed to model how humans actually perceive and operate lighting in their home.

---

## 2. Modeling User Behavior & Mental Models

### 2.1 Mental Model 1: The Three-State Hardware Truth (`ON` vs. `OFF Accessible Standby` vs. `Disconnected`)
When a user glances at a switch list, their brain asks two distinct questions in order:
1. *"Can my phone talk to this switch right now?"* (Connectivity)
2. *"Is the power relay ON or OFF?"* (Load State)

Conflating **OFF** with **Offline** erodes trust. Therefore, every switch row and custom Canvas hardware illustration in **WeMi Light Control** renders **three unmistakable visual states**:

```mermaid
flowchart LR
    Probe["Startup / Live LAN State\n(GetBinaryState + GENA NOTIFY)"]
    Probe -->|Reachable & Relay = 1| StateOn["1. Accessible & ON\n• Lit Hardware Illustration + Radial Glow\n• Emerald Corner Badge with White IEC 'I/O'\n• Green 'ON' Pill + Glowing Cyan-Teal 'I/O' Power Ring"]
    Probe -->|Reachable & Relay = 0| StateOff["2. Accessible & OFF (Standby)\n• Crisp Dark-Slate Hardware Housing\n• Very Dark Gray (#23272F) Corner Badge with White IEC 'I/O'\n• Neutral 'OFF' Pill + Very Dark Gray (#23272F) 'I/O' Power Ring"]
    Probe -->|Unreachable / Timeout| StateOffline["3. Disconnected / Unavailable\n• Muted Silhouette + Dashed Red Border\n• Red (#DC2626) Corner Badge with Broken-Link Slash\n• 'Not Detected' Label + Red Disconnected Ring Button"]
```

- **Why ON and OFF Share the Same IEC `"I/O"` Power Symbol (`drawIecPowerSymbol`)**:
  - A physical Belkin WeMo switch has an illuminated power icon on its paddle. Using an unfamiliar symbol (like an amber bullseye) for OFF confuses users into thinking the switch is in a warning state.
  - By rendering the **exact same IEC 60417-5009 power symbol** (`broken circle arc + vertical stem`) in **glowing cyan-teal (`#00838F`) when ON** and **very dark charcoal gray (`#23272F` ring / `#181B20` glyph) when OFF**, the button always reads as a tactile power switch ready to be pressed.
- **Why Unavailable Devices Swap the Power Ring for a Disconnected Ring (`WeMoDisconnectedRingButton`)**:
  - When a switch is unreachable (`!device.isReachable`), showing a normal power button invites a dead tap. Replacing it with `WeMoDisconnectedRingButton` (dashed red outer ring + broken-link diagonal slash `drawDisconnectedSymbol`) communicates *both* that the switch is offline *and* that tapping the ring will actively run a 2-stage self-healing reconnect probe.

### 2.2 Mental Model 2: Semantic Appliances, Not Generic Outlets (`WeMoDeviceCategory`)
Users rarely think in terms of Belkin hardware SKUs (`F7C030` vs. `WSP080`). They think in terms of **what is plugged into or wired to the switch**: *"Christmas Tree"*, *"Laundry Fan"*, *"Girls Light"*, *"Stairway Light"*.

`classifyDeviceCategory(name, deviceType)` automatically maps the user's natural naming into five multi-layered Jetpack Compose `Canvas` illustrations (`WeMoHardwareIllustrations.kt`):

| Semantic Category | Natural User Naming Triggers | Visual Feedback When `ON` | Visual Feedback When `OFF (Standby)` |
| :--- | :--- | :--- | :--- |
| **`CHRISTMAS_TREE` (`Tree Lights`)** | `tree`, `christmas`, `xmas`, `evergreen`, `spruce`, `fir `, `garland`, `wreath`, `tinsel`, `noel` | 3-tiered evergreen canopy with glowing gold star topper and 9 illuminated multi-color string bulbs | Deep pine silhouette with unlit frosted bulbs, wood trunk, and dark-gray IEC standby corner badge |
| **`FAN` (`Fan`)** | `fan`, `blower`, `vent`, `exhaust`, `ventilat`, `circulator` | Continuous `360°` spinning 4-blade rotor (`rememberInfiniteTransition`) with cyan airflow halo | Stationary brushed-steel blades inside a high-contrast dark-slate cage |
| **`LIGHT` (`Light`)** | `light`, `lamp`, `bulb`, `sconce`, `chandelier`, `lantern`, `pendant`, `flood`, `spot`, `dimmer`, `led`, `candle`, `strip`, `fixture` (or `LIGHTBULB_DIMMER`) | Warm-to-cyan luminous glass globe whose radial aura scales continuously (`1–100%`) with dimmer brightness | Smoke-slate glass dome revealing the unlit internal filament coil and metallic Edison base |
| **`MOTION_SENSOR` (`Motion`)** | `motion`, `sensor` (or `MOTION_SENSOR`) | Glowing emerald PIR lens dome with radiating motion wave arcs | Slate sensor housing with last-motion timestamp |
| **`SWITCH_PLUG` (`Switch`)** | All other switches & appliances (`Coffee Maker`, `Heater`, `Pump`) | Brushed wall paddle / pass-through mini plug with glowing cyan-green status bar | Tactile slate paddle / outlet face with dark-gray IEC standby badge |

### 2.3 Mental Model 3: Spatial Room Mental Model & Zero-Config Room Inference (`inferRoomFromName`)
In a house with 15–20 switches, a flat list forces cognitive scanning on every tap. Humans navigate their home spatially (*"turn off the Upstairs / Hallway lights"*, *"check the Bedroom"*).
- **Natural Household Vocabulary**: `inferRoomFromName` recognizes how families actually name switches:
  - `"bed"`, `"bedside"`, `"nightstand"`, `"bunk"`, `"primary"`, `"girls"`/`"girl"`, `"boys"`/`"boy"`, `"kids"`/`"kid"` → **`Bedroom`**
  - `"stair"` (`"stairway"`, `"stairs"`, `"upstairs"`, `"downstairs"`), `"landing"`, `"foyer"`, `"entry"`, `"mudroom"`, `"corridor"`, word-bounded `"hall"` → **`Hallway & Landing`**
  - `"kitchen"`, `"island"`, `"pantry"`, `"nook"` → **`Kitchen`**
  - `"basement"`, `"laundry"`, `"utility"`, `"cellar"` → **`Basement`**
- **Honest `"Unassigned"` Fallback**: If a switch name has no recognizable room keyword, it is placed in **`"Unassigned"`** (sorted at the bottom after named rooms) rather than silently dumped into `"Living Room"`. And on startup/SSDP scan in Real LAN Mode, switches previously stuck in `"Living Room"` are automatically migrated to their inferred rooms while preserving custom user edits.

### 2.4 Mental Model 4: Frictionless Multi-Home Context & Firmware Quirk Shielding
Users who own WeMo switches at more than one property (or across distinct routers/VLANs) expect the app to remember their switches per location without nagging for Android GPS/Location permissions (`ACCESS_FINE_LOCATION`):
- **Zero-Permission Subnet Memory**: `WeMoNetworkMonitor` inspects the active Wi-Fi interface's `/24` IPv4 prefix (`192.168.86.x`) to select or create the matching `WeMoHomeNetworkEntity` (`"My Home"`).
- **Shielding the UI from Firmware Soft-AP Pollution**: Physical Belkin WeMo switches (such as 2nd-Gen 3-Way switches `WLS0403` reporting `Wemo.LS3W.220` and 1st-Gen switches reporting `WeMo.Switch.xxx` in field 4 of `metainfo1#GetMetaInfo`) frequently return their own factory-setup hotspot name instead of the home router's SSID. `WeMoXmlAndSkuParser.isWemoDeviceSetupSsid()` filters out all `WeMo.*` / `Wemo.*` / `Belkin.*` soft-AP strings, and `WeMoRepository.ensureSeeded()` auto-sanitizes existing database rows on launch so the user's home is never renamed after a switch's internal AP.

### 2.5 Mental Model 5: Sub-Frame Optimistic Feedback with Honest Offline Rollback
When a user taps a power ring:
1. **Immediate Visual Ack (`<16ms`)**: The UI immediately writes the optimistic state to Room, animates the power ring color (`220ms`), and orbits a cyan progress arc (`spinAngle`) while `SetBinaryState` is in flight.
2. **GENA Echo Suppression (`1200ms` Window)**: Because physical WeMo switches broadcast a UPnP GENA `NOTIFY` event right after a SOAP toggle, `lastSetStateTimestampMs` suppresses redundant echo notifications for `1200ms` so the row never flickers.
3. **Honest Rollback on Unreachable Hardware**: If the switch does not respond on its last-known port *and* 2-stage self-healing reconnect (`9-port probe` → `SSDP scan by serialNumber`) fails in Real LAN Mode, the row honestly reverts `isOn`/`brightness` to its prior state and transitions to `Offline` (`WeMoDisconnectedRingButton`).

---

## 3. Strict Layout, Typography & Visual Hierarchy Invariants

Every screen and component in **WeMi Light Control** must obey these five layout invariants to prevent visual regressions across compact phones, large font scales, foldables, and 10-foot TVs:

### Invariant 1: Dedicated Full-Width Title Line (`AutoResizingDeviceNameText`)
- **Rule**: Never place secondary badges, status pills, or action buttons on the same horizontal line as a dynamic device name, room title, or Wi-Fi Home title if they can compete for width.
- **Implementation**:
  - In `WeMoDeviceRow.kt`, `device.name` occupies **Line 1** across the full width of the card (`Modifier.fillMaxWidth().testTag("device_name_${device.id}")`) using `AutoResizingDeviceNameText(initialFontSizeSp = 17.5f, minFontSizeSp = 11f)`. All secondary pills (`connection_status_badge`, `power_state_badge`, IP/Auto-Off, `category_badge`) live in a `FlowRow` on **Line 2** alongside the hardware illustration and 64dp Power/Disconnected ring.
  - Redundant room badges are omitted inside `WeMoDeviceRow` and `WeMoTvDeviceCard` because room context is already established by `WeMoRoomGroupCard` / room filters.

### Invariant 2: Uniform Card Height Across All Room Group Headers
- **Rule**: Every collapsed `WeMoRoomGroupHeaderCard` (e.g., `"Hallway & Landing"` vs. `"Living Room"`) must render at the **exact same height** regardless of room name length (`abs(heightA - heightB) <= 2f`).
- **Implementation**:
  - `roomName` sits on **Line 1** of the center column with `AutoResizingDeviceNameText(..., singleLineOnly = true)`.
  - The `"$activeCount/$totalControllable ON"` pill (`maxLines = 1, softWrap = false`) and `"$online/${roomDevices.size} Active • Avg ${avgLatency}ms"` subtitle (`maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis`) sit together on **Line 2**.
  - This prevents long room names from squeezing the `"0/1 ON"` pill into a 0dp-wide vertical character column.

### Invariant 3: Compact, Single-Line Top Utility Bars (`WeMoWifiHomeBar` & `WeMoSsdpDiscoveryBanner`)
- **Rule**: Top chrome on the **Devices** tab must stay compact so rooms and switches remain the visual hero above the fold.
- **Implementation**:
  - `WeMoWifiHomeBar` renders the active home name with `AutoResizingDeviceNameText(..., initialFontSizeSp = 15f, singleLineOnly = true)`, single-line status (`maxLines = 1, softWrap = false`), a `32.dp`-high `Reconnect` button, and a horizontally scrollable row of `28.dp`-high chips (`Home (N)`, `Add Home`, `Reset`).
  - `WeMoSsdpDiscoveryBanner` uses a single compact row (`32.dp` radar icon badge, `maxLines = 1` title/subtitle, and `32.dp` `Scan` button).

### Invariant 4: Top-First Primary Controls in Expandable Drawers (`WeMoExpandedDrawer`)
- **Rule**: When a user taps a dimmable light row to expand its inline drawer, the interactive controls they came for must appear **first**, before technical telemetry.
- **Implementation**:
  - `WeMoDeviceDiagnosticsDrawer.kt` renders the **Brightness Level Slider (`1–100%`)** and wrapping `FlowRow` of quick presets (**`1%`**, **`21%`**, **`25%`**, **`50%`**, **`75%`**, **`100%`**, and **`Night Mode (20%)`**) at the **very top** of `WeMoExpandedDrawer`, followed by the RTT latency bar (`Ping RTT`, `Configure IP`) and UPnP endpoint/serial metadata below.

### Invariant 5: Non-Blocking Feedback & Synchronous Modal Dismissal
- **Rule**: Transient status banners must never block touches on underlying list items, and modal dialogs must close immediately when the user taps Save/Confirm.
- **Implementation**:
  - `status_feedback_banner` in `MainActivity.kt` is a non-clickable `Box` overlay so taps pass straight through to switch rows and bottom navigation tabs.
  - All ViewModel dialog save/confirm actions (`saveDevice`, `addWifiHomeNetwork`, `deleteDevice`, `addRule`) set their dialog `MutableStateFlow` to `false`/`null` **synchronously on the main thread** before launching background `Dispatchers.IO` network probes.

---

## 4. Critical User Journeys (CUJs)

### CUJ 1: First-Time Real LAN Launch, One-Tap Reset & SSDP Auto-Adoption
1. **Context**: User installs the app on a physical Android phone or taps the **`Reset`** chip (`reset_lan_data_chip`) → **`Reset & Rescan LAN`** (or **`Reset Empty`**).
2. **Experience**:
   - When `0` switches are saved in the active Wi-Fi Home, `EmptyDevicesState` (`empty_devices_state_card`) presents a visual showcase of the 4 hardware categories (`Switch`, `Light`, `Tree Lights`, `Fan`) and 4 clear actions: **`Scan Local Network (SSDP)`**, **`Setup Factory-Reset WeMo AP (10.22.22.1)`**, **`Add Switch Manually (By IP)`**, and **`Restore Demo Showroom Preset`**.
   - Tapping **`Scan Local Network (SSDP)`** (`empty_state_scan_ssdp_button`) broadcasts UDP `M-SEARCH` bursts (`239.255.255.250:1900`) + a `/24` unicast sweep, displays live progress inside `WeMoSsdpScannerModal`, and **automatically adopts** all discovered switches into `My Home` when starting from an empty home (or lets the user tap **`Adopt`** / **`Adopt All`** when adding to an existing home).

### CUJ 2: Everyday Single-Tap Switch Toggle & `<100ms` Physical Wall-Switch Sync
1. **Context**: User taps a switch's 64dp `WeMoPowerRingButton` (`power_button_${id}`) on their phone, or someone physically presses the wall switch paddle in the room.
2. **Experience**:
   - **App Tap**: The power ring immediately animates between very dark charcoal (`#23272F` OFF) and glowing cyan-teal (`#00838F` ON) with a spinning orbital arc while `SetBinaryState` executes inside `Semaphore(3)`. Once confirmed, the status pill updates (`ON` / `OFF`) and the connection badge shows the live SOAP round-trip time (`Active • 18ms`).
   - **Physical Wall Press**: `WeMoGenaSubscriptionManager` receives the switch's HTTP `NOTIFY /upnp/event` callback and updates the Room entity in `<100ms`, reflecting the new state on screen without waiting for the 30-second fallback poll.

### CUJ 3: Multi-Modal Dimmer Brightness Scaling (`1–100%` & Hardware `Night Mode 20%`)
1. **Context**: User wants to adjust a Belkin WeMo Smart Dimmer (`F7C059` or `WDS060`) from standby or while ON.
2. **Experience**:
   - **Collapsed Row**: The status pill always shows both power state and stored brightness (`OFF • 35%` or `ON • 75%`), and the lightbulb illustration scales its cyan-white radial glow to match `brightness`.
   - **Inline Drawer**: Tapping the dimmer row expands `WeMoExpandedDrawer` with the continuous `Slider` (`brightness_slider_${id}`) and one-tap preset chips (`1%`, `21%`, `25%`, `50%`, `75%`, `100%`, and `Night Mode (20%)`) right at the top. Dragging the slider or tapping a preset on an `OFF` dimmer automatically turns it `ON` to that exact level.
   - **Room Master Dimmer**: Tapping the tune icon (`room_dimmer_button_${roomName}`) on a room header reveals a master room slider (`room_dimmer_slider_${roomName}`) that scales all dimmers in that room simultaneously.

### CUJ 4: Spatial Room Filtering, Collapsing & Whole-Room Master Toggle
1. **Context**: User wants to turn off an entire room (e.g., *Kitchen* or *Bedroom*) or focus on one area of the house.
2. **Experience**:
   - **Room Filter Bar (`WeMoRoomFilterBar`)**: Users can switch between **Grouped by Room** and **Flat List** (`toggle_group_by_room_chip`) or filter to a single room chip (`All Rooms (9)`, `Bedroom`, `Hallway & Landing`, etc.).
   - **Room Group Header (`WeMoRoomGroupHeaderCard`)**: Shows the room icon, single-line room name, `X/Y ON` badge, average latency, collapse chevron, optional room dimmer icon, and a 64dp **Room Master Power Ring** (`room_power_toggle_${roomName}`) that turns every reachable switch in that room ON or OFF in one tap.

### CUJ 5: Zero-Cloud `10.22.22.1` Factory-Reset Switch Provisioning & Screenshot Diagnostics
1. **Context**: User connects their phone's Wi-Fi to a blinking factory-reset Belkin WeMo switch (`WeMo.Light.xxx`, `WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Mini.xxx`) at `10.22.22.1`.
2. **Experience**:
   - **Auto-Detected Setup Banner**: `WeMoNetworkMonitor` detects the `10.22.22.x` subnet and surfaces the prominent **WeMo Setup AP Detected (`10.22.22.1`)** banner at the top of the **Devices** tab.
   - **Guided Wizard (`ProvisionWeMoApDialog`)**: Probes `10.22.22.1` across ports `49151–49159` via `OkHttpClient`, displays the detected hardware SKU/MAC/Serial and recommended AES-128-CBC method, scans nearby 2.4 GHz networks (`Scan APs` with an `8.5s` site-survey timeout), and previews the auto-classified icon and room as the user types a switch name.
   - **Non-Destructive Failure Handling & Screenshot Telemetry**: If the switch reports `NetworkStatus = 0` or `2` (e.g., password typo, 5 GHz-only SSID, or firmware requiring a different `pywemo` AES method / `xxyy` length suffix), the app **never** calls `CloseSetup`—keeping the `10.22.22.1` AP open for immediate retry—and keeps the dialog open showing the high-contrast **`SETUP DEBUG DETAILS (SCREENSHOT)`** terminal panel (`provision_debug_panel`) with one-tap **`Copy`** and **`Save/Share`** (`wemi-telemetry-latest.txt`).

### CUJ 6: Self-Healing Reconnection When a Switch Is Offline or Changes IP/Port
1. **Context**: A switch row shows the red **Disconnected Ring Button** (`reconnect_button_${id}`) and `Offline • Timeout` badge after a power outage or router reboot.
2. **Experience**:
   - Tapping the Disconnected Ring (or tapping **`Reconnect`** on `WeMoWifiHomeBar`) runs `attemptTwoStageReconnect`:
     1. **Stage 1**: Probes all 9 Belkin UPnP ports (`49153, 49152, 49154, 49151, 49155..49159`) at the switch's known IP, verifying `<serialNumber>` / `<UDN>` so a DHCP IP swap never toggles the wrong switch.
     2. **Stage 2**: Broadcasts a targeted SSDP `M-SEARCH` scan to locate the switch by `<serialNumber>` if the router assigned a new IPv4 address, automatically updating the Room database and restoring the glowing power ring.

### CUJ 7: 10-Foot Living Room Android TV Experience (`wemi TV`)
1. **Context**: User opens **WeMi Light Control** on an Android TV / Google TV (`UI_MODE_TYPE_TELEVISION`) or taps the **`TV Remote`** button (`header_tv_mode_button`) on their phone/tablet.
2. **Experience**:
   - Launches `WeMoTvDashboardScreen` (`tv_dashboard_screen`) with a deep-slate 10-foot dark palette (`#0B0F17`), glowing cyan D-Pad focus borders (`#22D3EE`), 1-click whole-room lighting scenes (**`All ON`**, **`Movie Mode (20%)`**, **`Night Mode`**, **`All OFF`**), hardware TV remote shortcuts (`DPAD_CENTER` to toggle, `MEDIA_PLAY_PAUSE` to toggle the entire room, `-10%` / `+10%` dimmer step buttons), and an optional on-screen virtual D-Pad bar.
