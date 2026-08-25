package com.wordforge.exercise

import com.wordforge.data.LearningItemType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePackParserTest {
    @Test
    fun parsesAValidExactPackAndSerializesSnakeCaseForHtml() {
        val pack = ExercisePackParser.parseAndValidate(validJson().toString(), request())

        assertEquals(4, pack.exercises.size)
        assertEquals(ExerciseType.MATCHING, pack.exercises.last().type)

        val htmlPayload = pack.toJsonString()
        val exercise = JSONObject(htmlPayload).getJSONArray("exercises").getJSONObject(0)
        assertTrue(exercise.has("correct_option_index"))
        assertTrue(exercise.has("accepted_answers"))
        assertTrue(exercise.has("word_refs"))
        assertFalse(exercise.has("correctOptionIndex"))
    }

    @Test
    fun rejectsWrongTypePlanAndUnknownFields() {
        val wrongType = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("type", ExerciseType.TRANSLATION.name)
        }
        assertInvalid(wrongType)

        val extraField = validJson().put("providerBody", "must not pass through")
        assertInvalid(extraField)
    }

    @Test
    fun rejectsUnknownDuplicateOrMissingReferences() {
        val unknown = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("wordRefs", JSONArray(listOf("w99")))
        }
        assertInvalid(unknown)

        val duplicate = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("wordRefs", JSONArray(listOf("w1", "w1")))
        }
        assertInvalid(duplicate)

        val missing = validJson().apply {
            val fill = getJSONArray("exercises").getJSONObject(1)
            fill.put("prompt", "The Spanish word casa means _____")
            fill.put("acceptedAnswers", JSONArray(listOf("house")))
            fill.put("explanation", "Casa means house.")
            fill.put("wordRefs", JSONArray(listOf("w1")))
            getJSONArray("exercises").getJSONObject(3)
                .put("wordRefs", JSONArray(listOf("w4")))
        }
        assertInvalid(missing)
    }

    @Test
    fun rejectsClaimedCoverageWithoutMaterialVocabularyUse() {
        val claimedOnly = validJson().apply {
            val fill = getJSONArray("exercises").getJSONObject(1)
            fill.put("prompt", "Complete this generic sentence: _____")
            fill.put("acceptedAnswers", JSONArray(listOf("answer")))
            fill.put("explanation", "This is a generic answer.")
            // w2 remains declared, but neither perro nor dog occurs anywhere.
        }

        val error = assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(claimedOnly.toString(), request())
        }
        assertTrue(error.message.orEmpty().contains("does not use"))
    }

    @Test
    fun acceptsVerbCoverageThroughAGradedConjugatedForm() {
        val config = ExerciseConfig(
            exerciseCount = 3,
            coveragePercent = 100,
            selectedTypes = setOf(ExerciseType.FILL_IN_BLANK),
        )
        val verb = SelectedExerciseWord(
            ref = "w1",
            sourceWordId = "private-id",
            itemType = LearningItemType.VERB_CONJUGATION,
            term = "decir",
            meaning = "",
            tense = "presente",
            conjugations = listOf(
                ExerciseConjugation("Yo", "digo"),
                ExerciseConjugation("Tú", "dices"),
            ),
        )
        val request = ExerciseSessionRequest(config, listOf(verb))
        val exercises = JSONArray().apply {
            repeat(3) { index ->
                put(
                    exerciseJson(
                        id = "v${index + 1}",
                        type = ExerciseType.FILL_IN_BLANK,
                        prompt = "Yo _____ la verdad (decir).",
                        acceptedAnswers = listOf("digo"),
                        wordRefs = listOf("w1"),
                        explanation = "Digo is the yo form.",
                    )
                )
            }
        }
        val json = JSONObject().put("title", "Verb session").put("exercises", exercises)

        assertEquals(3, ExercisePackParser.parseAndValidate(json.toString(), request).exercises.size)
    }

    @Test
    fun rejectsVerbTranslationAndSharedTenseCoverage() {
        val config = ExerciseConfig(
            exerciseCount = 3,
            coveragePercent = 100,
            selectedTypes = setOf(ExerciseType.TRANSLATION),
        )
        val verb = SelectedExerciseWord(
            ref = "w1",
            sourceWordId = "private-id",
            itemType = LearningItemType.VERB_CONJUGATION,
            term = "decir",
            meaning = "",
            tense = "presente",
            conjugations = listOf(ExerciseConjugation("Yo", "digo")),
        )
        assertThrows(IllegalArgumentException::class.java) {
            ExerciseSessionRequest(config, listOf(verb))
        }

        val fillConfig = config.copy(selectedTypes = setOf(ExerciseType.FILL_IN_BLANK))
        val fillRequest = ExerciseSessionRequest(fillConfig, listOf(verb))
        val exercises = JSONArray().apply {
            repeat(3) { index ->
                put(
                    exerciseJson(
                        id = "tense-${index + 1}",
                        type = ExerciseType.FILL_IN_BLANK,
                        prompt = "Complete this presente sentence: _____",
                        acceptedAnswers = listOf("generic"),
                        wordRefs = listOf("w1"),
                        explanation = "The presente tense is used here.",
                    )
                )
            }
        }
        val json = JSONObject().put("title", "Invalid coverage").put("exercises", exercises)
        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(json.toString(), fillRequest)
        }
    }

    @Test
    fun shortTermsMustBeExplicitTargetsAndWrittenAnswersMustUseStoredMeaning() {
        val config = ExerciseConfig(
            exerciseCount = 3,
            coveragePercent = 100,
            selectedTypes = setOf(ExerciseType.TRANSLATION),
        )
        val shortWord = simpleWord("w1", "in", "inside")
        val request = ExerciseSessionRequest(config, listOf(shortWord))
        val incidental = JSONArray().apply {
            repeat(3) { index ->
                put(
                    exerciseJson(
                        id = "short-${index + 1}",
                        type = ExerciseType.TRANSLATION,
                        prompt = "Translate house in Spanish.",
                        acceptedAnswers = listOf("inside"),
                        wordRefs = listOf("w1"),
                        explanation = "The saved term is in.",
                    )
                )
            }
        }
        val incidentalJson = JSONObject().put("title", "Short term").put("exercises", incidental)
        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(incidentalJson.toString(), request)
        }

        val wrongAnswer = JSONObject(incidentalJson.toString()).apply {
            getJSONArray("exercises").let { exercises ->
                repeat(exercises.length()) { index ->
                    exercises.getJSONObject(index)
                        .put("prompt", "Translate \"in\".")
                        .put("acceptedAnswers", JSONArray(listOf("outside")))
                }
            }
        }
        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(wrongAnswer.toString(), request)
        }
    }

    @Test
    fun enforcesTypeSpecificInvariants() {
        val multipleChoiceWithUnusedAnswer = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("acceptedAnswers", JSONArray(listOf("house")))
        }
        assertInvalid(multipleChoiceWithUnusedAnswer)

        val multipleChoiceWithWrongCorrectOption = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("correctOptionIndex", 1)
        }
        assertInvalid(multipleChoiceWithWrongCorrectOption)

        val fillWithTwoBlanks = validJson().apply {
            getJSONArray("exercises").getJSONObject(1)
                .put("prompt", "_____ perro means _____")
        }
        assertInvalid(fillWithTwoBlanks)

        val ambiguousMatching = validJson().apply {
            getJSONArray("exercises").getJSONObject(3).put(
                "pairs",
                JSONArray(
                    listOf(
                        JSONObject().put("left", "gato").put("right", "cat"),
                        JSONObject().put("left", "gato").put("right", "dog"),
                    )
                ),
            )
        }
        assertInvalid(ambiguousMatching)
    }

    @Test
    fun enforcesBoundsEvenThoughCommonSchemaOmitsUnsupportedKeywords() {
        val tooLong = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("prompt", "x".repeat(ExerciseJsonSchema.MAX_PROMPT_LENGTH + 1))
        }
        assertInvalid(tooLong)

        val tooManyOptions = validJson().apply {
            getJSONArray("exercises").getJSONObject(0)
                .put("options", JSONArray(List(7) { "option-$it" }))
        }
        assertInvalid(tooManyOptions)
    }

    @Test
    fun rejectsMalformedOrOversizedResponsesWithoutEchoingThem() {
        val malformed = "not-json-secret-value"
        val malformedError = assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(malformed, request())
        }
        assertFalse(malformedError.message.orEmpty().contains("secret-value"))

        val oversized = "x".repeat(ExerciseJsonSchema.MAX_RAW_RESPONSE_LENGTH + 1)
        val oversizedError = assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(oversized, request())
        }
        assertFalse(oversizedError.message.orEmpty().contains(oversized.take(20)))

        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(
                validJson().toString() + " trailing-data",
                request(),
            )
        }
    }

    private fun assertInvalid(json: JSONObject) {
        assertThrows(ExerciseGenerationException.InvalidResponse::class.java) {
            ExercisePackParser.parseAndValidate(json.toString(), request())
        }
    }

    private fun request(): ExerciseSessionRequest {
        val config = ExerciseConfig(
            exerciseCount = 4,
            coveragePercent = 100,
            selectedTypes = ExerciseType.entries.toSet(),
        )
        return ExerciseSessionRequest(
            config = config,
            selectedWords = listOf(
                simpleWord("w1", "casa", "house"),
                simpleWord("w2", "perro", "dog"),
                simpleWord("w3", "hola", "hello"),
                simpleWord("w4", "gato", "cat"),
            ),
        )
    }

    private fun simpleWord(ref: String, term: String, meaning: String) = SelectedExerciseWord(
        ref = ref,
        sourceWordId = "private-$ref",
        itemType = LearningItemType.SIMPLE_WORD,
        term = term,
        meaning = meaning,
        tense = "",
        conjugations = emptyList(),
    )

    private fun validJson(): JSONObject = JSONObject()
        .put("title", "Everyday Spanish")
        .put(
            "exercises",
            JSONArray(
                listOf(
                    exerciseJson(
                        id = "e1",
                        type = ExerciseType.MULTIPLE_CHOICE,
                        prompt = "What is the meaning of casa?",
                        options = listOf("house", "chair"),
                        correctOptionIndex = 0,
                        wordRefs = listOf("w1"),
                        explanation = "Casa means house.",
                    ),
                    exerciseJson(
                        id = "e2",
                        type = ExerciseType.FILL_IN_BLANK,
                        prompt = "The Spanish word perro means _____",
                        acceptedAnswers = listOf("dog"),
                        wordRefs = listOf("w2"),
                        explanation = "Perro means dog.",
                    ),
                    exerciseJson(
                        id = "e3",
                        type = ExerciseType.TRANSLATION,
                        prompt = "Translate hola into English.",
                        acceptedAnswers = listOf("hello"),
                        wordRefs = listOf("w3"),
                        explanation = "Hola means hello.",
                    ),
                    exerciseJson(
                        id = "e4",
                        type = ExerciseType.MATCHING,
                        prompt = "Match gato and perro with their meanings.",
                        pairs = listOf(
                            ExercisePair("gato", "cat"),
                            ExercisePair("perro", "dog"),
                        ),
                        wordRefs = listOf("w4", "w2"),
                        explanation = "Gato means cat and perro means dog.",
                    ),
                )
            ),
        )

    private fun exerciseJson(
        id: String,
        type: ExerciseType,
        prompt: String,
        options: List<String> = emptyList(),
        correctOptionIndex: Int = if (type == ExerciseType.MULTIPLE_CHOICE) 0 else -1,
        acceptedAnswers: List<String> = emptyList(),
        pairs: List<ExercisePair> = emptyList(),
        wordRefs: List<String>,
        explanation: String,
    ): JSONObject = JSONObject()
        .put("id", id)
        .put("type", type.name)
        .put("instructions", "Complete the exercise.")
        .put("prompt", prompt)
        .put("options", JSONArray(options))
        .put("correctOptionIndex", correctOptionIndex)
        .put("acceptedAnswers", JSONArray(acceptedAnswers))
        .put(
            "pairs",
            JSONArray().apply {
                pairs.forEach { pair ->
                    put(JSONObject().put("left", pair.left).put("right", pair.right))
                }
            },
        )
        .put("wordRefs", JSONArray(wordRefs))
        .put("explanation", explanation)
}
