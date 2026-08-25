package com.wordforge.exercise

import com.wordforge.data.LearningItemType
import kotlin.math.ceil

enum class LlmProvider(
    val displayName: String,
    val defaultModel: String,
) {
    OPENAI("OpenAI", "gpt-5.6-luna"),
    GEMINI("Gemini", "gemini-2.5-flash"),
}

enum class ExerciseType(
    val displayName: String,
    /** Maximum distinct saved items this renderer can meaningfully show in one exercise. */
    val meaningfulWordCapacity: Int,
) {
    MULTIPLE_CHOICE("Multiple choice", 6),
    FILL_IN_BLANK("Fill in the blank", 1),
    TRANSLATION("Translation", 1),
    MATCHING("Matching", 8),
}

data class ExerciseConfig(
    val exerciseCount: Int = DEFAULT_EXERCISE_COUNT,
    val selectedTypes: Set<ExerciseType> = ExerciseType.entries.toSet(),
    val coveragePercent: Int = DEFAULT_COVERAGE_PERCENT,
) {
    init {
        require(exerciseCount in MIN_EXERCISE_COUNT..MAX_EXERCISE_COUNT) {
            "Exercise count must be between $MIN_EXERCISE_COUNT and $MAX_EXERCISE_COUNT"
        }
        require(selectedTypes.isNotEmpty()) { "Select at least one exercise type" }
        require(selectedTypes.size <= exerciseCount) {
            "Exercise count must be at least the number of selected types"
        }
        require(coveragePercent in MIN_COVERAGE_PERCENT..MAX_COVERAGE_PERCENT) {
            "Coverage must be between $MIN_COVERAGE_PERCENT and $MAX_COVERAGE_PERCENT"
        }
    }

    /**
     * Produces the exact requested order. Enum declaration order deliberately
     * makes this stable even when [selectedTypes] is backed by an unordered set.
     */
    fun typePlan(): List<ExerciseType> {
        val orderedTypes = ExerciseType.entries.filter { it in selectedTypes }
        return List(exerciseCount) { index -> orderedTypes[index % orderedTypes.size] }
    }

    fun requestedWordCount(totalWordCount: Int): Int {
        if (totalWordCount <= 0) return 0
        return ceil(totalWordCount * coveragePercent / 100.0).toInt().coerceAtLeast(1)
    }

    fun meaningfulWordCapacity(): Int = typePlan().sumOf { it.meaningfulWordCapacity }

    fun nonTranslationWordCapacity(): Int = typePlan()
        .filterNot { it == ExerciseType.TRANSLATION }
        .sumOf { it.meaningfulWordCapacity }

    /**
     * Reconciles requested percentage with what the chosen interactive controls can actually test.
     * Translation needs a simple word with a stored meaning; verb-only items must fit in other slots.
     */
    fun targetWordCount(totalWordCount: Int, simpleWordCount: Int): Int {
        if (totalWordCount <= 0) return 0
        val normalizedSimpleCount = simpleWordCount.coerceIn(0, totalWordCount)
        if (ExerciseType.TRANSLATION in selectedTypes && normalizedSimpleCount == 0) return 0
        if (ExerciseType.MATCHING in selectedTypes && totalWordCount < 2) return 0

        val verbCount = totalWordCount - normalizedSimpleCount
        val compatibleDistinctWords = normalizedSimpleCount + minOf(
            verbCount,
            nonTranslationWordCapacity(),
        )
        val supportedMaximum = minOf(
            meaningfulWordCapacity(),
            compatibleDistinctWords,
            MAX_SELECTED_WORDS,
        )
        val minimumForControls = if (ExerciseType.MATCHING in selectedTypes) 2 else 1
        return requestedWordCount(totalWordCount)
            .coerceAtLeast(minimumForControls)
            .coerceAtMost(supportedMaximum)
    }

    companion object {
        const val MIN_EXERCISE_COUNT = 3
        const val MAX_EXERCISE_COUNT = 20
        const val DEFAULT_EXERCISE_COUNT = 10
        const val MIN_COVERAGE_PERCENT = 25
        const val MAX_COVERAGE_PERCENT = 100
        const val DEFAULT_COVERAGE_PERCENT = 70
        const val MAX_SELECTED_WORDS = 120
    }
}

/**
 * A deliberately sanitized snapshot of one learning item. Review timestamps,
 * scores, tiers, and database ids are never part of the provider payload.
 */
data class SelectedExerciseWord(
    val ref: String,
    val sourceWordId: String,
    val itemType: LearningItemType,
    val term: String,
    val meaning: String,
    val tense: String,
    val conjugations: List<ExerciseConjugation>,
)

data class ExerciseConjugation(
    val person: String,
    val form: String,
)

data class ExerciseSessionRequest(
    val config: ExerciseConfig,
    val selectedWords: List<SelectedExerciseWord>,
    val typePlan: List<ExerciseType> = config.typePlan(),
) {
    init {
        require(selectedWords.isNotEmpty()) { "At least one learning item is required" }
        require(selectedWords.size <= ExerciseConfig.MAX_SELECTED_WORDS) {
            "Too many selected learning items"
        }
        require(selectedWords.size <= config.meaningfulWordCapacity()) {
            "The selected learning items exceed the exercise plan capacity"
        }
        require(selectedWords.map { it.ref }.toSet().size == selectedWords.size) {
            "Learning item references must be unique"
        }
        require(typePlan == config.typePlan()) { "Exercise type plan does not match the config" }
        if (ExerciseType.TRANSLATION in config.selectedTypes) {
            require(selectedWords.any { it.itemType == LearningItemType.SIMPLE_WORD }) {
                "Translation exercises require a simple word with a meaning"
            }
            require(
                selectedWords.count { it.itemType == LearningItemType.VERB_CONJUGATION } <=
                    config.nonTranslationWordCapacity()
            ) { "Too many verb items for the selected exercise plan" }
        }
        if (ExerciseType.MATCHING in config.selectedTypes) {
            require(selectedWords.size >= 2) {
                "Matching exercises require at least two learning items"
            }
        }
    }
}

data class ExercisePack(
    val title: String,
    val exercises: List<GeneratedExercise>,
)

data class GeneratedExercise(
    val id: String,
    val type: ExerciseType,
    val instructions: String,
    val prompt: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val acceptedAnswers: List<String>,
    val pairs: List<ExercisePair>,
    val wordRefs: List<String>,
    val explanation: String,
)

data class ExercisePair(
    val left: String,
    val right: String,
)

data class ExercisePrompt(
    val instructions: String,
    val input: String,
)
