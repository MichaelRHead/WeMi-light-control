# WeMi Light Control — Dependency & Toolchain Upgrade Plan (`DEPENDENCY_UPGRADE_PLAN.md`)

**Status**: `WAVE_1_COMPLETE_AWAITING_AI_STUDIO_VERIFICATION`  
**Prepared By**: Antigravity App Promoter Agent (`app_promoter`) — `2026-11-22 PST`  
**Reviewed By**: Google AI Studio Agent (`ai_studio_ux_cuj`) — `2026-11-22 PST`  
**Wave 1 Executed By**: Antigravity Code Quality & E2E Testing Agent (`code_quality_and_testing`) — `2026-11-22 PST`  
**Target Baseline**: Post-`v34.0` (`versionCode = 34`, `org.suppressingfire.wemi`, `compileSdk = 36.1`, `targetSdk = 36`, `minSdk = 24`)  
**Scope**: Existing Gradle wrapper, Android Gradle Plugin (AGP), Kotlin/Compose/KSP plugins, AndroidX/Room/Coroutines libraries, and Robolectric/AndroidX test dependencies in [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties) and [`gradle/libs.versions.toml`](gradle/libs.versions.toml). Zero new dependency groups, zero new permissions, and zero license-tier changes.

---

## AI Studio Review Outcome & Wave 1 Verification Gate (`2026-11-22`)

> **NEXT STEP FOR `ai_studio_ux_cuj` (BEFORE STARTING WAVE 2):**  
> **Wave 1 is now landed on `main`** (`lifecycle 2.9.2`, `room 2.8.5`, `kotlinx-coroutines 1.11.0`, `androidx.test 1.7.0`, `robolectric 4.18.1`; `core-ktx` capped at `1.18.0` and `ksp` kept at `2.3.5`). Please run **`compile_applet` + `gradle :app:testDebugUnitTest --rerun-tasks`** in Google AI Studio and record `WAVE_1_VERIFIED_IN_AI_STUDIO` in Section 5 so we can proceed to **Wave 2** (`compose-bom` + `activity-compose` + `lifecycle 2.10.0`) with your UX co-review.
>
> **Summary of AI Studio Review Constraints (see Section 5 for full details):**
> - **Wave 1 (`AndroidX / Room / Coroutines / Test`):** **`COMPLETE_LOCAL_PENDING_AI_STUDIO_VERIFY`** — upgraded within AI Studio's **`Gradle 9.3.1` + `AGP 9.1.1` + `Kotlin 2.2.10` + `compileSdk = 36.1`** ceiling (`127/127` Kotlin tests across `25` suites `--rerun-tasks`, `0` lint errors/code warnings, `0` `.so`/`.ttf`/`.otf` in `app-debug.apk`, `23/23` Python tests).
> - **Wave 2 (`Compose BOM + activity-compose + lifecycle 2.10.0`):** `APPROVED_WITH_CO_REVIEW_AND_TOOLCHAIN_CAP` — cap at `Kotlin 2.2.10` (metadata `<= 2.3`) / `compileSdk = 36.1` compatibility and co-review with `ai_studio_ux_cuj` against [`UX_DESIGN.md`](UX_DESIGN.md) & [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).
> - **Wave 3 (`Gradle / AGP / Kotlin / KSP`):** `DEFERRED_PINNED_TO_AI_STUDIO_TOOLCHAIN` — keep `Gradle 9.3.1`, `AGP 9.1.1`, `Kotlin 2.2.10`, and `KSP 2.3.5` until AI Studio upgrades its container's system `gradle` binary.

---

## 1. Current Audit Baseline (Post-Wave 1)

Verified on `2026-11-22` via `sh gradlew testDebugUnitTest --rerun-tasks` (`127/127` Kotlin tests across `25` suites, `0` failures/errors/skipped), `sh gradlew lintDebug assembleDebug` (`0` lint errors, `0` Kotlin/Android API deprecation warnings, lint warnings reduced `25` → `16`, `0` `.so`/`.ttf`/`.otf` files in `app-debug.apk`), `python3 -W default -m unittest discover -s scripts` (`23/23` Python tests, `0` warnings), and GitHub Actions run `45004155662` (`0` annotations):

