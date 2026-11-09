# `docs/` Directory Guide — Human-Focused User Guides Only

> **IMPORTANT NOTICE FOR ALL AGENTS (`AI Studio`, CLI, & GitHub Agents)**
>
> **Do NOT duplicate or place `ALL_CAPS.md` architecture/changelog/roadmap documents inside `docs/`.**
>
> To prevent mirror drift and merge conflicts across multi-agent workflows, our documentation is organized into two non-overlapping locations:

## 1. Repository Root (`/`) — All `ALL_CAPS.md` Project & Architecture Documents

All project-level, architectural, UX, and engineering documents live **exclusively at the top level (`/`) of the repository**:

| Root File | Purpose | Ownership |
| :--- | :--- | :--- |
| [`../AGENTS.md`](../AGENTS.md) | Master architecture, package map, build gate, and multi-agent rules | Shared across agents |
| [`../UX_DESIGN.md`](../UX_DESIGN.md) | Android UX Design Guidelines: navigation patterns, accessibility standards & interaction flows | **Google AI Studio Agent ONLY (Read-Only for other agents)** |
| [`../UX_DESIGN_AND_EXPERIENCE.md`](../UX_DESIGN_AND_EXPERIENCE.md) | Behavioral mental models, 5 Compose layout invariants & Critical User Journeys (CUJs) | **Google AI Studio Agent ONLY (Read-Only for other agents)** |
| [`../USER_GUIDE.md`](../USER_GUIDE.md) | Top-level entry point & summary table linking to the human feature guides in `docs/user-guide/` | Shared across agents |
| [`../IMPLEMENTED.md`](../IMPLEMENTED.md) | Reverse-chronological feature & bug-fix change log (newest at top) | Shared across agents |
| [`../UPCOMING_FEATURES.md`](../UPCOMING_FEATURES.md) | Next-iteration engineering roadmap | Shared across agents |
| [`../CODE_QUALITY_AND_MODULARIZATION_PLAN.md`](../CODE_QUALITY_AND_MODULARIZATION_PLAN.md) | Code modularization & E2E validation plan | Shared across agents |

> **Rule**: Never run `cp IMPLEMENTED.md docs/IMPLEMENTED.md` or recreate `docs/IMPLEMENTED.md`, `docs/UPCOMING_FEATURES.md`, `docs/USER_GUIDE.md`, `docs/UX_DESIGN.md`, `docs/UX_DESIGN_AND_EXPERIENCE.md`, or `docs/CODE_QUALITY_AND_MODULARIZATION_PLAN.md`.

---

## 2. `docs/user-guide/` — Human-Focused Feature Guides

The `docs/` directory is dedicated **exclusively** to human-readable end-user guides under [`user-guide/`](user-guide/):

- [`user-guide/main-screen.md`](user-guide/main-screen.md) — Main Screen, Status Badges & Power Controls
- [`user-guide/switch-discovery.md`](user-guide/switch-discovery.md) — Finding & Adopting Existing Switches on Wi-Fi (SSDP)
- [`user-guide/fresh-device-setup.md`](user-guide/fresh-device-setup.md) — Setting Up a Factory-Reset WeMo Switch (`10.22.22.1`)
- [`user-guide/room-grouping.md`](user-guide/room-grouping.md) — Room Grouping, Master Power & Room Dimmer Sliders
- [`user-guide/editing-and-managing-devices.md`](user-guide/editing-and-managing-devices.md) — Renaming Switches, Appliance Icons & Hardware Diagnostics
- [`user-guide/wifi-homes.md`](user-guide/wifi-homes.md) — Managing Multiple Wi-Fi Homes & One-Tap LAN Reset
- [`user-guide/rules-and-timers.md`](user-guide/rules-and-timers.md) — Schedules, Auto-Off Timers & Sunset/Sunrise Rules
- [`user-guide/more-and-tv-mode.md`](user-guide/more-and-tv-mode.md) — Android TV 10-Foot Remote Mode (`wemi TV`) & Demo Showroom
- [`user-guide/telemetry-and-troubleshooting.md`](user-guide/telemetry-and-troubleshooting.md) — Troubleshooting, Diagnostic Screenshots & Sharing Telemetry Logs (`wemi-telemetry-latest.txt`)
