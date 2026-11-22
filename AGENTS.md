# WeMi Light Control — Agent Guide (`AGENTS.md`)

**WeMi Light Control** (`org.suppressingfire.wemi` / `com.aistudio.wemiswitch.kxpqzr`) is an Android, Android TV, and Home Screen widget local-LAN controller for Belkin's discontinued smart switch platform (`F7C030` 1st Gen Switch, `WLS040`/`WLS0403` 2nd Gen 3-Way RTOS Switch with `Semaphore(3)` protection, `F7C059`/`WDS060` Dimmer with Night Mode, and `F7C063`/`WSP080` Mini Plug). It pairs a tactile Ocean Teal (`#0891B2`), Electric Cyan (`#06B6D4`), and Deep Sapphire (`#0F4C81`) 3-state hardware UI with zero-third-party-HTTP UPnP/SSDP/SOAP/GENA networking, zero-cloud `10.22.22.1` provisioning, and Room v5 persistence.

> **Keep `AGENTS.md` Concise (`< 12 KB`)**: This file is auto-injected on every conversation launch and truncates above `~22 KB`. Put detailed per-file/test catalogs in [`ARCHITECTURE.md`](ARCHITECTURE.md), not here.

---

## 1. Mandatory Pre-Push Build & Test Gate

> **CRITICAL RULE**: **Only push to `origin/main` after verifying the full build, lint, and all automated tests pass with zero errors.**

```bash
# Run all 120 unit, protocol, widget, telemetry, governance, ChromeOS & Compose E2E tests (23 suites) + Lint
sh gradlew testDebugUnitTest --rerun-tasks lintDebug

# Assemble debug APK
sh gradlew assembleDebug
```

