package com.wordforge.exercise

import com.wordforge.data.LearningItemType
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.json.JSONTokener
import java.text.Normalizer
import java.util.Locale

sealed class ExerciseGenerationException(message: String) : Exception(message) {
    class InvalidConfiguration(message: String) : ExerciseGenerationException(message)
    class Authentication : ExerciseGenerationException("The provider rejected the API key")
    class RateLimited : ExerciseGenerationException("The provider rate limit was reached")
    class Timeout : ExerciseGenerationException("The provider request timed out")
    class Network : ExerciseGenerationException("The provider could not be reached")
    class ProviderUnavailable(val statusCode: Int) :
        ExerciseGenerationException("The provider request failed (HTTP $statusCode)")
    class InvalidResponse(message: String) : ExerciseGenerationException(message)
}

object ExercisePackParser {
    fun parseAndValidate(rawJson: String, request: ExerciseSessionRequest): ExercisePack {
        if (rawJson.length > ExerciseJsonSchema.MAX_RAW_RESPONSE_LENGTH) {
            throw ExerciseGenerationException.InvalidResponse("The exercise response was too large")
        }

        return try {
            val tokener = JSONTokener(rawJson)
            val root = tokener.nextValue()
            if (root !is JSONObject || tokener.nextClean() != '\u0000') {
                invalid("The provider returned malformed exercise JSON")
            }
            requireExactKeys(root, ROOT_KEYS, "exercise pack")
            val title = requiredString(
                root,
                "title",
                ExerciseJsonSchema.MAX_TITLE_LENGTH,
                "exercise pack",
            )
            val exerciseArray = requiredArray(root, "exercises", "exercise pack")
            val exercises = List(exerciseArray.length()) { index ->
                parseExercise(exerciseArray, index)
            }
            ExercisePackValidator.validate(
                ExercisePack(title = title, exercises = exercises),
                request,
            )
        } catch (error: ExerciseGenerationException.InvalidResponse) {
            throw error
        } catch (_: JSONException) {
            throw ExerciseGenerationException.InvalidResponse(
                "The provider returned malformed exercise JSON"
            )
        } catch (_: RuntimeException) {
            throw ExerciseGenerationException.InvalidResponse(
                "The provider returned invalid exercise data"
            )
        }
    }

    private fun parseExercise(array: JSONArray, index: Int): GeneratedExercise {
        val context = "exercise ${index + 1}"
        val value = array.get(index)
        if (value !is JSONObject) invalid("$context must be an object")
        requireExactKeys(value, EXERCISE_KEYS, context)

        val typeName = requiredString(value, "type", 32, context)
        val type = ExerciseType.entries.firstOrNull { it.name == typeName }
            ?: invalid("$context has an unknown type")

        return GeneratedExercise(
            id = requiredString(value, "id", ExerciseJsonSchema.MAX_ID_LENGTH, context),
            type = type,
            instructions = requiredString(
                value,
                "instructions",
                ExerciseJsonSchema.MAX_INSTRUCTIONS_LENGTH,
                context,
            ),
            prompt = requiredString(
                value,
                "prompt",
                ExerciseJsonSchema.MAX_PROMPT_LENGTH,
                context,
            ),
            options = requiredStringList(
                value,
                "options",
                ExerciseJsonSchema.MAX_OPTIONS,
                context,
            ),
            correctOptionIndex = requiredInt(value, "correctOptionIndex", context),
            acceptedAnswers = requiredStringList(
                value,
                "acceptedAnswers",
                ExerciseJsonSchema.MAX_ACCEPTED_ANSWERS,
                context,
            ),
            pairs = parsePairs(value, context),
            wordRefs = requiredStringList(
                value,
                "wordRefs",
                ExerciseConfig.MAX_SELECTED_WORDS,
                context,
                maxStringLength = ExerciseJsonSchema.MAX_REF_LENGTH,
            ),
            explanation = requiredString(
                value,
                "explanation",
                ExerciseJsonSchema.MAX_EXPLANATION_LENGTH,
                context,
            ),
        )
    }

