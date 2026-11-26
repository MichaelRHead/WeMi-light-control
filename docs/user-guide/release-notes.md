# Release Notes & Version History

[← Back to User Guide Index](../../USER_GUIDE.md)

Welcome to the official user-facing **Release Notes** for **WeMi Light Control** (`com.aistudio.wemiswitch.kxpqzr`) on Google Play for Android Phones, Tablets, Chromebooks (ChromeOS), and Android TV.

Unlike our developer engineering log ([`IMPLEMENTED.md`](../../IMPLEMENTED.md)), this guide provides a clear, high-level summary of the **major user-facing features, visual improvements, and reliability upgrades** introduced in each Google Play Store release—from our latest release (**`v37.0`**) all the way back to **`v1.0`**.

For step-by-step instructions on using any feature mentioned below, visit the **[WeMi Light Control User Guide (`USER_GUIDE.md`)](../../USER_GUIDE.md)**.

---

## Quick Release Summary (`v1.0` → `v37.0`)

| Version | Play Store `versionCode` | Release Date | Headline User-Facing Additions |
| :--- | :---: | :--- | :--- |
| **[`v37.0`](#version-370-versioncode--37--november-26-2026)** | `37` | Nov 26, 2026 | **Zero-Cloud Household Configuration Sync (JSON Export/Import & 6-Digit PIN Phone-to-TV Local Pairing), Comprehensive Security & Privacy Hardening & Expanded Switch Drawer Layout Fix**: Sync Wi-Fi Homes, custom switch names, rooms, and ordering across phones, tablets, Chromebooks, and Android TV with zero cloud accounts or Wi-Fi passwords; redact diagnostic exports by default with opt-in Full Diagnostics toggles, ephemeral auto-remasking Wi-Fi setup passwords, RFC 1918 LAN validation, non-exported widget receivers, and cloud Auto-Backup exclusions; plus a cleaner full-width expanded switch drawer |
| **[`v36.0`](#version-360-versioncode--36--november-25-2026)** | `36` | Nov 25, 2026 | **Live On-Device Switch Rules Management (`On-Switch`), Automatic Clock Sync, Offline Solar Location, Pre-Write Backup Restore & Security/UX Hardening**: Create, edit, toggle, and delete native **Long Press**, **Auto-Off Delay**, **Schedule** (with **Sunrise/Sunset** offsets), and **Timed Action** rules directly in compatible switch memory (retiring phone-only app rules cleanly in Room v7), with automatic clock sync, offline city/signed-coordinate solar setup, automatic pre-write backups with one-tap restore, masked Wi-Fi setup passwords, and a unified **Rules** tab + expanded drawer **Manage Rules** bridge |
| **[`v35.0`](#version-350-versioncode--35--november-22-2026)** | `35` | Nov 22, 2026 | **On-Device Switch Rules Inspector (Read-Only), Two-Tier Rules UX & Refreshed User Guides**: Zero-delay capability detection + firmware-cached verification, read-only inspection of stored switch rules (`Long Press`, `Auto-Off Delay`, `Schedule` / `Sunrise`-`Sunset`, `Away Mode`, clock-sync alerts) in the collapsible **Rules** tab and expanded switch drawer, plus refreshed User Guides & new Home Screen Widgets guide |
| **[`v34.0`](#version-340-versioncode--34--november-21-2026)** | `34` | Nov 21, 2026 | **Chromebook (ChromeOS ARCVM) Local-LAN Discovery & Control + Refreshed Play Store Visuals**: Network-bound multicast SSDP & automatic physical-subnet fallback on Chromebooks, guided manual `/24` subnet scanner fallback when routers block multicast, clean CGNAT home profile hygiene, 15s Chromebook foreground sync, and refreshed Store/TV graphics |
| **[`v33.0`](#version-330-versioncode--33--november-21-2026)** | `33` | Nov 21, 2026 | **In-App Release History, UX Hygiene Cleanup, Conditional TV Holiday Scene & Discovery/Diagnostics Polish**: Subtle top-bar version pill + scrollable `Release History` dialog, streamlined top bar/More/dialogs with Demo-only preset gating and reset confirmation, conditional Android TV `Holiday ON` with D-Pad focus recovery, word-bounded icon/room classification, and collapsible `SETUP DIAGNOSTICS` auto-expansion |
| **[`v32.0`](#version-320-versioncode--32--november-21-2026)** | `32` | Nov 21, 2026 | **Discovery, Rules, GENA Push & Widget Diagnostics Hardening**: 4-pass hardware identity & widget UDN self-healing, decoded XML switch names, unified case-insensitive room grouping across phone/TV/widgets, isolated GENA push handlers, cross-home rule isolation & Auto-Off OFF default, and headless widget SOAP telemetry |
| **[`v31.0`](#version-310-versioncode--31--november-1920-2026)** | `31` | Nov 19–20, 2026 | **Home Screen Light & Room Toggle Widgets** (zero-app-launch local Wi-Fi toggles, one-tap pinning, multi-home isolation & rapid-tap protection), plus motion-trigger dimmer brightness & Wi-Fi Home isolation fix and instant SSDP search-target switching |
| **[`v28.0`](#version-280-versioncode--28--november-1519-2026)** | `28` | Nov 15–19, 2026 | Google TV focusable inline & Room-Header dimmer controls, steady single-row TV top bar while dimming, deterministic `Up`/`Down` Room Master Header navigation across all 3 columns (`wemi TV`), immediate `<100ms` GENA push on adoption, hardened UDN/SSDP discovery & `10.22.22.1` setup diagnostics |
| **[`v25.0`](#version-250-versioncode--25--november-14-2026)** | `25` | Nov 14, 2026 | Top-header **`All Off`** button next to Refresh for 1-tap whole-home power-off, plus R8-shrunk pure Kotlin/DEX release App Bundle with embedded Play Console deobfuscation mapping (`proguard.map`) |
| **[`v24.0`](#version-240-versioncode--24--november-11-2026)** | `24` | Nov 11, 2026 | Smaller, faster R8-shrunk release App Bundle with embedded Play Console crash deobfuscation mapping (`proguard.map`) and pure Kotlin/DEX packaging |
| **[`v23.0`](#version-230-versioncode--23--november-10-2026)** | `23` | Nov 10, 2026 | Redesigned 3-column Room-Grouped Android TV dashboard (`wemi TV`) with unmistakable 10-foot cursor focus & non-interfering Dimmer Adjust Mode, plus compact single-row phone/tablet `TopHeader` |
| **[`v22.0`](#version-220-versioncode--22--november-10-2026)** | `22` | Nov 10, 2026 | Refreshed Google Play Store & in-app visual showcase across Phone, 7"/10" Tablets & Android TV matching the live Ocean Teal, Electric Cyan & Deep Sapphire UI, plus streamlined `MIT` + `Apache-2.0` runtime |
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

### Version 37.0 (`versionCode = 37`) — November 26, 2026
- **Zero-Cloud Household Configuration Sync (JSON & PIN-Encrypted Local Wi-Fi Pairing)**: Set up your switches, custom names, rooms, and ordering once on your phone and share them effortlessly to Android TV, another phone, a tablet, or a Chromebook—with zero cloud accounts, zero storage permissions, and zero Wi-Fi passwords ever leaving your device.
  - **One-Tap Phone-to-TV & Device Pairing**: Open **`Sync`** in the top bar of **Android TV (`wemi TV`)** (or **More → Household Config Sync → Receive** on another device) to display a temporary 2-minute **6-digit pairing code** (with screenshot protection while visible), then tap **Send to TV** on your phone to push your household setup over your home Wi-Fi using **SPAKE2** key exchange and authenticated `AES-256-GCM` encryption and choose identity-safe **Merge** or **Replace All**.
  - **Portable JSON Export & Import**: Export or import a compact `.json` household snapshot anytime via Android's built-in file picker (Storage Access Framework), preserving live local IP addresses, power states, and on-device rules caches on the receiving device.
- **Comprehensive Security & Privacy Hardening**:
  - **Privacy-First Diagnostics**: Exported and copied troubleshooting logs now redact Wi-Fi SSIDs, MAC addresses, serial numbers, UDNs, and DNS IP addresses by default, with an explicit opt-in **Full Diagnostics** toggle whenever you choose to share unredacted identifiers for support, plus Android `EXTRA_IS_SENSITIVE` clipboard flagging.
  - **Hardened Setup, LAN, Rules & Widget Boundaries**: `10.22.22.1` setup Wi-Fi passwords now stay strictly in volatile memory, auto-remask when switching apps, and activate reveal-scoped screenshot protection (`FLAG_SECURE`) while revealed; manual IP dialogs validate private RFC 1918 LAN endpoints inline; local switch rules archives undergo strict read-only SQLite schema checks; live wall-switch GENA callbacks authenticate per-subscription tokens and LAN source IPs; Home Screen widget actions run through non-exported receivers; Room databases and preferences are excluded from cloud Auto-Backup while keeping direct cable transfer; and **Reset Homes, Switches & Rooms** also wipes saved diagnostic logs and widget bindings.
- **Expanded Switch Drawer & Household Sync UX Polish**: Unified the expanded switch drawer into a single clean **Connection & Hardware Details** card so `UPnP Endpoint`, `Serial Number`, and `Firmware` span the full card width above wrapping action chips (`Add Widget`, `Configure IP`, `Reconnect`) with zero cramped vertical text wrapping, and ensured `48dp+` touch targets, TalkBack announcements, and compact-screen wrapping across all new Household Sync cards and TV overlays.

---

### Version 36.0 (`versionCode = 36`) — November 25, 2026
- **Live On-Device Switch Rules Management (`On-Switch`)**: The **Rules** tab is now dedicated exclusively to native on-device switch rules stored directly inside compatible wall switches, dimmers, and plugs (`F7C030`, `WLS040` / `WLS0403`, `F7C059`, `F7C063`)—so your schedules and timers keep running even when your phone is turned off or away from home. Create, edit, enable/disable, and delete **Long Press** (controlling one or more target switches), **Auto-Off Delay**, **Schedules** (fixed times or **Sunrise/Sunset** with `-60m` to `+60m` offsets), and **Timed Actions** directly over your local Wi-Fi network, retiring legacy phone-only app rules cleanly via Room v7.
- **Armored Safety, Automatic Clock Sync & One-Tap Backup Restore**: Every write automatically syncs drifted switch clocks first, validates the rules archive before upload (preserving existing firmware tables and rejecting malformed payloads), verifies the stored version back from the switch, and retains up to 5 rotating pre-write backups stored locally in `noBackupFilesDir` (excluded from Android cloud Auto-Backup) with one-tap restore—even bootstrapping a fresh `rules.db` on factory-reset switches.
- **Offline Solar Location & Polished Rules UX**: Configure switch **Sunrise/Sunset** coordinates offline with zero GPS permissions using built-in city presets or signed latitude/longitude entry (supporting negative Western/Southern Hemisphere coordinates). Enjoy wrapping `±1h`/`±15m`/`±5m`/`±1m` time steppers, rotation-safe editor state, distinct start/end time validation, a **Go to Devices** bridge on empty homes, a guarded top-bar **`+`** button, and a clickable **Manage Rules** link in every switch's expanded drawer.
- **Security, Privacy & Cross-Surface Hardening**: Wi-Fi passwords in the `10.22.22.1` setup wizard are now masked by default with an accessible show/hide toggle, rules payloads are scrubbed from SOAP diagnostic logs, Widget Picker previews show accurate 3-state offline rings, and Play Store screenshots are updated to match the live `v36.0` **On-Switch** Rules experience.

---

### Version 35.0 (`versionCode = 35`) — November 22, 2026
- **On-Device Switch Rules Inspector (Read-Only)**: View automation rules stored directly inside compatible wall switches, dimmers, and plugs (`F7C030`, `WLS040` / `WLS0403`, `F7C059`, `F7C063`)—including **Long Press** (resolving hardware target IDs into friendly switch names), **Auto-Off Delay** timers, **Schedules** with native **Sunrise/Sunset** offsets and stored solar location, **Away Mode**, and **Motion / Sensor** triggers—from both the **Rules** tab and each switch's expanded drawer on the **Devices** tab. *(Live on-device rule editing is intentionally planned for a future release; WeMi keeps a rotating local backup of each untouched archive it reads.)*
- **Zero-Delay Capability Detection, Clock-Sync Alerts & RTOS Clarity**: Normal Wi-Fi discovery infers switch rules support with zero extra network calls and caches verified capability verdicts per firmware version (preserving saved homes, switches, and rules cleanly across the database upgrade). Newer RTOS models (`WDS060` Dimmer V2, `WSP080` Mini V2) display a clear **`No On-Device Rules`** badge (noting Dimmer Night Mode stays on-device), while switches whose internal clock drifted after cloud shutdown highlight **`Switch clock needs sync`**.
- **Two-Tier Rules Tab UX & Expanded Drawer Readout**: Visually separates **On-Device Switch Rules (`Read-Only`)** from **Local Switch Rules & Timers (`App-Run`)** with a collapsible **`Hide Switches` / `Show Switches (N)`** toggle, priority switch ordering (stored rules first, offline switches last), accessible `48dp+` actions, and inline `trigger → action` rule cards in the expanded switch drawer.
- **Refreshed User Guides, Home Screen Widgets Guide & Play Store Automation**: Added a dedicated **[Home Screen Light & Room Widgets](home-screen-widgets.md)** guide, refreshed all user guides in plain English, updated Play Store screenshots to showcase On-Device Switch Rules, and modernized Play promotion workflows to Node.js 24.

---

### Version 34.0 (`versionCode = 34`) — November 21, 2026
- **Chromebook (ChromeOS ARCVM) Local-LAN Discovery & Control**: Run WeMi Switch Control directly on Chromebooks with zero extra permissions. Network-bound multicast SSDP discovery, non-Belkin Router IGD subnet harvesting, and a 5-tier physical `/24` subnet fallback automatically discover switches across ChromeOS's virtual Android network (`100.115.92.x`) while preserving full phone and Android TV discovery behavior.
- **Guided Manual `/24` Subnet Scanner Fallback**: When a strict mesh router or isolated Wi-Fi access point blocks multicast on ChromeOS, the SSDP Scanner displays a clean card letting you enter your router's IPv4 prefix (e.g., `192.168.1` or `192.168.86`) to scan and adopt local switches in one tap.
- **Clean Virtual-Subnet Home Hygiene, Diagnostics & Smooth Chromebook Sync**: Phantom ChromeOS CGNAT addresses (`100.115.92.0/24`) are excluded from saved Wi-Fi Homes and `Add Home` pre-fills (and legacy CGNAT home names repair cleanly), unreachable GENA push subscriptions are skipped with a single diagnostic notice on virtual NAT interfaces to protect 2nd-Gen RTOS switches, `[NET]` exports include ChromeOS NAT/DNS context, network-interface lookups run off the UI thread, and Chromebooks automatically refresh switch states every 15 seconds while in the foreground.
- **Refreshed Google Play Store, Chromebook & Android TV Graphics**: Updated all phone, tablet, Android TV screenshots, feature graphic, and TV banners—and added dedicated 16:9 **Chromebook desktop** screenshots—to showcase the single-row top bar, subtle version pill, Home Screen Widget pinning, ChromeOS guided `/24` subnet discovery, collapsible setup diagnostics, and room-header TV dimming.

---

### Version 33.0 (`versionCode = 33`) — November 21, 2026
- **Subtle Top-Bar Version Pill & In-App Release History**: Tap the low-contrast **`v33.0`** pill tucked neatly into the unused space beside the **`wemi`** wordmark to browse user-friendly release summaries and dates (`v33.0` → `v1.0`) without changing the top bar's height or shifting any action buttons.
- **Streamlined Everyday UI & Safer Reset Flows**: Removed obsolete toggles and internal cards (`Header Logo Wordmark`, `Google Play Store Assets Ready`, and manual reachability toggles), strictly gated Demo presets behind **Demo Showroom Mode**, auto-hid the discovery banner once switches are present, clarified Motion Sensor actions (**`Simulate Motion`** in Demo Mode vs. **`Refresh Motion`** on Real LAN), added a confirmation prompt before resetting saved homes on the **More** tab, and fixed long SOAP log titles from squishing timestamps.
- **Smarter Android TV Holiday Scene & D-Pad Focus Recovery**: The **`Holiday ON`** quick scene on Android TV now appears only when your active home includes matching holiday lights (Christmas tree, holiday, Diwali/Dewali, Hanukkah, Halloween, fairy/string lights), targets only those holiday switches, and automatically recovers D-Pad focus to **`Movie 20%`** if the last holiday switch is removed while focused.
- **Sharper Discovery, Room Icons & Setup Diagnostics**: Word-bounded icon classification prevents false matches on names like `"Front Street Light"` or `"Tiffany Lamp"`, bath/ensuite room icons take precedence over bedroom keywords (e.g., `"Kids Bath"`), hardened SSDP retransmit timing keeps local Wi-Fi switch discovery consistent even when socket setup is delayed, blank-serial switches recover cleanly from UPnP port hops, and the collapsible **`SETUP DIAGNOSTICS`** card automatically re-expands on new setup errors or traces.

---

### Version 32.0 (`versionCode = 32`) — November 21, 2026
- **Stronger Hardware Identity, Name Decoding & Unified Room Grouping**: Symmetric 4-pass identity matching and Tier-2 UDN deduplication prevent duplicate switches after discovery, adoption, or `10.22.22.1` setup and carry over to Home Screen Widgets. Switch names containing `&amp;` or apostrophes decode cleanly, and phone, Android TV, and Home Screen Widgets share unified case-insensitive room grouping (`Upstairs Bedroom` → `Bedroom`, `Finished Basement` → `Basement`, `WCLight` → `Ensuite`).
- **Isolated GENA Live Push & Multi-Home Rule Fixes**: Per-client GENA callback socket isolation keeps instant physical switch updates running smoothly even if a switch drops mid-connection, while local rules stay strictly isolated per Wi-Fi Home so identically named switches in different homes never collide and new `Auto-Off` timers default to turning lights OFF.
- **Headless Home Screen Widget Telemetry & Clearer Diagnostics**: Home screen widget toggles, timeouts, and configuration summaries now flow directly into **More** → **UPnP / SOAP Packet Log** and the 5-section local `wemi-telemetry-latest.txt` export, alongside exact offline failure reasons and hardened `<password>` redaction.
- **Privacy, Governance & Code-Quality Polish**: Strengthened zero-cloud privacy disclosures for local widget preferences, resolved remaining lint warnings, and expanded automated verification to **`102/102` tests across `20` suites**.

---

### Version 31.0 (`versionCode = 31`) — November 19–20, 2026
- **Home Screen Light & Room Toggle Widgets**: Place resizable widgets for any individual light/switch or entire room right on your Android home screen and turn them ON or OFF directly over your local Wi-Fi network without opening the app—hardened with rapid double-tap serialization, multi-home room isolation, and serial-number self-healing.
- **One-Tap Widget Pinning & Launcher Picker**: Pin a room directly from the **Add to Home Screen** icon on any Room Group header or pin a light from the **`Add Widget`** button in a switch's expanded drawer—or drag the **WeMi Light & Room Toggle** widget from your Android launcher's widget drawer and pick any room or light filtered by Wi-Fi Home.
- **3-State Visual Feedback & Live Auto-Sync**: Home screen widgets display glowing Ocean Teal (`ON`), dark charcoal standby (`OFF`), or red dashed (`Offline`) states alongside dimmer brightness (`ON • 75%`) and active room counts (`2/3 ON`), updating automatically whenever lights are toggled from a widget, the app, or a physical wall switch.
- **Smarter Local Rules & SSDP Discovery Reliability**: Motion-trigger rules now honor dimmer brightness targets and Wi-Fi Home isolation, multi-home soft-AP SSID sanitization stays isolated per home, and changing the SSDP search target during an active scan cancels the superseded sweep immediately.

---

### Version 28.0 (`versionCode = 28`) — November 15–19, 2026
- **Google TV Focusable Inline Dimmer Slider & Room-Header Dimming (`wemi TV`)**: On Android TV and Google TV remotes without dedicated `Play/Pause` or `Menu` buttons, pressing **Down** from any dimmer card's ON/OFF toggle now focuses the inline **Dimmer Slider** directly underneath it (`◄ -10% • +10% ►`), where **Left / Right** adjusts brightness in `10%` steps, **Up** or **Back** returns to the ON/OFF toggle, and **Down** advances to the next row. Focusing a **Room Master Header** also supports **Left / Right** `-10% / +10%` whole-room dimmer stepping, while the top header bar stays cleanly packed on a single unclipped row.
- **Deterministic Room Master Header Navigation Across All Columns**: Navigating **Up / Down** from any column—including the leftmost switch card—now deterministically focuses and traverses full-width **Room Master Headers** (`LIVING ROOM`, `BEDROOM`, `KITCHEN`, `UNASSIGNED`) while remembering your active column.
- **Faster Local Wi-Fi Control & Instant Physical Switch Sync**: Newly discovered, adopted, or reconnected switches immediately subscribe to `<100ms` UPnP GENA push updates, while whole-room toggles, **`All Off`**, and background health checks run in parallel (`Semaphore(3)`-bounded for embedded RTOS switch safety).
- **Hardened Switch Discovery, `10.22.22.1` Setup & Reliability**: Strengthened canonical `<UDN>` / `<serialNumber>` hardware identity matching, DHCP-swap protection, automatic `/24` unicast discovery fallback when multicast is blocked, clearer `Status=2` Wi-Fi password guidance during `10.22.22.1` setup, and clean Auto-Off timer lifecycle cleanup.
- **Android 7.0+ (`minSdk 24`), Ethernet TV Compatibility & Strictly Local Diagnostics**: Hardened Android 7.0+ Wi-Fi setup cryptography, enabled Ethernet-connected Android TV / Google TV compatibility on Google Play, stamped app version metadata into support snapshots, and excluded optional `wemi-telemetry-latest.txt` diagnostic logs from cloud Auto-Backup.

---

### Version 25.0 (`versionCode = 25`) — November 14, 2026
- **Top-Header `All Off` Button Next to Refresh**: Added a one-tap **`All Off`** button (`all_off_devices_button`) immediately next to the **Refresh** button in the compact single-row top header bar so you can turn off every reachable switch and dimmer on your active Wi-Fi Home from the top of the screen.
- **Google Play Store `v25.0` (`versionCode = 25`) Release App Bundle**: Enabled release R8 code minification (`isMinifyEnabled = true`), resource shrinking (`isShrinkResources = true`), embedded Play Console crash/ANR deobfuscation mapping (`proguard.map`), and pure Kotlin/DEX packaging (`0` un-symbolicated native `.so` shims).

---

### Version 24.0 (`versionCode = 24`) — November 11, 2026
- **Smaller, Faster R8-Optimized Release App Bundle**: Enabled release R8 code minification and resource shrinking (`isMinifyEnabled = true`, `isShrinkResources = true`) with embedded Play Console crash/ANR deobfuscation mapping (`proguard.map`) and Room v5 / enum preservation rules (`proguard-rules.pro`).
- **Pure Kotlin/DEX Packaging (`0` Un-Symbolicated Native `.so` Shims)**: Configured `ndk.debugSymbolLevel = "FULL"` and excluded the unused pre-stripped `libandroidx.graphics.path.so` shim from the release bundle so the app ships as a 100% pure Kotlin/DEX bundle with zero Google Play Console native-symbol warnings.

---

### Version 23.0 (`versionCode = 23`) — November 10, 2026
- **Redesigned 3-Column Room-Grouped Android TV Dashboard (`wemi TV`)**: Replaced the multi-row TV header chrome with a single compact top bar so over 90% of vertical screen height showcases your switches grouped by room (`LIVING ROOM`, `BEDROOM`, `KITCHEN`, etc.) in a 3-column grid with one-click Room Master toggling.
- **Unmistakable 10-Foot Cursor Focus & Non-Interfering Dimmer Control**: Automatic initial focus on the first switch card with a bright `3.5.dp` Electric Cyan border (`#22D3EE`), illuminated surface (`#133B66`), and `FOCUSED • OK` badge. D-Pad Left/Right now always navigates freely across the 3 columns without getting trapped on dimmers, while pressing `Play/Pause`, `Menu`, or Long-Pressing `OK` on a dimmer enters a dedicated Gold (`#FBBF24`) **Dimmer Adjust Mode (`DIM -/+`)** (`◀ -10% / +10% ▶`, `OK` or `Back` = Done). Also replaced blocking popup modals on TV with non-blocking background LAN rescan toasts.
- **Compact Single-Row Top Header on Phones & Tablets**: Moved the **Refresh** and **Edit** buttons down onto the same header row as the `wemi` wordmark and `+` button—replacing the static `"LAN SWITCHES"` badge—to save vertical space and fit more rooms and switches above the fold.

---

### Version 22.0 (`versionCode = 22`) — November 10, 2026
- **Refreshed Google Play Store & In-App Visual Showcase**: Updated all 21 Google Play Store screenshots, banners, icons, and in-app preview graphics (`More` tab & Android TV Leanback banner) across Phone (`1080×1920`), 7-Inch Tablet (`1200×1920`), 10-Inch Tablet (`1600×2560`), and 10-Foot Android TV (`1920×1080` & `1280×720`) to match the live **Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), and Deep Sapphire (`#0F4C81`)** interface, full-width auto-shrinking switch titles (`AutoShrinkSwitchName`), 3-state hardware icons (`ON`, `OFF` Standby `#23272F`, and Offline `Disconnected` `#DC2626`), top-first dimmer controls, and native Phone/Tablet vs. Android TV headers without manual Phone/TV toggle buttons.
- **Streamlined Dependency & Licensing Footprint (`MIT` + `Apache-2.0` Only)**: Replaced external HTTP libraries (`OkHttp`) with a lightweight first-party local UPnP/HTTP engine (`LanHttpClient`) and replaced bundled TrueType font files with Android's built-in platform `FontFamily.SansSerif` (`Type.kt`), reducing APK overhead and keeping the runtime licensing footprint strictly to first-party `MIT` and AndroidX/Kotlin `Apache-2.0`.

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
