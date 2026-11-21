---
name: wemi-license-governance
description: MIT license parity (LICENSE <-> MoreScreen.kt), minimal permissive dependency allowlist (AndroidX/Kotlin Apache-2.0 only), org.suppressingfire.wemi package/widget/Data-Safety hygiene, and Play Store "WeMo" trademark boundaries. EXCLUSIVELY OWNED by license_governance (read-only for all other agents).
---
# WeMi License & Brand Governance Skill (`wemi-license-governance`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP (READ-ONLY FOR OTHER AGENTS)**
> - **Owner**: **Antigravity License & Brand Governance Agent (`license_governance`)** is the **sole author and maintainer** of [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md), [`.agents/skills/wemi-license-governance/SKILL.md`](SKILL.md), and [`../../app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt).
> - **Policy for All Other Agents**: **DO NOT MODIFY, OVERWRITE, OR DELETE THESE GOVERNANCE FILES.** All other agents must read and follow the 5 invariants below when editing dependencies, widgets, permissions, UI copy, or preparing releases, and treat these files as **strictly read-only**.

---

## 1. The 5 Non-Negotiable License & Brand Governance Invariants

1. **Invariant 1 — Repository [`LICENSE`](../../LICENSE) & In-App [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) Parity**:
   - Root [`LICENSE`](../../LICENSE) is the standard OSI **MIT License** (`Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors`).
   - [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) (`OpenSourceLicenseCard`, alongside [`MoreControlAndLogCards.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreControlAndLogCards.kt)) must expose `open_source_licenses_card` and `toggle_license_details_button`, and expanding it must render both the verbatim MIT License (`mit_license_text_surface`) and accurate Third-Party Open Source Notices (`third_party_notices_surface`) listing only shipped third-party components (`AndroidX / Jetpack Compose / Room` `Apache-2.0` and `Kotlin & Kotlinx Coroutines` `Apache-2.0`).
2. **Invariant 2 — Minimal Permissive Dependency Allowlist & Zero Custom Bundled Font Policy ([`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), [`app/build.gradle.kts`](../../app/build.gradle.kts) & [`Type.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/theme/Type.kt))**:
   - **Runtime Libraries**: Minimized strictly to **AndroidX / Jetpack Compose / Room** (`Apache-2.0`) and **Kotlin / Kotlinx Coroutines** (`Apache-2.0`), managed exclusively via `gradle/libs.versions.toml` (no inline coordinates in `app/build.gradle.kts`, no test-only libs in `implementation`, and production `import` statements restricted to `android.*`, `androidx.*`, `java.*`, `javax.*`, `kotlin.*`, `kotlinx.*`, and `org.suppressingfire.wemi.*`), with typography rendered via Android's built-in system `FontFamily.SansSerif` (no custom `.ttf`/`.otf` font files in `app/src/main/res/font/`).
   - **Zero External HTTP/JSON/Image/Widget/Cloud SDKs**: Local-LAN HTTP/1.1 & UPnP GENA traffic uses first-party [`LanHttpClient.kt`](../../app/src/main/java/org/suppressingfire/wemi/network/LanHttpClient.kt) over JDK `SocketFactory`, and Home Screen widgets ([`widget/`](../../app/src/main/java/org/suppressingfire/wemi/widget)) use Android platform `AppWidgetProvider` + `RemoteViews`. Do **not** add `OkHttp`, `Retrofit`, `Moshi`, `Coil`, `Firebase`, `Play Services`, or unapproved third-party libraries.
   - **Tier B (Allowed Test-Only)**: `MIT` (`org.robolectric:robolectric`) and `EPL-1.0` (`junit:junit` in `testImplementation`).
   - **Tier C (Strictly Prohibited)**: `GPL-*`, `AGPL-*`, `LGPL-*`, `MPL-*`, `SSPL`, `Commons Clause`, or proprietary ad/analytics/telemetry SDKs.
   - `dependenciesInfo { includeInApk = false; includeInBundle = true }` in [`app/build.gradle.kts`](../../app/build.gradle.kts) must keep `includeInApk = false`, and `packaging { jniLibs { excludes += "**/libandroidx.graphics.path.so" } }` (paired with R8 rules in [`app/proguard-rules.pro`](../../app/proguard-rules.pro)) must keep APKs and Play Store AABs 100% pure Kotlin/DEX without un-symbolicated third-party `.so` native shims.
3. **Invariant 3 — Accurate Attribution & Zero `pywemo` / `OkHttp` / Custom Font / Native `.so` Bloat**:
   - Zero `pywemo` code, zero un-symbolicated native `.so` shims, and zero custom `.ttf` font files are included in the repository or APK/AAB; do not list `pywemo`, `OkHttp`, or removed font notices in `MoreScreen.kt` or production source comments.
