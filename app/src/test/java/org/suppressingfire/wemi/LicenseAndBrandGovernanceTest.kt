package org.suppressingfire.wemi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicenseAndBrandGovernanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun resolveRepoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "LICENSE").exists()) {
            cwd
        } else {
            cwd.parentFile ?: cwd
        }
    }

    @Test
    fun rootLicenseAndDependencyManifestsComplyWithPermissiveGovernance() {
        val repoRoot = resolveRepoRoot()

        val licenseFile = File(repoRoot, "LICENSE")
        assertTrue("Root LICENSE file must exist", licenseFile.exists())
        val licenseText = licenseFile.readText()
        assertTrue("Root LICENSE must declare MIT License", licenseText.contains("MIT License"))
        assertTrue(
            "Root LICENSE must include copyright attribution",
            licenseText.contains("Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors")
        )
        assertTrue(
            "Root LICENSE must include MIT permission grant",
            licenseText.contains("Permission is hereby granted, free of charge, to any person obtaining a copy")
        )

        val governanceDoc = File(repoRoot, "LICENSE_GOVERNANCE.md")
        assertTrue("Root LICENSE_GOVERNANCE.md specification must exist", governanceDoc.exists())

        val libsToml = File(repoRoot, "gradle/libs.versions.toml")
        assertTrue("gradle/libs.versions.toml must exist", libsToml.exists())
        val libsTomlLower = libsToml.readText().lowercase()
        val forbiddenTokens = listOf(
            "gpl", "agpl", "lgpl", "sspl", "commons-clause",
            "okhttp", "retrofit", "moshi", "coil", "firebase", "play-services"
        )
        for (token in forbiddenTokens) {
            assertFalse(
                "gradle/libs.versions.toml must not reference prohibited or unapproved third-party token '$token'",
                libsTomlLower.contains(token)
            )
        }

        val appBuildGradle = File(repoRoot, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", appBuildGradle.exists())
        val buildGradleText = appBuildGradle.readText()
        assertTrue(
            "app/build.gradle.kts must declare namespace = \"org.suppressingfire.wemi\"",
            buildGradleText.contains("namespace = \"org.suppressingfire.wemi\"")
        )
        assertTrue(
            "app/build.gradle.kts must omit dependency metadata block in APK",
            buildGradleText.contains("includeInApk = false")
        )
        assertFalse(
            "app/build.gradle.kts must not depend on okhttp",
            buildGradleText.lowercase().contains("okhttp")
        )

        // Verify zero custom bundled font files remain in app/src/main/res/font
        assertFalse(
            "No custom font files may be bundled in app/src/main/res/font",
            File(repoRoot, "app/src/main/res/font").exists()
        )

        // Verify zero okhttp3 imports and zero pywemo references across production Kotlin sources
        val mainJavaRoot = File(repoRoot, "app/src/main/java")
        mainJavaRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { ktFile ->
                val relPath = ktFile.relativeTo(repoRoot).path
                val src = ktFile.readText()
                assertFalse("$relPath must not import okhttp3", src.contains("okhttp3"))
                assertFalse("$relPath must not reference pywemo", src.contains("pywemo", ignoreCase = true))
            }
    }

    @Test
    fun packageNamespaceAndStoreAssetsEnforceTrademarkBoundaries() {
        val repoRoot = resolveRepoRoot()

        // 1. No legacy com.example Kotlin sources may remain
        val legacyMainDir = File(repoRoot, "app/src/main/java/com/example")
        val legacyTestDir = File(repoRoot, "app/src/test/java/com/example")
        val legacyMainFiles = legacyMainDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val legacyTestFiles = legacyTestDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        assertTrue("No Kotlin files may remain in app/src/main/java/com/example", legacyMainFiles.isEmpty())
        assertTrue("No Kotlin files may remain in app/src/test/java/com/example", legacyTestFiles.isEmpty())

        // 2. Every Kotlin source/test file must declare package org.suppressingfire.wemi... and never use a WeMo* filename prefix
        val srcRoot = File(repoRoot, "app/src")
        val allKotlinFiles = srcRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val wrongPackageFiles = allKotlinFiles.filter { ktFile ->
            val pkgLine = ktFile.useLines { lines -> lines.firstOrNull { it.trimStart().startsWith("package ") } } ?: ""
            !pkgLine.trim().startsWith("package org.suppressingfire.wemi")
        }.map { it.relativeTo(repoRoot).path }
        assertTrue(
            "Every Kotlin file must declare package org.suppressingfire.wemi..., found: $wrongPackageFiles",
            wrongPackageFiles.isEmpty()
        )

        val wemoNamedFiles = allKotlinFiles
            .filter { it.name.startsWith("WeMo", ignoreCase = true) }
            .map { it.relativeTo(repoRoot).path }
        assertTrue(
            "No Kotlin source/test files may use a WeMo* filename prefix, found: $wemoNamedFiles",
            wemoNamedFiles.isEmpty()
        )

        // 3. Store-facing, screenshot generator & app resource files must not leak the "WeMo" trademark
        val brandBoundaryFiles = listOf(
            "app/src/main/res/values/strings.xml",
            "metadata.json",
            "play_store_assets/STORE_LISTING.md",
            "play_store_assets/RELEASE_NOTES.txt",
            "play_store_assets/PRIVACY_POLICY.md",
            "play_store_assets/DATA_SAFETY_GUIDE.md",
            "scripts/GeneratePlayStoreAssets.java"
        )
        for (relPath in brandBoundaryFiles) {
            val file = File(repoRoot, relPath)
            assertTrue("Expected brand-boundary file $relPath to exist", file.exists())
            val content = file.readText()
            assertFalse(
                "Brand-boundary file $relPath must not contain 'WeMo' / 'Wemo' trademark",
                Regex("\\bWeMo\\b|\\bWemo\\b").containsMatchIn(content)
            )
        }

        // 4. Modular UI composables & controllers (MainActivity.kt + ui/**/*.kt) must not expose user-visible "WeMo" string literals
        val allowedUiProtocolOrTagTokens = setOf(
            "WeMo_WW_2.00.11532.PVT",
            "wemo_setup_ap_detected_banner",
            "wemo_setup_ap_banner",
            "more_provision_wemo_ap_button"
        )
        val uiFiles = listOf(File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/MainActivity.kt")) +
            File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/ui")
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .toList()
        val stringLiteralRegex = Regex("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"")
        val disallowedUiStrings = mutableListOf<String>()
        for (uiFile in uiFiles) {
            uiFile.readLines().forEachIndexed { idx, line ->
                stringLiteralRegex.findAll(line).forEach { match ->
                    val literal = match.groupValues[1]
                    if (literal.contains("wemo", ignoreCase = true) && literal !in allowedUiProtocolOrTagTokens) {
                        disallowedUiStrings += "${uiFile.relativeTo(repoRoot).path}:${idx + 1} -> \"$literal\""
                    }
                }
            }
        }
        assertTrue(
            "UI files must not expose user-visible 'WeMo' brand literals, found: $disallowedUiStrings",
            disallowedUiStrings.isEmpty()
        )

        // 5. Google Play Store What's New release notes must fit within the 500-character Play Console limit
        val releaseNotesText = File(repoRoot, "play_store_assets/RELEASE_NOTES.txt").readText().trim()
        assertTrue(
            "play_store_assets/RELEASE_NOTES.txt must be <= 500 characters (found ${releaseNotesText.length})",
            releaseNotesText.length <= 500
        )
    }

    @Test
    fun moreScreenDisplaysInAppMitLicenseAndThirdPartyNotices() {
        composeTestRule.waitForIdle()

        // Navigate to More tab
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()

        // Scroll to the Open Source Licenses card
        composeTestRule.onNodeWithTag("more_screen_list")
            .performScrollToNode(hasTestTag("open_source_licenses_card"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("open_source_licenses_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Source License").assertIsDisplayed()
        composeTestRule.onNodeWithText("MIT License • Copyright (c) 2026 Michael R. Head & Contributors").assertIsDisplayed()

        // Expand the full MIT license & third-party notices
        composeTestRule.onNodeWithTag("toggle_license_details_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mit_license_text_surface").assertExists()
        composeTestRule.onNodeWithTag("third_party_notices_surface").assertExists()
        composeTestRule.onNodeWithText("Permission is hereby granted, free of charge", substring = true).assertExists()
        composeTestRule.onNodeWithText("Third-Party Open Source Notices:", substring = true).assertExists()
        composeTestRule.onNodeWithText("Apache License 2.0", substring = true).assertExists()
        composeTestRule.onNodeWithText("OkHttp", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("pywemo", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("SIL Open Font License", substring = true).assertDoesNotExist()
    }
}
