package org.sableos.titan2.vault.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultModelTest {
    @Test fun sanitizeKeepsOneSafeSegment() {
        assertEquals("a_b_c.jpg", FileNames.sanitize("a/b\\c.jpg"))
        assertEquals("passwd", FileNames.sanitize("../passwd").removePrefix("_"))
        assertEquals("hidden", FileNames.sanitize(".hidden"))
        assertEquals("file", FileNames.sanitize(null))
        assertEquals("file", FileNames.sanitize(" .. "))
        assertFalse(FileNames.sanitize("x\u0000y").contains('\u0000'))
    }

    @Test fun sanitizeBoundsLengthAndKeepsExtension() {
        val long = "a".repeat(300) + ".pdf"
        val s = FileNames.sanitize(long)
        assertEquals(120, s.length)
        assertTrue(s.endsWith(".pdf"))
    }

    @Test fun uniqueNeverOverwrites() {
        assertEquals("a.jpg", FileNames.unique("a.jpg", setOf("b.jpg")))
        assertEquals("a (2).jpg", FileNames.unique("a.jpg", setOf("a.jpg")))
        assertEquals("a (3).jpg", FileNames.unique("a.jpg", setOf("a.jpg", "a (2).jpg")))
        assertEquals("notes (2)", FileNames.unique("notes", setOf("notes")))
    }

    @Test fun sectionsCycleAndHaveLetters() {
        assertEquals(Section.Docs, Section.Photos.next(1))
        assertEquals(Section.Recordings, Section.Photos.next(-1))
        assertEquals(listOf('p', 'd', 'r'), Section.entries.map { it.key })
        assertEquals(Section.Photos, Section.fromId("nope"))
        assertEquals(listOf("audio/*"), Section.Recordings.mimeTypes)
    }

    @Test fun keysMatchTheHint() {
        val k = VaultKeys
        assertEquals(VaultAction.Import, k.action(VaultKeys.Key.Letter('I')))
        assertEquals(VaultAction.Export, k.action(VaultKeys.Key.Letter('e')))
        assertEquals(VaultAction.Lock, k.action(VaultKeys.Key.Letter('l')))
        assertEquals(VaultAction.Delete, k.action(VaultKeys.Key.Letter('x')))
        assertEquals(VaultAction.Delete, k.action(VaultKeys.Key.Delete))
        assertEquals(VaultAction.Open, k.action(VaultKeys.Key.Enter))
        assertEquals(VaultAction.Recordings, k.action(VaultKeys.Key.Letter('r')))
        assertEquals(VaultAction.NextSection, k.action(VaultKeys.Key.Right))
        assertEquals(VaultAction.Leave, k.action(VaultKeys.Key.Back))
        assertNull(k.action(VaultKeys.Key.Letter('q')))
        assertNull(k.action(VaultKeys.Key.Other))
    }

    @Test fun confirmationNeedsAnExplicitYes() {
        assertEquals(ConfirmAction.Confirm, VaultKeys.confirm(VaultKeys.Key.Enter))
        assertEquals(ConfirmAction.Confirm, VaultKeys.confirm(VaultKeys.Key.Letter('Y')))
        assertEquals(ConfirmAction.Cancel, VaultKeys.confirm(VaultKeys.Key.Back))
        assertEquals(ConfirmAction.Cancel, VaultKeys.confirm(VaultKeys.Key.Letter('n')))
        // The keys that started the action never confirm it.
        assertNull(VaultKeys.confirm(VaultKeys.Key.Letter('e')))
        assertNull(VaultKeys.confirm(VaultKeys.Key.Letter('x')))
        assertNull(VaultKeys.confirm(VaultKeys.Key.Delete))
        assertNull(VaultKeys.confirm(VaultKeys.Key.Down))
    }

    @Test fun confirmationsSayWhatLeavesTheVault() {
        val item = VaultItem("scan.pdf", 10, 0)
        assertTrue(Confirmation.export(item).detail.contains("no longer protected"))
        assertTrue(Confirmation.open(item).detail.contains("this one file"))
        assertTrue(Confirmation.delete(item).detail.contains("cannot be recovered"))
        assertTrue(Confirmation.deleteOriginals(2).title.contains("copies"))
    }

    @Test fun importIsReportedAsCopy() {
        val o = ImportOutcome(listOf("a.jpg"), listOf("b.jpg (no access)"))
        assertEquals("Imported 1 file(s) as copies. Could not import: b.jpg (no access).", o.message())
    }

    @Test fun sizes() {
        assertEquals("512 B", VaultText.size(512))
        assertEquals("2 KB", VaultText.size(2048))
        assertEquals("3 MB", VaultText.size(3L * 1024 * 1024))
    }
}