4. **Invariant 4 — `org.suppressingfire.wemi` Package Hygiene, `"WeMo"` Trademark Boundary & Local-Only Data Safety Parity**:
   - All `.kt` source and test files under `app/src/` must declare `package org.suppressingfire.wemi...` (never `com.example`), and no `.kt` file may use a `WeMo*.kt` filename prefix.
   - [`app/src/main/AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml) must declare **only** the 4 install-time LAN permissions disclosed in `DATA_SAFETY_GUIDE.md` & `PRIVACY_POLICY.md` (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_MULTICAST_STATE`), and [`backup_rules.xml`](../../app/src/main/res/xml/backup_rules.xml) + [`data_extraction_rules.xml`](../../app/src/main/res/xml/data_extraction_rules.xml) must exclude `wemi-telemetry-latest.txt` from cloud Auto-Backup.
   - User-facing copy, widgets, and store/manifest XML in `AndroidManifest.xml`, `res/values/*.xml` (zero case-insensitive `wemo` in `<string>` values), `res/layout/*.xml`, `res/xml/*.xml`, [`metadata.json`](../../metadata.json), [`play_store_assets/`](../../play_store_assets/) (`STORE_LISTING.md` `<= 30 / 80 / 4000` chars, `RELEASE_NOTES.txt <= 500` chars), [`scripts/GeneratePlayStoreAssets.java`](../../scripts/GeneratePlayStoreAssets.java), [`MainActivity.kt`](../../app/src/main/java/org/suppressingfire/wemi/MainActivity.kt), `ui/**/*.kt`, and `widget/**/*.kt` must **never** use `"WeMo"` as a product brand or user-visible label (describe compatible hardware as **"Belkin's discontinued smart switch platform"** or **"Smart Switch"**).
   - `"WeMo"` / `"wemo"` is permitted in code strictly for hardware wire-protocol matching (`WeMo.Light.*`, `Wemo.LS3W.*`, `WeMo_WW_*`, `/setup.xml`), existing Room v5 table names (`wemo_devices`, `wemo_home_networks`, `wemo_rules`), and internal `testTag` / resource-key / theme identifiers (`wemo_setup_ap_banner`, `more_provision_wemo_ap_button`, `header_brand_wemo` key rendering `"wemi"`, `WemoGreenPrimary`).
5. **Invariant 5 — Automated Governance Test Suite ([`LicenseAndBrandGovernanceTest.kt`](../../app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt))**:
   - Run `sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest --rerun-tasks` and `python3 scripts/sync_play_assets.py validate-local` to verify all 5 invariants automatically (preserving `100644` non-executable script modes).

---

## 2. Stage 5 Release Review Checklist (`license_governance`)

In the **6-Agent / 7-Stage Autonomous Round-Robin Release Process** ([`../../RELEASE_PROCESS.md`](../../RELEASE_PROCESS.md) & [`../../RELEASE_SIGN_OFF.md`](../../RELEASE_SIGN_OFF.md)), `license_governance` is **Stage 5 (Review 4 — the 5th review step)**, executing immediately after `code_quality_and_testing` (Stage 4) and immediately before `ai_studio_ux_cuj` makes the final release decision (Stage 6) and `app_promoter` runs event-driven Play Store promotion & asset sync (Stage 7):

1. **Pull Latest `origin/main`**:
   ```bash
   git pull --rebase origin main
   ```
2. **Audit License, Minimal Dependency Footprint, Widgets, Zero Custom Fonts, Package, Trademark & Data Safety Invariants**:
   - Confirm word-for-word parity between [`LICENSE`](../../LICENSE) and [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) (`open_source_licenses_card`), listing only shipped `Apache-2.0` (AndroidX & Kotlin) notices, and confirm zero `.so`/`.ttf`/`.otf`/`.jar`/`.aar` files exist under `app/src/main` (including `app/src/main/res/font`).
   - Confirm [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), [`build.gradle.kts`](../../build.gradle.kts), [`settings.gradle.kts`](../../settings.gradle.kts), [`app/build.gradle.kts`](../../app/build.gradle.kts), and production imports stay strictly within the approved AndroidX + Kotlin allowlist (zero inline coordinates, zero copyleft/unapproved SDKs), with `includeInApk = false`, `libandroidx.graphics.path.so` excluded, and `namespace = "org.suppressingfire.wemi"`.
   - Confirm zero `.kt` files exist under `com/example` or use a `WeMo*.kt` prefix, `AndroidManifest.xml` declares only the 4 local-LAN permissions, `wemi-telemetry-latest.txt` is excluded in `backup_rules.xml` & `data_extraction_rules.xml`, Google Play listing limits (`STORE_LISTING.md` title `<= 30`, short desc `<= 80`, full desc `<= 4000`; `RELEASE_NOTES.txt <= 500`) & Play Store PNG assets in `DATA_SAFETY_GUIDE.md` are intact, and zero user-facing `"WeMo"` trademark leaks exist in `AndroidManifest.xml`, `res/values/*.xml`, `res/layout/*.xml`, `res/xml/*.xml`, `metadata.json`, `play_store_assets/`, `scripts/GeneratePlayStoreAssets.java`, active `docs/user-guide/*.md` feature guides, UI composables, or Home Screen widget sources.
3. **Run Automated Governance Verification**:
   ```bash
   sh gradlew testDebugUnitTest --tests org.suppressingfire.wemi.LicenseAndBrandGovernanceTest --rerun-tasks
   python3 scripts/sync_play_assets.py validate-local
   ```
4. **Sign Off & Advance Turn to `ai_studio_ux_cuj` (`AWAITING_FINAL_CONCLUSION`)**:
   ```bash
   bash scripts/advance_release_turn.sh sign_off license_governance "<concise verification summary>"
   ```
5. **End Turn Cleanly (Quota-Safe Zero-Idle-Polling Policy)**:
   - **Never re-arm `scripts/await_release_turn.sh` after signing off** (`SIGNED_OFF`) or between releases (`APPROVED_FOR_PLAY_STORE` / `PROMOTED_TO_CLOSED_TESTING` / `CURRENT_TURN: NONE`), and always honor `[QUOTA GUARD] DO NOT RE-ARM`.
   - Arm `bash scripts/await_release_turn.sh license_governance 15 510` (`WaitMsBeforeAsync: 5000`) **only** while an active release round is `IN_REVIEW` and `SIGN_OFF_LICENSE_GOVERNANCE` is `PENDING`.
   - Check `/home/antigravity/.gemini/usage/agy-usage --check-self` and recommend starting a fresh specialist conversation before the next release cycle once `>= 400` steps.