- **CI & Play Store Automation (`app_promoter` — Complete):**
  - [`.github/workflows/play-store-promote.yml`](.github/workflows/play-store-promote.yml) upgraded in `3d263cc` from `actions/checkout@v4` / `actions/setup-python@v5` (deprecated Node.js 20) to `actions/checkout@v7` / `actions/setup-python@v7` (native Node.js 24).
  - Verified live with `0` job annotations and `0` Python deprecation warnings.
- **Wave 1 Empirical Compatibility Findings (`code_quality_and_testing` — Complete):**
  - `androidx.core:core-ktx:1.19.0+` and `androidx.lifecycle:*:2.11.0+` require `compileSdk >= 37` (`checkDebugAarMetadata` fails against `compileSdk = 36.1`).
  - `androidx.lifecycle:lifecycle-runtime-compose:2.9.3..2.10.0` transitively pulls `androidx.compose.runtime`/`foundation-layout:1.9.0` onto the runtime classpath while `compose-bom:2024.09.00` compiles against `foundation-layout:1.7.0`, causing a runtime `NoSuchMethodError` in `FlowLayoutKt.FlowRow`. Pinning Wave 1 `lifecycle` to **`2.9.2`** (`Compose 1.7.x` ABI) keeps all `127/127` tests green and defers `lifecycle 2.9.2` → `2.10.0` cleanly into **Wave 2** alongside the Compose BOM upgrade.

---

## 2. Full Version Delta Matrix

All upgrades stay strictly inside the existing permissive Apache-2.0 / MIT dependency groups and Gradle plugin IDs enforced by [`LICENSE_GOVERNANCE.md`](LICENSE_GOVERNANCE.md) and [`LicenseAndBrandGovernanceTest.kt`](app/src/test/java/org/suppressingfire/wemi/LicenseAndBrandGovernanceTest.kt).

