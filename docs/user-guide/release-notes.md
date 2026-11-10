# Release Notes & Version History

[← Back to User Guide Index](../../USER_GUIDE.md)

Welcome to the official user-facing **Release Notes** for **WeMi Light Control** (`com.aistudio.wemiswitch.kxpqzr`) on Google Play for Android Phones, Tablets, and Android TV.

Unlike our developer engineering log ([`IMPLEMENTED.md`](../../IMPLEMENTED.md)), this guide provides a clear, high-level summary of the **major user-facing features, visual improvements, and reliability upgrades** introduced in each Google Play Store release—from our latest release (**`v22.0`**) all the way back to **`v1.0`**.

For step-by-step instructions on using any feature mentioned below, visit the **[WeMi Light Control User Guide (`USER_GUIDE.md`)](../../USER_GUIDE.md)**.

---

## Quick Release Summary (`v1.0` → `v22.0`)

| Version | Play Store `versionCode` | Release Date | Headline User-Facing Additions |
| :--- | :---: | :--- | :--- |
| **[`v22.0`](#version-220-versioncode--22--november-10-2026)** | `22` | Nov 10, 2026 | Refreshed Google Play Store & in-app visual showcase across Phone, 7"/10" Tablets & Android TV matching the live Ocean Teal, Electric Cyan & Deep Sapphire UI, auto-shrinking switch titles & 3-state power/reconnect rings |
| **[`v21.0`](#version-210-versioncode--21--november-910-2026)** | `21` | Nov 9–10, 2026 | Native Phone/Tablet vs. Android TV form-factor layouts, in-app Open Source MIT & Third-Party Notices card on the **More** tab, `org.suppressingfire.wemi` architecture & neutral hardware branding |
| **[`v20.0`](#version-200-versioncode--20--november-9-2026)** | `20` | Nov 9, 2026 | Unified 9-port self-healing reconnect, cellular-safe Wi-Fi binding, 3-tier hardware identity matching, CamelCase/`Ensuite` room grouping & instant offline reconnect recovery |
| **[`v19.0`](#version-190-versioncode--19--november-9-2026)** | `19` | Nov 9, 2026 | Dedicated Troubleshooting & Telemetry Sharing Guide (`wemi-telemetry-latest.txt`) for 1-tap support diagnostics |
| **[`v18.0`](#version-180-versioncode--18--november-9-2026)** | `18` | Nov 9, 2026 | Accessibility & navigation polish: 48dp+ touch targets, TalkBack switch labels, and predictable Back-button flows |
| **[`v17.0`](#version-170-versioncode--17--november-89-2026)** | `17` | Nov 8–9, 2026 | Major `10.22.22.1` factory-reset setup reliability upgrade, on-screen screenshot debug card, smart room matching & top-first dimmer slider |
| **[`v16.0`](#version-160-versioncode--16--november-8-2026)** | `16` | Nov 8, 2026 | Compact single-line Wi-Fi Home & Discovery bars, one-tap **Reset & Rescan LAN**, and protection against switch soft-AP names |
| **[`v15.0`](#version-150-versioncode--15--november-8-2026)** | `15` | Nov 8, 2026 | Full-width auto-resizing switch & room titles (`AutoShrinkSwitchName`)—no more truncated or wrapped switch names |
| **[`v14.0`](#version-140-versioncode--14--november-8-2026)** | `14` | Nov 8, 2026 | Distinct dark-gray `"I/O"` power button for `OFF` standby switches and red **Disconnected Reconnect Ring** for offline switches |
| **[`v13.0`](#version-130-versioncode--13--november-8-2026)** | `13` | Nov 8, 2026 | Uniform collapsed Room Group card heights and cleaner, uncluttered device rows |
| **[`v12.0`](#version-120-versioncode--12--november-8-2026)** | `12` | Nov 8, 2026 | Guided Empty-Home onboarding card, expanded holiday/fan/light icon recognition, and clearer setup error banners |
| **[`v11.0`](#version-110-versioncode--11--november-8-2026)** | `11` | Nov 8, 2026 | Multi-generation WeMo Wi-Fi setup (1st-Gen OpenWrt & 2nd-Gen RTOS), cellular-safe Wi-Fi binding, automatic 9-port manual IP probing & User Guide |
| **[`v10.0`](#version-100-versioncode--10--november-78-2026)** | `10` | Nov 7–8, 2026 | Automatic appliance illustrations (**Christmas Trees**, **Spinning Fans**, **Lightbulbs**, **Motion Sensors**, **Switches**) based on switch name |
| **[`v9.0`](#version-90-versioncode--9--november-7-2026)** | `9` | Nov 7, 2026 | Instant startup state detection (`ON`, `OFF Accessible Standby`, and `Disconnected`) so switches always reflect real hardware state |
| **[`v8.0`](#version-80-versioncode--8--november-7-2026)** | `8` | Nov 7, 2026 | Zero-cloud `10.22.22.1` factory-reset switch setup wizard, live `Auto-Off MM:SS` countdown badges & dimmer **Night Mode (20%)** |
| **[`v7.0`](#version-70-versioncode--7--november-7-2026)** | `7` | Nov 7, 2026 | Sub-100ms real-time updates when physical wall switches are pressed (UPnP GENA push) & zero-Location-permission Wi-Fi detection |
| **[`v6.0`](#version-60-versioncode--6--november-7-2026)** | `6` | Nov 7, 2026 | Self-healing 9-port local UPnP engine, RTOS switch protection, and **Real Home LAN Mode** vs. **Demo Showroom Mode** |
| **[`v5.0`](#version-50-versioncode--5--november-7-2026)** | `5` | Nov 7, 2026 | Multi-Home Wi-Fi profiles (*My Home*, *Lake Cabin*, *Studio Office*) with one-tap whole-home reconnection |
| **[`v4.0`](#version-40-versioncode--4--november-7-2026)** | `4` | Nov 7, 2026 | Live Wi-Fi response-time badges (`Excellent`, `Good`, `Slow`, `Offline`) and 4-bar wireless signal indicators |
| **[`v3.0`](#version-30-versioncode--3--november-7-2026)** | `3` | Nov 7, 2026 | **10-Foot Android TV Dashboard (`wemi TV`)** with TV remote D-Pad navigation, Play/Pause room toggles & 1-click lighting scenes |
| **[`v2.0`](#version-20-versioncode--2--november-7-2026)** | `2` | Nov 7, 2026 | Collapsible **Room Group Cards**, room filter chips, **Master Room Power Ring**, and **Master Room Dimmer Slider** |
| **[`v1.5`](#version-15-early-preview--november-7-2026)** | `2` | Nov 7, 2026 | Interactive local Wi-Fi switch discovery scanner (SSDP), one-tap switch adoption & pull-to-refresh |
| **[`v1.0`](#version-10-versioncode--1--november-7-2026)** | `1` | Nov 7, 2026 | Initial release: classic Belkin WeMo-inspired local switch & dimmer control, local schedules/timers, and live SOAP packet log |

---

## Release-by-Release Highlights (Newest to Oldest)

### Version 22.0 (`versionCode = 22`) — November 10, 2026
- **Refreshed Google Play Store & In-App Visual Showcase**: Updated all 21 Google Play Store screenshots, banners, icons, and in-app preview graphics (`More` tab & Android TV Leanback banner) across Phone (`1080×1920`), 7-Inch Tablet (`1200×1920`), 10-Inch Tablet (`1600×2560`), and 10-Foot Android TV (`1920×1080` & `1280×720`) to match the live **Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), and Deep Sapphire (`#0F4C81`)** interface, `Comfortaa` & `Work Sans` typography, full-width auto-shrinking switch titles (`AutoShrinkSwitchName`), 3-state hardware icons (`ON`, `OFF` Standby `#23272F`, and Offline `Disconnected` `#DC2626`), top-first dimmer controls, and native Phone/Tablet vs. Android TV headers without manual Phone/TV toggle buttons.

---

### Version 21.0 (`versionCode = 21`) — November 9–10, 2026
- **Dedicated Native Phone/Tablet & Android TV Form-Factor Layouts**: Removed the manual Phone ↔ TV layout switch button from both the phone header and the Android TV dashboard so each device form factor always runs its purpose-built interaction model—touch-first cards and inline drawers on phones/tablets, and the 10-foot D-Pad remote dashboard (`wemi TV`) automatically on Android TV and Google TV (`UI_MODE_TYPE_TELEVISION`).
- **In-App Open Source License & Third-Party Notices Card (`More` Tab)**: Added an expandable **Open Source Licenses (MIT)** card on the **More** tab displaying the full MIT License text, permissive third-party library attributions (`AndroidX Jetpack Compose & Room`, `Kotlin & Coroutines`, `Square OkHttp`, and clean-room `pywemo` protocol interoperability attribution), and a clear non-affiliation notice, paired with reproducible APK metadata (`includeInApk = false`).
- **Streamlined `org.suppressingfire.wemi` Package & Trademark-Clean Copy**: Repackaged the entire application and UPnP/SSDP/SOAP/GENA engine under `org.suppressingfire.wemi` and standardized all user-facing labels, dialogs, and Play Store metadata around neutral hardware compatibility descriptions (*"Belkin's discontinued smart switch platform"*).

---

### Version 20.0 (`versionCode = 20`) — November 9, 2026
- **Unified 9-Port Wi-Fi-Bound Self-Healing Reconnect**: Belkin WeMo switches occasionally shift their internal control port (`49151–49159`) after a power blink or router reboot. Every switch command—turning power ON/OFF, sliding dimmer brightness, renaming a switch, or toggling Night Mode—now automatically checks all 9 ports and heals the connection without interruption.
- **Cellular-Safe Wi-Fi Binding Across Discovery & Wall-Switch Push Events**: All local network discovery sweeps (`/24` unicast scanner), port-healing probes, and real-time UPnP GENA wall-switch event subscriptions now bind directly to your phone's Wi-Fi network handle so Android never routes smart-switch traffic over mobile cellular data.
- **Instant Recovery After Offline Toggles**: Fixed an issue where tapping an unreachable switch could briefly delay state updates if you tapped the red **Reconnect Ring** or flipped the physical wall switch within 1.5 seconds.
- **Smarter Automatic Room Grouping & 3-Tier Switch Identity Matching**:
  - Splits `CamelCase`, underscore, and hyphenated switch names (`"StairwayLight"` → **Hallway & Landing**, `"MasterBathVanity"` → **Ensuite**, `"ShpreseBedLamp"` → **Bedroom**) so switches are grouped into the right room even when named without spaces.
  - Prioritizes specific bathroom keywords (**Ensuite**) before general suite modifiers (`"Master"`, `"Guest"`, `"Kids"`) so `"Master Bath Vanity"` and `"Guest Bathroom Light"` land in **Ensuite** instead of **Bedroom**.
  - Matches discovered switches by hardware `<serialNumber>` first so router DHCP IP swaps never mix up two switches.
- **Streamlined Setup Diagnostic Panel**: Separated and polished the on-screen **`SETUP DEBUG DETAILS (SCREENSHOT)`** card inside the `10.22.22.1` setup wizard for cleaner single-screenshot troubleshooting and one-tap telemetry file export.

---

### Version 19.0 (`versionCode = 19`) — November 9, 2026
- **Step-by-Step Troubleshooting & Telemetry Sharing Guide**: Added **[Feature Guide #9: Troubleshooting, Screenshots & Sharing Telemetry Logs](telemetry-and-troubleshooting.md)** explaining how to diagnose stubborn factory-reset switches using either a phone screenshot or the exported **`wemi-telemetry-latest.txt`** report (`Save/Share` or `Copy`).
- **Privacy-First Diagnostics**: Diagnostic reports capture hardware model, firmware flags, 2.4 GHz channel/signal strength, and setup step status while **never** displaying or saving your Wi-Fi password.

---

### Version 18.0 (`versionCode = 18`) — November 9, 2026
- **Accessibility & Touch Ergonomics**: Standardized generous `48dp–64dp` touch targets across all power rings, room master toggles, and dimmer sliders, paired with clear `TalkBack` screen-reader announcements (*"Toggle power for Kitchen Island Switch, currently Off"*, *"Reconnect Master Bedroom Lamp"*).
- **Predictable Back-Button Navigation**: Pressing the Android system Back button now cleanly steps back through open dialogs, the SSDP scanner sheet, Edit Mode, or Android TV preview mode before exiting the app.

---

### Version 17.0 (`versionCode = 17`) — November 8–9, 2026
- **Major Upgrade to Zero-Cloud `10.22.22.1` Switch Setup**:
  - **Reliable Hardware Identification**: Fixed an issue where some WeMo switches held their setup connection open and timed out during hardware probing, ensuring your switch's real MAC address and Serial Number are always used to encrypt your Wi-Fi password.
  - **Full 11-Channel 2.4 GHz Wi-Fi Scan & 20-Second Handshake**: Expanded the Wi-Fi scan window so switches have plenty of time to survey all 2.4 GHz channels and complete the WPA2 handshake with your home router.
  - **Non-Destructive Retry on Password Errors**: If a switch fails to join your home Wi-Fi (for example, due to a mistyped password or selecting a 5 GHz-only network), **WeMi Light Control** no longer closes the switch's `10.22.22.1` setup hotspot—allowing you to correct the password and retry immediately without factory-resetting the switch again.
  - **On-Screen Screenshot Debug Card & All 6 Encryption Modes**: Added a dark monospace **`SETUP DEBUG DETAILS (SCREENSHOT)`** panel inside the setup dialog along with manual override chips for all 6 Belkin encryption combinations (`Method 1 / 2 / 3` × `Length Suffix ON / OFF`).
- **Smarter Automatic Room Grouping & `Unassigned` Room**:
  - Switches with names containing *"bed"*, *"bedside"*, *"nightstand"*, *"girls"*, *"boys"*, or *"kids"* now automatically group into **Bedroom**, while *"stair"*, *"stairway"*, *"upstairs"*, and *"downstairs"* group into **Hallway & Landing**.
  - Switches without a recognizable room word now land in an **`Unassigned`** group at the bottom of the list instead of cluttering **Living Room** (and existing real-network switches previously stuck in *Living Room* are automatically re-sorted into their proper rooms).
- **Top-First Dimmer Controls**: Expanding a dimmer row now places the **Brightness Slider (`1%–100%`)**, quick preset chips (`1%`, `21%`, `25%`, `50%`, `75%`, `100%`), and **Night Mode (`20%`)** at the very top of the drawer above network details.

---

### Version 16.0 (`versionCode = 16`) — November 8, 2026
- **Compact Single-Line Top Bars**: Condensed the **Wi-Fi Home Selector Bar** and **SSDP Local Network Discovery Banner** into sleek single-line bars so your room cards and switches take center stage on the screen.
- **One-Tap LAN Reset (`Reset Empty` / `Reset & Rescan LAN`)**: Added a **`Reset`** chip on the Wi-Fi Home Bar and a reset card on the **More** tab so you can clear out demo or stale switches in one tap and rescan your live home Wi-Fi from a clean slate.
- **Protection Against Switch Hotspot Names**: Some Belkin switches report their own factory setup SSID (such as `Wemo.LS3W.220` or `WeMo.Switch.xxx`) in their metadata even after joining your home Wi-Fi. The app now filters those out automatically so your **`My Home`** profile is never renamed after a switch's setup hotspot.

---

### Version 15.0 (`versionCode = 15`) — November 8, 2026
- **Full-Width Auto-Resizing Switch & Room Names**: Long descriptive names like *"Upstairs Master Bedroom Bedside Lamp"* or *"Hallway & Landing"* now sit on their own unobstructed top line and smoothly scale their font size to fit on a single line—eliminating mid-word truncation (`"Up..."`) and awkward vertical character wrapping across phones, tablets, and Android TV.

---

### Version 14.0 (`versionCode = 14`) — November 8, 2026
- **Clearer Power-Off vs. Disconnected Buttons**:
  - **Reachable & OFF (`Standby`)**: Uses the exact same classic `"I/O"` power symbol as `ON`, styled in a crisp dark charcoal gray ring (`#23272F`) so it always looks like a ready-to-press power button rather than a warning icon.
  - **Disconnected / Offline**: Replaces the power button with a dedicated red **Disconnected Reconnect Ring** (with a broken-link slash icon). Tapping the red ring actively triggers a 2-stage network search to find and reconnect the switch.

---

### Version 13.0 (`versionCode = 13`) — November 8, 2026
- **Uniform Room Group Headers**: All collapsed Room Group cards (`Living Room`, `Bedroom`, `Hallway & Landing`, etc.) now render at the exact same height and alignment for a clean, balanced dashboard.
- **Decluttered Switch Rows**: Removed the redundant room name badge from individual switch rows when already viewing switches in **Grouped by Room** mode.

---

### Version 12.0 (`versionCode = 12`) — November 8, 2026
- **Guided Empty-Home Onboarding Card**: When a Wi-Fi Home has no switches yet, the screen displays a visual showcase of supported appliance categories (**Switch**, **Light**, **Tree Lights**, **Fan**) and four clear starting actions: **Scan Local Network (SSDP)**, **Setup Factory-Reset WeMo AP (`10.22.22.1`)**, **Add Switch Manually (By IP)**, or **Restore Demo Showroom Preset**.
- **Expanded Appliance Icon Recognition**: Added automatic icon matching for holiday garlands & wreaths (**Christmas Tree**), exhaust/bathroom/attic fans (**Fan**), and sconces, pendants, chandeliers & vanity lights (**Lightbulb**).

---

### Version 11.0 (`versionCode = 11`) — November 8, 2026
- **Multi-Generation WeMo Wi-Fi Setup (`OpenWrt` & `RTOS`)**: Upgraded the `10.22.22.1` setup wizard with Belkin-exact AES-128-CBC password encryption supporting 1st-Gen OpenWrt switches (`F7C030`, `F7C059`, `F7C063`), 2nd-Gen RTOS 3-way switches & dimmers (`WLS040`, `WLS0403`, `WDS060`), and symmetric firmware models.
- **Cellular-Safe Wi-Fi Connection**: Keeps switch setup and local control traffic pinned to your phone's Wi-Fi connection even when Android detects that a `10.22.22.1` setup hotspot has no internet access and tries to fall back to mobile data.
- **Automatic 9-Port Probing for Manual IP Entry**: Adding a switch manually by IP address now automatically checks all 9 Belkin ports (`49151–49159`) and pulls the switch's real name, model, and power state.
- **Complete In-App & Repository User Guide**: Added [`USER_GUIDE.md`](../../USER_GUIDE.md) and detailed feature guides covering every screen and workflow.

---

### Version 10.0 (`versionCode = 10`) — November 7–8, 2026
- **Custom 3-State Appliance Illustrations Based on Switch Name**: Naming a switch or smart plug *"Christmas Tree"*, *"Holiday Lights"*, *"Laundry Fan"*, *"Ceiling Fan"*, or *"Bedside Lamp"* automatically gives it a custom multi-state illustration:
  - **Christmas Tree**: Evergreen tree with star topper and 9 multi-color fairy lights that glow when `ON`.
  - **Fan**: 4-blade circulation fan inside a grille shroud whose blades smoothly spin when `ON`.
  - **Smart Lightbulb**: Dimmable LED globe whose glow scales with your dimmer's `1%–100%` brightness level.
  - **Live Icon Preview**: The **Add / Edit Device** and **Setup AP** dialogs preview the matching appliance icon live as you type the switch's name.

---

### Version 9.0 (`versionCode = 9`) — November 7, 2026
- **Instant Startup State Detection**: As soon as you open the app or switch Wi-Fi Homes, **WeMi Light Control** queries your switches in parallel so every row immediately shows whether the hardware is **ON**, **OFF (Accessible Standby)**, or **Disconnected / Offline**.
- **Three Unmistakable Visual States**: Every hardware illustration, status pill, and power button clearly distinguishes between a switch that is turned `OFF` but connected to Wi-Fi versus a switch that has lost power or Wi-Fi connectivity.

---

### Version 8.0 (`versionCode = 8`) — November 7, 2026
- **Zero-Cloud `10.22.22.1` Setup Wizard for Factory-Reset Switches**: Automatically detects when your phone connects to a blinking `WeMo.Light.xxx` / `WeMo.Mini.xxx` / `Wemo.LS3W.xxx` setup hotspot (`10.22.22.1`), surfaces a one-tap setup banner, scans nearby 2.4 GHz Wi-Fi networks, and provisions the switch directly to your home router without needing a Belkin cloud account.
- **Live Auto-Off Countdown Badges & Dimmer Night Mode (`20%`)**: Active auto-off timers now display a live `Auto-Off MM:SS` countdown badge right on the switch row, and WeMo dimmers gained a one-tap **Night Mode (`20%`)** hardware toggle.

---

### Version 7.0 (`versionCode = 7`) — November 7, 2026
- **Instant Physical Wall-Switch Sync (`<100ms` UPnP GENA Push)**: Subscribes directly to local UPnP push events from your switches so flipping a physical wall paddle updates the app screen in under 100 milliseconds.
- **Zero-Location-Permission Wi-Fi Detection**: Detects your active home Wi-Fi `/24` subnet and matches your saved Wi-Fi Home profile automatically without asking for Android GPS / Location permissions.

---

### Version 6.0 (`versionCode = 6`) — November 7, 2026
- **Self-Healing Local UPnP Engine & RTOS Switch Protection**: Added 2-stage self-healing reconnection (9-port sweep + serial-verified SSDP rediscovery) when routers reassign DHCP addresses or switches hop ports, plus automatic concurrency pacing (`Semaphore(3)`) so 2nd-Gen Belkin RTOS 3-way switches never lock up during whole-room toggles.
- **Real Home LAN Mode vs. Demo Showroom Mode**: Physical phones and Android TVs now operate directly against real local hardware in **Real LAN Mode**, while keeping an optional **Demo Showroom Mode** toggle on the **More** tab for previewing a fully populated 9-device smart home.

---

### Version 5.0 (`versionCode = 5`) — November 7, 2026
- **Multi-Home Wi-Fi Profiles (`WeMoWifiHomeBar`)**: Save and switch between multiple Wi-Fi locations (such as *My Home*, *Lake Cabin*, and *Studio Office*), each with its own remembered switches, subnet, and one-tap **Reconnect** button.

---

### Version 4.0 (`versionCode = 4`) — November 7, 2026
- **Live Wi-Fi Signal Bars & Response-Time Badges**: Every switch row displays a 4-bar wireless signal indicator and real-time response badge (`Excellent` <= 28ms, `Good` <= 65ms, `Slow` > 65ms, or `Offline`) measured from live local SOAP commands.

---

### Version 3.0 (`versionCode = 3`) — November 7, 2026
- **10-Foot Android TV Remote Dashboard (`wemi TV`)**: Full Android TV / Google TV experience (`LEANBACK_LAUNCHER`) with high-contrast 10-foot cards, glowing cyan D-Pad focus rings, TV remote shortcuts (`OK/Select` to toggle power, `Play/Pause` to toggle an entire room, `Left/Right` for `-10% / +10%` dimming), and 1-click living room scenes (**Movie Mode 20%**, **All ON**, **Night Mode**, **All OFF**).

---

### Version 2.0 (`versionCode = 2`) — November 7, 2026
- **Room Grouping & Master Room Controls**: Group switches into collapsible room cards (*Living Room*, *Bedroom*, *Kitchen*, *Hallway & Landing*, *Outdoor*) or switch to a **Flat List**, filter by room chips, toggle an entire room ON or OFF with the **Master Room Power Ring**, and dim every smart dimmer in a room together with the **Master Room Dimmer Slider**.

---

### Version 1.5 (Early Preview) — November 7, 2026
- **Interactive Local Wi-Fi Switch Scanner (SSDP) & Pull-to-Refresh**: Broadcasts local UPnP `M-SEARCH` discovery packets across your Wi-Fi network to find Belkin WeMo switches, dimmers, mini plugs, and motion sensors and adopt them in a single tap, paired with pull-to-refresh on the main **Devices** tab.

---

### Version 1.0 (`versionCode = 1`) — November 7, 2026
- **Initial Launch of WeMi Light Control**: Classic 2013 Belkin WeMo-inspired tactile interface (`#73D41B` lime-green header, brushed metallic device rows, and glowing power rings) with 100% local Wi-Fi switch & dimmer control (`1%–100%`), local daily schedules and auto-off timers on the **Rules** tab, and a live UPnP/SOAP packet inspector on the **More** tab.
