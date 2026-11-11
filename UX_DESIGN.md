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
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent — read-only for the Google AI Studio Agent)*
- [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) — Open-Source MIT License Parity, Permissive Dependency Policy & Brand/Trademark Governance *(Owned Exclusively by the Antigravity License & Brand Governance Agent — read-only for the Google AI Studio Agent)*
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
- [`USER_GUIDE.md`](USER_GUIDE.md) — Modular User Guide Index & Feature Guides ([`docs/user-guide/`](docs/user-guide/))

---

## 1. Executive Summary & UX Philosophy

**WeMi Light Control** (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`, `v24.0`) reimagines the tactile, unmistakable physical affordances of a dedicated smart-home switch interface (Ocean Teal `#0891B2`, Electric Cyan `#06B6D4`, and Deep Sapphire `#0F4C81` header gradient, crisp white device rows, glowing cyan power rings, and skeuomorphic appliance illustrations) for Belkin's discontinued smart switch platform while pairing them with modern Android 15/16 responsiveness, zero-cloud local LAN privacy, and WCAG-grade accessibility.

Smart-home control apps fail when they treat physical wall switches like generic cloud database rows. Our UX design is anchored on four core principles:
1. **Sub-Frame Tactile Responsiveness (`<16ms`)**: Tapping a light switch in the app must feel as instantaneous as flipping a physical paddle on the wall, backed by optimistic state projection and `1200ms` UPnP GENA echo suppression.
2. **Honest 3-State Hardware Telemetry**: Never conflate a reachable switch that is turned `OFF` (`Accessible Standby`) with a switch that has lost Wi-Fi or power (`Disconnected`). Every state is communicated simultaneously via color, iconography, border geometry, and text labels.
3. **Zero-Truncation Spatial Clarity**: Device names are the primary anchor of human recognition (`"Kids Bedroom Bedside Lamp"`, `"Front Porch String Lights"`). Every row guarantees a dedicated, full-width auto-resizing title line (`AutoResizingDeviceNameText` / `AutoShrinkSwitchName`) that never collides with status badges or truncates into ambiguity.
4. **Native Hardware Form-Factor Ergonomics**: Phone/tablet touchscreens and 10-foot Android TV devices (`Configuration.UI_MODE_TYPE_TELEVISION`) have fundamentally different interaction models. Each device class automatically renders its dedicated native layout—touch-first `Scaffold` on handhelds/tablets and D-Pad-first `TvDashboardScreen` on Android TV—without cross-mode layout toggle buttons.

---

## 2. Navigation Patterns & Information Architecture

### 2.1 Primary Phone & Tablet Navigation (`HeaderAndNav.kt`)

WeMi Light Control uses a flat, predictable 3-tab bottom `NavigationBar` (`BottomNavigationBar`) paired with a compact single-row top header (`TopHeader`):

1. **Compact Single-Row Top Header (`TopHeader`)**:
   - **Brand Wordmark**: Displays the `wemi` / `wemo` wordmark (`brand_wordmark_text`) on the left, omitting redundant static subtitle pills (`"LAN SWITCHES"`) so all top-bar actions fit on a single compact row and maximize vertical screen space for room and switch cards.
   - **Inline Action Cluster (Right)**:
     - **Refresh / SSDP Scan Button (`refresh_devices_button`)**: Launches the live `SsdpScannerModal` and triggers background `GetBinaryState` verification across all saved switches.
     - **Edit / Done Toggle (`edit_devices_button`)**: Switches the device list between normal operation and management mode (exposing inline reorder, `Edit`, and `Delete` actions).
     - **Add Button (`add_device_button`)**: Opens `AddOrEditDeviceDialog` on the `Devices` tab or `AddRuleDialog` on the `Rules` tab.

2. **Wi-Fi Home Context Bar (`WifiHomeBar.kt`)**:
   - Situated immediately below the top header on the `Devices` tab, providing persistent awareness of the active Wi-Fi home profile (`"My Home"` vs. `"Main Home"` in Demo Showroom Mode), live online/offline switch counts, a one-tap **`Reconnect`** button, and a horizontally scrollable chip strip (`Home` chips, `Add Home`, and `Reset`).
   - **Multi-Home & Reset Controls**: Tapping a saved Wi-Fi Home chip (`HomeNetworkEntity`) switches `/24` home profiles immediately, while tapping **`Reset`** (`reset_lan_data_chip`) offers **`Reset & Rescan LAN`** and **`Reset Empty`**.

3. **Room Filter Strip (`RoomFilterBar`) & Collapsible Room Cards (`RoomGroupCard.kt`)**:
   - Horizontal filter chips (`All Rooms`, `Living Room`, `Bedroom`, `Kitchen`, etc.) with live active/total badges (`e.g., 2/3 ON`) allow instant single-room focus, paired with a **`Grouped` / `Flat List`** toggle chip (`toggle_group_by_room_chip`).
   - In **Grouped by Room** mode, rooms render as collapsible cards:
     - **Uniform Collapsed Card Height**: All collapsed room headers share a locked single-line title (`AutoResizingDeviceNameText(..., singleLineOnly = true)`) and single-line status pills (`"2/3 ON"` or `"0/1 ON"`) so vertical scanning is rhythmically consistent across short and long room names.
     - **Room Master Power Button (`room_power_toggle_<Room>`)**: Toggles every reachable switch and dimmer in that room in parallel without expanding the card.
     - **Room Master Dimmer Bar (`room_dimmer_button_<Room>` / `room_dimmer_slider_<Room>`)**: Surfaces inside rooms that contain at least one `LIGHTBULB_DIMMER`, offering a room-wide master brightness slider and quick presets (`10%`, `25%`, `50%`, `75%`, `100%`) while preserving binary switches in the same room.

4. **Bottom Navigation Bar (`BottomNavigationBar`)**:
   - **`Devices` Tab (`BottomTab.DEVICES`)**: Real-time switch & dimmer control, room groups, SSDP discovery, and hardware diagnostics.
   - **`Rules` Tab (`BottomTab.RULES`)**: Local schedule rules (`Daily Schedule`, `Sunset/Sunrise`, `Motion Trigger`, and `Auto-Off Timer` countdowns) with immediate enable/disable switches and CRUD dialogs.
   - **`More` Tab (`BottomTab.MORE` — `MoreScreen.kt` & `MoreControlAndLogCards.kt`)**: `10.22.22.1` factory-reset switch provisioning (`Provision New Switch AP`), `Showcase Demo Showroom` toggle, `Reset Homes, Discovered Switches & Rooms`, `Open Source License` & third-party notices card (`open_source_licenses_card`), and the live `UPnP / SOAP Packet Log` inspector.

### 2.2 Progressive Disclosure: Inline Drawers vs. Modal Dialogs

To avoid overwhelming the primary control canvas, secondary controls and diagnostics follow a strict two-tier progressive disclosure model:

- **Tier 1 — Inline Expandable Drawer (`ExpandedDrawer` inside `DeviceDiagnosticsDrawer.kt`)**:
  - Triggered by tapping anywhere on a device row body (outside the right-hand power ring button) or tapping the `ⓘ` details button (`info_button_<id>`).
  - **Dimmer Controls First**: For `LIGHTBULB_DIMMER` devices (`F7C059`, `WDS060`), the continuous `1%–100%` `Slider` (`brightness_slider_<id>`), quick-preset chips (`1%`, `21%`, `25%`, `50%`, `75%`, `100%`), and **Night Mode (`20%` hardware cap)** toggle appear **at the very top of the expanded drawer** before any network telemetry.
  - **Action Bar & Telemetry Below**: Quick-action buttons (`Ping RTT`, `Configure IP`) and UPnP/SOAP hardware telemetry (`IP:Port`, `MAC`, `Serial Number`, `Firmware`, `UDN`, `Wi-Fi Signal %`) sit cleanly below the primary controls.

- **Tier 2 — Focused Modal Dialogs (`WeMiModalDialogs.kt`)**:
  - **`SsdpScannerModal` (`SsdpScannerModal.kt` & `SsdpDiscoveryBanner.kt`)**: Live radar progress bar, subnet sweep status, and discovered device cards with `Adopt` / `Synced` badges.
  - **`ProvisionApDialog` (`ProvisionApDialog.kt` & `ProvisionApFormSections.kt`)**: Step-by-step `10.22.22.1` factory-reset switch setup wizard with 2.4 GHz AP scanning, encryption method selection, and collapsible `SETUP DEBUG DETAILS (SCREENSHOT)` telemetry (`ProvisionDebugPanel`).
  - **`AddOrEditDeviceDialog` & `AddRuleDialog` (`DeviceEditDialog.kt` & `RuleDialog.kt`)**: Structured form dialogs for manual IP probing, device renaming, category/room overrides, and rule scheduling.

### 2.3 Deterministic Back-Stack Priority (`MainActivity.kt`)

Because WeMi Light Control uses reactive state-driven navigation (`selectedTab`, `selectedRoomFilter`, and modal flags) for instantaneous transitions without fragment overhead, `BackHandler` interceptors enforce a strict, predictable priority order when the system Back gesture or hardware Back button is pressed:

1. **Active Modal Dialogs** (`showSsdpScannerSheet`, `showAddHomeDialog`, `showProvisionApDialog`) -> Dismisses the active modal and returns focus to the underlying screen.
2. **Device List Edit Mode (`isEditMode == true`)** -> Exits inline Edit mode back to normal switch operation.
3. **Active Room Filter (`selectedRoomFilter != null`)** -> Clears the single-room filter back to `All Rooms`.
4. **Secondary Bottom Tabs (`selectedTab != BottomTab.DEVICES`)** -> Navigates from `Rules` or `More` back to the primary `Devices` tab before allowing the activity to finish.

### 2.4 Native 10-Foot Android TV & D-Pad Navigation (`TvScreen.kt`, `TvHeadersAndScenes.kt` & `TvCardsAndScenes.kt`)

When running on an Android TV (`Configuration.UI_MODE_TYPE_TELEVISION`), the app launches directly into a dedicated 10-foot dark slate theater interface (`TvDashboardScreen`, `#0A192F` -> `#060E1A`) tailored exclusively for TV remote D-Pad interaction (without a phone-mode switch button or touch-only popup modals):

- **Single Compact Top Bar & Full-Height 3-Column Room-Grouped Grid**:
  - A single compact top bar (`wemi TV`, active switch count, focused card status pill `tv_remote_dpad_ok`, scene chips `Movie 20%`, `Holiday ON`, `All OFF`, and `Rescan`) dedicates over 90% of vertical screen height to the 3-column (`GridCells.Fixed(3)`) switch grid.
  - Switches are grouped by room under full-width focusable **Room Section Headers** (`TvRoomSectionHeader`, `span = { GridItemSpan(maxLineSpan) }`, `tv_room_header_<RoomName>`, with `"Unassigned"` sorted last). Pressing `OK / Select` on any room header toggles all switches in that room.
- **Unmistakable 10-Foot Cursor Focus**:
  - Initial hardware focus (`FocusRequester`) is requested automatically on the first switch card at launch.
  - Focused cards render a high-contrast `3.5.dp` Electric Cyan border (`#22D3EE`), bright illuminated surface (`#133B66`), `1.022f` scale lift, `12.dp` shadow elevation, and an explicit top-right **`FOCUSED • OK`** badge.
- **Predictable 3-Column Navigation & Non-Interfering Dimmer Adjust Mode**:
  - **Normal Navigation Mode**: `D-Pad Up / Down / Left / Right` **always** navigates freely between the 3 columns of switch and dimmer cards (never hijacking `Left / Right` on dimmers). Pressing `OK / Select / Enter` on any card immediately toggles power `ON` / `OFF` (or triggers a 9-port reconnect if offline).
  - **Dimmer Adjust Mode (`isDimmingMode`)**: Activated on a focused dimmer card via `KEYCODE_MEDIA_PLAY_PAUSE`, `KEYCODE_MENU`, Long-Press `OK`, or clicking the dimmer strip (`tv_card_dim_mode_<id>`). While active, the card border turns glowing Gold (`#FBBF24`) with a **`DIM -/+`** badge, capturing `Left / Right` (`-10%` / `+10%`) and `Up / Down` (`+20%` / `-20%`) until the user presses `OK` or `Back` to exit back to normal grid navigation.
- **Zero Blocking Popup Modals on TV**: Clicking `Rescan` (`tv_scan_button`) runs a non-blocking background SSDP + state refresh with a bottom toast, and touch-only modal sheets are guarded by `if (!isTvMode)` so popups never trap TV remote focus.

---

## 3. Accessibility Standards for Smart Home Controls

Smart-home interfaces control physical electrical loads (heaters, fans, holiday trees, room lighting) and are frequently used in dark rooms, by users with visual impairments, or with assistive technologies (TalkBack, Switch Access, Voice Access). All UI components must adhere to the following standards:

### 3.1 Touch Target Sizing & Motor Accessibility

- **Minimum `48.dp × 48.dp` Interactive Bounds**: Every interactive control—including header buttons, room filter chips, dimmer preset pills (`1%`–`100%`), and drawer action buttons—enforces at least `48.dp × 48.dp` touch bounds via `Modifier.minimumInteractiveComponentSize()` or explicit sizing.
- **Oversized `64.dp × 64.dp` Primary Power Ring (`PowerRingButton` & `DisconnectedRingButton`)**:
  - The right-hand power control on every device row is a `64.dp × 64.dp` recessed metallic well with a `52.dp` inner tactile button and `8.dp` minimum separation from adjacent row boundaries.
  - This allows effortless thumb activation without accidentally expanding the device details drawer.
- **Isolated Sub-Actions**: The Room Master Power button is isolated from the room header's expand/collapse click area so users can toggle a whole room without collapsing the card.

### 3.2 Multi-Cue Visual Redundancy (Never Rely on Color Alone)

Users with protanopia/deuteranopia (red-green color blindness) cannot distinguish a green `ON` ring from a red `Offline` ring or gray `OFF` ring by hue alone. WeMi Light Control enforces **triple redundancy** (Color + Iconography + Text/Border Geometry) across all three hardware states:

| Hardware State | Ring / Badge Color | Center Iconography (`Canvas`) | Border Geometry & Status Pill Text |
| :--- | :--- | :--- | :--- |
| **1. `ON` (Active Load)** | Glowing Cyan-Teal / Emerald (`#00838F` / `#4CD964` / `#73D41B`) + radial bloom | IEC 60417-5009 Power Symbol (`"I/O"` circle + top vertical bar) + animated appliance illustration (rotating fan blades, twinkling tree LEDs, warm bulb rays) | Solid emerald/cyan ring + solid green **`ON`** pill (or **`ON • 75%`** for dimmers) |
| **2. `OFF` (Accessible Standby)** | Very Dark Charcoal (`#23272F` ring on `#F8FAFC` well) | IEC 60417-5009 Power Symbol (`"I/O"`) in high-contrast dark slate (`#181B20`) | Solid `#23272F` ring + **`OFF`** pill (or **`OFF • 35%`** for dimmers) + green latency bars (`14ms`) |
| **3. `Disconnected` (Unreachable)** | Muted Coral-Red (`#EF4444` accent on `#FEF2F2` pale rose well) | **Broken-Link / Diagonal Slash** (`drawDisconnectedSymbol` — circle with full diagonal disconnect slash and gap, distinct from `"I/O"`) | **Dashed `2.dp` circular ring** (`PathEffect.dashPathEffect`) + **`Not Detected`** pill + **`Tap to Retry`** affordance |

### 3.3 Screen Reader (`TalkBack`) Semantics & State Announcements

Every custom `Canvas` or composite control exposes explicit `androidx.compose.ui.semantics` properties:
- **`PowerRingButton` (`HardwareIllustrations.kt`)**:
  - Declares `role = Role.Switch`.
  - Announces context-aware `contentDescription`: `"Toggle power for <Device Friendly Name>, currently On"` or `"Toggle power for <Device Friendly Name>, currently Off"`.
- **`DisconnectedRingButton`**:
  - Declares `role = Role.Button`.
  - Announces `contentDescription = "Reconnect <Device Friendly Name>"`, informing TalkBack users that tapping the button triggers a live 2-stage LAN probe rather than a local toggle.
- **Custom Appliance Illustrations (`SwitchAndPlugIllustration`, `FanIllustration`, `ChristmasTreeIllustration`, `LightAndSensorIllustrations`)**:
  - Purely decorative `Canvas` layers are merged with their parent row semantics so TalkBack reads a single cohesive device summary rather than stepping through unlabelled graphics.
- **Deterministic `testTag` Instrumentation**:
  - Every interactive element carries a unique, stable `Modifier.testTag(...)` in `snake_case` (e.g., `device_row_<id>`, `power_button_<id>`, `brightness_slider_<id>`, `room_power_toggle_<Room>`, `refresh_button`, `reset_lan_data_chip`) enabling both automated UI accessibility audits and Robolectric E2E verification.

### 3.4 Dynamic Typography & Anti-Truncation Guarantees (`AutoResizingDeviceNameText` / `AutoShrinkSwitchName`)

- **Full-Width Dedicated Name Line**: Inside `DeviceRow.kt`, the device's `<friendlyName>` occupies its own dedicated top horizontal row across the full card width—never sharing horizontal width with `Switch`/`Light`/`Tree Lights` category badges.
- **Reactive Font Scaling (`AutoResizingDeviceNameText` / `AutoShrinkSwitchName`)**:
  - Starts at `17.5.sp` (`FontWeight.SemiBold`) and uses `onTextLayout { layoutResult -> if (layoutResult.didOverflowWidth) ... }` to step down smoothly to `11.sp` before ever allowing `TextOverflow.Ellipsis`.
  - Respects system font scaling (`sp` units throughout) while preventing multi-word room/appliance names from clipping on compact phones.

---

## 4. User Interaction Flows & Critical User Journeys (CUJs)

### Flow 1: App Launch & Real-Time Hardware State Synchronization

```
[App Launch / Resume]
       │
       ├──► 1. Room DB Snapshot Emitted Immediately (<16ms cached UI render)
       │
       ├──► 2. Startup Sanitization (`SwitchRepository.ensureSeeded`)
       │       ├── Scrubs any persisted Belkin soft-AP SSID (`WeMo.*` / `Wemo.LS3W.*`)
       │       └── Re-infers rooms for legacy `"Living Room"` defaults (`inferRoomFromName`)
       │
       ├──► 3. Parallel Startup Probe (`DiscoveryCoordinator.detectStartupDeviceStates`)
       │       └── Bounded by `Semaphore(3)` RTOS protection -> SOAP `GetBinaryState`
       │           Updates `isOn`, `brightness`, `isReachable`, and `latencyMs`
       │
       └──► 4. UPnP GENA Push Subscription (`GenaSubscriptionManager`)
               └── `SUBSCRIBE /upnp/event/basicevent1` (`TIMEOUT: Second-300`)
                   Physical wall paddle press -> `<100ms` HTTP `NOTIFY` -> Live UI update
```

### Flow 2: Sub-Frame Optimistic Toggle, Dimmer Scaling & Self-Healing Reconnect

When the user taps a switch's power ring or adjusts a dimmer slider/preset (`SwitchRepository.executeOptimisticDeviceCommand`):

1. **Immediate Optimistic UI Projection (`<16ms`)**:
   - The target `isOn` or `brightness` (`1–100%`) state is written to Room immediately, illuminating the power ring and updating the status pill (`ON • 75%`) on the very next frame.
   - A `1200ms` per-device GENA echo-suppression window (`lastSetStateTimestampMs`) is armed so transitional UPnP `NOTIFY` packets from the dimmer firmware cannot cause slider jitter.
2. **Direct SOAP Command Dispatch**:
   - Sends `SetBinaryState` to the device's saved `ipAddress:port` (guarded by per-device `Mutex` and global `Semaphore(3)`).
   - Automatically handles the Belkin firmware quirk where `<BinaryState>Error</BinaryState>` is returned even though the physical relay clicked by immediately verifying state via `GetBinaryState`.
3. **2-Stage Self-Healing Reconnect (On Port Shift or DHCP Reassignment)**:
   - **Stage 1 (9-Port Sweep)**: If the saved port fails, probes all 9 UPnP ports (`49153, 49152, 49154, 49151, 49155..49159`) on the same IP, verifies `<serialNumber>` / `<UDN>`, updates Room, and retries the command.
   - **Stage 2 (SSDP + `/24` Subnet Sweep)**: If the IP changed via DHCP, triggers an SSDP `M-SEARCH` and `/24` unicast sweep to relocate the hardware by `<serialNumber>` / `<UDN>`, heals the stored `ipAddress:port`, and completes the command.
4. **Honest Offline Rollback**:
   - Only if both stages fail does the repository revert the optimistic `isOn` state and mark `isReachable = false`, transitioning the row to the dashed red `Disconnected` ring (`Not Detected • Tap to Retry`) and surfacing a clear snackbar diagnostic.

### Flow 3: First-Time Real LAN Onboarding, SSDP Discovery & One-Tap Reset

1. **Exiting Demo Showroom or Resetting Stale Switches**:
   - Users can switch from `Showcase Demo Showroom` to `Real Home LAN (UPnP)` via the top banner or `More` tab, or tap **`Reset`** (`reset_lan_data_chip`) -> **`Reset & Rescan LAN`** / **`Reset Empty`**.
   - `resetLanHomesSwitchesAndRooms()` purges all non-demo switches, rules, and stale home profiles in a single Room transaction, collapses the Wi-Fi Home Bar, and lands on the clean `EmptyDevicesState`.
2. **Live SSDP Discovery & Auto-Adoption (`SsdpScannerModal`)**:
   - Tapping **`Scan Local Network (SSDP)`** opens the live discovery modal, transmitting UDP `M-SEARCH` packets (`239.255.255.250:1900`) alongside a bounded `Semaphore(24)` `/24` unicast sweep.
   - Newly discovered switches and dimmers on the active LAN are **automatically adopted** into Room with inferred room assignments (`inferRoomFromName`) and semantic appliance categories (`classifyDeviceCategory`), while also displaying individual `Adopt` / `Synced` badges in the modal.

### Flow 4: Zero-Cloud `10.22.22.1` Factory-Reset Switch Provisioning

1. **Automatic Soft-AP Detection (`NetworkMonitor`)**:
   - When the user connects their phone's Wi-Fi to a factory-reset smart switch (`WeMo.Light.xxx`, `Wemo.Mini.xxx`, `Wemo.LS3W.xxx` on subnet `10.22.22.x`), the app automatically surfaces a prominent top banner (`Smart Switch Setup AP Detected (10.22.22.1)`) without requiring Android Location permissions.
   - `XmlAndSkuParser.isWemoDeviceSetupSsid` guarantees that temporary `10.22.22.x` soft-AP SSIDs are **never** saved as a permanent Home Network profile.
2. **Guided 3-Step Provisioning Wizard (`ProvisionApDialog`)**:
   - **Step 1 (Probe)**: Reads `/setup.xml` and `GetMetaInfo` over a Wi-Fi-bound socket (`createWifiBoundSocketFactory`) to display the exact hardware model (`F7C030`, `WLS040`, `F7C059`, `WDS060`, `F7C063`, `WSP080`), MAC, and Serial Number.
   - **Step 2 (Scan 2.4 GHz APs)**: Executes `WiFiSetup1#GetApList` / `GetNetworkList` (`8500ms` site-survey timeout) so the user can tap their home 2.4 GHz SSID from a live signal-strength list.
   - **Step 3 (Encrypt & Connect)**: Encrypts the password via first-party OpenSSL `EVP_BytesToKey` AES-128-CBC (`PasswordCrypto`), sends `ConnectHomeNetwork` twice (bridging firmware `Status=2`), and polls `GetNetworkStatus` for up to 20 seconds.
   - **Non-Destructive Failure Handling & Screenshot Telemetry (`ProvisionDebugPanel.kt`)**: If the password fails (`NetworkStatus=0` or `2`), `CloseSetup` is **withheld** so the switch's `10.22.22.1` AP stays open for an immediate retry, and a collapsible **`SETUP DEBUG DETAILS (SCREENSHOT)`** card (`ProvisionDebugPanel` / `SetupDebugSnapshot`) renders full diagnostic telemetry on-screen for single-screenshot debugging and `wemi-telemetry-latest.txt` export.

---

## 5. UX Verification Checklist for All Agents

Before committing any UI, ViewModel, or Repository change, verify that:
- [ ] **No Truncated Device Names**: `AutoResizingDeviceNameText` (`AutoShrinkSwitchName`) remains on its own full-width top line inside `DeviceRow.kt`.
- [ ] **3-State Visual Redundancy**: `ON`, `OFF (Accessible Standby)`, and `Disconnected` remain visually distinct across color, center `Canvas` icon (`"I/O"` vs. diagonal slash), border style (solid vs. dashed), and status pill text.
- [ ] **Dimmer Controls First**: Expanded `LIGHTBULB_DIMMER` rows render the brightness slider, quick presets (`1%`–`100%`), and Night Mode toggle at the top of `ExpandedDrawer` before network metadata.
- [ ] **Native Hardware Form-Factor Routing**: Phone/tablet (`Scaffold`) and Android TV (`TvDashboardScreen` under `UI_MODE_TYPE_TELEVISION`) remain tailored to their hardware form factors without manual Phone/TV mode switch buttons.
- [ ] **48dp+ Touch Targets & Semantics**: All interactive controls meet `>= 48.dp` touch target minimums, include `Role` / `contentDescription` semantics, and carry deterministic `Modifier.testTag(...)` identifiers.
- [ ] **End-to-End Tests Pass**: `./gradlew testDebugUnitTest --rerun-tasks` passes all 38 unit, protocol, governance, and Compose E2E UI tests across 10 suites (`ExampleUnitTest`, `ExampleRobolectricTest`, `EndToEndUiTest`, `HardwareCujEndToEndTest`, `TvAndHeaderLayoutE2ETest`, `LicenseAndBrandGovernanceTest`, `SkuAndCryptoTest`, `SoapAndSelfHealingTest`, `RepositorySelfHealingTest`, and `GenaProvisioningAndRulesTest`).
