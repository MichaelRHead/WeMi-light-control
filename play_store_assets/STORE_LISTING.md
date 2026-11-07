# Google Play Store Listing — WeMi Switch Control

## App Identity & Metadata (Play Policy Compliant)
- **App Title (19 / 30 chars max):** `WeMi Switch Control`
- **Application ID:** `com.aistudio.wemiswitch.kxpqzr`
- **Category:** House & Home / Tools
- **Content Rating:** Everyone

## Short Description (78 / 80 chars max)
Local Wi-Fi controller for WeMi and WeMo smart switches, plugs, bulbs & rules.

## Full Description
WeMi Switch Control brings reliable, instant local-network control back to your home's smart light switches, plugs, dimmable LED bulbs, and motion sensors.

Designed with the familiar, classic layout you already know, WeMi Switch Control communicates directly with your switches over your home Wi-Fi network using standard UPnP / SSDP multicast discovery and local SOAP HTTP commands—no cloud account, external server, or subscription required.

### Key Features
- **Instant One-Tap Power Ring Toggles:** Turn light switches, holiday lights, and smart outlets ON or OFF directly over your local Wi-Fi network.
- **Inline Brightness & Dimmer Drawer:** Smoothly dim smart bulbs and dimmer switches from 1% to 100%, or jump to quick brightness presets (21%, 50%, 75%, 100%).
- **Automatic Local Wi-Fi Discovery (SSDP):** Scan `239.255.255.250:1900` to automatically locate compatible UPnP switches on ports `49152–49155`, or add switches directly by IP address.
- **Motion Sensor Status & Triggers:** View the latest motion detection timestamps and link motion events to turn on bedroom or hallway lights automatically.
- **Local Rules & Schedules:** Create daily schedules, sunset timers, motion triggers, and auto-off countdowns that execute locally.
- **Built-In UPnP / SOAP Packet Inspector:** Diagnose network connectivity and inspect real-time `GetBinaryState` and `SetBinaryState` XML payloads from the More tab.

## Graphic Assets Included in `/play_store_assets/`
1. **High-Res App Icon (`play_store_icon_512x512.png`):** 512 × 512 px, 32-bit PNG.
2. **Feature Graphic (`feature_graphic_1024x500.png`):** 1024 × 500 px, PNG.

## Google Play Data Safety Summary
- **Data Collection:** No personal or device data is collected or transmitted off-device.
- **Data Sharing:** No data is shared with third parties.
- **Network Usage:** Local LAN HTTP/UDP traffic (`INTERNET`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`) is used strictly to discover and toggle smart switches on the user's local subnet.
