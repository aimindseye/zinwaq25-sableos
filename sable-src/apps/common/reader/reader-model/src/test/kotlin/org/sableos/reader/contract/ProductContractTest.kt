package org.sableos.reader.contract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.reader.contract.ReaderTree.android
import org.sableos.reader.contract.ReaderTree.tools
import org.w3c.dom.Element
import org.w3c.dom.NodeList

private fun NodeList.elements(): List<Element> = (0 until length).map { item(it) as Element }

/**
 * Executable P5 product contract. These tests read the real source tree, so a regression in the manifest,
 * the build identity or the dependency set fails here before any Android build is attempted.
 */
class ProductContractTest {
    private val manifest = ReaderTree.manifest().documentElement

    private fun permissions(): List<Element> = manifest.getElementsByTagName("uses-permission").elements()

    /** Permissions that survive manifest merging: a `tools:node="remove"` declaration drops the permission. */
    private fun effectivePermissions(): Set<String> = permissions()
        .filter { it.tools("node") != "remove" }
        .map { it.android("name") }
        .toSet()

    @Test
    fun exactlyOneLauncherActivityExists() {
        val launchers = manifest.getElementsByTagName("activity").elements().filter { activity ->
            activity.getElementsByTagName("intent-filter").elements().any { filter ->
                val actions = filter.getElementsByTagName("action").elements().map { it.android("name") }
                val categories = filter.getElementsByTagName("category").elements().map { it.android("name") }
                "android.intent.action.MAIN" in actions && "android.intent.category.LAUNCHER" in categories
            }
        }
        assertEquals(1, launchers.size)
    }

    @Test
    fun internetAndCameraPermissionsAreAbsent() {
        val effective = effectivePermissions()
        assertFalse("INTERNET must be absent", "android.permission.INTERNET" in effective)
        assertFalse("CAMERA must be absent", "android.permission.CAMERA" in effective)
    }

    @Test
    fun manifestActivelyRemovesLibraryContributedNetworkAndCameraPermissions() {
        val removed = permissions().filter { it.tools("node") == "remove" }.map { it.android("name") }.toSet()
        assertTrue("android.permission.INTERNET" in removed)
        assertTrue("android.permission.CAMERA" in removed)
    }

    @Test
    fun noTextReaderComponentsOrIntentsExist() {
        val text = ReaderTree.text("leisure/src/main/AndroidManifest.xml")
        listOf(
            "SableTextActivity",
            "SableOcrActivity",
            "android.intent.action.PROCESS_TEXT",
            "android.intent.action.SEND",
            "text/plain",
        ).forEach { assertFalse("TextReader surface present: $it", text.contains(it)) }
    }

    @Test
    fun cleartextTrafficIsDisabledAndNothingIsExportedByAccident() {
        val application = manifest.getElementsByTagName("application").elements().single()
        assertEquals("false", application.android("usesCleartextTraffic"))
        assertEquals("false", application.android("allowBackup"))
        val exported = manifest.getElementsByTagName("activity").elements().filter { it.android("exported") == "true" }
        assertEquals(1, exported.size)
    }

    @Test
    fun productIdentityIsSableReader() {
        val gradle = ReaderTree.text("leisure/build.gradle.kts")
        assertTrue(gradle.contains("applicationId = \"org.sableos.reader\""))
        assertFalse(gradle.contains("applicationIdSuffix"))
        assertTrue(gradle.contains("archivesName.set(\"SableReader\")"))
        assertEquals("Sable Reader", Regex("<string name=\"app_name\">([^<]*)</string>")
            .find(ReaderTree.text("leisure/src/main/res/values/strings.xml"))?.groupValues?.get(1))
        val application = manifest.getElementsByTagName("application").elements().single()
        assertEquals("@string/app_name", application.android("label"))
    }

    @Test
    fun noSigningMaterialOrVaachakBuildIdentityRemains() {
        val gradle = ReaderTree.gradleFiles().joinToString("\n") { ReaderTree.text(ReaderTree.rel(it)) }
        listOf("signingConfig", "keystore", "vaachak-key.jks", "VAACHAK_KEY", "sonarqube", "sonar.", "LeisureVaachak")
            .forEach { assertFalse("build identity leftover: $it", gradle.contains(it)) }
        assertTrue(ReaderTree.root.walkTopDown().none { it.extension in setOf("jks", "keystore", "pk8", "p12") })
    }

