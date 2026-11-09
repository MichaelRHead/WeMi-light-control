# WeMi Light Control — Android UX Design Guidelines (`UX_DESIGN.md`)

> **EXCLUSIVE AGENT OWNERSHIP NOTICE (READ-ONLY FOR EXTERNAL AGENTS)**
>
> **This document (`UX_DESIGN.md`), `UX_DESIGN_AND_EXPERIENCE.md`, and the companion skill (`.agents/skills/wemi-ux-design-and-experience/SKILL.md`) are maintained EXCLUSIVELY by the Google AI Studio Agent.**
>
> - **Google AI Studio Agent**: Sole owner and maintainer of this specification. Responsible for evolving the Android & Android TV user experience, navigation architecture, accessibility standards, human-factors modeling, and Critical User Journeys (CUJs).
> - **All Other Agents (CLI, GitHub, CI/CD, or External Coding Agents)**: **DO NOT MODIFY, OVERWRITE, RENAME, OR DELETE THIS FILE.** You **must** read, understand, and strictly follow these UX guidelines when implementing features, refactoring code, or writing tests, but treat this document as **strictly read-only**.

Related documentation:
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent — read-only for the Google AI Studio Agent)*
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
- [`USER_GUIDE.md`](USER_GUIDE.md) — Modular User Guide Index & Feature Guides ([`docs/user-guide/`](docs/user-guide/))

---

## 1. Executive Summary & UX Philosophy

**WeMi Light Control** (`com.example.wemi` / `com.aistudio.wemiswitch.kxpqzr`, `v20.0`) reimagines the tactile, unmistakable physical affordances of the 2013 Belkin WeMo smart-home interface (`#73D41B` Belkin lime header, brushed metallic surfaces, glowing emerald power rings, and skeuomorphic appliance illustrations) while pairing them with modern Android 15/16 responsiveness, zero-cloud local LAN privacy, and WCAG-grade accessibility.

Smart-home control apps fail when they treat physical wall switches like generic cloud database rows. Our UX design is anchored on four core principles:
1. **Sub-Frame Tactile Responsiveness (`<16ms`)**: Tapping a light switch in the app must feel as instantaneous as flipping a physical paddle on the wall, backed by optimistic state projection and `1200ms` UPnP GENA echo suppression.
2. **Honest 3-State Hardware Telemetry**: Never conflate a reachable switch that is turned `OFF` (`Accessible Standby`) with a switch that has lost Wi-Fi or power (`Disconnected`). Every state is communicated simultaneously via color, iconography, border geometry, and text labels.
3. **Zero-Truncation Spatial Clarity**: Device names are the primary anchor of human recognition (`"Kids Bedroom Bedside Lamp"`, `"Front Porch String Lights"`). Every row guarantees a dedicated, full-width auto-resizing title line (`AutoResizingDeviceNameText`) that never collides with status badges or truncates into ambiguity.
4. **Adaptive Multi-Form-Factor Ergonomics**: Seamless transition between one-handed smartphone operation, wide-screen tablet layouts, and 10-foot Android TV D-Pad navigation (`UI_MODE_TYPE_TELEVISION`) with hardware media-key shortcuts.

---

## 2. Navigation Patterns & Information Architecture

### 2.1 Primary Phone & Tablet Navigation (`WeMoHeaderAndNav.kt`)

WeMi Light Control uses a flat, predictable 3-tab bottom `NavigationBar` (`WeMoBottomNavigationBar`) paired with a contextual top header (`WeMoClassicTopHeader`):

1. **Top Header (`WeMoClassicTopHeader`)**:
   - **Brand & Mode Toggle**: Displays the iconic `wemi` wordmark alongside a one-tap **TV Mode (`wemi TV`)** pill button (`testTag("tv_mode_toggle_button")`) for previewing or entering the 10-foot D-Pad interface on any screen.
   - **Contextual Primary Actions**:
     - **Edit / Done Toggle**: Switches the device list between normal operation and management mode (exposing inline `Edit`, `Room`, and `Delete` actions).
     - **Grouped / Flat Toggle**: Switches the primary canvas between collapsible spatial **Room Group Cards** (`Grouped`) and an alphabetical **Flat List** (`Flat`).
     - **Refresh / SSDP Scan Button (`testTag("ssdp_scan_button")`)**: Launches the live `WeMoSsdpScannerModal` and triggers background `GetBinaryState` verification across all saved switches.