    private fun parsePairs(exercise: JSONObject, context: String): List<ExercisePair> {
        val array = requiredArray(exercise, "pairs", context)
        if (array.length() > ExerciseJsonSchema.MAX_PAIRS) {
            invalid("$context has too many matching pairs")
        }
        return List(array.length()) { index ->
            val value = array.get(index)
            if (value !is JSONObject) invalid("$context has an invalid matching pair")
            requireExactKeys(value, PAIR_KEYS, "$context matching pair ${index + 1}")
            ExercisePair(
                left = requiredString(
                    value,
                    "left",
                    ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH,
                    context,
                ),
                right = requiredString(
                    value,
                    "right",
                    ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH,
                    context,
                ),
            )
        }
    }

    private fun requiredStringList(
        obj: JSONObject,
        key: String,
        maxItems: Int,
        context: String,
        maxStringLength: Int = ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH,
    ): List<String> {
        val array = requiredArray(obj, key, context)
        if (array.length() > maxItems) invalid("$context has too many $key values")
        return List(array.length()) { index ->
            val value = array.get(index)
            if (value !is String) invalid("$context has a non-text $key value")
            boundedString(value, maxStringLength, "$context $key value ${index + 1}")
        }
    }

    private fun requiredString(
        obj: JSONObject,
        key: String,
        maxLength: Int,
        context: String,
    ): String {
        val value = obj.get(key)
        if (value !is String) invalid("$context has a non-text $key")
        return boundedString(value, maxLength, "$context $key")
    }

    private fun boundedString(value: String, maxLength: Int, context: String): String {
        val normalized = value.trim()
        if (normalized.isEmpty()) invalid("$context is empty")
        if (normalized.length > maxLength) invalid("$context is too long")
        if (normalized.any { it == '\u0000' }) invalid("$context contains invalid text")
        return normalized
    }

    private fun requiredInt(obj: JSONObject, key: String, context: String): Int {
        val value = obj.get(key)
        if (value !is Number) invalid("$context has a non-numeric $key")
        val asLong = value.toLong()
        if (value.toDouble() != asLong.toDouble() || asLong !in Int.MIN_VALUE..Int.MAX_VALUE) {
            invalid("$context has an invalid $key")
        }
        return asLong.toInt()
    }

    private fun requiredArray(obj: JSONObject, key: String, context: String): JSONArray {
        val value = obj.get(key)
        if (value !is JSONArray) invalid("$context has a non-array $key")
        return value
    }

    private fun requireExactKeys(obj: JSONObject, expected: Set<String>, context: String) {
        val actual = buildSet { obj.keys().forEachRemaining { add(it) } }
        if (actual != expected) invalid("$context does not match the required fields")
    }

    private fun invalid(message: String): Nothing =
        throw ExerciseGenerationException.InvalidResponse(message)

    private val ROOT_KEYS = setOf("title", "exercises")
    private val EXERCISE_KEYS = setOf(
        "id",
        "type",
        "instructions",
        "prompt",
        "options",
        "correctOptionIndex",
        "acceptedAnswers",
        "pairs",
        "wordRefs",
        "explanation",
    )
    private val PAIR_KEYS = setOf("left", "right")
}

object ExercisePackValidator {
    fun validate(pack: ExercisePack, request: ExerciseSessionRequest): ExercisePack {
        validateStructure(pack)
        if (pack.exercises.size != request.config.exerciseCount) {
            invalid("The provider returned the wrong number of exercises")
        }
        pack.exercises.forEachIndexed { index, exercise ->
            if (exercise.type != request.typePlan[index]) {
                invalid("Exercise ${index + 1} does not match the requested type plan")
            }
        }

        val selectedByRef = request.selectedWords.associateBy { it.ref }
        val selectedRefs = selectedByRef.keys
        val returnedRefs = pack.exercises.flatMapTo(linkedSetOf()) { it.wordRefs }
        if (!selectedRefs.containsAll(returnedRefs)) {
            invalid("The provider referenced an unknown learning item")
        }
        pack.exercises.forEachIndexed { index, exercise ->
            exercise.wordRefs.forEach { ref ->
                val selected = selectedByRef[ref]
                    ?: invalid("The provider referenced an unknown learning item")
                if (
                    exercise.type == ExerciseType.TRANSLATION &&
                    selected.itemType != LearningItemType.SIMPLE_WORD
                ) {
                    invalid("Exercise ${index + 1} cannot translate a verb without a stored meaning")
                }
                if (!materiallyRepresents(exercise, selected)) {
                    invalid(
                        "Exercise ${index + 1} declares a learning item it does not use"
                    )
                }
            }
            if (
                exercise.type == ExerciseType.MULTIPLE_CHOICE &&
                !hasSupportedMultipleChoiceAnswer(
                    exercise,
                    exercise.wordRefs.mapNotNull(selectedByRef::get),
                )
            ) {
                invalid("Exercise ${index + 1} has an answer unsupported by the selected items")
            }
        }
        if (!returnedRefs.containsAll(selectedRefs)) {
            invalid("The exercises did not cover every selected learning item")
        }
        return pack
    }

