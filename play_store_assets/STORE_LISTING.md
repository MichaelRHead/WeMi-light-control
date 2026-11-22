# Google Play Store Listing — WeMi Switch Control (v35.0 — Phone, Tablet, Chromebook, Widgets & Android TV)

## App Identity & Metadata (Play Policy Compliant)
- **App Title (19 / 30 chars max):** `WeMi Switch Control`
- **Application ID:** `com.aistudio.wemiswitch.kxpqzr`
- **Version:** `35.0` (`versionCode = 35`)
- **Supported Form Factors:** Phone, 7-Inch Tablet, 10-Inch Tablet, **Chromebooks (ChromeOS)**, **Android Home Screen Widgets**, and **Android TV (Leanback)**
- **Category:** House & Home / Tools
- **Brand Color Palette:** Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), Deep Sapphire (`#0F4C81`), Cool Slate (`#EEF2F6`)
- **Content Rating:** Everyone

## Short Description (75 / 80 chars max)
Local Wi-Fi widgets, phone & TV control for Belkin's discontinued switches.

## Full Description
WeMi Switch Control brings fast, reliable local-network control back to Belkin's discontinued smart switch platform—including in-wall light switches, 3-way switches, smart dimmers, mini smart plugs, and motion sensors—across your Android phone, tablet, **Chromebook**, **Home Screen Widgets**, and **Android TV**, styled with an Ocean Teal, Electric Cyan, and Deep Sapphire interface.

WeMi Switch Control communicates directly with your switches over your home Wi-Fi network using standard UPnP / SSDP multicast discovery, local SOAP HTTP commands, and `<100ms` UPnP GENA push updates—with no cloud account, external server, or subscription required.

### What's New in Version 35.0
- **On-Device Switch Rules Inspector (Read-Only):** Inspect schedules, Sunrise/Sunset timers, Auto-Off delays, Away Mode, and Long Press targets already stored inside compatible switches directly from the Rules tab or any switch's expanded drawer—resolving target switch names automatically and flagging drifted switch clocks.
- **Zero-Delay Capability Detection & Clear Two-Tier Rules UX:** Instantly detects on-device rules support during normal scans with firmware-cached verification, clear RTOS disclosures (noting Dimmer Night Mode stays on-device), and a collapsible **`Read-Only`** vs. **`App-Run`** layout that keeps local rule creation one tap away.
- **Refreshed User Guides & Home Screen Widgets Guide:** Expanded plain-English documentation covering Home Screen Light & Room Widgets, On-Device Switch Rules, and ChromeOS discovery.
- **Chromebook (ChromeOS) LAN Support, Widgets & Android TV:** Network-bound multicast discovery with physical-subnet fallback on Chromebooks, one-tap Home Screen Widgets, 3-column Android TV controls, zero-cloud `10.22.22.1` setup, and pure Kotlin/DEX packaging (`minSdk 24+`).

### Key Features
- **Home Screen Light & Room Widgets:** One-tap local Wi-Fi toggles for individual lights or whole rooms straight from your Android launcher without opening the app.
- **Instant One-Tap Cyan Power Rings:** Toggle individual switches or entire room groups ON or OFF over your local LAN with tactile 3-state hardware illustrations (On, Standby, Offline).
- **Native Android TV (`wemi TV`) 10-Foot Dashboard:** Automatic Leanback launcher UI with a 3-column room-grouped switch grid, glowing cyan `"FOCUSED • OK"` focus rings, TV Quick Scenes (`Movie 20%`, `Holiday ON`, `All OFF`), and inline D-Pad dimming.
- **Inline Dimmer & Night Mode Controls:** Smoothly dim smart dimmers and bulbs from 1% to 100%, jump to quick presets (21%, 50%, 75%, 100%), or toggle hardware Night Mode.
- **Interactive SSDP & Unicast Local Scanner:** Discover switches over `239.255.255.250:1900` with automatic `/24` unicast fallback when routers block multicast.
- **On-Device & Local Switch Rules:** Inspect schedules, Auto-Off delays, and Long Press targets stored directly inside compatible switches alongside app-run schedules, sunset/sunrise timers, and motion triggers over local Wi-Fi.
- **Multi-Home Wi-Fi Profiles & Built-In SOAP Inspector:** Organize switches across multiple Wi-Fi networks and inspect real-time local UPnP/SOAP packets and RTT health metrics.