2. **Wi-Fi Home Context Bar (`WeMoWifiHomeBar.kt`)**:
   - Situated immediately below the top header on the `Devices` tab, providing persistent awareness of the active Wi-Fi home profile (`"My Home Wi-Fi (192.168.1.x)"` vs. `"Showcase Demo Showroom"`), live online/offline switch counts, a one-tap **Discover (`+`)** action, and a **Reset Empty** button when all LAN devices are unreachable.
   - **Expandable Multi-Home Manager**: Tapping the bar expands saved Wi-Fi Home Profiles (`WeMoHomeNetworkEntity`) with active `/24` subnet indicators, auto-reconnect toggles, and a **Reset & Rescan LAN** recovery button.

3. **Room Filter Strip (`WeMoRoomFilterBar`) & Collapsible Room Cards (`WeMoRoomGroupCard.kt`)**:
   - Horizontal filter chips (`All Rooms`, `Living Room`, `Bedroom`, `Kitchen`, etc.) with live active/total badges (`e.g., 2/3 ON`) allow instant single-room focus.
   - In **Grouped by Room** mode, rooms render as collapsible cards:
     - **Uniform Collapsed Card Height (`72.dp`)**: All collapsed room headers share a locked minimum height (`72.dp`) and single-line status pills (`"2/3 ON"` or `"All OFF"`) so vertical scanning is rhythmically consistent.
     - **Room Master Power Button (`46.dp`, `testTag("room_master_toggle_<Room>")`)**: Toggles every reachable switch and dimmer in that room in parallel without expanding the card.
     - **Room Master Dimmer Bar (`testTag("room_dimmer_preset_<Room>_<pct>")`)**: Automatically surfaces inside expanded rooms that contain at least one `LIGHTBULB_DIMMER`, offering 1-tap room-wide brightness presets (`10%`, `25%`, `50%`, `75%`, `100%`) while preserving binary switches in the same room.

4. **Bottom Navigation Bar (`WeMoBottomNavigationBar`)**:
   - **`Devices` Tab (`WeMoTab.DEVICES`)**: Real-time switch & dimmer control, room groups, SSDP discovery, and hardware diagnostics.
   - **`Rules` Tab (`WeMoTab.RULES`)**: Local schedule rules (`Daily Schedule`, `Sunset/Sunrise`, `Motion Trigger`, and `Auto-Off Timer` countdowns) with immediate enable/disable switches and CRUD dialogs.
   - **`More` Tab (`WeMoTab.MORE`)**: `10.22.22.1` factory-reset switch provisioning (`Provision New WeMo AP`), manual IP entry (`Add Manual Switch / IP`), `Showcase Demo Showroom` toggle, and protocol reference documentation.

### 2.2 Progressive Disclosure: Inline Drawers vs. Modal Dialogs

To avoid overwhelming the primary control canvas, secondary controls and diagnostics follow a strict two-tier progressive disclosure model:

- **Tier 1 — Inline Expandable Drawer (`WeMoExpandedDrawer` inside `WeMoDeviceRow.kt`)**:
  - Triggered by tapping anywhere on a device row body (outside the right-hand power ring button) or tapping the `Dimmer` / `Details` pill.
  - **Dimmer Controls First**: For `LIGHTBULB_DIMMER` devices (`F7C059`, `WDS060`), the continuous `1%–100%` `Slider`, quick-preset chips (`1%`, `25%`, `50%`, `75%`, `100%`), and **Night Mode (`20%` hardware cap)** toggle appear **at the very top of the expanded drawer** (`testTag("dimmer_control_panel_<id>")`) before any network telemetry.
  - **Action Bar & Telemetry Below**: Quick-action buttons (`Ping / Verify`, `Set Auto-Off Timer`, `Room Assignment`, `Edit`, `Delete`) and collapsible UPnP/SOAP hardware telemetry (`IP:Port`, `MAC`, `Serial Number`, `Firmware`, `UDN`, `Wi-Fi Signal %`) sit cleanly below the primary controls.

