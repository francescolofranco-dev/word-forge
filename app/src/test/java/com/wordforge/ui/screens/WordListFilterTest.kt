package com.wordforge.ui.screens

import com.wordforge.data.LearningItemType
import com.wordforge.data.VerbConjugation
import com.wordforge.data.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordListFilterTest {
    private val hello = Word(word = "hola", meaning = "hello", nextPromptAt = 1, createdAt = 1)
    private val weather = hello.copy(id = "weather", word = "tiempo", meaning = "weather")
    private val verb = hello.copy(
        id = "verb",
        word = "tener",
        meaning = "obsolete imported meaning",
        itemType = LearningItemType.VERB_CONJUGATION,
        verbConjugation = VerbConjugation(
            tense = "pretérito perfecto simple",
            yo = "tuve", tu = "tuviste", elEllaUsted = "tuvo",
            nosotros = "tuvimos", vosotros = "tuvisteis", ellosEllasUstedes = "tuvieron",
        ),
    )
    private val words = listOf(weather, verb, hello)

    @Test
    fun blankSearchPreservesAllItemsAndTheirOrder() {
        assertEquals(words, filterWords(words, "  ", null))
        assertTrue(filterWords(emptyList(), "hola", null).isEmpty())
    }

    @Test
    fun searchesTermsAndMeaningsIgnoringCaseAndSurroundingWhitespace() {
        assertEquals(listOf(hello), filterWords(words, " HOLA ", null))
        assertEquals(listOf(weather), filterWords(words, "WEATH", null))
        assertTrue(filterWords(words, "missing", null).isEmpty())
    }

    @Test
    fun searchesVerbTensesAndConjugationsButNotRetiredMeanings() {
        assertEquals(listOf(verb), filterWords(words, "PRETÉRITO", null))
        assertEquals(listOf(verb), filterWords(words, "TUVIERON", null))
        assertTrue(filterWords(words, "obsolete", null).isEmpty())
    }

    @Test
    fun typeAndSearchAreAppliedTogether() {
        assertEquals(listOf(weather, hello), filterWords(words, "", LearningItemType.SIMPLE_WORD))
        assertEquals(listOf(verb), filterWords(words, "", LearningItemType.VERB_CONJUGATION))
        assertEquals(listOf(hello), filterWords(words, "hello", LearningItemType.SIMPLE_WORD))
        assertTrue(filterWords(words, "hello", LearningItemType.VERB_CONJUGATION).isEmpty())
    }
}