    /** Structural validation used again before handing data to the HTML renderer. */
    fun validateStructure(pack: ExercisePack): ExercisePack {
        bounded(pack.title, ExerciseJsonSchema.MAX_TITLE_LENGTH, "Exercise pack title")
        if (pack.exercises.size !in ExerciseConfig.MIN_EXERCISE_COUNT..ExerciseConfig.MAX_EXERCISE_COUNT) {
            invalid("Exercise pack size is outside the supported range")
        }
        if (pack.exercises.map { it.id }.toSet().size != pack.exercises.size) {
            invalid("Exercise ids must be unique")
        }
        pack.exercises.forEachIndexed(::validateExercise)
        return pack
    }

    private fun validateExercise(index: Int, exercise: GeneratedExercise) {
        val label = "Exercise ${index + 1}"
        bounded(exercise.id, ExerciseJsonSchema.MAX_ID_LENGTH, "$label id")
        bounded(
            exercise.instructions,
            ExerciseJsonSchema.MAX_INSTRUCTIONS_LENGTH,
            "$label instructions",
        )
        bounded(exercise.prompt, ExerciseJsonSchema.MAX_PROMPT_LENGTH, "$label prompt")
        bounded(
            exercise.explanation,
            ExerciseJsonSchema.MAX_EXPLANATION_LENGTH,
            "$label explanation",
        )
        validateStrings(exercise.options, ExerciseJsonSchema.MAX_OPTIONS, "$label options")
        validateStrings(
            exercise.acceptedAnswers,
            ExerciseJsonSchema.MAX_ACCEPTED_ANSWERS,
            "$label accepted answers",
        )
        validateStrings(
            exercise.wordRefs,
            ExerciseConfig.MAX_SELECTED_WORDS,
            "$label word refs",
            ExerciseJsonSchema.MAX_REF_LENGTH,
        )
        if (exercise.wordRefs.isEmpty()) invalid("$label must reference a learning item")
        if (exercise.pairs.size > ExerciseJsonSchema.MAX_PAIRS) {
            invalid("$label has too many matching pairs")
        }
        exercise.pairs.forEach { pair ->
            bounded(pair.left, ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH, "$label pair")
            bounded(pair.right, ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH, "$label pair")
        }

        when (exercise.type) {
            ExerciseType.MULTIPLE_CHOICE -> {
                if (exercise.options.size !in 2..ExerciseJsonSchema.MAX_OPTIONS) {
                    invalid("$label must have between 2 and 6 options")
                }
                if (exercise.wordRefs.size > exercise.options.size) {
                    invalid("$label references more learning items than it shows")
                }
                if (exercise.correctOptionIndex !in exercise.options.indices) {
                    invalid("$label has an invalid correct option")
                }
                if (exercise.acceptedAnswers.isNotEmpty() || exercise.pairs.isNotEmpty()) {
                    invalid("$label has values in unused fields")
                }
            }

            ExerciseType.FILL_IN_BLANK -> {
                if (exercise.prompt.windowed(BLANK_MARKER.length).count { it == BLANK_MARKER } != 1) {
                    invalid("$label must contain exactly one blank marker")
                }
                if (exercise.wordRefs.size != 1) {
                    invalid("$label must test exactly one learning item")
                }
                validateWrittenAnswerExercise(exercise, label)
            }

            ExerciseType.TRANSLATION -> {
                if (exercise.wordRefs.size != 1) {
                    invalid("$label must test exactly one learning item")
                }
                validateWrittenAnswerExercise(exercise, label)
            }

            ExerciseType.MATCHING -> {
                if (exercise.pairs.size !in 2..ExerciseJsonSchema.MAX_PAIRS) {
                    invalid("$label must have between 2 and 8 matching pairs")
                }
                if (exercise.wordRefs.size != exercise.pairs.size) {
                    invalid("$label must have one pair per referenced learning item")
                }
                if (
                    exercise.options.isNotEmpty() ||
                    exercise.acceptedAnswers.isNotEmpty() ||
                    exercise.correctOptionIndex != -1
                ) {
                    invalid("$label has values in unused fields")
                }
                val left = exercise.pairs.map { normalizedForUniqueness(it.left) }
                val right = exercise.pairs.map { normalizedForUniqueness(it.right) }
                if (left.toSet().size != left.size || right.toSet().size != right.size) {
                    invalid("$label matching pairs must be distinct")
                }
            }
        }
    }

