# Google Play Store Listing — WeMi Switch Control (v25.0 — Phone, Tablet & Android TV)

## App Identity & Metadata (Play Policy Compliant)
- **App Title (19 / 30 chars max):** `WeMi Switch Control`
- **Application ID:** `com.aistudio.wemiswitch.kxpqzr`
- **Version:** `25.0` (`versionCode = 25`)
- **Supported Form Factors:** Phone, 7-Inch Tablet, 10-Inch Tablet, and **Android TV (Leanback)**
- **Category:** House & Home / Tools
- **Brand Color Palette:** Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), Deep Sapphire (`#0F4C81`), Cool Slate (`#EEF2F6`)
- **Content Rating:** Everyone

## Short Description (78 / 80 chars max)
Local Wi-Fi controller for Belkin's discontinued smart switch platform & TV.

## Full Description
WeMi Switch Control brings reliable, instant local-network control back to Belkin's discontinued smart switch platform—including smart light switches, plugs, dimmable LED bulbs, and motion sensors—across your Android phone, tablet, and **Android TV**, styled with a modern Ocean Teal, Electric Cyan, and Sapphire Blue interface.

WeMi Switch Control communicates directly with your switches over your home Wi-Fi network using standard UPnP / SSDP multicast discovery and local SOAP HTTP commands—no cloud account, external server, or subscription required.

### What's New in Version 25.0
- **Smaller, R8-Optimized Pure Kotlin/DEX Release Bundle:** Enabled R8 minification, resource shrinking, and embedded Play Console deobfuscation mapping (`proguard.map`) while excluding unused transitive native `.so` shims.
- **Redesigned Android TV (`wemi TV`) 3-Column Room-Grouped Dashboard:** Full-height 3-column switch grid organized by Room Section Headers (`LIVING ROOM`, `BEDROOM`, `KITCHEN`) with unmistakable 10-foot Electric Cyan cursor focus (`FOCUSED • OK`), non-interfering `DIM -/+` brightness mode, and non-blocking LAN rescan.
- **Compact Single-Row Phone & Tablet Header:** Moved `Refresh` and `Edit` alongside the `wemi` wordmark and `+` button to maximize vertical screen space for room and switch cards.
- **Streamlined First-Party UPnP/HTTP Engine & `MIT` + `Apache-2.0` Footprint:** Replaced external HTTP libraries and bundled font files with a zero-dependency local-LAN HTTP/1.1 & UPnP GENA client (`LanHttpClient`) over JDK `SocketFactory` and platform `FontFamily.SansSerif` typography—keeping runtime dependencies strictly to AndroidX & Kotlin (`Apache-2.0`) under the `MIT` License (`includeInApk = false`).
- **Dedicated Native Phone/Tablet & Android TV Layouts:** Automatically runs the touch-first dashboard on phones/tablets and the 10-foot D-Pad dashboard (`wemi TV`) on Android TV / Google TV (`UI_MODE_TYPE_TELEVISION`) without manual Phone/TV mode switch buttons.
- **Streamlined `org.suppressingfire.wemi` Architecture & 9-Port Self-Healing Reconnect:** Automatically recovers from firmware port shifts (`49151–49159`) across power toggles, dimmer sliders, friendly-name updates, and Night Mode while keeping sockets bound to Wi-Fi even when cellular data is active.
- **Smarter Room Grouping & 3-Tier Hardware Identity Matching:** Splits `CamelCase`/underscore/hyphenated switch names, prioritizes **Ensuite** before general bedroom modifiers (`"Master Bath Vanity"`, `"Guest Bathroom"`), groups into Bedroom, Hallway & Landing, Kitchen, Living Room, or `Unassigned`, and matches switches by `<serialNumber>` first so DHCP IP swaps never mix up switches.
- **Zero-Cloud `10.22.22.1` Switch Provisioning & Screenshot Diagnostics:** Set up factory-reset switches from Belkin's discontinued smart switch platform (`F7C030`, `WLS0403`, `F7C059`, `WDS060`, `F7C063`, `WSP080`) directly over local Wi-Fi with automatic OpenWrt/RTOS AES-128-CBC encryption, non-destructive retry, and one-tap `wemi-telemetry-latest.txt` export.

