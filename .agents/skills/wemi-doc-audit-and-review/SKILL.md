---
name: wemi-doc-audit-and-review
description: Audit repository and user-facing documentation against live app behavior for stale content, missing major-feature guides, plain-English clarity, broken links, and ownership/trademark compliance. Executed by telemetry_and_debugging during Stage 3 of every release.
---

# WeMi Documentation Audit & User-Guide Review Skill (`wemi-doc-audit-and-review`)

Use this skill during **Stage 3 (`telemetry_and_debugging`)** of every release candidate in [`RELEASE_PROCESS.md`](../../../RELEASE_PROCESS.md) — and whenever performing an on-demand documentation audit in `MichaelRHead/WeMi-light-control` — to verify that user-facing guides, troubleshooting docs, Play Store operational guides, and shared agent docs accurately match the current shipped app in plain English.

This skill complements [`wemi-changelog-and-docs`](../wemi-changelog-and-docs/SKILL.md) (per-change changelog/guide updates) with a full **code-to-docs accuracy, readability, and link-integrity review** before each release advances to Stage 4.

---

## 1. Ownership, Layout & Governance Boundaries

1. **Single-Source Root Layout**:
   - All `ALL_CAPS.md` documents live **only** at repository root (`/`). Never duplicate `ALL_CAPS.md` files into `docs/` and never create a root `RELEASE_NOTES.md`.
   - `docs/` is exclusively for human-focused user guides ([`docs/README.md`](../../../docs/README.md) and [`docs/user-guide/*.md`](../../../docs/user-guide/)).
2. **Strict Specialist Ownership ([`AGENTS.md`](../../../AGENTS.md) Section 2)**:
   - **Editable by `telemetry_and_debugging` during Stage 3**:
     - Its own exclusively-owned files: [`DEBUGGING_AND_SUPPORT.md`](../../../DEBUGGING_AND_SUPPORT.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](../../../docs/user-guide/telemetry-and-troubleshooting.md), and [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](../wemi-device-telemetry-debug/SKILL.md).
     - All shared user guides & indexes: [`USER_GUIDE.md`](../../../USER_GUIDE.md), [`docs/README.md`](../../../docs/README.md), and non-owned [`docs/user-guide/*.md`](../../../docs/user-guide/) (`main-screen.md`, `switch-discovery.md`, `fresh-device-setup.md`, `room-grouping.md`, `home-screen-widgets.md`, `editing-and-managing-devices.md`, `wifi-homes.md`, `rules-and-timers.md`, `more-and-tv-mode.md`).
     - Shared engineering/operational docs & skills when stale against real app behavior: [`CHROMEOS_ARCVM_DISCOVERY_DESIGN.md`](../../../CHROMEOS_ARCVM_DISCOVERY_DESIGN.md), [`IMPLEMENTED.md`](../../../IMPLEMENTED.md), [`UPCOMING_FEATURES.md`](../../../UPCOMING_FEATURES.md), [`play_store_assets/DATA_SAFETY_GUIDE.md`](../../../play_store_assets/DATA_SAFETY_GUIDE.md), and shared `.agents/skills/*/SKILL.md` files (syncing any modified skill via `save_skill(overwrite = true, ...)`).
   - **STRICT READ-ONLY (Audit & Report Only — Never Modify)**:
     - `ai_studio_ux_cuj`: [`UX_DESIGN.md`](../../../UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](../../../UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](../wemi-ux-design-and-experience/SKILL.md).
     - `discovery_and_grouping`: [`DISCOVERY_AND_ROOM_GROUPING.md`](../../../DISCOVERY_AND_ROOM_GROUPING.md), [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](../wemi-discovery-and-room-grouping/SKILL.md).
     - `license_governance`: [`LICENSE_GOVERNANCE.md`](../../../LICENSE_GOVERNANCE.md), [`.agents/skills/wemi-license-governance/SKILL.md`](../wemi-license-governance/SKILL.md), [`LicenseAndBrandGovernanceTest.kt`](../../../app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt).
     - *(If an agent other than `telemetry_and_debugging` invokes this skill on demand, treat `telemetry_and_debugging`'s 3 owned files as read-only as well.)*
3. **Trademark & Play Store Hygiene**:
   - Active `docs/user-guide/*.md` guides (excluding `release-notes.md`) must never reintroduce removed legacy brand UI labels checked by `LicenseAndBrandGovernanceTest`.
   - `play_store_assets/*` must contain zero standalone brand-name leaks (`scripts/sync_play_assets.py validate-local`).
   - Clearly distinguish the launcher name (**WeMi Switch Control**) from the in-app header wordmark (**wemi**) and project name (**WeMi Light Control**) where users look for the app or widget picker.

---

## 2. Six-Step Code-to-Docs Audit Playbook

### Step 1: Inventory Release Changes & Major Feature Coverage
1. Inspect [`app/build.gradle.kts`](../../../app/build.gradle.kts) (`versionName`, `versionCode`), [`ReleaseHistoryCatalog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ReleaseHistoryCatalog.kt), and the newest entries in [`IMPLEMENTED.md`](../../../IMPLEMENTED.md) plus `git log` since the previous release tag/version.
2. Verify that **every shipped major feature and platform** (Android Phones, Tablets, Chromebooks / ChromeOS ARCVM, Home Screen Widgets, and Android TV) is clearly explained in [`USER_GUIDE.md`](../../../USER_GUIDE.md), [`docs/README.md`](../../../docs/README.md), and [`docs/user-guide/`](../../../docs/user-guide/).
3. If a major new user-facing feature shipped without dedicated documentation, create a plain-English guide under `docs/user-guide/` and link it from both [`USER_GUIDE.md`](../../../USER_GUIDE.md) and [`docs/README.md`](../../../docs/README.md).

### Step 2: Verify Behavioral & UI Truth Against Live Source Code
Cross-check high-drift user-facing details directly against `app/src/main/` rather than trusting older documentation:

- **Top Header, Theme & Banners** ([`HeaderAndNav.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HeaderAndNav.kt), [`DevicesTabScreen.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/screens/DevicesTabScreen.kt), [`ReleaseHistoryDialog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ReleaseHistoryDialog.kt)):
  - Confirm header palette (Ocean Teal / Deep Sapphire — not legacy green), version pill (`v<X.0>`) & Release History dialog, top-bar action buttons (`All Off`, `Refresh`, `Edit`, `+`), and setup banner styling (Deep Teal `#0E7490`).
