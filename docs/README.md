# WeMi Light Control — User Documentation Index

Welcome to the user documentation for **WeMi Light Control**, a local-network Android and Android TV controller for **Belkin's discontinued smart switch platform** (in-wall switches, dimmers, motion sensors, and mini smart plugs).[^1]

## User Guides (`docs/user-guide/`)

| Guide | Topics Covered |
| :--- | :--- |
| **[Main Screen & Device Controls](user-guide/main-screen.md)** | Devices dashboard, power states (`ON`, `OFF / Accessible Standby`, `Disconnected`), custom hardware and appliance illustrations, inline dimmer sliders (`1–100%`), Night Mode (`20%`), and live signal quality badges. |
| **[Switch Discovery & Network Scanning](user-guide/switch-discovery.md)** | Finding switches over local Wi-Fi via UPnP/SSDP discovery, adopting discovered switches, `/24` subnet fallback scanning, and automatic 9-port self-healing reconnection. |
| **[Fresh Device Setup (`10.22.22.1`)](user-guide/fresh-device-setup.md)** | Provisioning brand-new or factory-reset smart switches onto your 2.4 GHz home Wi-Fi without the discontinued cloud app. |
| **[Room Grouping & Master Controls](user-guide/room-grouping.md)** | Automatic room assignment from switch names, switching between **Grouped by Room** and **Flat List** views, room filter chips, and whole-room power/dimmer controls. |
| **[Editing, Renaming & Managing Switches](user-guide/editing-and-managing-devices.md)** | Reordering, renaming, or removing switches in **Edit Mode**, syncing `<friendlyName>` directly to switch hardware, live appliance icon previews, and manual IP entry. |
| **[Multi-Home Wi-Fi Profiles](user-guide/wifi-homes.md)** | Managing switches across multiple Wi-Fi networks/locations (`My Home`, `Lake Cabin`, `Studio Office`), one-tap reconnection, and resetting remembered homes and switches. |
| **[Rules, Schedules & Auto-Off Timers](user-guide/rules-and-timers.md)** | Creating local daily schedules, Sunset/Sunrise triggers, Auto-Off countdown timers, and motion-triggered rules. |
| **[Master Controls, Demo Mode & Android TV](user-guide/more-and-tv-mode.md)** | Using the **More** tab (`All Switches ON/OFF`, Demo Showroom Mode, Open Source License) and controlling your home with a TV remote in the 10-foot **Android TV (`wemi TV`)** interface. |
| **[Troubleshooting & Telemetry Logs](user-guide/telemetry-and-troubleshooting.md)** | Capturing on-screen diagnostic views (`SETUP DEBUG DETAILS`, `UPnP / SOAP Packet Log`, Inline Switch Diagnostics) and exporting or sharing `wemi-telemetry-latest.txt`. |
| **[Release Notes & Version History](user-guide/release-notes.md)** | Summary of user-facing features, visual refinements, and reliability improvements across releases (`v1.0` → `v23.0`). |

---

[^1]: Top-level user guide overview and developer/architecture documentation (`USER_GUIDE.md`, `AGENTS.md`, `IMPLEMENTED.md`, `UPCOMING_FEATURES.md`, `UX_DESIGN.md`, etc.) are maintained as `ALL_CAPS.md` files in the [repository root (`../`)](../).
