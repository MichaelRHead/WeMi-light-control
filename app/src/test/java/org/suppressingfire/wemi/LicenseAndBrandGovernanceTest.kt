package org.suppressingfire.wemi

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicenseAndBrandGovernanceTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private fun resolveRepoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return if (File(cwd, "LICENSE").exists()) cwd else (cwd.parentFile ?: cwd)
    }

    private fun excludedFilePaths(parent: Element): Set<String> {
        val nodes = parent.getElementsByTagName("exclude")
        return buildSet {
            for (i in 0 until nodes.length) {
                val el = nodes.item(i) as? Element ?: continue
                if (el.getAttribute("domain") == "file") {
                    add(el.getAttribute("path").trim().trimEnd('/'))
                }
            }
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

        // Word-for-word MIT LICENSE parity with MoreScreen.kt (OpenSourceLicenseCard)
        val moreScreenFile = File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/ui/screens/MoreScreen.kt")
        assertTrue("MoreScreen.kt must exist", moreScreenFile.exists())
        val mitConstMatch = Regex(
            pattern = "private val MIT_LICENSE_TEXT = \"\"\"(.*?)\"\"\"\\.trimIndent\\(\\)",
            option = RegexOption.DOT_MATCHES_ALL
        ).find(moreScreenFile.readText())
        assertNotNull("MoreScreen.kt must define MIT_LICENSE_TEXT", mitConstMatch)
        val whitespaceRegex = Regex("\\s+")
        assertEquals(
            "In-app MIT_LICENSE_TEXT in MoreScreen.kt must match root LICENSE word-for-word",
            licenseText.trim().split(whitespaceRegex),
            mitConstMatch!!.groupValues[1].trim().split(whitespaceRegex)
        )

        assertTrue("Root LICENSE_GOVERNANCE.md specification must exist", File(repoRoot, "LICENSE_GOVERNANCE.md").exists())

        val forbiddenTokens = listOf(
            "gpl", "agpl", "lgpl", "sspl", "commons-clause",
            "okhttp", "retrofit", "moshi", "coil", "firebase", "play-services"
        )
        for (manifestRel in listOf("gradle/libs.versions.toml", "build.gradle.kts", "settings.gradle.kts")) {
            val manifestFile = File(repoRoot, manifestRel)
            assertTrue("$manifestRel must exist", manifestFile.exists())
            val manifestLower = manifestFile.readText().lowercase()
            for (token in forbiddenTokens) {
                assertFalse("$manifestRel must not reference prohibited or unapproved token '$token'", manifestLower.contains(token))
            }
        }

        val tomlText = File(repoRoot, "gradle/libs.versions.toml").readText()
        val declaredGroups = Regex("""\bgroup\s*=\s*"([^"]+)"""").findAll(tomlText).map { it.groupValues[1] }.toList()
        assertTrue("gradle/libs.versions.toml must declare managed dependency groups", declaredGroups.isNotEmpty())
        val unapprovedGroups = declaredGroups.filterNot { group ->
            group.startsWith("androidx.") || group == "org.jetbrains.kotlinx" || group == "org.robolectric" || group == "junit"
        }
        assertTrue("gradle/libs.versions.toml contains unapproved third-party dependency groups: $unapprovedGroups", unapprovedGroups.isEmpty())

        val approvedPluginIds = setOf("com.android.application", "org.jetbrains.kotlin.plugin.compose", "com.google.devtools.ksp")
        val pluginsSection = tomlText.substringAfter("[plugins]", missingDelimiterValue = "")
        val declaredPluginIds = Regex("""\bid\s*=\s*"([^"]+)"""").findAll(pluginsSection).map { it.groupValues[1] }.toList()
        assertTrue("gradle/libs.versions.toml [plugins] section must declare Gradle plugins", declaredPluginIds.isNotEmpty())
        val unapprovedPluginIds = declaredPluginIds.filterNot { it in approvedPluginIds }
        assertTrue("gradle/libs.versions.toml contains unapproved Gradle plugin IDs: $unapprovedPluginIds", unapprovedPluginIds.isEmpty())

        val approvedSettingsPluginIds = setOf("org.gradle.toolchains.foojay-resolver-convention")
        val settingsPluginIds = Regex("""\bid\s*\(\s*"([^"]+)"\s*\)""")
            .findAll(File(repoRoot, "settings.gradle.kts").readText()).map { it.groupValues[1] }.toList()
        val unapprovedSettingsPlugins = settingsPluginIds.filterNot { it in approvedSettingsPluginIds }
        assertTrue("settings.gradle.kts contains unapproved Gradle plugin IDs: $unapprovedSettingsPlugins", unapprovedSettingsPlugins.isEmpty())

        val appBuildGradle = File(repoRoot, "app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", appBuildGradle.exists())
        val buildGradleText = appBuildGradle.readText()
        assertTrue("app/build.gradle.kts must declare namespace = \"org.suppressingfire.wemi\"", buildGradleText.contains("namespace = \"org.suppressingfire.wemi\""))
        assertTrue("app/build.gradle.kts must omit dependency metadata block in APK", buildGradleText.contains("includeInApk = false"))
        assertTrue("app/build.gradle.kts must exclude unused libandroidx.graphics.path.so shim", buildGradleText.contains("excludes += \"**/libandroidx.graphics.path.so\""))
        assertTrue("app/proguard-rules.pro must exist for R8 release shrinking", File(repoRoot, "app/proguard-rules.pro").exists())
        val inlineCoordRegex = Regex(
            """\b(?:implementation|api|runtimeOnly|compileOnly|testImplementation|androidTestImplementation|debugImplementation)\s*\(\s*"[^"]+:[^"]+""""
        )
        assertFalse("app/build.gradle.kts must manage all dependencies via libs.versions.toml", inlineCoordRegex.containsMatchIn(buildGradleText))
        for (line in buildGradleText.lines().filter { it.trimStart().startsWith("implementation(") }) {
            assertFalse("Test-only Robolectric must not be in implementation scope: $line", line.contains("robolectric", ignoreCase = true))
            assertFalse("Test-only JUnit must not be in implementation scope: $line", line.contains("junit", ignoreCase = true))
        }
        val buildGradleLower = buildGradleText.lowercase()
        for (token in forbiddenTokens) {
            assertFalse("app/build.gradle.kts must not reference prohibited token '$token'", buildGradleLower.contains(token))
        }

        // Verify zero custom bundled font or binary archive/native files remain anywhere under app/src/main
        assertFalse("No custom font files may be bundled in app/src/main/res/font", File(repoRoot, "app/src/main/res/font").exists())
        val disallowedAssetExtensions = setOf("so", "ttf", "otf", "jar", "aar")
        val disallowedMainAssets = File(repoRoot, "app/src/main")
            .walkTopDown().filter { it.isFile && it.extension.lowercase() in disallowedAssetExtensions }
            .map { it.relativeTo(repoRoot).path }.toList()
        assertTrue("app/src/main must remain pure Kotlin/DEX without bundled binaries, found: $disallowedMainAssets", disallowedMainAssets.isEmpty())

        // Verify production Kotlin sources only import platform/AndroidX/Kotlin/first-party packages and never reference pywemo
        val allowedImportPrefixes = listOf("android.", "androidx.", "java.", "javax.", "kotlin.", "kotlinx.", "org.suppressingfire.wemi.")
        val disallowedImports = mutableListOf<String>()
        File(repoRoot, "app/src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { ktFile ->
            val relPath = ktFile.relativeTo(repoRoot).path
            val src = ktFile.readText()
            assertFalse("$relPath must not import okhttp3", src.contains("okhttp3"))
            assertFalse("$relPath must not reference pywemo", src.contains("pywemo", ignoreCase = true))
            ktFile.readLines().forEachIndexed { idx, rawLine ->
                val line = rawLine.trim()
                if (line.startsWith("import ")) {
                    val target = line.removePrefix("import ").trim()
                    if (allowedImportPrefixes.none { target.startsWith(it) }) {
                        disallowedImports += "$relPath:${idx + 1} -> $target"
                    }
                }
            }
        }
        assertTrue("Production Kotlin sources must only import approved packages, found: $disallowedImports", disallowedImports.isEmpty())
    }

    @Test
    fun packageNamespaceAndStoreAssetsEnforceTrademarkBoundaries() {
        val repoRoot = resolveRepoRoot()

        // 1. No legacy com.example Kotlin sources may remain across main, test, or androidTest
        val legacyFiles = listOf(
            "app/src/main/java/com/example",
            "app/src/test/java/com/example",
            "app/src/androidTest/java/com/example"
        ).flatMap { dir ->
            File(repoRoot, dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.map { it.relativeTo(repoRoot).path }.toList()
        }
        assertTrue("No Kotlin files may remain under com/example, found: $legacyFiles", legacyFiles.isEmpty())

        // 2. Every Kotlin source/test file must declare package org.suppressingfire.wemi... and never use a WeMo* filename prefix
        val allKotlinFiles = File(repoRoot, "app/src").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        val wrongPackageFiles = allKotlinFiles.filter { ktFile ->
            val pkgLine = ktFile.useLines { lines -> lines.firstOrNull { it.trimStart().startsWith("package ") } } ?: ""
            !pkgLine.trim().startsWith("package org.suppressingfire.wemi")
        }.map { it.relativeTo(repoRoot).path }
        assertTrue("Every Kotlin file must declare package org.suppressingfire.wemi..., found: $wrongPackageFiles", wrongPackageFiles.isEmpty())

        val wemoNamedFiles = allKotlinFiles.filter { it.name.startsWith("WeMo", ignoreCase = true) }.map { it.relativeTo(repoRoot).path }
        assertTrue("No Kotlin source/test files may use a WeMo* filename prefix, found: $wemoNamedFiles", wemoNamedFiles.isEmpty())

        // 3. Store-facing, manifest/backup/widget XML, screenshot generator & app resource files must not leak the "WeMo" trademark
        val trademarkRegex = Regex("\\bwemo\\b", RegexOption.IGNORE_CASE)
        val brandBoundaryFiles = listOf(
            "app/src/main/AndroidManifest.xml",
            "app/src/main/res/values/strings.xml",
            "app/src/main/res/xml/backup_rules.xml",
            "app/src/main/res/xml/data_extraction_rules.xml",
            "app/src/main/res/xml/wemi_switch_widget_info.xml",
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
            assertFalse(
                "Brand-boundary file $relPath must not contain case-insensitive 'wemo' trademark",
                trademarkRegex.containsMatchIn(file.readText())
            )
        }
        val resXmlHits = File(repoRoot, "app/src/main/res")
            .walkTopDown()
            .filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
            .filter { it.readText().contains("wemo", ignoreCase = true) }
            .map { it.relativeTo(repoRoot).path }
            .toList()
        assertTrue("All app/src/main/res XML files must not contain case-insensitive 'wemo' references, found: $resXmlHits", resXmlHits.isEmpty())

        // Verify Play Store Data Safety & Privacy Policy parity: exact 4 LAN permissions + structural XML Auto-Backup exclusions
        val declaredPermissions = Regex("""<uses-permission\s+android:name="([^"]+)"""")
            .findAll(File(repoRoot, "app/src/main/AndroidManifest.xml").readText())
            .map { it.groupValues[1] }.toSet()
        assertEquals(
            "AndroidManifest.xml permissions must match Play Store Data Safety & Privacy Policy disclosures",
            setOf(
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.ACCESS_WIFI_STATE",
                "android.permission.CHANGE_WIFI_MULTICAST_STATE"
            ),
            declaredPermissions
        )
        val requiredBackupExclusions = setOf("wemi-telemetry-latest.txt", "hardware_rules_backups")
        val dbf = DocumentBuilderFactory.newInstance()
        val backupDoc = dbf.newDocumentBuilder().parse(File(repoRoot, "app/src/main/res/xml/backup_rules.xml"))
        assertEquals("full-backup-content", backupDoc.documentElement.tagName)
        val backupExcluded = excludedFilePaths(backupDoc.documentElement)
        assertTrue("backup_rules.xml <full-backup-content> must exclude $requiredBackupExclusions, found: $backupExcluded", backupExcluded.containsAll(requiredBackupExclusions))

        val extractDoc = dbf.newDocumentBuilder().parse(File(repoRoot, "app/src/main/res/xml/data_extraction_rules.xml"))
        for (sectionTag in listOf("cloud-backup", "device-transfer")) {
            val sectionEl = extractDoc.getElementsByTagName(sectionTag).item(0) as? Element
            assertNotNull("data_extraction_rules.xml must declare <$sectionTag>", sectionEl)
            val excluded = excludedFilePaths(sectionEl!!)
            assertTrue("data_extraction_rules.xml <$sectionTag> must exclude $requiredBackupExclusions, found: $excluded", excluded.containsAll(requiredBackupExclusions))
        }

        // 4. Modular UI, widget & user-facing rules files must not expose user-visible "WeMo" literals (single-line or multi-line """...""")
        val allowedUiProtocolOrTagTokens = setOf(
            "WeMo_WW_2.00.11532.PVT",
            "wemo_setup_ap_detected_banner",
            "wemo_setup_ap_banner",
            "more_provision_wemo_ap_button"
        )
        val userFacingKotlinFiles = listOf(
            File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/MainActivity.kt"),
            File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/data/DemoHardwareRulesStore.kt"),
            File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/data/HardwareRulesCoordinator.kt"),
            File(repoRoot, "app/src/main/java/org/suppressingfire/wemi/network/HardwareRulesModels.kt")
        ) + listOf("app/src/main/java/org/suppressingfire/wemi/ui", "app/src/main/java/org/suppressingfire/wemi/widget")
            .flatMap { dirRel -> File(repoRoot, dirRel).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
        val kotlinStringRegex = Regex("/\\*[\\s\\S]*?\\*/|//[^\\n]*|\"\"\"([\\s\\S]*?)\"\"\"|\"([^\"\\\\\\n]*(?:\\\\.[^\"\\\\\\n]*)*)\"")
        val disallowedUiStrings = mutableListOf<String>()
        for (uiFile in userFacingKotlinFiles) {
            val src = uiFile.readText()
            kotlinStringRegex.findAll(src).forEach { match ->
                val literal = match.groups[1]?.value ?: match.groups[2]?.value ?: return@forEach
                if (literal.contains("wemo", ignoreCase = true) && literal !in allowedUiProtocolOrTagTokens) {
                    val lineNum = src.substring(0, match.range.first).count { it == '\n' } + 1
                    disallowedUiStrings += "${uiFile.relativeTo(repoRoot).path}:$lineNum -> \"$literal\""
                }
            }
        }
        assertTrue("User-facing UI, widget & rules files must not expose 'WeMo' brand literals, found: $disallowedUiStrings", disallowedUiStrings.isEmpty())

        // 5. Google Play Console listing character limits & graphic asset inventory
        val releaseNotesText = File(repoRoot, "play_store_assets/RELEASE_NOTES.txt").readText().trim()
        assertTrue("play_store_assets/RELEASE_NOTES.txt must be <= 500 characters (found ${releaseNotesText.length})", releaseNotesText.length <= 500)

        val storeListingText = File(repoRoot, "play_store_assets/STORE_LISTING.md").readText()
        val appTitleMatch = Regex("\\*\\*App Title.*?:\\*\\*\\s*`([^`]+)`").find(storeListingText)
        assertNotNull("play_store_assets/STORE_LISTING.md must declare App Title", appTitleMatch)
        val appTitle = appTitleMatch!!.groupValues[1].trim()
        assertTrue("Google Play App Title must be <= 30 characters (found ${appTitle.length}: '$appTitle')", appTitle.length <= 30)
        val shortDescMatch = Regex("## Short Description[^\\n]*\\n([^\\n]+)").find(storeListingText)
        assertNotNull("play_store_assets/STORE_LISTING.md must declare Short Description", shortDescMatch)
        val shortDesc = shortDescMatch!!.groupValues[1].trim()
        assertTrue("Google Play Short Description must be <= 80 characters (found ${shortDesc.length}: '$shortDesc')", shortDesc.length <= 80)
        val fullDescMatch = Regex("""(?ms)^##\s+Full Description[^\n]*\n(.*?)(?=^##\s+|\z)""").find(storeListingText)
        assertNotNull("play_store_assets/STORE_LISTING.md must declare Full Description", fullDescMatch)
        val fullDescPlain = fullDescMatch!!.groupValues[1].replace("\r\n", "\n").lines().joinToString("\n") { rawLine ->
            rawLine.trimEnd()
                .replace(Regex("^#{1,6}\\s+"), "")
                .replace(Regex("^\\s*[-*]\\s+"), "• ")
                .replace(Regex("\\[([^\\]]+)]\\([^)]+\\)"), "$1")
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("`([^`]+)`"), "$1")
                .trimEnd()
        }.trim().replace(Regex("\n{3,}"), "\n\n")
        assertTrue("Google Play Full Description must be 1..4000 characters (found ${fullDescPlain.length})", fullDescPlain.length in 1..4000)

        val dataSafetyText = File(repoRoot, "play_store_assets/DATA_SAFETY_GUIDE.md").readText()
        val declaredPlayAssets = Regex("`(play_store_assets/[^`]+\\.png)`").findAll(dataSafetyText).map { it.groupValues[1] }.distinct().toList()
        assertTrue("DATA_SAFETY_GUIDE.md must inventory at least 19 Play Store PNG assets (found ${declaredPlayAssets.size})", declaredPlayAssets.size >= 19)
        val missingPlayAssets = declaredPlayAssets.filterNot { File(repoRoot, it).isFile }
        assertTrue("All Play Store PNG graphic assets listed in DATA_SAFETY_GUIDE.md must exist, missing: $missingPlayAssets", missingPlayAssets.isEmpty())

        // 6. Active feature User Guides (USER_GUIDE.md, docs/README.md & docs/user-guide/*.md excluding release-notes.md) must not quote removed "WeMo" UI labels
        val staleUiLabels = listOf(
            "Provision New WeMo AP", "New WeMo Switch Rule", "All WeMi/WeMo Devices",
            "Setup Factory-Reset WeMo AP", "Belkin WeMo Setup AP Detected", "classic wemo", "Sync Name to WeMo Hardware"
        )
        val userGuideDir = File(repoRoot, "docs/user-guide")
        assertTrue("docs/user-guide directory must exist", userGuideDir.isDirectory)
        val rootUserGuide = File(repoRoot, "USER_GUIDE.md")
        val docsReadme = File(repoRoot, "docs/README.md")
        assertTrue("USER_GUIDE.md must exist", rootUserGuide.isFile)
        assertTrue("docs/README.md must exist", docsReadme.isFile)
        val activeGuides = listOf(rootUserGuide, docsReadme) +
            userGuideDir.listFiles().orEmpty().filter { it.isFile && it.extension == "md" && it.name != "release-notes.md" }
        val staleGuideHits = mutableListOf<String>()
        activeGuides.forEach { guideFile ->
            val guideText = guideFile.readText()
            for (label in staleUiLabels) {
                if (guideText.contains(label, ignoreCase = true)) {
                    staleGuideHits += "${guideFile.relativeTo(repoRoot).path} -> \"$label\""
                }
            }
        }
        assertTrue("Active User Guide docs must match neutral current UI labels without stale 'WeMo' UI strings, found: $staleGuideHits", staleGuideHits.isEmpty())
    }

    @Test
    fun moreScreenDisplaysInAppMitLicenseAndThirdPartyNotices() {
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("nav_tab_more").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("more_screen_list").performScrollToNode(hasTestTag("open_source_licenses_card"))
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("open_source_licenses_card").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Source License").assertIsDisplayed()
        composeTestRule.onNodeWithText("MIT License • Copyright (c) 2026 Michael R. Head & Contributors").assertIsDisplayed()

        composeTestRule.onNodeWithTag("toggle_license_details_button").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("mit_license_text_surface").assertExists()
        composeTestRule.onNodeWithTag("third_party_notices_surface").assertExists()
        composeTestRule.onNodeWithText("Permission is hereby granted, free of charge", substring = true).assertExists()
        composeTestRule.onNodeWithText("Third-Party Open Source Notices:", substring = true).assertExists()
        composeTestRule.onNodeWithText("Apache License 2.0", substring = true).assertExists()
        composeTestRule.onNodeWithText("not affiliated with or endorsed by Belkin International, Inc.", substring = true).assertExists()
        composeTestRule.onNodeWithText("OkHttp", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("pywemo", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("SIL Open Font License", substring = true).assertDoesNotExist()
    }
}