- **Device Rows, Icons & Expanded Drawer** ([`DeviceRow.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceRow.kt), [`DeviceDiagnosticsDrawer.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceDiagnosticsDrawer.kt), [`HardwareRuleRowItems.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRuleRowItems.kt), [`RoomAndCategoryRules.kt`](../../../app/src/main/java/org/suppressingfire/wemi/data/RoomAndCategoryRules.kt)):
  - Confirm tap behavior for Switches, Dimmers, Motion Sensors, and Offline devices (row tap expands details; no obsolete `ⓘ` icon).
  - Confirm exact dimmer preset chips (`21%`, `50%`, `75%`, `100%`), drawer actions (`Add Widget`, `Ping RTT`, `Refresh Motion` vs. Demo `Simulate Motion`, `Configure IP`, and `ExpandedDrawerHardwareRulesSection` actions `Inspect Rules` / `Refresh`, `Add Rule`, `Sync Clock`, `Manage Rules`, plus clickable `+N more rule(s) in Rules tab`), icon keyword rules & exclusions, and 3-state visuals (no stale amber standby LED copy).
- **Discovery, Wi-Fi Homes & ChromeOS ARCVM** ([`SsdpScannerModal.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/SsdpScannerModal.kt), [`SsdpDiscoveryBanner.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/SsdpDiscoveryBanner.kt), [`WifiHomeBar.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/WifiHomeBar.kt), [`SsdpDiscoveryEngine.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/SsdpDiscoveryEngine.kt)):
  - Confirm exact scanner labels (`Adopt` / `Adopted`), Chromebook fallback card labels (`ChromeOS Virtual Network Detected`, `Home Subnet (e.g. 192.168.1)`, `Scan Subnet`), clean `Local LAN` home profile behavior, and `15s` foreground SOAP polling.
- **Fresh Device Setup (`10.22.22.1`)** ([`ProvisionApDialog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApDialog.kt), [`ProvisionApFormSections.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionApFormSections.kt), [`ProvisionDebugPanel.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/ProvisionDebugPanel.kt)):
  - Confirm exact field labels, default masked `Wi-Fi Password` input with show/hide toggle, `Unassigned` default room & `DEFAULT_ROOMS` preset chips (`DemoFixtures.kt` — exact count and names; do not document fields that do not exist), encryption method & length suffix chips, 2.4 GHz requirement, supported SKUs (including 2nd-Gen 3-way `WLS040`/`WLS0403`), and Chromebook manual launch instructions.
- **Room Grouping & Device Editing** ([`RoomGroupCard.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/RoomGroupCard.kt), [`DeviceEditDialog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/DeviceEditDialog.kt)):
  - Confirm continuous `1%–100%` Room Master Brightness slider behavior, exact `DEFAULT_ROOMS` preset chips, and Edit Mode access for offline devices.
