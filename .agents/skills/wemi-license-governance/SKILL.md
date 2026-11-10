---
name: wemi-license-governance
description: Open-source MIT license parity (LICENSE <-> MoreScreen.kt), minimal permissive third-party dependency policy (AndroidX + Kotlin Apache-2.0 + OFL-1.1 fonts only; zero OkHttp/pywemo), org.suppressingfire.wemi package/filename hygiene, and "WeMo" trademark boundary governance for WeMi Light Control. EXCLUSIVELY OWNED by the Antigravity License & Brand Governance Agent (other agents MUST read and follow this skill when modifying dependencies, licenses, packages, or user-facing brand strings, but MUST NOT edit this skill or LICENSE_GOVERNANCE.md).
---
# WeMi License & Brand Governance Skill (`wemi-license-governance`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP (READ-ONLY FOR OTHER AGENTS)**
> - **Owner**: **Antigravity License & Brand Governance Agent (`license_governance`)** is the **sole author and maintainer** of [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) and [`.agents/skills/wemi-license-governance/SKILL.md`](SKILL.md).
> - **Policy for All Other Agents**: **DO NOT MODIFY, OVERWRITE, OR DELETE [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) OR THIS `SKILL.md`.** All other agents must read and follow the 5 invariants below when editing dependencies, UI copy, or preparing releases, and must treat these two governance files as **strictly read-only**.

---

## 1. The 5 Non-Negotiable License & Brand Governance Invariants

1. **Invariant 1 — Repository [`LICENSE`](../../LICENSE) & In-App [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) Parity**:
   - Root [`LICENSE`](../../LICENSE) is the standard OSI **MIT License** (`Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors`).
   - [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) must expose `open_source_licenses_card` and `toggle_license_details_button`, and expanding it must render both the verbatim MIT License (`mit_license_text_surface`) and accurate Third-Party Open Source Notices (`third_party_notices_surface`) listing only shipped third-party components (`AndroidX / Jetpack Compose / Room` `Apache-2.0`, `Kotlin & Kotlinx Coroutines` `Apache-2.0`, and `Comfortaa & Work Sans` `SIL Open Font License 1.1`).
2. **Invariant 2 — Minimal Permissive Dependency & Font Policy ([`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), [`app/build.gradle.kts`](../../app/build.gradle.kts) & [`app/src/main/res/font/`](../../app/src/main/res/font/))**:
   - **Runtime Libraries**: Minimized strictly to **AndroidX / Jetpack Compose / Room** (`Apache-2.0`) and **Kotlin / Kotlinx Coroutines** (`Apache-2.0`), plus bundled `comfortaa.ttf` & `work_sans.ttf` (`OFL-1.1`).
   - **Zero External HTTP/JSON/Image/Cloud SDKs**: Local-LAN HTTP/1.1 & UPnP GENA traffic uses first-party [`LanHttpClient.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/LanHttpClient.kt) over JDK `SocketFactory`. Do **not** add `OkHttp`, `Retrofit`, `Moshi`, `Coil`, `Firebase`, `Play Services`, or unapproved third-party libraries.
   - **Tier B (Allowed Test-Only)**: `MIT` (`org.robolectric:robolectric`) and `EPL-1.0` (`junit:junit` in `testImplementation`).
   - **Tier C (Strictly Prohibited)**: `GPL-*`, `AGPL-*`, `LGPL-*`, `SSPL`, `Commons Clause`, or proprietary ad/analytics/telemetry SDKs.
   - `dependenciesInfo { includeInApk = false; includeInBundle = true }` in [`app/build.gradle.kts`](../../app/build.gradle.kts) must keep `includeInApk = false`.
3. **Invariant 3 — Accurate Attribution & Zero `pywemo` Code/Notice Bloat**:
   - Zero `pywemo` code is included in the repository or APK; do not list `pywemo` or removed libraries (`OkHttp`) in `MoreScreen.kt` or production source comments.
   - Maintain `SIL Open Font License 1.1` attribution for `Comfortaa` & `Work Sans` in [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) and [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt).
4. **Invariant 4 — `org.suppressingfire.wemi` Package Hygiene & `"WeMo"` Trademark Boundary**:
   - All `.kt` source and test files under `app/src/` must declare `package org.suppressingfire.wemi...` (never `com.example`), and no `.kt` file may use a `WeMo*.kt` filename prefix.
   - User-facing copy in [`app/src/main/res/values/strings.xml`](../../app/src/main/res/values/strings.xml), [`metadata.json`](../../metadata.json), [`play_store_assets/`](../../play_store_assets/), [`scripts/GeneratePlayStoreAssets.java`](../../scripts/GeneratePlayStoreAssets.java), and UI composables under `app/src/main/java/org/suppressingfire/wemi/ui/` must **never** use `"WeMo"` as a product brand or user-visible label (describe compatible hardware as **"Belkin's discontinued smart switch platform"** or **"Smart Switch"**).
   - `"WeMo"` / `"wemo"` is permitted in code strictly for hardware wire-protocol matching (`WeMo.Light.*`, `Wemo.LS3W.*`, `WeMo_WW_*`, `/setup.xml`), existing Room v5 table names (`wemo_devices`, `wemo_home_networks`, `wemo_rules`), and internal `testTag` / theme identifiers.
5. **Invariant 5 — Automated Governance Test Suite ([`LicenseAndBrandGovernanceTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt))**:
   - Run `./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest` to verify all 5 invariants automatically.

---

## 2. Stage 5 Release Review Checklist (`license_governance`)

In the **5-Agent / 6-Stage Autonomous Round-Robin Release Process** ([`../../RELEASE_PROCESS.md`](../../RELEASE_PROCESS.md) & [`../../RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md)), `license_governance` is **Stage 5 (Review 4 — the 5th review step)**, executing immediately after `code_quality_and_testing` (Stage 4) and immediately before `ai_studio_ux_cuj` makes the final release decision (Stage 6):

1. **Pull Latest `origin/main`**:
   ```bash
   git pull --rebase origin main
   ```
2. **Audit License, Minimal Dependency Footprint, Font, Package & Trademark Invariants**:
   - Confirm [`LICENSE`](../../LICENSE) and [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) (`open_source_licenses_card`) match and list only shipped `Apache-2.0` (AndroidX & Kotlin) and `OFL-1.1` (`Comfortaa` & `Work Sans`) notices.
   - Confirm [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) and [`app/build.gradle.kts`](../../app/build.gradle.kts) contain only the minimal approved dependencies (zero `OkHttp`/`Retrofit`/`Moshi`/`Coil`/`Firebase`/copyleft dependencies) and `namespace = "org.suppressingfire.wemi"`.
   - Confirm zero `.kt` files exist under `com/example` or use a `WeMo*.kt` prefix, and zero user-facing `"WeMo"` trademark leaks exist in `strings.xml`, `metadata.json`, `play_store_assets/`, `scripts/GeneratePlayStoreAssets.java`, or UI composables.
3. **Run Automated Governance Verification**:
   ```bash
   ./gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest
   ```
4. **Sign Off & Advance Turn to `ai_studio_ux_cuj` (`AWAITING_FINAL_CONCLUSION`)**:
   ```bash
   bash scripts/advance_release_turn.sh sign_off license_governance "<concise verification summary>"
   ```
5. **Re-Arm Background Watcher**:
   ```bash
   bash scripts/await_release_turn.sh license_governance
   ```
