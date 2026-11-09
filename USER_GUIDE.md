# WeMi Light Control — User Guide (`USER_GUIDE.md`)

Welcome to **WeMi Light Control**, a 100% local-network Android and Android TV controller for **Belkin WeMo** smart switches, dimmers, motion sensors, and mini smart plugs.

**WeMi Light Control** communicates directly with your switches over your home Wi-Fi using standard local UPnP, SSDP, SOAP, and GENA push protocols — **no Belkin cloud account, external server, or internet connection is required**.

> **What's New in Each Version?** See **[Release Notes & Version History (`docs/user-guide/release-notes.md`)](docs/user-guide/release-notes.md)** for a high-level summary of major features and improvements in every Google Play Store release (`v1.0` → `v20.0`).

---

## Feature Guides Index

Each guide below explains one major area of the app in detail:

| # | Feature Guide | Summary of What You'll Learn |
| :--- | :--- | :--- |
| **1** | **[Main Screen & Device Controls](docs/user-guide/main-screen.md)** | How the **Devices** screen works: startup state detection (`ON`, `OFF / Accessible Standby` with dark-gray IEC power icon, and `Disconnected`), full-width reactive switch names, automatic switch-name icons (**Christmas Trees**, **Fans**, **Lights/Lamps**, **Motion Sensors**, and **Switches**), live Wi-Fi latency & 4-bar signal badges, inline dimmer sliders (`1–100%`), hardware **Night Mode (`20%`)**, and real-time physical wall-switch updates. |
| **2** | **[Switch Discovery & Network Scanning](docs/user-guide/switch-discovery.md)** | How to discover Belkin WeMo switches on your Wi-Fi using the **SSDP Local Network Discovery** banner and scanner sheet, choose UPnP Search Targets (`ST`), adopt individual or all discovered switches (`Adopt All`), and how automatic unicast `/24` fallback and 9-port self-healing reconnection keep switches connected when IPs or ports change. |
| **3** | **[Fresh Device Setup & Wi-Fi Provisioning (`10.22.22.1`)](docs/user-guide/fresh-device-setup.md)** | Step-by-step instructions for setting up a brand-new or factory-reset Belkin WeMo switch (`WeMo.Light.xxx`, `WeMo.Switch.xxx`, `Wemo.LS3W.xxx`, `WeMo.Mini.xxx`) without the Belkin cloud app: using the auto-detected **`10.22.22.1` Setup AP Banner**, scanning 2.4 GHz networks (`Scan APs`), automatic OpenWrt/RTOS AES-128-CBC password encryption (all 6 `pywemo` method × length-suffix combinations), and the on-screen **`Setup Debug Details (Screenshot)`** panel. |
| **4** | **[Room Grouping & Master Room Controls](docs/user-guide/room-grouping.md)** | How switches are organized by room (`Living Room`, `Bedroom`, `Kitchen`, `Hallway & Landing`, `Outdoor`, `Unassigned`, etc.) using expanded `<friendlyName>` keyword inference (`"bed"`, `"stair"`, `"girls"`/`"kids"`), automatic migration for switches previously stuck in `Living Room`, switching between **Grouped by Room** and **Flat List** views, filtering by a single room, and toggling or dimming an entire room at once. |
| **5** | **[Editing, Renaming, Reordering & Adding Switches](docs/user-guide/editing-and-managing-devices.md)** | How to use **Edit Mode** (`Edit` / `Done`) to reorder (`↑`/`↓`), edit (`✎`), or remove (`🗑`) switches; how the **Add / Edit Device** dialog previews auto-classified icons (`Tree Lights`, `Fan`, `Light`) live as you type; how **Sync Name to WeMo Hardware** updates `<friendlyName>` on the physical switch; and how manual IP entry automatically probes all 9 WeMo ports. |
| **6** | **[Multi-Home Wi-Fi Profiles & Switching](docs/user-guide/wifi-homes.md)** | How the **Wi-Fi Home Selector Bar** manages switches across multiple locations (e.g., `My Home`, `Lake Cabin`, `Studio Office`), detects your local `/24` subnet without requiring Android Location permissions, filters out Belkin soft-AP SSIDs (`WeMo.*` / `Wemo.LS3W.*`) from `GetMetaInfo`, reconnects all remembered switches in one tap, and resets remembered homes/switches/rooms via the **`Reset`** chip. |
| **7** | **[Rules, Schedules & Auto-Off Timers](docs/user-guide/rules-and-timers.md)** | How to create and manage local **Schedule Rules** (`Daily at 6:30 PM`, `Sunset`, `Sunrise`), **Auto-Off Timers** (with live `Auto-Off MM:SS` countdown badges on the switch row), and **Motion Trigger Rules** on the **Rules** tab, plus testing rules immediately with the `▶` button. |
| **8** | **[Master Controls, Diagnostics, Demo Mode & Android TV](docs/user-guide/more-and-tv-mode.md)** | How to use the **More** tab (`All Switches ON` / `All Switches OFF`, **Real LAN Mode** vs. **Demo Showroom Mode**, one-tap **Reset Homes, Discovered Switches & Rooms**, `wemi`/`wemo` header wordmark toggle, and the **Live UPnP / SOAP Packet Log Inspector** with **`Save/Share Log`**) and how to control your home from the couch with the **10-Foot Android TV Dashboard (`wemi TV`)** and TV remote D-Pad shortcuts. |
| **9** | **[Troubleshooting, Screenshots & Sharing Telemetry Logs](docs/user-guide/telemetry-and-troubleshooting.md)** | Step-by-step instructions for capturing on-screen diagnostic screenshots (**`SETUP DEBUG DETAILS (SCREENSHOT)`**, **`UPnP / SOAP Packet Log`**, and **Inline Switch Diagnostics (`ⓘ`)**), exporting or sharing **`wemi-telemetry-latest.txt`** via **`Save/Share`** / **`Copy`**, and decoding `[NET]`, `[PROBE]`, `[FLAGS]`, `[TARGET AP]`, and `[STEP 1..5]` setup status codes *(Owned Exclusively by the Antigravity Debugging & Support Agent)*. |
| **10** | **[Release Notes & Version History (`v1.0` → `v20.0`)](docs/user-guide/release-notes.md)** | High-level, non-technical summary of the major user-facing features, visual improvements, and reliability upgrades introduced in each Google Play Store release from **`v20.0`** back to **`v1.0`**. |

