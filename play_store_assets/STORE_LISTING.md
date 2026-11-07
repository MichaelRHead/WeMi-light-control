# Google Play Store Listing — WeMi Switch Control (v2.0 — Phone, Tablet & Android TV)

## App Identity & Metadata (Play Policy Compliant)
- **App Title (19 / 30 chars max):** `WeMi Switch Control`
- **Application ID:** `com.aistudio.wemiswitch.kxpqzr`
- **Version:** `2.0` (`versionCode = 2`)
- **Supported Form Factors:** Phone, 7-Inch Tablet, 10-Inch Tablet, and **Android TV (Leanback)**
- **Category:** House & Home / Tools
- **Brand Color Palette:** Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), Deep Sapphire (`#0F4C81`), Cool Slate (`#EEF2F6`)
- **Content Rating:** Everyone

## Short Description (79 / 80 chars max)
Local Wi-Fi controller for WeMi & WeMo switches on Phone, Tablet & Android TV.

## Full Description
WeMi Switch Control brings reliable, instant local-network control back to your home's smart light switches, plugs, dimmable LED bulbs, and motion sensors across your Android phone, tablet, and **Android TV**—styled with a modern Ocean Teal, Electric Cyan, and Sapphire Blue interface.

WeMi Switch Control communicates directly with your switches over your home Wi-Fi network using standard UPnP / SSDP multicast discovery and local SOAP HTTP commands—no cloud account, external server, or subscription required.

### What's New in Version 2.0
- **Android TV 10-Foot Remote Interface:** Control your living room, bedroom, and kitchen lights right from your couch using your Android TV remote (D-Pad navigation, OK/Select power toggle, Left/Right `-10% / +10%` dimmer control, Play/Pause Master Room toggle, and one-click Movie Mode / Holiday / Bedtime TV scenes).
- **Room Grouping System & Master Room Toggle:** Organize switches into rooms (Living Room, Bedroom, Kitchen, Hallway & Landing, Ensuite, Office, Outdoor) and toggle every light in a room ON or OFF simultaneously with one tap on the Master Room Power Ring.
- **Master Room Brightness Control:** Smoothly adjust the brightness of all dimmable smart bulbs in a room at once (`1%–100%`).
- **Pull-to-Refresh SSDP Network Scan:** Swipe down on the Devices list at any time to broadcast a live UDP multicast `M-SEARCH` scan (`239.255.255.250:1900`) and sync newly discovered switches into their assigned rooms.

### Key Features
- **Instant One-Tap Cyan Power Ring Toggles:** Turn individual switches or entire room groups ON or OFF directly over your local Wi-Fi network.
- **Android TV D-Pad & Remote Shortcuts:** Includes native Leanback launcher support (`LEANBACK_LAUNCHER`), high-contrast 10-foot switch cards with glowing cyan focus rings, and an interactive on-screen TV remote D-Pad bar.
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
   - `tv_1_room_groups_and_remote_focus.png` (10-Foot Room Groups & Glowing Cyan D-Pad Remote Focus)
   - `tv_2_dimmer_and_ssdp_discovery.png` (TV Remote Left/Right Dimmer Bar & SSDP Multicast Discovery)
   - `tv_3_living_room_scenes_and_rules.png` (TV Remote Quick Scenes — Movie Mode 20%, Holiday Lights ON, Bedtime All OFF & Local Rules)
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
