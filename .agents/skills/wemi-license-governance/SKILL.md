---
name: wemi-license-governance
description: Open-source MIT license parity (LICENSE <-> MoreScreen.kt), permissive third-party dependency allowlist (gradle/libs.versions.toml & app/build.gradle.kts), clean-room pywemo protocol attribution, org.suppressingfire.wemi package/filename hygiene, and "WeMo" trademark boundary governance for WeMi Light Control. EXCLUSIVELY OWNED by the Antigravity License & Brand Governance Agent (other agents MUST read and follow this skill when modifying dependencies, licenses, packages, or user-facing brand strings, but MUST NOT edit this skill or LICENSE_GOVERNANCE.md).
---

# WeMi License & Brand Governance Skill (`wemi-license-governance`)

> [!CAUTION]
> **EXCLUSIVE AGENT OWNERSHIP (READ-ONLY FOR OTHER AGENTS)**
> - **Owner**: **Antigravity License & Brand Governance Agent (`license_governance`)** is the **sole author and maintainer** of [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) and [`.agents/skills/wemi-license-governance/SKILL.md`](SKILL.md).
> - **Policy for All Other Agents**: **DO NOT MODIFY, OVERWRITE, OR DELETE [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) OR THIS `SKILL.md`.** All other agents must read and follow the 5 invariants below when adding dependencies, editing UI copy, or preparing releases, and must treat these two governance files as **strictly read-only**.

---

## 1. The 5 Non-Negotiable License & Brand Governance Invariants

1. **Invariant 1 — Repository [`LICENSE`](../../LICENSE) & In-App [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) Parity**:
   - Root [`LICENSE`](../../LICENSE) is the standard OSI **MIT License** (`Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors`).
   - [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) must expose `open_source_licenses_card` and `toggle_license_details_button`, and expanding it must render both the verbatim MIT License (`mit_license_text_surface`) and the Third-Party Open Source Notices (`third_party_notices_surface`).
2. **Invariant 2 — Permissive-Only Dependency Allowlist ([`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) & [`app/build.gradle.kts`](../../app/build.gradle.kts))**:
   - **Tier A (Allowed Runtime & Test)**: `MIT`, `Apache-2.0`, `BSD-2-Clause`, `BSD-3-Clause`, `ISC`, `0BSD`.
   - **Tier B (Allowed Test-Only)**: `EPL-1.0` / `EPL-2.0` (`junit:junit` in `testImplementation`).
   - **Tier C (Strictly Prohibited)**: `GPL-*`, `AGPL-*`, `LGPL-*`, `SSPL`, `Commons Clause`, or proprietary ad/analytics/telemetry SDKs.
   - `dependenciesInfo { includeInApk = false; includeInBundle = true }` in [`app/build.gradle.kts`](../../app/build.gradle.kts) must keep `includeInApk = false`.
3. **Invariant 3 — Clean-Room Protocol Attribution (`pywemo` MIT Reference)**:
   - Maintain explicit attribution to `pywemo` (`MIT License`) as the clean-room UPnP/SSDP/SOAP/GENA and OpenSSL `EVP_BytesToKey` AES-128-CBC protocol reference in [`../../LICENSE_GOVERNANCE.md`](../../LICENSE_GOVERNANCE.md) and [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt).
4. **Invariant 4 — `org.suppressingfire.wemi` Package Hygiene & `"WeMo"` Trademark Boundary**:
   - All `.kt` source and test files under `app/src/` must declare `package org.suppressingfire.wemi...` (never `com.example`), and no `.kt` file may use a `WeMo*.kt` filename prefix.
   - User-facing copy in [`app/src/main/res/values/strings.xml`](../../app/src/main/res/values/strings.xml), [`metadata.json`](../../metadata.json), [`play_store_assets/`](../../play_store_assets/), and UI composables under `app/src/main/java/org/suppressingfire/wemi/ui/` must **never** use `"WeMo"` as a product brand or user-visible label (describe compatible hardware as **"Belkin's discontinued smart switch platform"** or **"Smart Switch"**).
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
2. **Audit License, Dependency, Package & Trademark Invariants**:
   - Confirm [`LICENSE`](../../LICENSE) and [`MoreScreen.kt`](../../app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt) (`open_source_licenses_card`) match and include third-party notices.
   - Confirm [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml) and [`app/build.gradle.kts`](../../app/build.gradle.kts) contain only approved Tier A/B dependencies and `namespace = "org.suppressingfire.wemi"`.
   - Confirm zero `.kt` files exist under `com/example` or use a `WeMo*.kt` prefix, and zero user-facing `"WeMo"` trademark leaks exist in `strings.xml`, `metadata.json`, `play_store_assets/`, or UI composables.
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