| Wave | Component / Artifact | File | Previous | Current / Target | Status | Key Compatibility Notes |
| :---: | :--- | :--- | :---: | :---: | :---: | :--- |
| **1** | `androidx.core:core-ktx` | `libs.versions.toml` | `1.18.0` | **`1.18.0`** *(capped)* | **Capped (`SDK 36.1`)** | `1.19.0`/`1.19.1` require `compileSdk >= 37` (`checkDebugAarMetadata`). Stay on `1.18.0` until `compileSdk >= 37`. |
| **1** | `androidx.lifecycle:lifecycle-{runtime-ktx,viewmodel-compose,runtime-compose}` | `libs.versions.toml` | `2.8.7` | **`2.9.2`** *(Wave 2 → `2.10.0`)* | **Landed (Wave 1)** | `2.11.x` requires `compileSdk >= 37`; `2.9.3–2.10.0` pull Compose `1.9.0` (conflicts with `compose-bom:2024.09.00` `FlowRow` `1.7.0` ABI). Bump `2.9.2` → `2.10.0` in Wave 2 with Compose BOM. |
| **1** | `androidx.room:room-{runtime,ktx,compiler}` | `libs.versions.toml` | `2.7.0` | **`2.8.5`** | **Landed (Wave 1)** | Verified on Room v6 (`AppDatabase`, `MIGRATION_5_6`), KSP `2.3.5` codegen, and Robolectric in-memory DB teardown. |
| **1** | `org.jetbrains.kotlinx:kotlinx-coroutines-{android,core,test}` | `libs.versions.toml` | `1.10.2` | **`1.11.0`** | **Landed (Wave 1)** | Verified `Semaphore(3)` concurrency, `Dispatchers.IO` offloading, and `runTest` dispatchers across all `25` suites. |
| **1** | `androidx.test:core` & `androidx.test:runner` | `libs.versions.toml` | `1.6.1` / `1.6.2` | **`1.7.0` / `1.7.0`** | **Landed (Wave 1)** | Verified Robolectric & Compose test runner stability. |
| **1** | `org.robolectric:robolectric` | `libs.versions.toml` | `4.16.1` | **`4.18.1`** | **Landed (Wave 1)** | Verified single-SDK `@Config(sdk = [36])` isolation and GCS Maven mirror fetch (`robolectric.dependency.repo.url`). |
| **2** | `androidx.compose:compose-bom` | `libs.versions.toml` | `2024.09.00` | *Highest `Kotlin 2.2.10` / `SDK 36.1` BOM (up to `2026.11.01`)* | **Next (Wave 2)** | Co-reviewed by `ai_studio_ux_cuj` against `UX_DESIGN*.md` (5 layout invariants, compact `320dp` header, `ModalBottomSheet`, `Slider`, TV D-Pad focus, `libandroidx.graphics.path.so`). |
| **2** | `androidx.activity:activity-compose` | `libs.versions.toml` | `1.10.1` | *Highest `SDK 36.1` (`1.12.4` or `1.13.0`)* | **Next (Wave 2)** | Check `compileSdk = 36.1` & Kotlin metadata (`<= 2.3`); verify `enableEdgeToEdge()`, `BackHandler`, and TV/Phone/ChromeOS routing. |
| **2** | `androidx.lifecycle:*` (follow-up) | `libs.versions.toml` | `2.9.2` | **`2.10.0`** | **Next (Wave 2)** | Pairs cleanly once Wave 2 upgrades Compose BOM to `1.9+` (`2.11.x` still requires `compileSdk >= 37`). |
| **3** | `com.google.devtools.ksp` (`googleDevtoolsKsp`) | `libs.versions.toml` | `2.3.5` | `2.3.5` *(target `2.3.14`)* | **Deferred (Wave 3)** | Kept at `2.3.5` to match AI Studio's validated plugin set cleanly. |
| **3** | `org.jetbrains.kotlin.plugin.compose` (`kotlin`) | `libs.versions.toml` | `2.2.10` | `2.2.10` *(target `2.4.21`)* | **Deferred (Wave 3)** | Pinned to AGP `9.1.1`'s built-in Kotlin (`2.2.10`) in AI Studio. |
| **3** | `com.android.application` (`agp`) | `libs.versions.toml` | `9.1.1` | `9.1.1` *(target `9.5.2`)* | **Deferred (Wave 3)** | Pinned to AI Studio's `Gradle 9.3.1` ceiling. |
| **3** | Gradle Wrapper (`distributionUrl`) | `gradle-wrapper.properties` | `9.3.1` | `9.3.1` *(target `9.9.2`)* | **Deferred (Wave 3)** | Hard AI Studio container ceiling (`gradle --version` = `9.3.1`). |

---

## 3. Phased Execution & Rollback Strategy

Execute each wave as an isolated commit so any regression or AI Studio incompatibility can be pinpointed and rolled back cleanly without reverting unrelated upgrades:

### Wave 1 — Low-Risk AndroidX, Room, Coroutines & Test Harness *(Landed Locally; Awaiting AI Studio Verification)*
1. Updated `lifecycle*` (`2.8.7` → `2.9.2`), `room*` (`2.7.0` → `2.8.5`), `kotlinxCoroutines*` (`1.10.2` → `1.11.0`), `androidx.test` (`1.6.1`/`1.6.2` → `1.7.0`), and `robolectric` (`4.16.1` → `4.18.1`) in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), keeping `coreKtx = "1.18.0"` (`compileSdk 36.1` max) and `googleDevtoolsKsp = "2.3.5"`.
2. Verified `sh gradlew testDebugUnitTest --rerun-tasks` (`127/127` passing across `25` suites), `sh gradlew lintDebug assembleDebug` (`0` errors, `0` code/API deprecation warnings), `0` `.so`/`.ttf`/`.otf` binaries in `app-debug.apk`, and `23/23` Python tests (`-W default`).
3. **Gate before Wave 2:** Confirm `compile_applet` + `gradle :app:testDebugUnitTest --rerun-tasks` pass in Google AI Studio.

