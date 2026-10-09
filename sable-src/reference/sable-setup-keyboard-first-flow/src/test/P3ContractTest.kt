package org.sableos.reference.setupflow

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level negatives: this is a specification module, not a Setup Wizard, a package or a fork. */
class P3ContractTest {
    private fun moduleDir(): File? {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            val r = File(d, "reference/sable-setup-keyboard-first-flow")
            if (r.isDirectory) return r
            d = d.parentFile
        }
        return null
    }

    private fun files(): List<File> = moduleDir()?.walkTopDown()?.filter {
        it.isFile
    }?.toList().orEmpty()

    private fun main(): List<File> = files().filter {
        it.path.contains("/src/main/") &&
            it.name.endsWith(".kt")
    }

    @Test fun theModuleExistsAndHasMainSources() {
        assertTrue(main().size >= 3)
    }

    @Test fun noManifestGradleBuildOrHomeCategory() {
        val home = "android.intent.category." + "HOME"
        files().forEach {
            assertFalse(
                it.name,
                it.name == "AndroidManifest.xml" || it.name.endsWith(".gradle.kts")
            )
            assertFalse(it.name, it.readText().contains(home))
        }
    }

    @Test fun mainSourceIsFrameworkFreeAndDeviceFree() {
        val banned = listOf(
            "import android.",
            "Build." + "MODEL",
            "Build." + "DEVICE",
            "scan" + "Code",
            "SystemProperties"
        )
        main().forEach { f ->
            banned.forEach { assertFalse("${f.name}: $it", f.readText().contains(it)) }
        }
    }

    @Test fun mainSourceNamesNoPackageInAStringLiteral() {
        val pkg = Regex("\"[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}\"")
        main().forEach { f ->
            assertEquals(
                f.name,
                emptyList<String>(),
                pkg.findAll(f.readText()).map {
                    it.value
                }.toList()
            )
        }
    }

    @Test fun itDoesNotImplementOrRenameASetupWizard() {
        val wizardPackage = "app.grapheneos." + "setupwizard"
        files().forEach {
            assertFalse(it.name, it.readText().contains(wizardPackage))
            assertFalse(
                it.name,
                Regex("class\\s+\\w*SetupWizard\\w*\\s*[:({]").containsMatchIn(it.readText())
            )
        }
    }

    @Test fun simStepDoesNotMentionRadioInternals() {
        val text = main().joinToString("\n") { it.readText() }
        listOf("IMS", "VoLTE", "APN", "CarrierConfig", "IWLAN").forEach {
            val simMessages = Regex("\"[^\"]*\\b$it\\b[^\"]*\"").findAll(text).map { m ->
                m.value
            }.toList()
            assertEquals(it, emptyList<String>(), simMessages)
        }
    }
}