- **Tier 2 — Focused Modal Dialogs**:
  - **`WeMoSsdpScannerModal`**: Live radar progress bar, subnet sweep status, and discovered device cards with `Adopt` / `Synced` badges.
  - **`ProvisionWeMoApDialog`**: Step-by-step `10.22.22.1` factory-reset switch setup wizard with 2.4 GHz AP scanning, encryption method selection, and collapsible `SETUP DEBUG DETAILS` telemetry.
  - **`AddOrEditWeMoDeviceDialog` & `AddOrEditWeMoRuleDialog`**: Structured form dialogs for manual IP probing, device renaming, category/room overrides, and rule scheduling.

### 2.3 Deterministic Back-Stack Priority (`MainActivity.kt`)

Because WeMi Light Control uses reactive state-driven navigation (`selectedTab`, modal flags, and `isTvModeOverride`) for instantaneous transitions without fragment overhead, `BackHandler` interceptors enforce a strict, predictable priority order when the system Back gesture or hardware Back button is pressed:

1. **Active Modal Dialogs** (`showSsdpScannerModal`, `showProvisionApDialog`, `editingDeviceDialogTarget`, `showAddRuleDialog`) -> Dismisses the active modal and returns focus to the underlying screen.
2. **Manual TV Mode Override (`isTvModeOverride == true`)** -> Exits the 10-foot `wemi TV` preview back to the handheld/tablet layout.
3. **Device List Edit Mode (`isEditMode == true`)** -> Exits inline Edit mode back to normal switch operation.
4. **Secondary Bottom Tabs (`selectedTab != WeMoTab.DEVICES`)** -> Navigates from `Rules` or `More` back to the primary `Devices` tab before allowing the activity to finish.

### 2.4 10-Foot Android TV & D-Pad Navigation (`WeMoTvScreen.kt` & `WeMoTvCardsAndScenes.kt`)

When running on an Android TV (`Configuration.UI_MODE_TYPE_TELEVISION`) or toggled via the `wemi TV` header button, the UI switches to a dedicated 10-foot dark slate theater interface (`#0D131A` -> `#151F2B`):

- **D-Pad Focus Ring & Scale**: Every interactive card and scene pill uses `onFocusChanged` to render a high-contrast `3.dp` glowing border (`#73D41B` when `ON`, `#38BDF8` sky-blue when `OFF`) and a subtle `1.04f` scale elevation so the user always knows where the remote cursor rests from 10 feet away.
- **Remote Control Key Bindings (`onPreviewKeyEvent`)**:
  - **`KEYCODE_DPAD_CENTER` / `KEYCODE_ENTER`**: Toggles the focused switch or dimmer `ON`/`OFF` (or triggers reconnect if offline).
  - **`KEYCODE_MEDIA_PLAY_PAUSE`**: Instant room-wide master toggle for the room of the currently focused device card.
- **TV Dimmer Step Buttons**: Focused dimmer cards expose D-Pad navigable `-10%`, `+10%`, and `Night` buttons alongside a high-contrast progress bar so users never have to drag a touch slider with a TV remote.
- **Top Quick-Scene Bar**: 1-click D-Pad buttons at the top of the screen for whole-home presets (`All Lights ON`, `Movie Night 20%`, `All Lights OFF`, `Verify All`, and `SSDP Scan`).

---

## 3. Accessibility Standards for Smart Home Controls

Smart-home interfaces control physical electrical loads (heaters, fans, holiday trees, room lighting) and are frequently used in dark rooms, by users with visual impairments, or with assistive technologies (TalkBack, Switch Access, Voice Access). All UI components must adhere to the following standards:

### 3.1 Touch Target Sizing & Motor Accessibility

- **Minimum `48.dp × 48.dp` Interactive Bounds**: Every interactive control—including header buttons, room filter chips, dimmer preset pills (`1%`–`100%`), and drawer action buttons—enforces at least `48.dp × 48.dp` touch bounds via `Modifier.minimumInteractiveComponentSize()` or explicit sizing.
- **Oversized `64.dp × 64.dp` Primary Power Ring (`WeMoPowerRingButton` & `WeMoDisconnectedRingButton`)**:
  - The right-hand power control on every device row is a `64.dp × 64.dp` recessed metallic well with a `52.dp` inner tactile button and `8.dp` minimum separation from adjacent row boundaries.
  - This allows effortless thumb activation without accidentally expanding the device details drawer.