### Wave 2 — Jetpack Compose BOM, `activity-compose` & `lifecycle 2.10.0` *(Next — Approved w/ Co-Review & Toolchain Cap)*
1. Only start after Wave 1 is verified green in Google AI Studio (`compile_applet`).
2. Update `composeBom`, `activityCompose`, and `lifecycle*` (`2.9.2` → `2.10.0`) in [`gradle/libs.versions.toml`](gradle/libs.versions.toml) up to the highest versions compatible with `AGP 9.1.1` / `Kotlin 2.2.10` (metadata `<= 2.3`) / `compileSdk = 36.1`.
3. Fix any Material 3 / Compose API deprecations (e.g., deprecated icons, `clickable`/indication overloads, `Slider`, `ModalBottomSheet`, `FlowRow`, or TV focus APIs) without altering UX invariants in [`UX_DESIGN.md`](UX_DESIGN.md) or [`UX_DESIGN_AND_EXPERIENCE.md`](UX_DESIGN_AND_EXPERIENCE.md).
4. Run the full `127+`-test Compose, Android TV, Hardware-Rules, Widget, and ChromeOS ARCVM E2E suite (`sh gradlew testDebugUnitTest --rerun-tasks lintDebug assembleDebug`), plus `ai_studio_ux_cuj` visual co-review and `compile_applet` verification on Phone, Tablet, Chromebook, and Android TV.

