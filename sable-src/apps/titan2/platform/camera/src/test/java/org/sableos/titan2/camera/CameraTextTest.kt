package org.sableos.titan2.camera

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.sableos.titan2.camera.core.CameraText

/** The English lines in code and in `values/strings.xml` stay equal, and every translation fits them. */
class CameraTextTest {
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

    private fun placeholders(s: String) = Regex("%\\d+\\$[sd]").findAll(s).map {
        it.value
    }.toSortedSet()

    @Test
    fun englishResourcesMatchTheCode() {
        val english = strings(File(res, "values/strings.xml"))
        CameraText.EN.forEach { (key, text) -> assertEquals(key, text, english[key]) }
    }

    @Test
    fun translationsHaveEveryKeyAndTheSamePlaceholders() {
        res.listFiles { f ->
            f.name.startsWith("values-") && File(f, "strings.xml").isFile
        }.orEmpty().forEach { dir ->
            val tr = strings(File(dir, "strings.xml"))
            assertEquals(dir.name, CameraText.EN.keys, tr.keys.intersect(CameraText.EN.keys))
            CameraText.EN.forEach { (key, text) ->
                assertEquals("${dir.name}/$key", placeholders(text), placeholders(tr.getValue(key)))
            }
        }
    }
}