- **Isolated Sub-Actions**: The Room Master Power button (`46.dp` circle inside a `48.dp` touch target) is isolated from the room header's expand/collapse click area so users can toggle a whole room without collapsing the card.

### 3.2 Multi-Cue Visual Redundancy (Never Rely on Color Alone)

Users with protanopia/deuteranopia (red-green color blindness) cannot distinguish a green `ON` ring from a red `Offline` ring or gray `OFF` ring by hue alone. WeMi Light Control enforces **triple redundancy** (Color + Iconography + Text/Border Geometry) across all three hardware states:

| Hardware State | Ring / Badge Color | Center Iconography (`Canvas`) | Border Geometry & Status Pill Text |
| :--- | :--- | :--- | :--- |
| **1. `ON` (Active Load)** | Glowing Belkin Emerald (`#4CD964` / `#73D41B`) + radial bloom | IEC 60417-5009 Power Symbol (`"I/O"` circle + top vertical bar) + animated appliance illustration (rotating fan blades, twinkling tree LEDs, warm bulb rays) | Solid `2.5.dp` emerald ring + solid green **`ON`** pill (or **`ON · 75%`** for dimmers) |
| **2. `OFF` (Accessible Standby)** | Brushed Charcoal (`#4B5563` ring on `#F8FAFC` well) | IEC 60417-5009 Power Symbol (`"I/O"`) in high-contrast slate (`#475569`) | Solid `2.5.dp` slate ring + **`OFF · Standby`** subtitle + **`OFF`** pill + green latency bars (`14ms`) |
| **3. `Disconnected` (Unreachable)** | Muted Coral-Red (`#EF4444` accent on `#FEF2F2` pale rose well) | **Broken-Link / Diagonal Slash** (`drawDisconnectedSymbol` — circle with full diagonal disconnect slash and gap, distinct from `"I/O"`) | **Dashed `2.dp` circular ring** (`PathEffect.dashPathEffect`) + **`Not Detected`** pill + **`Tap to Retry`** affordance |

### 3.3 Screen Reader (`TalkBack`) Semantics & State Announcements

Every custom `Canvas` or composite control exposes explicit `androidx.compose.ui.semantics` properties:
- **`WeMoPowerRingButton` (`WeMoHardwareIllustrations.kt`)**:
  - Declares `role = Role.Switch`.
  - Announces context-aware `contentDescription`: `"Toggle power for <Device Friendly Name>, currently On"` or `"Toggle power for <Device Friendly Name>, currently Off"`.
- **`WeMoDisconnectedRingButton`**:
  - Declares `role = Role.Button`.
  - Announces `contentDescription = "Reconnect <Device Friendly Name>"`, informing TalkBack users that tapping the button triggers a live 2-stage LAN probe rather than a local toggle.
- **Custom Appliance Illustrations (`WeMoSwitchAndPlugIllustration`, `WeMoFanIllustration`, `WeMoChristmasTreeIllustration`, `WeMoLightAndSensorIllustrations`)**:
  - Purely decorative `Canvas` layers are merged with their parent row semantics (`clearAndSetSemantics` or `contentDescription = null` on decorative sub-layers) so TalkBack reads a single cohesive device summary rather than stepping through unlabelled graphics.
- **Deterministic `testTag` Instrumentation**:
  - Every interactive element carries a unique, stable `Modifier.testTag(...)` in `snake_case` (e.g., `device_row_<id>`, `power_button_<id>`, `dimmer_slider_<id>`, `dimmer_preset_<id>_<pct>`, `room_master_toggle_<Room>`, `ssdp_scan_button`, `reset_lan_button`) enabling both automated UI accessibility audits and Robolectric E2E verification.

### 3.4 Dynamic Typography & Anti-Truncation Guarantees (`AutoResizingDeviceNameText`)

- **Full-Width Dedicated Name Line**: Inside `WeMoDeviceRow.kt`, the device's `<friendlyName>` occupies its own dedicated top horizontal row across the full middle column—never sharing horizontal width with `Switch`/`Dimmer`/`Tree Lights` category badges.
- **Reactive Font Scaling (`AutoResizingDeviceNameText`)**:
  - Starts at `18.sp` (`FontWeight.Bold`) and uses `onTextLayout { layoutResult -> if (layoutResult.didOverflowWidth) ... }` to step down in `0.92f` increments down to `12.sp` before ever allowing `TextOverflow.Ellipsis`.
  - Respects system font scaling (`sp` units throughout) while preventing multi-word room/appliance names from clipping on compact phones.