### Wave 3 — Gradle Wrapper, AGP, Kotlin & KSP Toolchain *(Deferred — Pinned to AI Studio's Gradle 9.3.1 / AGP 9.1.1 / Kotlin 2.2.10 Ceiling)*
1. Do **not** execute until Google AI Studio upgrades its container's system `gradle` binary beyond `9.3.1` and `ai_studio_ux_cuj` confirms new supported versions.
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
- **Review Status:** `REVIEWED_AND_CONFIRMED (2026-11-22 01:08 PST by ai_studio_ux_cuj)`
- **Confirmed Wave 1 (`AndroidX / Room / Coroutines / Test`):** `APPROVED_WITH_TOOLCHAIN_CAP` — All proposed Wave 1 coordinates passed Google Play Creator Program / SDK validation (`restricted_check`: `13/13 allowed: true`). Approve upgrading each Wave 1 library up to the highest version compatible with AI Studio's fixed **`Gradle 9.3.1` + `AGP 9.1.1` + `Kotlin 2.2.10` + `compileSdk = 36.1`** (if any proposed target requires Kotlin `2.4` metadata or `compileSdk >= 37`, cap that artifact at the highest compatible version).
- **Wave 1 Execution Status (`2026-11-22 07:26 PST by code_quality_and_testing`):** `LANDED_AWAITING_AI_STUDIO_COMPILE_APPLET_VERIFY` — Upgraded `lifecycle` (`2.8.7` → `2.9.2`; `2.10.0` deferred to Wave 2 for Compose 1.9 ABI alignment, `2.11.x` requires `compileSdk 37`), `room` (`2.7.0` → `2.8.5`), `kotlinx-coroutines` (`1.10.2` → `1.11.0`), `androidx.test:core`/`runner` (`1.6.1`/`1.6.2` → `1.7.0`), and `robolectric` (`4.16.1` → `4.18.1`), keeping `core-ktx = 1.18.0` (`1.19.x` requires `compileSdk 37`) and `ksp = 2.3.5`. Verified `127/127` Kotlin tests (`25` suites, `--rerun-tasks`), `0` lint errors/code warnings, `0` `.so`/`.ttf`/`.otf` binaries in `app-debug.apk`, and `23/23` Python tests. **Google AI Studio Agent (`ai_studio_ux_cuj`): please verify `compile_applet` + `gradle :app:testDebugUnitTest --rerun-tasks` and confirm ready for Wave 2.**
- **Confirmed Wave 2 (`Compose BOM + activity-compose + lifecycle 2.10.0`):** `APPROVED_WITH_CO_REVIEW_AND_TOOLCHAIN_CAP` — `androidx.compose:compose-bom:2026.11.01` and `androidx.activity:activity-compose:1.13.0` passed `restricted_check` (`allowed: true`), but cap at the highest Compose BOM / `activity-compose` version that compiles cleanly under **`AGP 9.1.1` + `Kotlin 2.2.10` + `compileSdk = 36.1`** (Kotlin `2.2.10` reads up to `2.3` metadata, not `2.4`). **`ai_studio_ux_cuj` will co-review Wave 2** against `UX_DESIGN.md` and `UX_DESIGN_AND_EXPERIENCE.md` (5 layout invariants, compact `320dp` header, `ModalBottomSheet`, `Slider`, ripple/focus rings, Android TV D-Pad focus & `Holiday ON` recovery, and `libandroidx.graphics.path.so` exclusion).
- **Confirmed Wave 3 (`Gradle 9.9.2 / AGP 9.5.2 / Kotlin 2.4.21 / KSP 2.3.14`):** `DEFERRED_PINNED_TO_AI_STUDIO_TOOLCHAIN` — **Do NOT upgrade Gradle, AGP, or Kotlin right now:**
  - **Gradle Ceiling = `9.3.1` (HARD CONTAINER CEILING):** Google AI Studio does not use `./gradlew`; it executes the container's pre-installed `gradle` binary directly, verified empirically via `gradle --version` as **`Gradle 9.3.1`** (JVM `21.0.13`, embedded Kotlin `2.2.21`). Keep `gradle/wrapper/gradle-wrapper.properties` pinned to `9.3.1` so Antigravity VMs match AI Studio identically.
  - **AGP Ceiling = `9.1.1`:** AGP `9.5.2` requires a newer Gradle than `9.3.1` and would break AI Studio `compile_applet` and `.aab` generation.
  - **Kotlin Compose Plugin Ceiling = `2.2.10`:** AGP `9.1.1` ships built-in Kotlin support (`2.2.10`); bumping `org.jetbrains.kotlin.plugin.compose` to `2.4.21` while AGP remains `9.1.1` causes a Kotlin/Compose compiler mismatch.
  - **KSP (`com.google.devtools.ksp`):** Keep at **`2.3.5`** by default; optionally test `2.3.14` during Wave 1 *only* if standalone KSP2 `2.3.14` runs cleanly with Gradle `9.3.1` + AGP `9.1.1` + Kotlin `2.2.10` + Room `2.8.x` (otherwise stay on `2.3.5`).
  - **SDK Versions:** Keep `compileSdk = 36.1`, `targetSdk = 36`, `minSdk = 24` unchanged.
- **AI Studio Notes / Version Constraints & Build Invariants:**
  1. **Verify every wave in AI Studio (`compile_applet` + `gradle :app:testDebugUnitTest --rerun-tasks`)** before starting the next wave.
  2. **Preserve all `gradle.properties` flags untouched** — especially `kotlin.compiler.execution.strategy=in-process` (critical in AI Studio's gVisor container), `org.gradle.workers.max=4`, `org.gradle.jvmargs=-Xmx4g -Dfile.encoding=UTF-8`, `org.gradle.configuration-cache=true`, `org.gradle.configuration-cache.parallel=true`, and `googleServices.missing.passthrough=true`.
  3. **Preserve all `app/build.gradle.kts` invariants untouched** — `signingConfigs` (`release` / `debugConfig`), `signingConfig` assignments, `isMinifyEnabled = true`, `isShrinkResources = true`, `excludes += "**/libandroidx.graphics.path.so"`, `dependenciesInfo { includeInApk = false; includeInBundle = true }`, no product flavors, no `local.properties`.
  4. **Revisit Wave 3** only after Google AI Studio upgrades its container's system `gradle` binary beyond `9.3.1`.
<!-- AI_STUDIO_DEPENDENCY_PLAN_REVIEW_END -->
