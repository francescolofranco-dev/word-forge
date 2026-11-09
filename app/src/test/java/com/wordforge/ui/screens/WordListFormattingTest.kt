package com.wordforge.ui.screens

import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class WordListFormattingTest {
    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-07-20T10:00:00Z").toEpochMilli()

    @Test
    fun compactDueUsesTwoUnitsAndReadyLanguage() {
        assertEquals("ready", formatCompactDue(now, now))
        assertEquals("2d 3h", formatCompactDue(now + hours(51), now))
        assertEquals("5m 04s", formatCompactDue(now + 304_000L, now))
    }

    @Test
    fun upcomingWordsAreGroupedAndSortedByLocalDay() {
        val words = listOf(
            word("later", "2026-08-02T10:00:00Z"),
            word("week", "2026-07-24T10:00:00Z"),
            word("today", "2026-07-20T18:00:00Z"),
            word("tomorrow", "2026-07-21T08:00:00Z"),
        )

        val grouped = groupUpcomingWords(words, now, zone)

        assertEquals(listOf("Later today", "Tomorrow", "This week", "Later"), grouped.keys.toList())
        assertEquals("today", grouped.getValue("Later today").single().word)
        assertEquals("tomorrow", grouped.getValue("Tomorrow").single().word)
    }

    @Test
    fun verbLabelContainsOnlyTheFullTense() {
        val verb = word("tener", "2026-07-20T18:00:00Z").copy(
            meaning = "legacy meaning",
            itemType = LearningItemType.VERB_CONJUGATION,
            verbConjugation = VerbConjugation(
                tense = "pretérito perfecto simple",
                yo = "tuve",
                tu = "tuviste",
                elEllaUsted = "tuvo",
                nosotros = "tuvimos",
                vosotros = "tuvisteis",
                ellosEllasUstedes = "tuvieron",
            ),
        )

        assertEquals("PRETÉRITO PERFECTO SIMPLE", listTypeLabel(verb))
        assertNull(listMeaning(verb))
        assertNull(listTypeLabel(word("tiempo", "2026-07-20T18:00:00Z")))
        assertEquals("meaning", listMeaning(word("tiempo", "2026-07-20T18:00:00Z")))
        assertEquals("meaning", listMeaning(word("tiempo", "2026-07-20T18:00:00Z"), now))
        assertNull(listMeaning(word("tiempo", "2026-07-20T09:00:00Z"), now))
    }

    private fun word(text: String, due: String) = Word(
        id = text,
        word = text,
        meaning = "meaning",
        nextPromptAt = Instant.parse(due).toEpochMilli(),
        createdAt = now,
    )

    private fun hours(value: Long) = value * 60 * 60 * 1000L
}