- **Cloud VM Environment Setup ([`.agents/environment.json`](.agents/environment.json) & [`scripts/setup_dev_env.sh`](scripts/setup_dev_env.sh))**: Fresh VMs automatically run `bash scripts/setup_dev_env.sh`, which reuses preinstalled JDK 21 and writable `/opt/android-sdk` (guaranteeing accepted SDK licenses and deferring missing `platforms;android-36.1` / `build-tools;36.0.0` deltas to AGP's built-in on-demand auto-download in `~0.2s`), restores optional persistent `~/.gemini/caches/wemi-android-v1/` archives when present, and configures fast proxy/TLS truststore and Maven Central GCS mirror hooks in `~/.bashrc` and `~/.gradle/init.d/`. Pass `--install-sdk` to eagerly pre-install SDK packages or `--save-caches` after dependency upgrades.

---

## 2. Exclusive Ownership Boundaries (STRICT READ-ONLY FOR NON-OWNERS)

Other agents **must read and follow** these specifications when touching related code, but **must never modify, overwrite, rename, or delete** files owned by another specialist agent:

| Owner | Exclusively Owned Files (Read-Only for All Other Agents) |
| :--- | :--- |
| **Google AI Studio Agent (`ai_studio_ux_cuj`)** | [`UX_DESIGN.md`](UX_DESIGN.md), [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md), [`.agents/skills/wemi-ux-design-and-experience/SKILL.md`](.agents/skills/wemi-ux-design-and-experience/SKILL.md) |
| **Antigravity Debugging & Support Agent (`telemetry_and_debugging`)** | [`DEBUGGING_AND_SUPPORT.md`](DEBUGGING_AND_SUPPORT.md), [`.agents/skills/wemi-device-telemetry-debug/SKILL.md`](.agents/skills/wemi-device-telemetry-debug/SKILL.md), [`docs/user-guide/telemetry-and-troubleshooting.md`](docs/user-guide/telemetry-and-troubleshooting.md) |
| **Antigravity Device Discovery & Room Grouping Agent (`discovery_and_grouping`)** | [`DISCOVERY_AND_ROOM_GROUPING.md`](DISCOVERY_AND_ROOM_GROUPING.md), [`.agents/skills/wemi-discovery-and-room-grouping/SKILL.md`](.agents/skills/wemi-discovery-and-room-grouping/SKILL.md) |
| **Antigravity License & Brand Governance Agent (`license_governance`)** | [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md), [`.agents/skills/wemi-license-governance/SKILL.md`](.agents/skills/wemi-license-governance/SKILL.md), [`LicenseAndBrandGovernanceTest.kt`](app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt) |

---

## 3. Core Engineering & Multi-Agent Rules

- **Multi-Agent Git Sync ([`multi-agent-git-sync`](.agents/skills/multi-agent-git-sync/SKILL.md))**: Before starting work or pushing, run `git fetch --all`, inspect incoming commits (`git log -p HEAD..origin/main`), verify none of your prior work was stomped, and rebase additively (`git pull --rebase origin main`). Keep all `ALL_CAPS.md` docs exclusively at repository root `/` and human user guides in `docs/user-guide/`.
- **Human-Readable Commits & Agent Attribution ([`agent-commit-attribution`](.agents/skills/agent-commit-attribution/SKILL.md))**: Every commit must use `bash .agents/skills/agent-commit-attribution/scripts/commit_as_agent.sh` (or matching format): plain-English `<= 72`-char Conventional Commits subject (no `[...Agent]` tags), concise plain-text reflowable bullets explaining *what* and *why* (`<= 260` chars/bullet, `<= 1,000` chars body, no symbol dumps or Markdown formatting), brief `Verified:` trailer (`<= 120` chars), and mandatory `Agent: <Canonical Agent Name> (<role_id>)` trailer.
- **Change Log, Roadmap & User Guide ([`wemi-changelog-and-docs`](.agents/skills/wemi-changelog-and-docs/SKILL.md))**: Prepend completed changes newest-first to [`IMPLEMENTED.md`](IMPLEMENTED.md), remove finished items from [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md), and update [`USER_GUIDE.md`](USER_GUIDE.md) / [`docs/user-guide/`](docs/user-guide/) for user-visible changes.
- **Modularity & Permissive Footprint ([`android-code-modularization`](.agents/skills/android-code-modularization/SKILL.md), [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md))**: Keep every `.kt` file `< 500` lines (`98` files currently); preserve MIT [`LICENSE`](LICENSE) parity with `MoreScreen.kt`, zero third-party HTTP/font/native `.so` bloat, `org.suppressingfire.wemi` namespace/filename hygiene, and non-executable (`100644`) git modes on `gradlew` and scripts (always invoke via `sh gradlew`, `bash ...`, or `python3 ...` so Google AI Studio exports never flip `+x`).
- **Proactive Deprecation Hygiene**: During reviews and releases, check GitHub Actions job annotations, Kotlin/Compose compiler warnings, `lintDebug`, Python `-W default`, and Android/Play API deprecation notices within your domain, and proactively migrate deprecated actions, runtimes, or APIs (keeping Google AI Studio Gradle/AGP compatibility and [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) allowlists intact).
- **6-Agent / 7-Stage Release Pipeline & Quota Guard ([`wemi-release-coordinator`](.agents/skills/wemi-release-coordinator/SKILL.md), [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md), [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md))**:
  - Stages: `ai_studio_ux_cuj` (initiate) → `discovery_and_grouping` → `telemetry_and_debugging` → `code_quality_and_testing` → `license_governance` → `ai_studio_ux_cuj` (conclude & push version bump) → `app_promoter` (event-driven GitHub Actions [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) promotes `internal` → Closed testing `alpha` and syncs `play_store_assets/`).
  - **Quota-Safe Turn Watcher**: Arm `bash scripts/await_release_turn.sh <role_id> 15 510` (`WaitMsBeforeAsync: 5000`, `<= 510s`, `MAX_IDLE_REARMS=2`) **only** when `RELEASE_STATUS: IN_REVIEW` and your own stage is `PENDING` — never between releases, never for `app_promoter`, never after `SIGNED_OFF`. Honor `[QUOTA GUARD] DO NOT RE-ARM` and rotate conversations around `~400` steps (`/home/antigravity/.gemini/usage/agy-usage --check-self`).

---

## 4. Reference Docs & Skills Index (Read On Demand)

- **Codebase & Protocol**: [`ARCHITECTURE.md`](ARCHITECTURE.md) (all `98` `.kt` files & `23` test suites), [`.agents/skills/wemi-light-control/SKILL.md`](.agents/skills/wemi-light-control/SKILL.md), [`.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md`](.agents/skills/wemi-light-control/references/wemo-upnp-protocol.md)
- **Testing & Modularity**: [`.agents/skills/android-e2e-testing/SKILL.md`](.agents/skills/android-e2e-testing/SKILL.md), [`.agents/skills/android-code-modularization/SKILL.md`](.agents/skills/android-code-modularization/SKILL.md)
- **Releases & Play Store**: [`RELEASE_PROCESS.md`](RELEASE_PROCESS.md), [`RELEASE_SIGN_OFF.md`](RELEASE_SIGN_OFF.md), [`.agents/skills/wemi-release-coordinator/SKILL.md`](.agents/skills/wemi-release-coordinator/SKILL.md), [`.agents/skills/wemi-app-promoter/SKILL.md`](.agents/skills/wemi-app-promoter/SKILL.md)
- **Docs, History & License**: [`IMPLEMENTED.md`](IMPLEMENTED.md), [`UPCOMING_FEATURES.md`](UPCOMING_FEATURES.md), [`DEPENDENCY_UPGRADE_PLAN.md`](DEPENDENCY_UPGRADE_PLAN.md) *(Awaiting AI Studio Review)*, [`CHROMEOS_ARCVM_DISCOVERY_DESIGN.md`](CHROMEOS_ARCVM_DISCOVERY_DESIGN.md), [`USER_GUIDE.md`](USER_GUIDE.md), [`docs/user-guide/release-notes.md`](docs/user-guide/release-notes.md), [`LICENSE`](LICENSE)
