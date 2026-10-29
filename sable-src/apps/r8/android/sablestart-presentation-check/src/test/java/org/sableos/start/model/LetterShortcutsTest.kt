package org.sableos.start.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hold-a-letter app shortcuts: one app per letter, one letter per app, damage tolerant storage. */
class LetterShortcutsTest {
    private val mail = "com.sable.mail/com.sable.mail.Main"
    private val notes = "com.sable.notes/.NotesActivity"

    @Test fun assignAndLookupIgnoreCase() {
        val s = LetterShortcuts.EMPTY.assign('M', mail)
        assertEquals(mail, s.componentFor('m'))
        assertEquals(mail, s.componentFor('M'))
        assertEquals('m', s.letterOf(mail))
        assertEquals(setOf('m'), s.letters)
    }

    @Test fun assigningMovesInBothDirections() {
        val s = LetterShortcuts.EMPTY.assign('m', mail).assign('n', mail)
        assertNull(s.componentFor('m'))
        assertEquals('n', s.letterOf(mail))
        val t = s.assign('n', notes)
        assertNull(t.letterOf(mail))
        assertEquals(notes, t.componentFor('n'))
    }

    @Test fun rejectsNonLettersAndBadComponents() {
        assertTrue(LetterShortcuts.EMPTY.assign('1', mail).letters.isEmpty())
        assertTrue(LetterShortcuts.EMPTY.assign('m', "no slash").letters.isEmpty())
        assertTrue(LetterShortcuts.EMPTY.assign('m', "a/b;c=d").letters.isEmpty())
    }

    @Test fun removeFreesTheLetter() {
        val s = LetterShortcuts.EMPTY.assign('m', mail).assign('n', notes).remove(mail)
        assertEquals(setOf('n'), s.letters)
    }

    @Test fun encodeDecodeRoundTripAndDamage() {
        val s = LetterShortcuts.EMPTY.assign('n', notes).assign('m', mail)
        assertEquals(s.encode(), LetterShortcuts.decode(s.encode()).encode())
        val damaged = LetterShortcuts.decode("zz=a/b;;x;q=bad;m=$mail;=;")
        assertEquals(setOf('m'), damaged.letters)
        assertTrue(LetterShortcuts.decode(null).letters.isEmpty())
    }
}
