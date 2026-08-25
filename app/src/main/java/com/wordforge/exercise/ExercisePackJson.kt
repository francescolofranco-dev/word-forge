package com.wordforge.exercise

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializes only after re-validating the normalized domain model. The local
 * HTML renderer consumes this snake_case shape; no provider response is passed
 * directly into a WebView.
 */
fun ExercisePack.toJsonString(): String {
    ExercisePackValidator.validateStructure(this)
    return JSONObject()
        .put("title", title)
        .put(
            "exercises",
            JSONArray().apply {
                exercises.forEach { exercise ->
                    put(
                        JSONObject()
                            .put("id", exercise.id)
                            .put("type", exercise.type.name)
                            .put("instructions", exercise.instructions)
                            .put("prompt", exercise.prompt)
                            .put("options", JSONArray(exercise.options))
                            .put("correct_option_index", exercise.correctOptionIndex)
                            .put("accepted_answers", JSONArray(exercise.acceptedAnswers))
                            .put(
                                "pairs",
                                JSONArray().apply {
                                    exercise.pairs.forEach { pair ->
                                        put(
                                            JSONObject()
                                                .put("left", pair.left)
                                                .put("right", pair.right)
                                        )
                                    }
                                },
                            )
                            .put("word_refs", JSONArray(exercise.wordRefs))
                            .put("explanation", exercise.explanation)
                    )
                }
            },
        )
        .toString()
}
