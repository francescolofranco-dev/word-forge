package com.wordforge.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import com.wordforge.exercise.ExerciseConfig
import com.wordforge.exercise.ExerciseType
import com.wordforge.exercise.LlmProvider
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class LlmSettingsStore(context: Context) {
    private val settings = context.applicationContext.getSharedPreferences(
        SETTINGS_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val credentials = context.applicationContext.getSharedPreferences(
        CREDENTIAL_PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    var provider: LlmProvider
        get() = settings.getString(KEY_PROVIDER, null)
            ?.let { stored -> LlmProvider.entries.firstOrNull { it.name == stored } }
            ?: LlmProvider.OPENAI
        set(value) {
            settings.edit { putString(KEY_PROVIDER, value.name) }
        }

    var selectedProvider: LlmProvider
        get() = provider
        set(value) {
            provider = value
        }

    var model: String
        get() = getModel(provider)
        set(value) {
            setModel(provider, value)
        }

    fun getModel(provider: LlmProvider): String {
        val stored = settings.getString(modelKey(provider), null)
        return if (stored != null && MODEL_NAME.matches(stored)) {
            stored
        } else {
            provider.defaultModel
        }
    }

    fun setModel(provider: LlmProvider, model: String) {
        val normalized = model.trim()
        require(MODEL_NAME.matches(normalized)) { "The model name is invalid" }
        settings.edit { putString(modelKey(provider), normalized) }
    }

    fun getExerciseConfig(): ExerciseConfig {
        val storedCount = settings.getInt(
            KEY_EXERCISE_COUNT,
            ExerciseConfig.DEFAULT_EXERCISE_COUNT,
        ).coerceIn(ExerciseConfig.MIN_EXERCISE_COUNT, ExerciseConfig.MAX_EXERCISE_COUNT)
        val coverage = settings.getInt(
            KEY_COVERAGE_PERCENT,
            ExerciseConfig.DEFAULT_COVERAGE_PERCENT,
        ).coerceIn(ExerciseConfig.MIN_COVERAGE_PERCENT, ExerciseConfig.MAX_COVERAGE_PERCENT)
        val storedTypes = settings.getString(KEY_EXERCISE_TYPES, null)
        val types = storedTypes
            ?.split(',')
            ?.mapNotNullTo(linkedSetOf()) { name ->
                ExerciseType.entries.firstOrNull { it.name == name }
            }
            ?.takeIf { it.isNotEmpty() }
            ?: ExerciseType.entries.toSet()
        return ExerciseConfig(
            exerciseCount = storedCount.coerceAtLeast(types.size),
            selectedTypes = types,
            coveragePercent = coverage,
        )
    }

    fun saveExerciseConfig(config: ExerciseConfig) {
        val orderedTypes = ExerciseType.entries.filter { it in config.selectedTypes }
        settings.edit {
            putInt(KEY_EXERCISE_COUNT, config.exerciseCount)
            putInt(KEY_COVERAGE_PERCENT, config.coveragePercent)
            putString(KEY_EXERCISE_TYPES, orderedTypes.joinToString(",") { it.name })
        }
    }

    @Synchronized
    fun setApiKey(provider: LlmProvider, apiKey: String) {
        if (apiKey.isBlank()) {
            clearApiKey(provider)
            return
        }
        require(apiKey.length <= MAX_API_KEY_LENGTH && apiKey.none { it.isISOControl() }) {
            "The API key is invalid"
        }
        try {
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(provider))
            val encrypted = cipher.doFinal(apiKey.toByteArray(StandardCharsets.UTF_8))
            credentials.edit(commit = true) {
                putString(ivKey(provider), Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                putString(ciphertextKey(provider), Base64.encodeToString(encrypted, Base64.NO_WRAP))
            }
        } catch (_: Exception) {
            throw IllegalStateException("Unable to protect the API key")
        }
    }

    @Synchronized
    fun getApiKey(provider: LlmProvider): String? {
        val encodedIv = credentials.getString(ivKey(provider), null) ?: return null
        val encodedCiphertext = credentials.getString(ciphertextKey(provider), null) ?: return null
        return try {
            val keyStore = loadKeyStore()
            val key = keyStore.getKey(keyAlias(provider), null) as? SecretKey ?: run {
                clearApiKey(provider)
                return null
            }
            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, Base64.decode(encodedIv, Base64.NO_WRAP)),
            )
            val plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP))
            String(plaintext, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            clearApiKey(provider)
            null
        }
    }

    fun hasApiKey(provider: LlmProvider): Boolean = getApiKey(provider) != null

    @Synchronized
    fun clearApiKey(provider: LlmProvider) {
        credentials.edit(commit = true) {
            remove(ivKey(provider))
            remove(ciphertextKey(provider))
        }
        try {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(keyAlias(provider))) {
                keyStore.deleteEntry(keyAlias(provider))
            }
        } catch (_: Exception) {
            // Ciphertext is already gone, so the credential is disconnected.
        }
    }

    fun disconnect(provider: LlmProvider) = clearApiKey(provider)

    private fun getOrCreateSecretKey(provider: LlmProvider): SecretKey {
        val keyStore = loadKeyStore()
        (keyStore.getKey(keyAlias(provider), null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias(provider),
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadKeyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private fun modelKey(provider: LlmProvider) = "model_${provider.name.lowercase()}"
    private fun ivKey(provider: LlmProvider) = "iv_${provider.name.lowercase()}"
    private fun ciphertextKey(provider: LlmProvider) = "ciphertext_${provider.name.lowercase()}"
    private fun keyAlias(provider: LlmProvider) = "wordforge_llm_${provider.name.lowercase()}"

    companion object {
        const val CREDENTIAL_PREFERENCES_NAME = "wordforge_llm_credentials"
        const val SETTINGS_PREFERENCES_NAME = "wordforge_llm_settings"

        private const val KEY_PROVIDER = "provider"
        private const val KEY_EXERCISE_COUNT = "exercise_count"
        private const val KEY_COVERAGE_PERCENT = "coverage_percent"
        private const val KEY_EXERCISE_TYPES = "exercise_types"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val CIPHER_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val MAX_API_KEY_LENGTH = 4_096
        private val MODEL_NAME = Regex("^[A-Za-z0-9._-]{1,100}$")
    }
}
