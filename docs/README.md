# WeMi Light Control — User Documentation Index

Welcome to the user documentation for **WeMi Light Control** (installed as **WeMi Switch Control** on your device), a 100% local-network controller for **Belkin's discontinued smart switch platform** (in-wall switches, 3-way switches, dimmers, motion sensors, and mini smart plugs) on **Android Phones, Tablets, Chromebooks (ChromeOS), Home Screen Widgets, and Android TV**.[^1]

## User Guides (`docs/user-guide/`)

| Guide | Topics Covered |
| :--- | :--- |
| **[Main Screen & Device Controls](user-guide/main-screen.md)** | Devices dashboard, top-bar controls (`All Off`, `v35.0` Release History), 3-state icons (`ON`, `OFF Standby`, `Offline`), automatic appliance illustrations, dimmer sliders (`1%–100%`), Night Mode (`20%`), and live signal quality badges. |
| **[Switch Discovery & Network Scanning](user-guide/switch-discovery.md)** | Finding switches over local Wi-Fi via UPnP/SSDP discovery, adopting switches, Chromebook (ChromeOS) local-LAN discovery & manual `/24` subnet scan, and automatic self-healing reconnection. |
| **[Fresh Device Setup (`10.22.22.1`)](user-guide/fresh-device-setup.md)** | Connecting brand-new or factory-reset smart switches onto your 2.4 GHz home Wi-Fi without the discontinued cloud app. |
| **[Room Grouping & Master Controls](user-guide/room-grouping.md)** | Automatic room assignment from switch names, switching between **Grouped by Room** and **Flat List** views, room filter chips, and whole-room power & dimmer controls. |
| **[Home Screen Light & Room Widgets](user-guide/home-screen-widgets.md)** | Pinning and configuring resizable one-tap Android Home Screen toggle widgets for individual lights or whole rooms, with 3-state feedback and live two-way sync. |
| **[Editing, Renaming & Managing Switches](user-guide/editing-and-managing-devices.md)** | Reordering, renaming, or removing switches in **Edit Mode**, syncing names directly to switch hardware, live appliance icon previews, and manual IP entry. |
| **[Multi-Home Wi-Fi Profiles](user-guide/wifi-homes.md)** | Managing switches across multiple Wi-Fi locations (*My Home*, *Lake Cabin*, *Studio Office*), Chromebook home hygiene, one-tap reconnection, and resetting remembered homes and switches. |
| **[Rules, Schedules & Auto-Off Timers](user-guide/rules-and-timers.md)** | Inspecting **`Read-Only`** **On-Device Switch Rules** stored in switch memory (`Long Press`, Auto-Off delays, Sunrise/Sunset schedules, capability badges & clock checks) and creating **`App-Run`** local daily schedules, Auto-Off countdown timers, and motion-triggered rules. |
| **[Master Controls, Demo Mode & Android TV](user-guide/more-and-tv-mode.md)** | Using the **More** tab (`All Switches ON/OFF`, Demo Showroom Mode, Reset Homes, Live Packet Log, Open Source License) and controlling your home with a TV remote in the 10-foot **Android TV (`wemi TV`)** interface. |
| **[Troubleshooting & Telemetry Logs](user-guide/telemetry-and-troubleshooting.md)** | Capturing on-screen diagnostic screenshots (`SETUP DIAGNOSTICS`, `UPnP / SOAP Packet Log`, inline switch diagnostics) and exporting or sharing `wemi-telemetry-latest.txt`. |
| **[Release Notes & Version History](user-guide/release-notes.md)** | Plain-English summary of user-facing features, visual refinements, and reliability improvements across releases (`v1.0` → `v35.0`). |

---

[^1]: Top-level user guide overview and developer/architecture documentation (`USER_GUIDE.md`, `AGENTS.md`, `ARCHITECTURE.md`, `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `UX_DESIGN.md`, etc.) are maintained as `ALL_CAPS.md` files in the [repository root (`../`)](../).
