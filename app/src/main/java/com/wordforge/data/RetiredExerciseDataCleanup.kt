package com.wordforge.data

import android.content.Context
import java.security.KeyStore

/** Removes data left by the retired exercise integration, including on upgrades. */
internal fun removeRetiredExerciseData(context: Context) {
    for (name in listOf("wordforge_llm_credentials", "wordforge_llm_settings")) {
        // Retry at the next startup if the device cannot delete a file yet.
        runCatching { context.deleteSharedPreferences(name) }
    }

    val keyStore = runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }.getOrNull() ?: return
    for (alias in listOf("wordforge_llm_openai", "wordforge_llm_gemini")) {
        runCatching { keyStore.deleteEntry(alias) }
    }
}
