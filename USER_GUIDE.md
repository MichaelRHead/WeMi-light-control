# WeMi Light Control — User Guide (`USER_GUIDE.md`)

Welcome to **WeMi Light Control** (installed as **WeMi Switch Control** on your device), a 100% local-network controller for **Belkin's discontinued smart switch platform** (in-wall switches, 3-way switches, dimmers, motion sensors, and mini smart plugs) on **Android Phones, Tablets, Chromebooks (ChromeOS), Home Screen Widgets, and Android TV**.

WeMi communicates directly with your switches over your home Wi-Fi using standard local UPnP, SSDP, SOAP, and live push protocols — **no Belkin cloud account, external server, or internet connection is required**.

> **What's New in Each Version?** See **[Release Notes & Version History (`docs/user-guide/release-notes.md`)](docs/user-guide/release-notes.md)** for a plain-English summary of major features and improvements in every Google Play Store release (`v1.0` → `v35.0`), or tap the subtle version badge (`v35.0`) in the app's top header anytime.

---

## Quick Start

- **Already have switches on your home Wi-Fi?** Open the **Devices** tab, make sure your phone, tablet, Chromebook, or TV is on the same Wi-Fi network, and tap **`Scan Local Network (SSDP)`** (or the top-right **Refresh** icon). Your switches will appear automatically and group themselves into rooms.
- **Setting up a brand-new or factory-reset switch?** Connect your device's Wi-Fi to the switch's blinking setup hotspot (`WeMo.*` / `Wemo.LS3W.*`) and follow **[Fresh Device Setup (`10.22.22.1`)](docs/user-guide/fresh-device-setup.md)** — no cloud account needed.
- **Want to explore the app without switches nearby?** Open the **More** tab and switch on **Demo Showroom Mode** to try 14 sample switches, dimmers, fans, holiday trees, and rules across 3 sample homes.

---

## Feature Guides Index

Each guide below explains one major area of the app in clear, step-by-step language:

| # | Feature Guide | Summary of What You'll Learn |
| :--- | :--- | :--- |
| **1** | **[Main Screen & Device Controls](docs/user-guide/main-screen.md)** | How the **Devices** screen works: top header controls (**`wemi`**, tappable **`v35.0`** Release History pill, **`All Off`**, **Refresh**, **`Edit`**, **`+`**), three unmistakable power states (**ON**, **OFF Standby**, and **Offline**), automatic appliance icons (**Christmas Trees**, **Spinning Fans**, **Lights/Lamps**, **Motion Sensors**, and **Switches**), live Wi-Fi latency & 4-bar signal badges, dimmer sliders (`1%–100%`) & hardware **Night Mode (`20%`)**, and real-time wall-switch sync. |
| **2** | **[Switch Discovery & Network Scanning](docs/user-guide/switch-discovery.md)** | How to find switches on your Wi-Fi with the **SSDP Network Discovery Scan** sheet, choose discovery targets, adopt one or all switches (**`Adopt All`**), discover switches on **Chromebooks (ChromeOS)** including the guided manual `/24` subnet scan, and how automatic fallback scanning and self-healing reconnection keep switches working when routers change IP addresses or ports. |
| **3** | **[Fresh Device Setup & Wi-Fi Provisioning (`10.22.22.1`)](docs/user-guide/fresh-device-setup.md)** | Step-by-step instructions for connecting a brand-new or factory-reset switch to your 2.4 GHz home Wi-Fi without the discontinued cloud app: using the auto-detected **`10.22.22.1`** setup banner, scanning nearby 2.4 GHz networks (**`Scan APs`**), automatic encryption detection for both 1st-Gen OpenWrt and 2nd-Gen RTOS switches, and using the collapsible **`SETUP DIAGNOSTICS`** panel if a switch fails to connect. |
| **4** | **[Room Grouping & Master Room Controls](docs/user-guide/room-grouping.md)** | How switches automatically group into rooms (`Living Room`, `Bedroom`, `Kitchen`, `Ensuite`, `Hallway & Landing`, `Outdoor`, `Unassigned`, etc.) from their names, switching between **Grouped by Room** and **Flat List** views, filtering to a single room, toggling every switch in a room at once, and dimming an entire room together. |
| **5** | **[Home Screen Light & Room Widgets](docs/user-guide/home-screen-widgets.md)** | How to add resizable one-tap **Light** and **Whole-Room** toggle widgets to your Android Home Screen—either by pinning directly from a Room header or switch drawer, or from your launcher's widget picker—plus resizing, reconfiguring, 3-state visual feedback, and automatic two-way sync. |
| **6** | **[Editing, Renaming, Reordering & Adding Switches](docs/user-guide/editing-and-managing-devices.md)** | How to use **Edit Mode** (`Edit` / `Done`) to reorder switches (`↑`/`↓`), edit settings (`✎`), or remove switches (`🗑`); how the **Add / Edit Device** dialog previews appliance icons live as you type; how **Sync Name to Switch Hardware** updates the name stored inside the physical switch; and how manual IP entry automatically detects the switch's port and hardware info. |
| **7** | **[Multi-Home Wi-Fi Profiles & Switching](docs/user-guide/wifi-homes.md)** | How the **Wi-Fi Home Selector Bar** separates switches across multiple locations (e.g., *My Home*, *Lake Cabin*, *Studio Office*) without needing Android Location permissions, filters out factory switch hotspot names, keeps Chromebook home profiles clean, reconnects remembered switches in one tap, and resets remembered homes, switches, and rooms from the **More** tab. |
| **8** | **[On-Device Switch Rules, Timers & Schedules](docs/user-guide/rules-and-timers.md)** | How the **Rules** tab manages **`On-Switch`** hardware rules stored directly inside switch memory—creating, editing, pausing, and deleting **`Long Press`**, **`Auto-Off Delay`**, **`Sunrise`/`Sunset` Schedules**, and **`Timed Action`** rules, automatic switch clock sync, offline **Solar Location**, and one-tap **Backup Restore**. |
| **9** | **[Master Controls, Diagnostics, Demo Mode & Android TV](docs/user-guide/more-and-tv-mode.md)** | How to use the **More** tab (**`All Switches ON`** / **`All Switches OFF`**, **Real LAN Mode** vs. **Demo Showroom Mode**, confirmation-protected **Reset Homes, Discovered Switches & Rooms**, live **UPnP / SOAP Packet Log** with **`Save/Share Log`**, and **Open Source License** notices) and how to control your home from the couch using the 3-column **Android TV (`wemi TV`)** dashboard with D-Pad room dimming, inline dimmer sliders, and quick lighting scenes. |
| **10** | **[Troubleshooting, Screenshots & Sharing Telemetry Logs](docs/user-guide/telemetry-and-troubleshooting.md)** | Step-by-step instructions for capturing on-screen diagnostic screenshots (**`SETUP DIAGNOSTICS`**, **`UPnP / SOAP Packet Log`** including Home Screen widget taps, and inline switch diagnostics), exporting or sharing the 5-section **`wemi-telemetry-latest.txt`** report via **`Save/Share`** / **`Copy`**, and reading diagnostic lines *(Owned Exclusively by the Antigravity Debugging & Support Agent)*. |
| **11** | **[Release Notes & Version History (`v1.0` → `v35.0`)](docs/user-guide/release-notes.md)** | Plain-English summary of the major user-facing features, visual improvements, and reliability upgrades introduced in each Google Play Store release from **`v35.0`** back to **`v1.0`**. |