- **Home Screen Widgets** ([`WeMiSwitchWidgetProvider.kt`](../../../app/src/main/java/org/suppressingfire/wemi/widget/WeMiSwitchWidgetProvider.kt), [`SwitchWidgetConfigureActivity.kt`](../../../app/src/main/java/org/suppressingfire/wemi/widget/SwitchWidgetConfigureActivity.kt)):
  - Confirm all add/pin paths, 3-state visual feedback, resizing, orphaned widget reconfiguration, live sync, and post-reset recovery.
- **On-Device Switch Rules (`On-Switch`)** ([`RulesScreen.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/screens/RulesScreen.kt), [`HardwareRulesSection.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRulesSection.kt), [`HardwareRuleRowItems.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRuleRowItems.kt), [`HardwareRuleEditorDialog.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRuleEditorDialog.kt), [`HardwareRuleEditorFields.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRuleEditorFields.kt), [`HardwareRulesAuxDialogs.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/HardwareRulesAuxDialogs.kt), [`HardwareRulesModels.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesModels.kt), [`HardwareRulesCoordinator.kt`](../../../app/src/main/java/org/suppressingfire/wemi/data/HardwareRulesCoordinator.kt)):
  - Confirm exact **Rules** tab labels (`On-Device Switch Rules`, `On-Switch` badge, `New Rule`, `Read Rules` / `Refresh All`, empty-home `Go to Devices`, per-switch `Read` / `Refresh`, `Add Rule`, `Sync Clock` / `Clock Sync`, `Solar Location`, `Backups (<count>)`, capability badges `Not Checked`, `Likely Supported` / `Likely Ready • Long Press`, `Likely Unsupported (RTOS)`, `On-Device Ready` / `On-Device Ready • Long Press`, `No On-Device Rules`, `Offline`, and `Switch clock needs sync` warning).
  - Confirm exact Step 2 live editor, read-only ownership guards, and auxiliary dialog controls: 4 editable native rule types (`Long Press` with `Toggle`/`Turn Off`/`Turn On` + friendly-name target chips, `Auto-Off Delay` `1–1440` min with `5m`/`15m`/`30m`/`1h`/`2h` presets, `Schedule` with `Mode: Turn ON → then OFF` / `Turn OFF → then ON`, `Timed Action` with `Action: Turn Switch ON` / `Action: Turn Switch OFF`), wrapping time steppers (`-1h`, `+1h`, `-15m`, `+15m`, `-5m`, `+5m`, `-1m`, `+1m`), 7 solar offset presets (`-60m`..`+60m` + custom preservation), `Rule Active on Switch` / `Rule Paused`, `Save to Switch`, single-switch `View only` toggle/delete vs. read-only `Shared rule` and `Target` rows (Edit hidden, Delete & Toggle disabled), `Solar Location` 10 city presets + signed latitude/longitude entry, and automatic pre-restore backup + `Restore` in `Restore Rules Backup`. (Confirm legacy phone-only local rules are retired cleanly.)
- **Android TV & More Tab** ([`TvScreen.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvScreen.kt), [`TvHeadersAndScenes.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/components/TvHeadersAndScenes.kt), [`HomeAndRulesUiController.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/HomeAndRulesUiController.kt), [`MoreScreen.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt), [`MoreControlAndLogCards.kt`](../../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreControlAndLogCards.kt)):
  - Confirm exact TV scene behavior (`Movie 20%` dims reachable dimmers on the active home to `20%`, `All OFF`, conditional `Holiday ON`), dimmer adjust indicators (`DIM -/+`, `Done Dimming`, `10%–100%` stepping, remote shortcuts), `UPnP / SOAP Packet Log` timestamp format (`h:mm:ss a`) and limits (10 shown / 50 buffered), and default Real LAN Mode vs. Demo Showroom Mode parity with [`play_store_assets/DATA_SAFETY_GUIDE.md`](../../../play_store_assets/DATA_SAFETY_GUIDE.md).
- **Telemetry, Protocol & Roadmap Docs** ([`SetupDiagnostics.kt`](../../../app/src/main/java/org/suppressingfire/wemi/network/SetupDiagnostics.kt), [`wemo-upnp-protocol.md`](../wemi-light-control/references/wemo-upnp-protocol.md), [`DEPENDENCY_UPGRADE_PLAN.md`](../../../DEPENDENCY_UPGRADE_PLAN.md)):
  - Confirm `wemi-telemetry-latest.txt` sections, `SETUP DIAGNOSTICS` lines, SSDP/`rules1`/`timesync1` protocol notes, Room schema version (`version = 7`, `MIGRATION_5_6`, `MIGRATION_6_7`), and roadmap/dependency-plan review statuses across `AGENTS.md` and `UPCOMING_FEATURES.md` match live state.

### Step 3: Plain-English User Readability Pass
Review [`USER_GUIDE.md`](../../../USER_GUIDE.md), [`docs/README.md`](../../../docs/README.md), and every guide in [`docs/user-guide/`](../../../docs/user-guide/) as an everyday smart-home user:
- Replace leaked internal Compose `testTag` identifiers, Kotlin function/class names, and raw Compose layout modifiers with plain-English action descriptions.
- Use the **exact visible button, tab, chip, and dialog labels** shown on screen.
- Keep troubleshooting steps concrete and actionable (what the user sees, why it happens, and what to tap next), reserving wire-level protocol internals for `ARCHITECTURE.md`, `DEBUGGING_AND_SUPPORT.md`, and `wemo-upnp-protocol.md`.

### Step 4: Version Numbers, Counts & Relative Link Integrity
1. Check current release version (`v<X.0>`), pipeline stage count (`6-Agent / 7-Stage`), `.kt` file count, and test counts across [`USER_GUIDE.md`](../../../USER_GUIDE.md), [`docs/README.md`](../../../docs/README.md), and editable docs/skills.
2. Verify all relative Markdown links in modified docs and skills resolve to real files (remember `.agents/skills/<name>/SKILL.md` is 3 directories below repo root and must use `../../../` for root files, not `../../`):
   ```bash
   python3 - <<'PY'
   import os, re, glob
   files = ["USER_GUIDE.md", "docs/README.md", "RELEASE_PROCESS.md", "AGENTS.md"] + \
           sorted(glob.glob("docs/user-guide/*.md")) + \
           sorted(glob.glob(".agents/skills/*/SKILL.md"))
   link_re = re.compile(r'\[[^\]]+\]\(([^)]+)\)')
   broken = []
   for f in files:
       in_fence = False
       for i, line in enumerate(open(f), 1):
           if line.strip().startswith("```"):
               in_fence = not in_fence
               continue
           if in_fence:
               continue
           for m in link_re.finditer(line):
               target = m.group(1).split("#")[0]
               if not target or target.startswith(("http://", "https://", "mailto:", "file://")):
                   continue
               resolved = os.path.normpath(os.path.join(os.path.dirname(f), target))
               if not os.path.exists(resolved):
                   broken.append((f, i, target))
   print(f"Broken links found: {len(broken)}")
   for f, i, t in broken:
       print(f"  {f}:{i} -> {t}")
   PY
   ```
3. Whenever editing any `.agents/skills/<name>/SKILL.md` you are allowed to edit, sync it to the persistent registry via `save_skill(overwrite = true, skill_dir = "...")`.

### Step 5: Report Drift in Peer-Owned Read-Only Docs Without Editing Them
1. Inspect peer-owned specs (`UX_DESIGN.md`, `UX_DESIGN_AND_EXPERIENCE.md`, `DISCOVERY_AND_ROOM_GROUPING.md`, `LICENSE_GOVERNANCE.md`, and their `SKILL.md` files) read-only for factual drift or broken relative links.
2. **Never edit peer-owned files.** Record any stale items concisely in your Stage 3 sign-off summary or handoff note (citing exact file and line numbers) so the owning specialist agent can resolve them cleanly on its turn.

### Step 6: Verify, Log & Advance Stage 3
1. If substantive documentation fixes or new guides were added, prepend a concise reverse-chronological entry to [`IMPLEMENTED.md`](../../../IMPLEMENTED.md).
2. Run the verification suite (always set `PYTHONDONTWRITEBYTECODE=1` to avoid leaving `scripts/__pycache__/` behind):
   ```bash
   PYTHONDONTWRITEBYTECODE=1 python3 -W default scripts/sync_play_assets.py validate-local
   PYTHONDONTWRITEBYTECODE=1 python3 -W default -m unittest discover -s scripts -p "test_*.py" -v
   sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.GenaProvisioningAndRulesTest \
     --tests org.suppressingfire.wemi.ProvisioningAndGenaCujE2ETest \
     --tests org.suppressingfire.wemi.TelemetryDiagnosticsTest \
     --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest
   ```
   *(Before pushing any standalone commit outside Stage 3 — or when verifying a full pre-push gate per [`AGENTS.md`](../../../AGENTS.md) Section 1 — run the full `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` gate.)*
3. Commit documentation fixes via `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh telemetry_and_debugging ...` and note the completed documentation audit (plus any read-only specialist follow-ups) when running `bash scripts/advance_release_turn.sh sign_off telemetry_and_debugging "<summary>"`.