    private fun validateWrittenAnswerExercise(exercise: GeneratedExercise, label: String) {
        if (exercise.acceptedAnswers.size !in 1..ExerciseJsonSchema.MAX_ACCEPTED_ANSWERS) {
            invalid("$label must have at least one accepted answer")
        }
        if (exercise.options.isNotEmpty() || exercise.pairs.isNotEmpty()) {
            invalid("$label has values in unused fields")
        }
        if (exercise.correctOptionIndex != -1) {
            invalid("$label must use -1 for its unused correct option")
        }
    }

    private fun validateStrings(
        values: List<String>,
        maxItems: Int,
        label: String,
        maxLength: Int = ExerciseJsonSchema.MAX_SHORT_TEXT_LENGTH,
    ) {
        if (values.size > maxItems) invalid("$label has too many values")
        values.forEach { bounded(it, maxLength, label) }
        val normalized = values.map(::normalizedForUniqueness)
        if (normalized.toSet().size != normalized.size) invalid("$label must be unique")
    }

    private fun bounded(value: String, maxLength: Int, label: String) {
        if (value.isBlank()) invalid("$label cannot be empty")
        if (value.length > maxLength) invalid("$label is too long")
        if (value.any { it == '\u0000' }) invalid("$label contains invalid text")
    }

    private fun normalizedForUniqueness(value: String): String =
        normalizedText(value)

    private fun materiallyRepresents(
        exercise: GeneratedExercise,
        selected: SelectedExerciseWord,
    ): Boolean = when (selected.itemType) {
        LearningItemType.SIMPLE_WORD -> when (exercise.type) {
            ExerciseType.FILL_IN_BLANK,
            ExerciseType.TRANSLATION,
            -> simpleWordIsWrittenAnswerTarget(exercise, selected)

            ExerciseType.MULTIPLE_CHOICE -> {
                val expected = listOf(selected.term, selected.meaning)
                    .map(::normalizedText)
                    .filter(String::isNotEmpty)
                exercise.options.map(::normalizedText).any { it in expected } ||
                    expected.any { containsSimpleTarget(exercise.prompt, it) }
            }

            ExerciseType.MATCHING -> exercise.pairs.any { pair ->
                val left = normalizedText(pair.left)
                val right = normalizedText(pair.right)
                val term = normalizedText(selected.term)
                val meaning = normalizedText(selected.meaning)
                (left == term && right == meaning) || (left == meaning && right == term)
            }
        }

        LearningItemType.VERB_CONJUGATION -> when (exercise.type) {
            ExerciseType.TRANSLATION -> false

            ExerciseType.FILL_IN_BLANK -> selected.conjugations.any { conjugation ->
                val answer = normalizedText(conjugation.form)
                exercise.acceptedAnswers.any { normalizedText(it) == answer } &&
                    (
                        containsBounded(exercise.prompt, normalizedText(conjugation.person)) ||
                            containsBounded(exercise.prompt, normalizedText(selected.term))
                    )
            }

            ExerciseType.MULTIPLE_CHOICE -> {
                val expected = buildList {
                    add(selected.term)
                    selected.conjugations.forEach { add(it.form) }
                }.map(::normalizedText).filter(String::isNotEmpty)
                exercise.options.map(::normalizedText).any { it in expected } ||
                    expected.any { containsBounded(exercise.prompt, it) }
            }

            ExerciseType.MATCHING -> exercise.pairs.any { pair ->
                val left = normalizedText(pair.left)
                val right = normalizedText(pair.right)
                selected.conjugations.any { conjugation ->
                    val person = normalizedText(conjugation.person)
                    val form = normalizedText(conjugation.form)
                    val term = normalizedText(selected.term)
                    (left == person && right == form) ||
                        (left == form && right == person) ||
                        (left == term && right == form) ||
                        (left == form && right == term)
                }
            }
        }
    }