---

## 4. User Interaction Flows & Critical User Journeys (CUJs)

### Flow 1: App Launch & Real-Time Hardware State Synchronization

```
[App Launch / Resume]
       │
       ├──► 1. Room DB Snapshot Emitted Immediately (<16ms cached UI render)
       │
       ├──► 2. Startup Sanitization (`WeMoRepository.ensureSeeded`)
       │       ├── Scrubs any persisted Belkin soft-AP SSID (`WeMo.*` / `Wemo.LS3W.*`)
       │       └── Re-infers rooms for legacy `"Living Room"` defaults (`inferRoomFromName`)
       │
       ├──► 3. Parallel Startup Probe (`WeMoDiscoveryCoordinator.detectStartupDeviceStates`)
       │       └── Bounded by `Semaphore(3)` RTOS protection -> SOAP `GetBinaryState`
       │           Updates `isOn`, `brightness`, `isReachable`, and `latencyMs`
       │
       └──► 4. UPnP GENA Push Subscription (`WeMoGenaSubscriptionManager`)
               └── `SUBSCRIBE /upnp/event/basicevent1` (`TIMEOUT: Second-300`)
                   Physical wall paddle press -> `<100ms` HTTP `NOTIFY` -> Live UI update
```

### Flow 2: Sub-Frame Optimistic Toggle, Dimmer Scaling & Self-Healing Reconnect

When the user taps a switch's power ring or adjusts a dimmer slider/preset (`WeMoRepository.executeOptimisticDeviceCommand`):

1. **Immediate Optimistic UI Projection (`<16ms`)**:
   - The target `isOn` or `brightness` (`1–100%`) state is written to Room immediately, illuminating the emerald power ring and updating the status pill (`ON · 75%`) on the very next frame.
   - A `1200ms` per-device GENA echo-suppression window (`lastSetStateTimestampMs`) is armed so transitional UPnP `NOTIFY` packets from the dimmer firmware cannot cause slider jitter.
2. **Direct SOAP Command Dispatch**:
   - Sends `SetBinaryState` to the device's saved `ipAddress:port` (guarded by per-device `Mutex` and global `Semaphore(3)`).
   - Automatically handles the Belkin firmware quirk where `<BinaryState>Error</BinaryState>` is returned even though the physical relay clicked by immediately verifying state via `GetBinaryState`.
3. **2-Stage Self-Healing Reconnect (On Port Shift or DHCP Reassignment)**:
   - **Stage 1 (9-Port Sweep)**: If the saved port fails, probes all 9 Belkin UPnP ports (`49153, 49152, 49154, 49151, 49155..49159`) on the same IP, verifies `<serialNumber>` / `<UDN>`, updates Room, and retries the command.
   - **Stage 2 (SSDP + `/24` Subnet Sweep)**: If the IP changed via DHCP, triggers an SSDP `M-SEARCH` and `/24` unicast sweep to relocate the hardware by `<serialNumber>` / `<UDN>`, heals the stored `ipAddress:port`, and completes the command.
4. **Honest Offline Rollback**:
   - Only if both stages fail does the repository revert the optimistic `isOn` state and mark `isReachable = false`, transitioning the row to the dashed red `Disconnected` ring (`Not Detected · Tap to Retry`) and surfacing a clear snackbar diagnostic.

### Flow 3: First-Time Real LAN Onboarding, SSDP Discovery & One-Tap Reset

1. **Exiting Demo Showroom or Resetting Stale Switches**:
   - Users can switch from `Showcase Demo Showroom` to `Real Home LAN (UPnP)` via the top banner or `More` tab, or tap **`Reset Empty`** / **`Reset & Rescan LAN`** (`testTag("reset_lan_button")`).
   - `resetLanHomesSwitchesAndRooms()` purges all non-demo switches, rules, and stale home profiles in a single Room transaction, collapses the Wi-Fi Home Bar, and lands on the clean `EmptyDevicesState`.
