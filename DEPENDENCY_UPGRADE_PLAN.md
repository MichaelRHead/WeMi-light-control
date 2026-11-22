# WeMi Light Control — Dependency & Toolchain Upgrade Plan (`DEPENDENCY_UPGRADE_PLAN.md`)

**Status**: `AWAITING_AI_STUDIO_REVIEW_AND_CONFIRMATION`  
**Prepared By**: Antigravity App Promoter Agent (`app_promoter`) — `2026-11-22 PST`  
**Target Baseline**: Post-`v34.0` (`versionCode = 34`, `org.suppressingfire.wemi`, `compileSdk = 36.1`, `targetSdk = 36`, `minSdk = 24`)  
**Scope**: Existing Gradle wrapper, Android Gradle Plugin (AGP), Kotlin/Compose/KSP plugins, AndroidX/Room/Coroutines libraries, and Robolectric/AndroidX test dependencies in [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Zero new dependency groups, zero new permissions, and zero license-tier changes.

---

## Note for Google AI Studio Agent (`ai_studio_ux_cuj`) — Please Review & Confirm

> **ACTION REQUIRED BEFORE EXECUTING UPGRADES:**  
> Before any Antigravity specialist (`code_quality_and_testing` or `license_governance`) modifies [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) or [`gradle/libs.versions.toml`](gradle/libs.versions.toml), please review this plan and confirm compatibility with the **Google AI Studio Android build & Play export environment** (`compile_applet`, release `.aab` signing/minification, and Gradle/SDK caches).
>
> Please confirm (or annotate directly in the **AI Studio Review & Confirmation** block at the bottom of this file):
> 1. **Toolchain Ceiling (Wave 3):** Are **Gradle `9.9.2`**, **AGP `9.5.2`**, **Kotlin `2.4.21`** (Compose compiler plugin), and **KSP `2.3.14`** supported by the Google AI Studio Android container (`compile_applet` + release `.aab` build), or should we pin to specific intermediate versions that AI Studio supports?
> 2. **Phased Rollout Order:** Do you approve executing the upgrade in three independent, test-gated waves (Wave 1: non-Compose AndroidX/Room/Coroutines/Test libs → Wave 2: Compose BOM `2024.09.00` → `2026.11.01` + `activity-compose` → Wave 3: Gradle/AGP/Kotlin/KSP toolchain)?
> 3. **Compose BOM `2026.11.01` UX / Material 3 Review (Wave 2):** Upgrading Compose BOM across ~2 years (`2024.09.00` → `2026.11.01`) may adjust Material 3 component defaults, touch ripple/focus rings, or `ModalBottomSheet` / `Slider` / D-Pad TV focus behavior. Please confirm whether you want to lead or co-review Wave 2 against [`UX_DESIGN.md`](UX_DESIGN.md) and [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).
> 4. **Build Configuration Invariants:** Confirm whether any AI Studio-specific flags in [`gradle.properties`](gradle.properties) (such as `kotlin.compiler.execution.strategy=in-process` or `org.gradle.configuration-cache.parallel=true`) or [`app/build.gradle.kts`](app/build.gradle.kts) (`signingConfigs`, `isMinifyEnabled = true`, `isShrinkResources = true`, `libandroidx.graphics.path.so` exclusion) require adjustments for AGP `9.5.2` / Gradle `9.9.2`.

---

## 1. Current Audit Baseline (Post-`v34.0`)

Verified on `2026-11-22` via `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug` (`120/120` Kotlin tests across `23` suites, `0` lint errors, `0` Kotlin/Android API deprecation warnings), `python3 -W default -m unittest discover -s scripts` (`23/23` Python tests, `0` warnings), and GitHub Actions run `45004155662` (`0` annotations):

- **CI & Play Store Automation (`app_promoter` — Already Complete):**
  - [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) upgraded in `3d263cc` from `actions/checkout@v4` / `actions/setup-python@v5` (deprecated Node.js 20) to `actions/checkout@v7` / `actions/setup-python@v7` (native Node.js 24).
  - Verified live with `0` job annotations and `0` Python deprecation warnings.
- **Android Build & Dependencies (`lintDebug` Version Audit):**
  - `0` lint errors and `0` deprecated Android/Kotlin API usages in production code.
  - `18` newer-version notices flagged by Android Lint across [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and [`gradle/libs.versions.toml`](gradle/libs.versions.toml), plus KSP `2.3.5` → `2.3.14` (latest `google/ksp` standalone KSP2 release).

---

## 2. Full Version Delta Matrix

All proposed upgrades stay strictly inside the existing permissive Apache-2.0 / MIT dependency groups and Gradle plugin IDs enforced by [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) and [`LicenseAndBrandGovernanceTest.kt`](app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt).

| Wave | Component / Artifact | File | Current | Proposed Target | Risk | Key Compatibility Notes |
| :---: | :--- | :--- | :---: | :---: | :---: | :--- |
| **1** | `androidx.core:core-ktx` | `libs.versions.toml` | `1.18.0` | `1.19.1` | Low | Minor AndroidX Core update; check `compileSdk = 36.1` compatibility. |
| **1** | `androidx.lifecycle:lifecycle-{runtime-ktx,viewmodel-compose,runtime-compose}` | `libs.versions.toml` | `2.8.7` | `2.11.1` | Low | Verify `CreationExtras` `SwitchViewModel.Factory` and `collectAsStateWithLifecycle` behavior. |
| **1** | `androidx.room:room-{runtime,ktx,compiler}` | `libs.versions.toml` | `2.7.0` | `2.8.5` | Medium | Verify Room v5 schema (`AppDatabase`), KSP codegen, and Robolectric in-memory DB teardown. |
| **1** | `org.jetbrains.kotlinx:kotlinx-coroutines-{android,core,test}` | `libs.versions.toml` | `1.10.2` | `1.11.0` | Low | Verify `Semaphore(3)` concurrency, `Dispatchers.IO` offloading, and `runTest` test dispatchers. |
| **1** | `androidx.test:core` & `androidx.test:runner` | `libs.versions.toml` | `1.6.1` / `1.6.2` | `1.7.0` / `1.7.0` | Low | Test-only dependencies; verify Robolectric Compose test runner stability. |
| **1** | `org.robolectric:robolectric` | `libs.versions.toml` | `4.16.1` | `4.18.1` | Medium | Verify single-SDK `@Config(sdk = [36])` isolation and GCS Maven mirror fetch (`robolectric.dependency.repo.url`). |
| **2** | `androidx.compose:compose-bom` | `libs.versions.toml` | `2024.09.00` | `2026.11.01` | High | ~2-year jump across Compose UI, Material 3, and `material-icons-extended`. Audit all 5 Compose Layout Invariants, Android TV D-Pad focus, `ModalBottomSheet`, dimmers, and R8 shrink rules. |
| **2** | `androidx.activity:activity-compose` | `libs.versions.toml` | `1.10.1` | `1.13.0` | Medium | Verify `enableEdgeToEdge()`, `BackHandler`, and `MainActivity` TV/Phone/ChromeOS routing. |
| **3** | `com.google.devtools.ksp` (`googleDevtoolsKsp`) | `libs.versions.toml` | `2.3.5` | `2.3.14` | Medium | Standalone KSP2; verify Room `2.8.5` annotation processing and check whether the benign KSP `AWT-EventQueue-0` NPE log vanishes. |
| **3** | `org.jetbrains.kotlin.plugin.compose` (`kotlin`) | `libs.versions.toml` | `2.2.10` | `2.4.21` | High | Must be supported by Google AI Studio's Kotlin/Gradle environment; audit new Kotlin compiler deprecation warnings (`-Werror` / clean compile logs). |
| **3** | `com.android.application` (`agp`) | `libs.versions.toml` | `9.1.1` | `9.5.2` | High | **AI Studio critical.** Confirm AI Studio build/export compatibility, `dependenciesInfo`, `packaging.jniLibs.excludes` (`libandroidx.graphics.path.so`), and R8/ProGuard release minification. |
| **3** | Gradle Wrapper (`distributionUrl`) | `gradle-wrapper.properties` | `9.3.1` | `9.9.2` | High | **AI Studio critical.** Pair with AGP `9.5.2`; verify parallel configuration cache (`org.gradle.configuration-cache.parallel=true`) and `ensureDebugKeystore` task compatibility. |

---

## 3. Phased Execution & Rollback Strategy

Execute each wave as an isolated commit (or branch/PR if preferred) so any regression or AI Studio incompatibility can be pinpointed and rolled back cleanly without reverting unrelated upgrades:

### Wave 1 — Low-Risk AndroidX, Room, Coroutines & Test Harness
1. Update `coreKtx`, `lifecycle*`, `room*`, `kotlinxCoroutines*`, `androidx.test` (`core`/`runner`), and `robolectric` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
2. Run full verification gate (`sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug`) and inspect for any new Room/Coroutines/Robolectric deprecation warnings.
3. Confirm `app/build/outputs/apk/debug/app-debug.apk` still ships `0` third-party `.so` binaries or bundled fonts (`LicenseAndBrandGovernanceTest`).

### Wave 2 — Jetpack Compose BOM (`2024.09.00` → `2026.11.01`) & `activity-compose`
1. Update `composeBom` and `activityCompose` in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
2. Fix any Material 3 / Compose API deprecations (e.g., deprecated icons, `clickable`/indication overloads, `Slider`, `ModalBottomSheet`, or TV focus APIs) without altering UX invariants in [`UX_DESIGN.md`](UX_DESIGN.md) or [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).
3. Run the full `120+`-test Compose, Android TV, Widget, and ChromeOS ARCVM E2E suite (`sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug`), plus `ai_studio_ux_cuj` visual inspection on Phone, Tablet, Chromebook, and Android TV.

### Wave 3 — Gradle Wrapper, AGP, Kotlin & KSP Toolchain *(Blocked on AI Studio Confirmation)*
1. Only proceed after `ai_studio_ux_cuj` confirms exact supported versions in Google AI Studio.
2. Update [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and plugin versions (`agp`, `kotlin`, `googleDevtoolsKsp`) in [`gradle/libs.versions.toml`](gradle/libs.versions.toml).
3. Verify:
   - `sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug`
   - Release R8/ProGuard build compatibility (`isMinifyEnabled = true`, `isShrinkResources = true`, `proguard-rules.pro`, `libandroidx.graphics.path.so` still excluded, `includeInApk = false`).
   - Non-executable (`100644`) git modes preserved on `gradlew` and scripts.
   - AI Studio `compile_applet` and signed `.aab` build succeed cleanly.

### Post-Upgrade Environment Cache & Release Gate
- Refresh persistent Antigravity VM caches after each wave lands:
  ```bash
  bash scripts/setup_dev_env.sh --save-caches
  ```
  *(Because [`scripts/setup_dev_env.sh`](scripts/setup_dev_env.sh) fingerprints `gradle-wrapper.properties`, `libs.versions.toml`, `settings.gradle.kts`, `build.gradle.kts`, and `app/build.gradle.kts`, saving caches keeps fresh VM startups fast.)*
- Run the full 6-agent / 7-stage release review ring ([`RELEASE_PROCESS.md`](RELEASE_PROCESS.md)) before shipping any upgraded release to Google Play.

---

## 4. Non-Negotiable Governance & Build Invariants

- **Strict Dependency Allowlist ([`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md)):** Only existing approved groups (`androidx.*`, `org.jetbrains.kotlinx`, `org.robolectric`, `junit`) and plugins (`com.android.application`, `org.jetbrains.kotlin.plugin.compose`, `com.google.devtools.ksp`, `org.gradle.toolchains.foojay-resolver-convention`). Zero third-party HTTP/image/DI/analytics libraries.
- **Pure Kotlin/DEX Release Bundle:** Keep `excludes += "**/libandroidx.graphics.path.so"`, `dependenciesInfo { includeInApk = false; includeInBundle = true }`, and `0` bundled `.ttf`/`.otf` fonts.
- **Untouched AI Studio Signing:** Never alter `signingConfigs` or `signingConfig` assignments in [`app/build.gradle.kts`](app/build.gradle.kts).
- **Zero File Bloat:** Keep all `.kt` files `< 500` lines.

---

## 5. AI Studio Review & Confirmation Log

<!-- AI_STUDIO_DEPENDENCY_PLAN_REVIEW_BEGIN -->
- **Review Status:** `PENDING_AI_STUDIO_CONFIRMATION`
- **Confirmed Wave 1 (`AndroidX / Room / Coroutines / Test`):** `PENDING`
- **Confirmed Wave 2 (`Compose BOM 2026.11.01 + activity-compose`):** `PENDING`
- **Confirmed Wave 3 (`Gradle 9.9.2 / AGP 9.5.2 / Kotlin 2.4.21 / KSP 2.3.14`):** `PENDING`
- **AI Studio Notes / Version Constraints:** *(Google AI Studio Agent: please record supported Gradle/AGP/Kotlin/KSP ceilings and any Compose BOM / `compile_applet` notes here.)*
<!-- AI_STUDIO_DEPENDENCY_PLAN_REVIEW_END -->
