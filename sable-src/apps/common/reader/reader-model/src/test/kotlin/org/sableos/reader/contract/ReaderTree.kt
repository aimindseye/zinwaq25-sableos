package org.sableos.reader.contract

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/** Locates the Reader source tree relative to this module (Gradle runs tests with the module as working directory). */
internal object ReaderTree {
    val root: File = File("..").canonicalFile.also {
        check(File(it, "settings.gradle.kts").isFile) { "not the Reader root: $it" }
    }

    fun file(path: String): File = File(root, path).also { check(it.isFile) { "missing $path" } }

    fun text(path: String): String = file(path).readText()

    fun kotlinSources(): List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" }
        .filter { it.isFile && it.extension == "kt" }
        .toList()

    fun gradleFiles(): List<File> = root.walkTopDown()
        .onEnter { it.name != "build" && it.name != ".gradle" }
        .filter { it.isFile && (it.name.endsWith(".gradle.kts") || it.name.endsWith(".toml")) }
        .toList()

    fun rel(f: File): String = f.relativeTo(root).path

    const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    const val TOOLS_NS = "http://schemas.android.com/tools"

    fun manifest(): Document {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        return factory.newDocumentBuilder().parse(file("leisure/src/main/AndroidManifest.xml"))
    }

    fun Element.android(name: String): String = getAttributeNS(ANDROID_NS, name)

    fun Element.tools(name: String): String = getAttributeNS(TOOLS_NS, name)
}