    @Test
    fun noUserVisibleVaachakBrandingInResourcesOrManifest() {
        val resources = ReaderTree.root.resolve("leisure/src/main/res").walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .joinToString("\n") { it.readText() }
        val visible = resources + ReaderTree.text("leisure/src/main/AndroidManifest.xml")
        assertFalse(visible.contains("Leisure Vaachak"))
        assertFalse(Regex("android:label=\"[^\"]*[Vv]aachak").containsMatchIn(visible))
    }

    @Test
    fun noUserVisibleVaachakStringLiteralsInKotlinUi() {
        val offenders = ReaderTree.kotlinSources()
            .filter { "/src/main/" in it.invariantSeparatorsPath }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { i, line ->
                    val code = line.substringBefore("//")
                    val inString = Regex("\"[^\"]*[Vv]aachak[^\"]*\"").containsMatchIn(code)
                    val trimmed = code.trimStart()
                    val isPackageOrImport = trimmed.startsWith("import ") || trimmed.startsWith("package ")
                    val attribution = code.contains("Based on Vaachak Mobile")
                    if (inString && !isPackageOrImport && !attribution) "${ReaderTree.rel(file)}:${i + 1}" else null
                }
            }
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun noDeviceIdentityBranching() {
        val identityFields = "MODEL|DEVICE|BRAND|MANUFACTURER|HARDWARE|PRODUCT|BOARD|FINGERPRINT|ID|SERIAL"
        val banned = Regex("""Build\.($identityFields)\b""")
        val offenders = ReaderTree.kotlinSources().filter { banned.containsMatchIn(it.readText()) }.map(ReaderTree::rel)
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun retiredRemoteStackIsAbsentFromSourcesAndBuildFiles() {
        val retired = listOf(
            "retrofit", "okhttp", "io.ktor", "ktor-client", "generativeai", "generative-ai",
            "readium-opds", "readium-lcp",
            "readium.opds", "readium.lcp", "leakcanary", "commons-compress", "CloudflareAi", "OpdsRepository",
        )
        val haystack = (ReaderTree.kotlinSources() + ReaderTree.gradleFiles())
            .filterNot { it.name == "ProductContractTest.kt" }
            .joinToString("\n") { it.readText() }
        retired.forEach { assertFalse("retired dependency or symbol present: $it", haystack.contains(it)) }
    }

    @Test
    fun pinnedDependencyVersionsAreUnchanged() {
        val toml = ReaderTree.text("gradle/libs.versions.toml")
        fun v(name: String) = Regex("(?m)^$name\\s*=\\s*\"([^\"]+)\"").find(toml)?.groupValues?.get(1)
        assertEquals("3.1.2", v("readium"))
        assertEquals("2.8.4", v("room"))
        assertEquals("1.2.0", v("datastore"))
        assertEquals("2.57.2", v("hilt"))
        assertEquals("2.7.0", v("coil"))
    }

    @Test
    fun destructiveRoomMigrationIsForbiddenEverywhere() {
        val offenders = ReaderTree.kotlinSources()
            .filter { it.name != "ProductContractTest.kt" }
            .filter { Regex("fallbackToDestructiveMigration").containsMatchIn(it.readText()) }
            .map(ReaderTree::rel)
        assertEquals(emptyList<String>(), offenders)
    }

    @Test
    fun gradleDependencyVerificationIsConfiguredWithSha256() {
        val xml = ReaderTree.text("gradle/verification-metadata.xml")
        assertTrue(xml.contains("<verify-metadata>true</verify-metadata>"))
        assertTrue(xml.contains("<verify-signatures>false</verify-signatures>"))
        assertFalse("pgp-only verification is not allowed", Regex("<pgp\\b").containsMatchIn(xml))
    }

    @Test
    fun noAccountLoginOrCloudSyncCodeIsReachable() {
        val names = ReaderTree.kotlinSources().map { it.name }
        listOf("LoginScreen.kt", "LoginViewModel.kt", "SyncRepository.kt", "SyncApi.kt", "AiRepository.kt",
            "AiBottomSheet.kt", "AiConfigScreen.kt", "SyncSettingsScreen.kt").forEach {
            assertFalse("retired feature file present: $it", it in names)
        }
        val settings = ReaderTree.text(
            "core/src/androidMain/kotlin/org/vaachak/reader/core/data/repository/SettingsRepository.kt",
        )
        listOf("GEMINI_KEY", "CF_TOKEN", "SYNC_PASSWORD", "SYNC_USERNAME")
            .forEach { assertFalse(it, settings.contains(it)) }
    }
}
