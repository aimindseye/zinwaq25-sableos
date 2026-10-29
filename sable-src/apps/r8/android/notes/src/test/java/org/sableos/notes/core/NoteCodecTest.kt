package org.sableos.notes.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteCodecTest {
    @Test
    fun roundTripsTextVerbatim() {
        val note = Note("id", "Title\n\n- [ ] a\n\nend\n", 10, 20, NoteLabel.BLUE)
        assertEquals(note, NoteCodec.decode("id", NoteCodec.encode(note)))
    }

    @Test
    fun toleratesMissingFieldsAndRejectsOtherFiles() {
        assertEquals(NoteLabel.NONE, NoteCodec.decode("id", "sable-note 1\ncreated 5\n\nx")?.label)
        assertEquals(5L, NoteCodec.decode("id", "sable-note 1\ncreated 5\nlabel NOPE\n\nx")?.updatedAt)
        assertNull(NoteCodec.decode("id", "hello\n\nworld"))
        assertNull(NoteCodec.decode("id", "sable-note 1\nupdated 5\n\nx"))
        assertNull(NoteCodec.decode("id", "sable-note 1"))
    }

    @Test
    fun titleAndPreviewSkipMarkers() {
        assertEquals("Shopping", NoteText.title("\n# Shopping\n- [ ] milk\n- [x] eggs"))
        assertEquals("milk · eggs", NoteText.preview("# Shopping\n- [ ] milk\n\n- [x] eggs"))
        assertEquals(NoteText.UNTITLED, NoteText.title("  \n"))
        assertEquals(NoteLabel.NONE, NoteLabel.GREEN.next())
    }
}
