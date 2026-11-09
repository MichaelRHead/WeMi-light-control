# WeMi Light Control — User Guide (`docs/USER_GUIDE.md`)

Welcome to **WeMi Light Control**, a 100% local-network Android and Android TV controller for **Belkin WeMo** smart switches, dimmers, motion sensors, and mini smart plugs.

**WeMi Light Control** communicates directly with your switches over your home Wi-Fi using standard local UPnP, SSDP, SOAP, and GENA push protocols — **no Belkin cloud account, external server, or internet connection is required**.

---

## Feature Guides Index

Each guide below explains one major area of the app in detail:

| # | Feature Guide | Summary of What You'll Learn |
| :--- | :--- | :--- |
| **1** | **[Main Screen & Device Controls](user-guide/main-screen.md)** | How the **Devices** screen works: startup state detection (`ON`, `OFF / Accessible Standby` with dark-gray IEC power icon, and `Disconnected`), full-width reactive switch names, automatic switch-name icons (**Christmas Trees**, **Fans**, **Lights/Lamps**, **Motion Sensors**, and **Switches**), live Wi-Fi latency & 4-bar signal badges, inline dimmer sliders (`1–100%`), hardware **Night Mode (`20%`)**, and real-time physical wall-switch updates. |
| **2** | **[Switch Discovery & Network Scanning](user-guide/switch-discovery.md)** | How to discover Belkin WeMo switches on your Wi-Fi using the **SSDP Local Network Discovery** banner and scanner sheet, choose UPnP Search Targets (`ST`), adopt individual or all discovered switches (`Adopt All`), and how automatic unicast `/24` fallback and 9-port self-healing reconnection keep switches connected when IPs or ports change. |
| **3** | **[Fresh Device Setup & Wi-Fi Provisioning (`10.22.22.1`)](user-guide/fresh-device-setup.md)** | Step-by-step instructions for setting up a brand-new or factory-reset Belkin WeMo switch (`WeMo.Light.xxx`, `WeMo.Switch.xxx`, `WeMo.Mini.xxx`) without the Belkin cloud app: using the auto-detected **`10.22.22.1` Setup AP Banner**, scanning 2.4 GHz networks (`Scan APs`), automatic OpenWrt/RTOS AES-128-CBC password encryption, and adopting the switch onto your Home Wi-Fi. |
| **4** | **[Room Grouping & Master Room Controls](user-guide/room-grouping.md)** | How to organize switches by room (`Living Room`, `Bedroom`, `Kitchen`, `Porch & Patio`, etc.), switch between **Grouped by Room** and **Flat List** views, filter by a single room, toggle an entire room **ON/OFF** with one tap on the **Master Room Power Ring**, and dim all lights in a room together using the **Room Master Brightness Drawer**. |
| **5** | **[Editing, Renaming, Reordering & Adding Switches](user-guide/editing-and-managing-devices.md)** | How to use **Edit Mode** (`Edit` / `Done`) to reorder (`↑`/`↓`), edit (`✎`), or remove (`🗑`) switches; how the **Add / Edit Device** dialog previews auto-classified icons (`Tree Lights`, `Fan`, `Light`) live as you type; how **Sync Name to WeMo Hardware** updates `<friendlyName>` on the physical switch; and how manual IP entry automatically probes all 9 WeMo ports. |
| **6** | **[Multi-Home Wi-Fi Profiles & Switching](user-guide/wifi-homes.md)** | How the **Wi-Fi Home Selector Bar** manages switches across multiple locations (e.g., `My Home`, `Lake Cabin`, `Studio Office`), detects your local `/24` subnet without requiring Android Location permissions, filters out Belkin soft-AP SSIDs (`WeMo.*` / `Wemo.LS3W.*`) from `GetMetaInfo`, reconnects all remembered switches in one tap, and resets remembered homes/switches/rooms via the **`Reset`** chip. |
| **7** | **[Rules, Schedules & Auto-Off Timers](user-guide/rules-and-timers.md)** | How to create and manage local **Schedule Rules** (`Daily at 6:30 PM`, `Sunset`, `Sunrise`), **Auto-Off Timers** (with live `Auto-Off MM:SS` countdown badges on the switch row), and **Motion Trigger Rules** on the **Rules** tab, plus testing rules immediately with the `▶` button. |
| **8** | **[Master Controls, Diagnostics, Demo Mode & Android TV](user-guide/more-and-tv-mode.md)** | How to use the **More** tab (`All Switches ON` / `All Switches OFF`, **Real LAN Mode** vs. **Demo Showroom Mode**, one-tap **Reset Homes, Discovered Switches & Rooms**, `wemi`/`wemo` header wordmark toggle, and the **Live UPnP / SOAP Packet Log Inspector**) and how to control your home from the couch with the **10-Foot Android TV Dashboard (`wemi TV`)** and TV remote D-Pad shortcuts. |

---

## Supported Belkin WeMo Hardware

- **Belkin WeMo In-Wall Light Switch (1st Gen — `F7C030`)**: Single-pole Wi-Fi wall switch (`ON` / `OFF`).
- **Belkin WeMo 3-Way Light Switch (2nd Gen RTOS — `WLS040` / `WLS0403`)**: Single-pole / 3-way rocker switch (`ON` / `OFF`).
- **Belkin WeMo Wi-Fi Smart Dimmer (`F7C059` OpenWrt & `WDS060` RTOS)**: Continuous `1%–100%` dimming plus hardware `20%` Night Mode.
- **Belkin WeMo Mini Smart Plug (`F7C063` & `WSP080`)**: Compact smart outlet plug (`ON` / `OFF`) — automatically displays a **Christmas Tree**, **Fan**, or **Lightbulb** icon when named accordingly!
- **Belkin WeMo Motion Sensor (`F7C028`)**: Local UPnP motion sensor trigger.

---

## Developer & Architecture Documentation

For protocol details, architecture diagrams, and roadmap information, see:
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Change Log of Implemented Features & Improvements
- [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Human-Readable Code Modularization & End-to-End Validation Plan
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`../AGENTS.md`](../AGENTS.md) — Architecture & Agent Guide
