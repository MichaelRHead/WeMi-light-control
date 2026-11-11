---
name: wemi-changelog-and-docs
description: Maintain the top-level reverse-chronological Change Log (IMPLEMENTED.md), Upcoming Features roadmap (UPCOMING_FEATURES.md), and modular User Guide (USER_GUIDE.md & docs/user-guide/*.md) for the WeMi Light Control repository. Use whenever implementing a new feature, fixing a bug, refactoring architecture, or completing an item from UPCOMING_FEATURES.md.
---

# WeMi Change Log & Documentation Maintenance Skill (`wemi-changelog-and-docs`)

Use this skill whenever you land a feature, bug fix, UI change, or architectural improvement in `MichaelRHead/WeMi-light-control`.

---

## 1. Repository Documentation Organization Policy (Single-Source Root `ALL_CAPS.md`)

> [!IMPORTANT]
> **NO DUPLICATE `ALL_CAPS.md` FILES IN `docs/`**:
> - **All `ALL_CAPS.md` documentation files live EXCLUSIVELY at the top level (`/`) of the repository**:
>   - [`AGENTS.md`](../../AGENTS.md)
>   - [`UX_DESIGN.md`](../../UX_DESIGN.md) *(Owned Exclusively by the Google AI Studio Agent — Read-Only for other agents)*
>   - [`UX_DESIGN_AND_EXPERIENCE.md`](../../UX_DESIGN_AND_EXPERIENCE.md) *(Owned Exclusively by the Google AI Studio Agent — Read-Only for other agents)*
>   - [`DEBUGGING_AND_SUPPORT.md`](../../DEBUGGING_AND_SUPPORT.md) *(Owned Exclusively by the Antigravity Debugging & Support Agent — Read-Only for other agents)*
>   - [`DISCOVERY_AND_ROOM_GROUPING.md`](../../DISCOVERY_AND_ROOM_GROUPING.md) *(Owned Exclusively by the Antigravity Device Discovery & Room Grouping Agent — Read-Only for other agents)*
>   - [`LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) *(Owned Exclusively by the Antigravity License & Brand Governance Agent — Read-Only for other agents)*
>   - [`RELEASE_PROCESS.md`](../../RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md)
>   - [`IMPLEMENTED.md`](../../IMPLEMENTED.md)
>   - [`UPCOMING_FEATURES.md`](../../UPCOMING_FEATURES.md)
>   - [`USER_GUIDE.md`](../../USER_GUIDE.md)
> - **The `docs/` folder is dedicated EXCLUSIVELY to the human-focused user guide** ([`docs/README.md`](../../docs/README.md) and [`docs/user-guide/*.md`](../../docs/user-guide/), including the user-facing release notes in [`docs/user-guide/release-notes.md`](../../docs/user-guide/release-notes.md), and where [`docs/user-guide/telemetry-and-troubleshooting.md`](../../docs/user-guide/telemetry-and-troubleshooting.md) is owned exclusively by the Antigravity Debugging & Support Agent).
> - **Do NOT copy or mirror `ALL_CAPS.md` files into `docs/`** and do **not** create a duplicate root `RELEASE_NOTES.md`.

| File Path | Role & Format |
| :--- | :--- |
| [`IMPLEMENTED.md`](../../IMPLEMENTED.md) | **Reverse-Chronological Feature Change Log**: Records every completed feature, bug fix, and improvement from **newest at the top to oldest at the bottom**, including the PST timestamp, version/commit reference, summary table row, and detailed bullet points. |
| [`RELEASE_PROCESS.md`](../../RELEASE_PROCESS.md) & [`RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md) | **5-Agent Round-Robin Release Process & Sign-Off Ledger**: Coordinates autonomous release reviews (`ai_studio_ux_cuj` → `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing` → `license_governance` → `ai_studio_ux_cuj`). |
| [`docs/user-guide/release-notes.md`](../../docs/user-guide/release-notes.md) | **User-Facing Google Play Store Release Notes**: High-level, non-technical summary of major user-facing features and improvements introduced in each Play Store release version (`v1.0` → `v23.0+`). |
| [`UPCOMING_FEATURES.md`](../../UPCOMING_FEATURES.md) | **Upcoming Features Roadmap**: Lists planned/unimplemented features and engineering tracks. When an upcoming feature is implemented, remove it from `UPCOMING_FEATURES.md` and prepend it to `IMPLEMENTED.md`. |
| [`USER_GUIDE.md`](../../USER_GUIDE.md) | **User Guide Index**: Summarizes each user-facing feature in a table and links to detailed guides under [`docs/user-guide/`](../../docs/user-guide/). |
| [`docs/user-guide/*.md`](../../docs/user-guide/) | **Detailed Human-Focused Feature Guides**: Individual user-facing guides (`main-screen.md`, `switch-discovery.md`, `fresh-device-setup.md`, `room-grouping.md`, `editing-and-managing-devices.md`, `wifi-homes.md`, `rules-and-timers.md`, `more-and-tv-mode.md`, `telemetry-and-troubleshooting.md`, `release-notes.md`). |

---

## 2. Mandatory Workflow When Landing Any Change

Whenever you implement a feature, fix a bug, or adjust existing behavior:

### Step 1: Prepend to the Root Change Log (`IMPLEMENTED.md`)
1. Get the current local timestamp in Pacific Time (`TZ=America/Los_Angeles date "+%Y-%m-%d %H:%M %Z"`).
2. Add a new row at the **top** of **Section 1 (`Reverse-Chronological Summary Table`)** in [`IMPLEMENTED.md`](../../IMPLEMENTED.md):
   ```markdown
   | **YYYY-MM-DD HH:MM PST** | `vX.Y` | **Short Title of Feature or Improvement** | [`FileA.kt`](app/src/main/java/org/suppressingfire/wemi/...), [`FileB.kt`](app/src/main/java/org/suppressingfire/wemi/...) |
   ```
3. Add a new subsection at the **top** of **Section 2 (`Detailed Change Log (Newest to Oldest)`)** in [`IMPLEMENTED.md`](../../IMPLEMENTED.md):
   ```markdown
   ### [YYYY-MM-DD HH:MM PST] — Title of Feature or Improvement (`vX.Y`)
   - **Key Subsystem / Behavior Change ([`FileA.kt`](app/src/main/java/org/suppressingfire/wemi/...))**:
     - Concise explanation of what changed, why, and how it was verified.
   ```

### Step 2: Update `UPCOMING_FEATURES.md` (If Applicable)
1. If the change completes an item or sub-item from [`UPCOMING_FEATURES.md`](../../UPCOMING_FEATURES.md), remove that completed item from `UPCOMING_FEATURES.md` (since it now lives at the top of `IMPLEMENTED.md`).

### Step 3: Update the User Guide (`USER_GUIDE.md` & `docs/user-guide/*.md`)
1. If the change adds or modifies any user-visible screen, button, icon, badge, dialog, or workflow:
   - Update the corresponding detailed feature guide in [`docs/user-guide/`](../../docs/user-guide/) (or create a new guide in `docs/user-guide/` if introducing a major new feature area).
   - Update the summary table in [`USER_GUIDE.md`](../../USER_GUIDE.md).

### Step 4: Run Pre-Push Build & Test Gate and Commit with Agent Attribution
Never push code or documentation to `origin/main` unless the full Gradle build and unit/Robolectric test suite succeed, and always include your canonical agent name in the commit description per [`agent-commit-attribution`](../agent-commit-attribution/SKILL.md):
```bash
./gradlew assembleDebug testDebugUnitTest --rerun-tasks
bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh \
  <your_role_id> \
  "<type>(<scope>): <concise summary>" \
  "<1-4 sentence commit description explaining what changed, why, and how it was verified.>"
```