---

## Supported Belkin WeMo Hardware

- **Belkin WeMo In-Wall Light Switch (1st Gen — `F7C030`)**: Single-pole Wi-Fi wall switch (`ON` / `OFF`).
- **Belkin WeMo 3-Way Light Switch (2nd Gen RTOS — `WLS040` / `WLS0403`)**: Single-pole / 3-way rocker switch (`ON` / `OFF`).
- **Belkin WeMo Wi-Fi Smart Dimmer (`F7C059` OpenWrt & `WDS060` RTOS)**: Continuous `1%–100%` dimming plus hardware `20%` Night Mode.
- **Belkin WeMo Mini Smart Plug (`F7C063` & `WSP080`)**: Compact smart outlet plug (`ON` / `OFF`) — automatically displays a **Christmas Tree**, **Fan**, or **Lightbulb** icon when named accordingly!
- **Belkin WeMo Motion Sensor (`F7C028`)**: Local UPnP motion sensor trigger.

---

## Developer & Architecture Documentation

For UX architecture, protocol details, architecture diagrams, and roadmap information, see:
- [`LICENSE`](LICENSE) — MIT License
- [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md) — User-Facing Release Notes for Every Google Play Store Version (`v1.0` → `v20.0`)
- [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md) — 4-Agent Autonomous Round-Robin Release Review Process & Live Sign-Off State Machine
- [`UX_DESIGN.md`](UX_DESIGN.md) — Android UX Design Guidelines: Navigation Patterns, Accessibility Standards & User Interaction Flows *(Owned Exclusively by the Google AI Studio Agent)*
- [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md) — Android & Android TV UX Design, Behavioral Model & Critical User Journeys *(Owned Exclusively by the Google AI Studio Agent)*
- [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md) — On-Device Telemetry, `10.22.22.1` Provisioning Diagnostics & Support Architecture *(Owned Exclusively by the Antigravity Debugging & Support Agent)*
- [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md) — Internal Device Discovery Logic, Self-Healing Identity Resolution & Automatic Room Grouping Architecture *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent)*
- [`IMPLEMENTED.md`](IMPLEMENTED.md) — Reverse-Chronological Change Log of Implemented Features & Improvements
- [`CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](CODE_QUALITY_AND_MODULARIZATION_PLAN.md) — Human-Readable Code Modularization & End-to-End Validation Plan
- [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md) — Upcoming Features & Next-Iteration Roadmap
- [`AGENTS.md`](AGENTS.md) — Architecture & Agent Guide