## Complete Google Play Console Asset Inventory (`/play_store_assets/`)
1. **High-Res App Icon (`play_store_icon_512x512.png`):** 512 × 512 px, 32-bit PNG.
2. **Feature Graphic (`feature_graphic_1024x500.png`):** 1024 × 500 px, PNG.
3. **Android TV Banners (`tv/` & `@drawable/tv_banner`):**
   - `tv/tv_banner_1280x720.png`: 1280 × 720 px, 24-bit PNG (Google Play Console Android TV Banner upload).
   - `tv/tv_banner_320x180.png`: 320 × 180 px, 24-bit PNG (also bundled in APK at `res/drawable-xhdpi/tv_banner.png`).
4. **Android TV 16:9 Screenshots (`screenshots/android_tv/` — 1920 × 1080 px, 16:9 PNG):**
   - `tv_1_room_groups_and_remote_focus.png` (3-Column Room-Grouped TV Grid, Room Header `-10%`/`+10%` Dimming & Glowing Cyan `"FOCUSED • OK"` Focus Ring)
   - `tv_2_dimmer_and_ssdp_discovery.png` (Inline `"DIM -/+"` Dimmer Mode with `"Done Dimming"` Header Bar & Left/Right D-Pad Controls)
   - `tv_3_living_room_scenes_and_rules.png` (TV Remote Quick Scenes — Movie Mode 20%, Conditional Holiday ON, All OFF & Room Master Toggle)
5. **Phone Screenshots (`screenshots/phone/` — 1080 × 1920 px, 9:16 PNG):**
   - `phone_1_devices_and_power_rings.png` (Single-Row Top Bar with Subtle Version Badge, Room Grouping & 3-State Power Rings)
   - `phone_2_ssdp_network_scanner.png` (Pull-to-Refresh & SSDP Network Discovery Scanner with Port-Healed Switch Adoption)
   - `phone_3_inline_dimmer_control.png` (Master Room Dimmer, Inline Brightness Presets, `Pin Widget` & Compact On-Device Rules Drawer Readout)
   - `phone_4_local_rules_and_upnp_log.png` (On-Device Switch Rules Read-Only Inspector with Decoded Long Press/Sunset/Auto-Off Rules & App-Run Local Rules)
6. **7-Inch Tablet Screenshots (`screenshots/tablet_7_inch/` — 1200 × 1920 px PNG):**
   - `tablet7_1_devices_dashboard.png` (Two-Column Room Dashboard, Expanded Dimmer, `Pin Widget` & On-Device Rules Drawer)
   - `tablet7_2_ssdp_and_rules.png` (Zero-Cloud `10.22.22.1` Switch AP Provisioning, Collapsible `SETUP DIAGNOSTICS`, On-Device Switch Rules & SOAP/MIT Log)
7. **10-Inch Tablet & Large-Screen Screenshots (`screenshots/tablet_10_inch/` — 1600 × 2560 px & 1920 × 1080 px PNG):**
   - `tablet10_1_devices_dashboard.png` (Two-Column Room Dashboard, Expanded Dimmer, `Pin Widget` & On-Device Rules Drawer)
   - `tablet10_2_ssdp_and_rules.png` (Zero-Cloud `10.22.22.1` Switch AP Provisioning, Collapsible `SETUP DIAGNOSTICS`, On-Device Switch Rules & SOAP/MIT Log)
   - `tablet10_3_chromebook_desktop.png` (Chromebook 16:9 Widescreen Desktop 3-Column Multi-Room Dashboard & `15s` LAN Sync)
   - `tablet10_4_chromebook_ssdp_scanner.png` (Chromebook Split-Subnet SSDP Discovery, Guided `/24` Subnet Fallback & ChromeOS LAN Diagnostics)
8. **Chromebook (ChromeOS) 16:9 Desktop Screenshots (`screenshots/chromebook/` — 1920 × 1080 px, 16:9 PNG):**
   - `chromebook_1_desktop_dashboard.png` (Chromebook Widescreen Multi-Room Switch & Dimmer Dashboard with Clean Physical LAN Profile)
   - `chromebook_2_ssdp_subnet_scanner.png` (ChromeOS Virtual Network Detection, Guided `/24` Subnet Scanner & Live ChromeOS LAN Diagnostics)
9. **Release Notes (`RELEASE_NOTES.txt`), Privacy Policy (`PRIVACY_POLICY.md`), and Upload & Data Safety Guide (`DATA_SAFETY_GUIDE.md`).**
