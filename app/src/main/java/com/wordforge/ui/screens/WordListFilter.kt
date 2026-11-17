package com.wordforge.ui.screens

import com.wordforge.data.LearningItemType
import com.wordforge.data.Word

/** Filters content without changing the collection's order or review schedule. */
internal fun filterWords(
    words: List<Word>,
    query: String,
    type: LearningItemType?,
): List<Word> {
    val search = query.trim()
    return words.filter { word ->
        (type == null || word.itemType == type) &&
            (search.isEmpty() || word.word.contains(search, ignoreCase = true) ||
                when (word.itemType) {
                    LearningItemType.SIMPLE_WORD -> word.meaning.contains(search, ignoreCase = true)
                    LearningItemType.VERB_CONJUGATION -> word.verbConjugation?.let { verb ->
                        verb.tense.contains(search, ignoreCase = true) ||
                            verb.rows().any { it.form.contains(search, ignoreCase = true) }
                    } == true
                })
    }
}