### Key Features
- **Instant One-Tap Cyan Power Ring Toggles:** Turn individual switches or entire room groups ON or OFF directly over your local Wi-Fi network.
- **Android TV 3-Column Room Grid & Dimmer Adjust Mode:** Includes native Leanback launcher support (`LEANBACK_LAUNCHER`), room-grouped 3-column switch cards with glowing cyan `"FOCUSED • OK"` focus rings, focusable room master headers, and non-interfering `DIM -/+` brightness control.
- **Inline Brightness & Dimmer Drawer:** Smoothly dim smart bulbs and dimmer switches from 1% to 100%, or jump to quick brightness presets (21%, 50%, 75%, 100%).
- **Interactive SSDP Local Wi-Fi Scanner:** Broadcast `M-SEARCH` packets over `239.255.255.250:1900` across `urn:Belkin:service:basicevent:1`, `urn:Belkin:device:**`, and `upnp:rootdevice` to automatically discover switches, inspect their `/setup.xml` descriptors, and infer room assignments.
- **Motion Sensor Status & Triggers:** View the latest motion detection timestamps and link motion events to turn on bedroom or hallway lights automatically.
- **Local Rules & Schedules:** Create daily schedules, sunset timers, motion triggers, and auto-off countdowns that execute locally.
- **Built-In UPnP / SOAP Packet Inspector:** Diagnose network connectivity and inspect real-time `GetBinaryState` and `SetBinaryState` XML payloads.

## Complete Google Play Console Asset Inventory (`/play_store_assets/`)
1. **High-Res App Icon (`play_store_icon_512x512.png`):** 512 × 512 px, 32-bit PNG.
2. **Feature Graphic (`feature_graphic_1024x500.png`):** 1024 × 500 px, PNG.
3. **Android TV Banners (`tv/` & `@drawable/tv_banner`):**
   - `tv/tv_banner_1280x720.png`: 1280 × 720 px, 24-bit PNG (Google Play Console Android TV Banner upload).
   - `tv/tv_banner_320x180.png`: 320 × 180 px, 24-bit PNG (also bundled in APK at `res/drawable-xhdpi/tv_banner.png`).
4. **Android TV 16:9 Screenshots (`screenshots/android_tv/` — 1920 × 1080 px, 16:9 PNG):**
   - `tv_1_room_groups_and_remote_focus.png` (3-Column Room-Grouped TV Grid & Glowing Cyan `"FOCUSED • OK"` D-Pad Remote Focus)
   - `tv_2_dimmer_and_ssdp_discovery.png` (Non-Interfering `"DIM -/+"` Dimmer Adjust Mode & Background LAN Rescan)
   - `tv_3_living_room_scenes_and_rules.png` (TV Remote Quick Scenes — Movie Mode 20%, Holiday Lights ON, All OFF & Room Master Toggle)
5. **Phone Screenshots (`screenshots/phone/` — 1080 × 1920 px, 9:16 PNG):**
   - `phone_1_devices_and_power_rings.png` (Room Grouping System & Master Room Power Rings)
   - `phone_2_ssdp_network_scanner.png` (Pull-to-Refresh & SSDP Network Discovery Scanner)
   - `phone_3_inline_dimmer_control.png` (Master Room Dimmer & Inline Bulb Brightness Presets)
   - `phone_4_local_rules_and_upnp_log.png` (Local LAN Automation Rules & Live UPnP/SOAP Packet Log)
6. **7-Inch Tablet Screenshots (`screenshots/tablet_7_inch/` — 1200 × 1920 px PNG):**
   - `tablet7_1_devices_dashboard.png`
   - `tablet7_2_ssdp_and_rules.png`
7. **10-Inch Tablet Screenshots (`screenshots/tablet_10_inch/` — 1600 × 2560 px PNG):**
   - `tablet10_1_devices_dashboard.png`
   - `tablet10_2_ssdp_and_rules.png`
8. **Release Notes (`RELEASE_NOTES.txt`), Privacy Policy (`PRIVACY_POLICY.md`), and Upload & Data Safety Guide (`DATA_SAFETY_GUIDE.md`).**
