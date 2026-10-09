package org.sableos.reference.typetofind

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Source-level negatives that keep Sable Start reference code from becoming a HOME app or a device fork. */
class P1ContractTest {
    private fun moduleDir(): File? {
        var d: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (d != null) {
            val r = File(d, "reference/sable-start-type-to-find")
            if (r.isDirectory) return r
            d = d.parentFile
        }
        return null
    }

    private fun files(): List<File> = moduleDir()?.walkTopDown()?.filter {
        it.isFile
    }?.toList().orEmpty()

    @Test fun referenceModuleHasNoManifestOrGradleBuild() {
        val names = files().map { it.name }
        assertFalse(names.contains("AndroidManifest.xml"))
        assertFalse(names.any { it.endsWith(".gradle.kts") || it.endsWith(".gradle") })
    }

    @Test fun noHomeCategoryAnywhereInTheReferenceModule() {
        val home = "android.intent.category." + "HOME"
        files().forEach { assertFalse(it.name, it.readText().contains(home)) }
    }

    @Test fun mainSourceUsesNoFrameworkOrDeviceIdentity() {
        val banned = listOf(
            "import android.",
            "Build." + "MODEL",
            "Build." + "DEVICE",
            "scan" + "Code",
            "SystemProperties"
        )
        files().filter { it.path.contains("/src/main/") && it.name.endsWith(".kt") }.forEach { f ->
            banned.forEach { assertFalse("${f.name}: $it", f.readText().contains(it)) }
        }
    }

    @Test fun mainSourceNamesNoPackageInAStringLiteral() {
        val pkgLiteral = Regex("\"[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*){2,}\"")
        files().filter { it.path.contains("/src/main/") && it.name.endsWith(".kt") }.forEach { f ->
            assertEquals(
                f.name,
                emptyList<String>(),
                pkgLiteral.findAll(f.readText()).map {
                    it.value
                }.toList()
            )
        }
    }
}
