package org.sableos.notes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.notes.core.NotesText
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/** The English lines in code and in `values/strings.xml` stay equal, and every translation fits them. */
class NotesTextTest {
    private val res = File("src/main/res")

    private fun strings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it) }
            .filter { it.attributes.getNamedItem("translatable")?.nodeValue != "false" }
            .associate { node ->
                val raw =
                    node.textContent.let {
                        if (it.length > 1 && it.startsWith('"') &&
                            it.endsWith('"')
                        ) {
                            it.drop(1).dropLast(1)
                        } else {
                            it
                        }
                    }
                node.attributes.getNamedItem("name").nodeValue to
                    raw
                        .replace("\\n", "\n")
                        .replace("\\'", "'")
                        .replace("\\\"", "\"")
                        .replace("\\@", "@")
                        .replace("\\\\", "\\")
            }
    }

    private fun placeholders(s: String) = Regex("%\\d+\\$[sd]").findAll(s).map { it.value }.toSortedSet()

    @Test
    fun englishResourcesMatchTheCode() {
        val english = strings(File(res, "values/strings.xml"))
        NotesText.EN.forEach { (key, text) -> assertEquals(key, text, english[key]) }
    }

    @Test
    fun translationsHaveEveryKeyAndTheSamePlaceholders() {
        res.listFiles { f -> f.name.startsWith("values-") && File(f, "strings.xml").isFile }.orEmpty().forEach { dir ->
            val tr = strings(File(dir, "strings.xml"))
            assertEquals(dir.name, NotesText.EN.keys, tr.keys.intersect(NotesText.EN.keys))
            NotesText.EN.forEach { (key, text) ->
                assertEquals("${dir.name}/$key", placeholders(text), placeholders(tr.getValue(key)))
            }
        }
    }

    @Test
    fun linesFillTheirArguments() {
        assertEquals("3 notes", NotesText.t("note_count", 3))
        assertEquals("Deleted “Milk”", NotesText.t("deleted", "Milk"))
    }
}
