package com.wordforge.exercise

import com.wordforge.data.LearningItemType
import com.wordforge.data.Word

object ExerciseWordSelector {
    fun select(
        words: List<Word>,
        config: ExerciseConfig,
        now: Long = System.currentTimeMillis(),
        sessionSeed: Long = now,
    ): List<SelectedExerciseWord> {
        if (words.isEmpty()) return emptyList()

        val prioritized = words
            .distinctBy { it.id }
            .sortedWith(priorityComparator(now, sessionSeed))
        val simpleWordCount = prioritized.count { it.itemType == LearningItemType.SIMPLE_WORD }
        val desiredCount = config.targetWordCount(prioritized.size, simpleWordCount)
        if (desiredCount == 0) return emptyList()

        val selected = mutableListOf<Word>()
        if (ExerciseType.TRANSLATION in config.selectedTypes) {
            prioritized.firstOrNull { it.itemType == LearningItemType.SIMPLE_WORD }
                ?.let(selected::add)
        }
        var selectedVerbCount = 0
        prioritized.forEach { word ->
            if (selected.size >= desiredCount || word in selected) return@forEach
            if (word.itemType == LearningItemType.VERB_CONJUGATION) {
                if (selectedVerbCount >= config.nonTranslationWordCapacity()) return@forEach
                selectedVerbCount += 1
            }
            selected += word
        }

        return selected
            .mapIndexed { index, word -> word.toSelectedWord(ref = "w${index + 1}") }
    }

    private fun priorityComparator(now: Long, sessionSeed: Long): Comparator<Word> =
        compareBy<Word> { if (it.nextPromptAt <= now) 0 else 1 }
            .thenBy { it.currentTier }
            .thenBy { correctnessRatio(it) }
            .thenBy { it.currentStreak }
            .thenBy { it.nextPromptAt }
            .thenBy { stableTieBreak(sessionSeed, it.id) }
            .thenBy { it.id }

    private fun correctnessRatio(word: Word): Double {
        val total = word.totalCorrect + word.totalIncorrect
        return if (total == 0) 0.5 else word.totalCorrect.toDouble() / total
    }

    /** FNV-1a with a seed gives a stable, process-independent tie break. */
    private fun stableTieBreak(seed: Long, value: String): Long {
        var hash = -0x340d631b7bdddcdbL xor seed
        value.forEach { character ->
            hash = hash xor character.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }

    private fun Word.toSelectedWord(ref: String): SelectedExerciseWord {
        val conjugation = verbConjugation
        return SelectedExerciseWord(
            ref = ref,
            sourceWordId = id,
            itemType = itemType,
            term = word.trim(),
            meaning = if (itemType == LearningItemType.SIMPLE_WORD) meaning.trim() else "",
            tense = if (itemType == LearningItemType.VERB_CONJUGATION) {
                conjugation?.tense?.trim().orEmpty()
            } else {
                ""
            },
            conjugations = if (itemType == LearningItemType.VERB_CONJUGATION) {
                conjugation?.rows().orEmpty().map { row ->
                    ExerciseConjugation(person = row.person, form = row.form.trim())
                }
            } else {
                emptyList()
            },
        )
    }
}
