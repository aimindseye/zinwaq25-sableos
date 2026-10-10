package com.android.contacts.sable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure tests for the letter navigation carried in the phone-contacts framework patches (0701/0751). */
class SableAlphabetIndexTest {
    private val none = SableAlphabetIndex.NO_POSITION

    @Test fun sectionTitlesFileUnderTheirBaseLetter() {
        assertEquals('A'.code, SableAlphabetIndex.bucketKey("A"))
        assertEquals('A'.code, SableAlphabetIndex.bucketKey(" a "))
        assertEquals('A'.code, SableAlphabetIndex.bucketKey("Ä"))
        assertEquals('E'.code, SableAlphabetIndex.bucketKey("é"))
        assertEquals(0x0411, SableAlphabetIndex.bucketKey("б"))
        assertEquals(0, SableAlphabetIndex.bucketKey("★"))
        assertEquals(0, SableAlphabetIndex.bucketKey("#"))
        assertEquals(0, SableAlphabetIndex.bucketKey("3"))
        assertEquals(0, SableAlphabetIndex.bucketKey("…"))
        assertEquals(0, SableAlphabetIndex.bucketKey(""))
        assertEquals(0, SableAlphabetIndex.bucketKey(null))
        assertEquals('Q'.code, SableAlphabetIndex.keyOf('q'.code))
        assertEquals(0, SableAlphabetIndex.keyOf('/'.code))
        assertEquals(0, SableAlphabetIndex.keyOf(-5))
    }

    // "+" add-contact row at 0, then A x3 (1..3), B x0, C x2 (4..5), # x1 (6).
    private val dialer = SableAlphabetIndex.fromCounts(
        arrayOf("A", "B", "C", "#"), intArrayOf(3, 0, 2, 1), 1)

    @Test fun firstPositionUsesTheExistingCountsAndOffset() {
        assertEquals(4, dialer.sectionCount())
        assertEquals(1, dialer.firstPosition('a'.code))
        assertEquals(4, dialer.firstPosition('C'.code))
        assertEquals(none, dialer.firstPosition('B'.code))
        assertEquals(none, dialer.firstPosition('Z'.code))
        assertEquals(none, dialer.firstPosition('#'.code))
        assertTrue(dialer.hasLetter('A'.code))
        assertFalse(dialer.hasLetter('B'.code))
    }

    @Test fun repeatingALetterCyclesThroughItsRowsAndWraps() {
        assertEquals(1, dialer.nextJump('A'.code, RecyclerViewNoPosition))
        assertEquals(1, dialer.nextJump('A'.code, 5))
        assertEquals(2, dialer.nextJump('A'.code, 1))
        assertEquals(3, dialer.nextJump('A'.code, 2))
        assertEquals(1, dialer.nextJump('A'.code, 3))
        assertEquals(5, dialer.nextJump('c'.code, 4))
        assertEquals(4, dialer.nextJump('c'.code, 5))
        assertEquals(none, dialer.nextJump('B'.code, 1))
        assertEquals(none, dialer.nextJump('5'.code, 1))
        assertTrue(dialer.isInBucket('A'.code, 3))
        assertFalse(dialer.isInBucket('A'.code, 4))
    }

    @Test fun aLetterSplitOverTwoSectionsIsOneBucket() {
        // Some locales give "A" and "Ä" separate sections; both file under A.
        val index = SableAlphabetIndex.fromCounts(arrayOf("A", "Ä", "B"), intArrayOf(2, 1, 1), 0)
        assertEquals(0, index.nextJump('A'.code, -1))
        assertEquals(1, index.nextJump('A'.code, 0))
        assertEquals(2, index.nextJump('A'.code, 1))
        assertEquals(0, index.nextJump('A'.code, 2))
        assertEquals(3, index.nextJump('B'.code, 0))
    }

    @Test fun fromPositionsMatchesASectionIndexerWithFavorites() {
        // Contacts: list header view (1) + favorites "" section (2 rows), then A (2), C (1).
        val index = SableAlphabetIndex.fromPositions(arrayOf("", "A", "C"), intArrayOf(0, 2, 4), 5, 1)
        assertEquals(3, index.firstPosition('A'.code))
        assertEquals(4, index.nextJump('A'.code, 3))
        assertEquals(3, index.nextJump('A'.code, 4))
        assertEquals(5, index.firstPosition('C'.code))
        assertEquals(5, index.nextJump('C'.code, 5))
        assertEquals(none, index.firstPosition('F'.code))
    }

    @Test fun malformedInputGivesAnEmptyIndex() {
        assertEquals(0, SableAlphabetIndex.fromCounts(null, intArrayOf(1), 0).sectionCount())
        assertEquals(0, SableAlphabetIndex.fromPositions(arrayOf<Any?>("A"), null, 1, 0).sectionCount())
        val short = SableAlphabetIndex.fromCounts(arrayOf("A", "B"), intArrayOf(1), 0)
        assertEquals(1, short.sectionCount())
        val negative = SableAlphabetIndex.fromCounts(arrayOf("A"), intArrayOf(-4), -3)
        assertEquals(none, negative.firstPosition('A'.code))
        assertEquals(none, SableAlphabetIndex.empty().nextJump('A'.code, 0))
    }

    @Test fun theRailHasTheTwentySixLatinLetters() {
        assertEquals(26, SableAlphabetIndex.RAIL_LETTERS.length)
        assertEquals('A', SableAlphabetIndex.RAIL_LETTERS.first())
        assertEquals('Z', SableAlphabetIndex.RAIL_LETTERS.last())
    }

    private companion object {
        const val RecyclerViewNoPosition = -1
    }
}
