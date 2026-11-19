# Google Play Store Listing — WeMi Switch Control (v28.0 — Phone, Tablet & Android TV)

## App Identity & Metadata (Play Policy Compliant)
- **App Title (19 / 30 chars max):** `WeMi Switch Control`
- **Application ID:** `com.aistudio.wemiswitch.kxpqzr`
- **Version:** `28.0` (`versionCode = 28`)
- **Supported Form Factors:** Phone, 7-Inch Tablet, 10-Inch Tablet, and **Android TV (Leanback)**
- **Category:** House & Home / Tools
- **Brand Color Palette:** Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), Deep Sapphire (`#0F4C81`), Cool Slate (`#EEF2F6`)
- **Content Rating:** Everyone

## Short Description (78 / 80 chars max)
Local Wi-Fi controller for Belkin's discontinued smart switch platform & TV.

## Full Description
WeMi Switch Control brings fast, reliable local-network control back to Belkin's discontinued smart switch platform—including in-wall light switches, 3-way switches, smart dimmers, mini smart plugs, and motion sensors—across your Android phone, tablet, and **Android TV**, styled with an Ocean Teal, Electric Cyan, and Deep Sapphire interface.

WeMi Switch Control communicates directly with your switches over your home Wi-Fi network using standard UPnP / SSDP multicast discovery, local SOAP HTTP commands, and `<100ms` UPnP GENA push updates—with no cloud account, external server, or subscription required.

### What's New in Version 28.0
- **Google TV Focusable Dimmer Slider & Room Navigation:** Press `Down` from any Android TV dimmer toggle to focus the inline brightness slider (`Left`/`Right` steps `-10%`/`+10%`, `Back`/`Up` returns to the toggle), step whole-room dimmers from any Room Master Header, and navigate `Up`/`Down` cleanly across all 3 columns.
- **Faster Parallel Local Wi-Fi Control & Instant GENA Push:** Newly discovered, adopted, and reconnected switches subscribe immediately to `<100ms` physical switch updates, while whole-room toggles, **`All Off`**, and health checks run concurrently with embedded RTOS safety limiting.
- **Compact Single-Row Header with One-Tap `All Off`:** Dedicated `All Off`, `Refresh`, `Edit`, and `+` controls sit neatly beside the `wemi` wordmark on phones and tablets to maximize screen space for rooms and switches.
- **Self-Healing 9-Port Reconnect & 3-Tier Hardware Identity:** Automatically recovers from firmware port shifts (`49151–49159`) across toggles, dimming, renaming, and Night Mode, while matching switches by hardware serial and UDN so DHCP IP changes never mix up devices.
- **Smarter Room Grouping:** Automatically parses `CamelCase` and delimited switch names into rooms (Living Room, Bedroom, Ensuite, Kitchen, Hallway & Landing, or `Unassigned`) without overwriting custom room choices.
- **Zero-Cloud `10.22.22.1` Switch Setup & Local Diagnostics:** Provision factory-reset switches directly over local Wi-Fi with automatic OpenWrt/RTOS encryption and one-tap local diagnostic log export (`wemi-telemetry-latest.txt`, excluded from cloud backup).
- **Lean, Open-Source Pure Kotlin/DEX Release (`minSdk 24+`):** Built under the MIT License with a first-party local HTTP/UPnP engine, R8 optimization, and optional Wi-Fi hardware manifest support for Ethernet-connected Google TV boxes.

### Key Features
- **Instant One-Tap Cyan Power Rings:** Toggle individual switches or entire room groups ON or OFF over your local LAN with tactile 3-state hardware illustrations (On, Standby, Offline).
- **Native Android TV (`wemi TV`) 10-Foot Dashboard:** Automatic Leanback launcher UI with a 3-column room-grouped switch grid, glowing cyan `"FOCUSED • OK"` focus rings, TV Quick Scenes (`Movie 20%`, `Holiday ON`, `All OFF`), and inline D-Pad dimming.
- **Inline Dimmer & Night Mode Controls:** Smoothly dim smart dimmers and bulbs from 1% to 100%, jump to quick presets (21%, 50%, 75%, 100%), or toggle hardware Night Mode.
- **Interactive SSDP & Unicast Local Scanner:** Discover switches over `239.255.255.250:1900` with automatic `/24` unicast fallback when routers block multicast.
- **Local Rules, Schedules & Auto-Off Countdowns:** Create daily schedules, sunset/sunrise timers, motion triggers, and live `Auto-Off` countdowns that run locally.
- **Multi-Home Wi-Fi Profiles & Built-In SOAP Inspector:** Organize switches across multiple Wi-Fi networks and inspect real-time local UPnP/SOAP packets and RTT health metrics.

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