    private fun simpleWordIsWrittenAnswerTarget(
        exercise: GeneratedExercise,
        selected: SelectedExerciseWord,
    ): Boolean {
        val term = normalizedText(selected.term)
        val meaning = normalizedText(selected.meaning)
        if (term.isEmpty() || meaning.isEmpty()) return false
        val answers = exercise.acceptedAnswers.map(::normalizedText)
        return (
            containsSimpleTarget(exercise.prompt, term) && meaning in answers
        ) || (
            containsSimpleTarget(exercise.prompt, meaning) && term in answers
        )
    }

    private fun hasSupportedMultipleChoiceAnswer(
        exercise: GeneratedExercise,
        selectedWords: List<SelectedExerciseWord>,
    ): Boolean {
        val correct = exercise.options
            .getOrNull(exercise.correctOptionIndex)
            ?.let(::normalizedText)
            ?: return false
        return selectedWords.any { selected ->
            when (selected.itemType) {
                LearningItemType.SIMPLE_WORD -> {
                    val term = normalizedText(selected.term)
                    val meaning = normalizedText(selected.meaning)
                    (correct == meaning && containsSimpleTarget(exercise.prompt, term)) ||
                        (correct == term && containsSimpleTarget(exercise.prompt, meaning))
                }

                LearningItemType.VERB_CONJUGATION -> {
                    selected.conjugations.any { conjugation ->
                        val form = normalizedText(conjugation.form)
                        correct == form && (
                            containsBounded(exercise.prompt, normalizedText(selected.term)) ||
                                containsBounded(
                                    exercise.prompt,
                                    normalizedText(conjugation.person),
                                )
                            )
                    } || (
                        correct == normalizedText(selected.term) &&
                            selected.conjugations.any { conjugation ->
                                containsBounded(
                                    exercise.prompt,
                                    normalizedText(conjugation.form),
                                )
                            }
                        )
                }
            }
        }
    }

    private fun containsSimpleTarget(content: String, normalizedExpected: String): Boolean {
        if (normalizedExpected.length > 2) return containsBounded(content, normalizedExpected)
        val normalizedContent = normalizedText(content)
        if (normalizedContent == normalizedExpected) return true
        return listOf(
            "\"$normalizedExpected\"",
            "'$normalizedExpected'",
            "“$normalizedExpected”",
            "‘$normalizedExpected’",
            "«$normalizedExpected»",
        ).any(normalizedContent::contains)
    }

    private fun containsBounded(content: String, normalizedExpected: String): Boolean {
        if (normalizedExpected.isEmpty()) return false
        val normalizedContent = normalizedText(content)
        var start = normalizedContent.indexOf(normalizedExpected)
        while (start >= 0) {
            val end = start + normalizedExpected.length
            val beginsAtBoundary = start == 0 || !normalizedContent[start - 1].isLetterOrDigit()
            val endsAtBoundary = end == normalizedContent.length ||
                !normalizedContent[end].isLetterOrDigit()
            if (beginsAtBoundary && endsAtBoundary) return true
            start = normalizedContent.indexOf(normalizedExpected, start + 1)
        }
        return false
    }

    private fun normalizedText(value: String): String = Normalizer
        .normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT)
        .trim()
        .replace(WHITESPACE, " ")

    private fun invalid(message: String): Nothing =
        throw ExerciseGenerationException.InvalidResponse(message)

    private const val BLANK_MARKER = "_____"
    private val WHITESPACE = Regex("\\s+")
}