2. **Live SSDP Discovery & Auto-Adoption (`WeMoSsdpScannerModal`)**:
   - Tapping **`Scan Local Wi-Fi (SSDP)`** opens the live discovery modal, transmitting UDP `M-SEARCH` packets (`239.255.255.250:1900`) alongside a bounded `Semaphore(24)` `/24` unicast sweep.
   - Newly discovered switches and dimmers on the active LAN are **automatically adopted** into Room with inferred room assignments (`inferRoomFromName`) and semantic appliance categories (`classifyDeviceCategory`), while also displaying individual `Adopt` / `Synced` badges in the modal.

### Flow 4: Zero-Cloud `10.22.22.1` Factory-Reset Switch Provisioning

1. **Automatic Soft-AP Detection (`WeMoNetworkMonitor`)**:
   - When the user connects their phone's Wi-Fi to a factory-reset Belkin switch (`WeMo.Light.xxx`, `Wemo.Mini.xxx`, `Wemo.LS3W.xxx` on subnet `10.22.22.x`), the app automatically surfaces a prominent top banner (`WeMo Setup AP Detected (10.22.22.1)`) without requiring Android Location permissions.
   - `WeMoXmlAndSkuParser.isWemoDeviceSetupSsid` guarantees that temporary `10.22.22.x` soft-AP SSIDs are **never** saved as a permanent Home Network profile.
2. **Guided 3-Step Provisioning Wizard (`ProvisionWeMoApDialog`)**:
   - **Step 1 (Probe)**: Reads `/setup.xml` and `GetMetaInfo` over a Wi-Fi-bound socket (`createWifiBoundSocketFactory`) to display the exact hardware model (`F7C030`, `WLS040`, `F7C059`, `WDS060`, `F7C063`, `WSP080`), MAC, and Serial Number.
   - **Step 2 (Scan 2.4 GHz APs)**: Executes `WiFiSetup1#GetApList` / `GetNetworkList` (`8500ms` site-survey timeout) so the user can tap their home 2.4 GHz SSID from a live signal-strength list.
   - **Step 3 (Encrypt & Connect)**: Encrypts the password via `pywemo`-exact OpenSSL `EVP_BytesToKey` AES-128-CBC (`WeMoPasswordCrypto`), sends `ConnectHomeNetwork` twice (bridging firmware `Status=2`), and polls `GetNetworkStatus` for up to 20 seconds.
   - **Non-Destructive Failure Handling & Screenshot Telemetry (`WeMoProvisionDebugPanel.kt`)**: If the password fails (`NetworkStatus=0` or `2`), `CloseSetup` is **withheld** so the switch's `10.22.22.1` AP stays open for an immediate retry, and a collapsible **`SETUP DEBUG DETAILS`** card (`WeMoProvisionDebugPanel` / `WeMoSetupDebugSnapshot`) renders full diagnostic telemetry on-screen for single-screenshot debugging and `wemi-telemetry-latest.txt` export.

---

## 5. UX Verification Checklist for All Agents

Before committing any UI, ViewModel, or Repository change, verify that:
- [ ] **No Truncated Device Names**: `AutoResizingDeviceNameText` remains on its own full-width top line inside `WeMoDeviceRow.kt`.
- [ ] **3-State Visual Redundancy**: `ON`, `OFF (Accessible Standby)`, and `Disconnected` remain visually distinct across color, center `Canvas` icon (`"I/O"` vs. diagonal slash), border style (solid vs. dashed), and status pill text.
- [ ] **Dimmer Controls First**: Expanded `LIGHTBULB_DIMMER` rows render the brightness slider, quick presets (`1%`–`100%`), and Night Mode toggle at the top of `WeMoExpandedDrawer` before network metadata.
- [ ] **48dp+ Touch Targets & Semantics**: All interactive controls meet `>= 48.dp` touch target minimums, include `Role` / `contentDescription` semantics, and carry deterministic `Modifier.testTag(...)` identifiers.
- [ ] **End-to-End Tests Pass**: `./gradlew testDebugUnitTest --rerun-tasks` passes all 26 Robolectric unit, protocol, and Compose E2E UI tests (`ExampleRobolectricTest`, `WeMoEndToEndUiTest`, and `WeMoHardwareCujEndToEndTest`).