---

## Supported Hardware (Belkin's Discontinued Smart Switch Platform)

- **In-Wall Light Switch (1st Gen — `F7C030`)**: Single-pole Wi-Fi wall switch (`ON` / `OFF`).
- **3-Way Light Switch (2nd Gen RTOS — `WLS040` / `WLS0403`)**: Single-pole / 3-way rocker switch (`ON` / `OFF`).
- **Wi-Fi Smart Dimmer (`F7C059` OpenWrt & `WDS060` RTOS)**: Continuous `1%–100%` dimming plus hardware `20%` Night Mode.
- **Mini Smart Plug (`F7C063` & `WSP080`)**: Compact smart outlet plug (`ON` / `OFF`) — automatically displays a **Christmas Tree**, **Fan**, or **Lightbulb** icon when named accordingly!
- **Motion Sensor (`F7C028`)**: Local UPnP motion sensor trigger.

---

## Developer & Architecture Documentation

For engineering architecture, protocol details, design specifications, release governance, and roadmap information, see:
- [`AGENTS.md`](AGENTS.md) & [`ARCHITECTURE.md`](ARCHITECTURE.md) — Agent Guide & Detailed Source / Test Catalog
- [`LICENSE`](LICENSE) & [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) — MIT License & Open-Source / Brand Governance Specification *(Owned Exclusively by the Antigravity License & Brand Governance Agent)*
- [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) — User-Facing Release Notes for Every Google Play Store Version (`v1.0` → `v35.0`)
- [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — 6-Agent / 7-Stage Autonomous Release Review, Sign-Off & Play Store Promotion State Machine
- [`UX_DESIGN.md`](UX_DESIGN.md) — Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows *(Owned Exclusively by the Google AI Studio Agent)*
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`CHROMEOS_ARCVM_DISCOVERY_DESIGN.md`](CHROMEOS_ARCVM_DISCOVERY_DESIGN.md) — ChromeOS VM (ARCVM) Local-LAN Discovery & Split-Subnet Architecture
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Change Log of Implemented Features & Improvements (`v1.0` → `v35.0`)
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
