package org.sableos.titan2.setup

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.sableos.titan2.setup.core.SetupText

/** The English lines in code and in `values/strings.xml` stay equal, and every translation fits them. */
class SetupTextTest {
    private val res = File("src/main/res")

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate {
                it.attributes.getNamedItem("name").nodeValue to
                    it.textContent.replace("\\'", "'").replace("\\\"", "\"")
            }
    }

    private fun placeholders(s: String) = Regex("%\\d+\\$[sd]").findAll(s).map {
        it.value
    }.toSortedSet()

    @Test fun englishResourcesMatchTheCode() {
        assertEquals(SetupText.EN, strings(File(res, "values/strings.xml")))
    }

    @Test fun translationsHaveEveryKeyAndTheSamePlaceholders() {
        val dirs = res.listFiles { f -> f.name.startsWith("values-") }.orEmpty()
        assertTrue("no translation found", dirs.isNotEmpty())
        dirs.forEach { dir ->
            val tr = strings(File(dir, "strings.xml"))
            assertEquals(dir.name, SetupText.EN.keys, tr.keys)
            tr.forEach { (key, text) ->
                assertEquals(
                    "${dir.name}/$key",
                    placeholders(SetupText.EN.getValue(key)),
                    placeholders(text)
                )
            }
        }
    }

    @Test fun linesFillTheirArguments() {
        assertEquals("3 of 4 confirmed", SetupText.t("summary_confirmed", 3, 4))
        assertEquals("In use: Gboard", SetupText.t("row_keyboard_in_use", "Gboard"))
    }
}
