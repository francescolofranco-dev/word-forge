package com.wordforge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wordforge.data.LlmSettingsStore
import com.wordforge.data.WordDatabase
import com.wordforge.data.WordRepository
import com.wordforge.exercise.ExerciseConfig
import com.wordforge.exercise.ExerciseGenerationException
import com.wordforge.exercise.ExerciseGeneratorFactory
import com.wordforge.exercise.ExercisePack
import com.wordforge.exercise.ExerciseSessionRequest
import com.wordforge.exercise.ExerciseType
import com.wordforge.exercise.ExerciseWordSelector
import com.wordforge.exercise.LlmProvider
import com.wordforge.exercise.toJsonString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

data class LlmSettingsUiState(
    val provider: LlmProvider,
    val model: String,
    val isConnected: Boolean,
    val exerciseConfig: ExerciseConfig,
    val errorMessage: String? = null,
)

sealed interface ExerciseGenerationState {
    data object Idle : ExerciseGenerationState
    data object Loading : ExerciseGenerationState
    data class Ready(
        val pack: ExercisePack,
        val sessionJson: String,
        val answerStateJson: String = "",
    ) : ExerciseGenerationState

    data class Error(val message: String) : ExerciseGenerationState
}

/** Owns provider configuration and one in-memory generated practice session. */
class ExerciseSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsStore = LlmSettingsStore(application)
    private val repository = WordRepository(
        WordDatabase.getDatabase(application).wordDao()
    )

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<LlmSettingsUiState> = _settings.asStateFlow()

    private val _generationState = MutableStateFlow<ExerciseGenerationState>(
        ExerciseGenerationState.Idle
    )
    val generationState: StateFlow<ExerciseGenerationState> = _generationState.asStateFlow()

    private var generationJob: Job? = null

    fun saveConnection(provider: LlmProvider, model: String, apiKey: String): Boolean {
        return try {
            settingsStore.setModel(provider, model)
            settingsStore.setApiKey(provider, apiKey)
            settingsStore.provider = provider
            _settings.value = readSettings()
            _generationState.value = ExerciseGenerationState.Idle
            true
        } catch (error: IllegalArgumentException) {
            _settings.value = readSettings(
                error.message ?: "The provider settings are invalid"
            )
            false
        } catch (_: IllegalStateException) {
            _settings.value = readSettings("The API key could not be protected on this device")
            false
        }
    }

    fun disconnect() {
        val provider = settingsStore.provider
        settingsStore.disconnect(provider)
        _settings.value = readSettings()
        clearSession()
    }

    fun generate(config: ExerciseConfig) {
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            _generationState.value = ExerciseGenerationState.Loading
            try {
                val provider = settingsStore.provider
                val apiKey = settingsStore.getApiKey(provider) ?: run {
                    _settings.value = readSettings()
                    throw ExerciseGenerationException.InvalidConfiguration(
                        "Reconnect ${provider.displayName} before generating a session"
                    )
                }
                settingsStore.saveExerciseConfig(config)
                _settings.value = readSettings()

                val now = System.currentTimeMillis()
                val words = repository.getAllOnce()
                val selectedWords = ExerciseWordSelector.select(
                    words = words,
                    config = config,
                    now = now,
                    sessionSeed = now,
                )
                if (selectedWords.isEmpty()) {
                    val message = when {
                        words.isEmpty() -> "Add at least one learning item before starting a session"
                        ExerciseType.TRANSLATION in config.selectedTypes ->
                            "Translation exercises require a simple word with a stored meaning"
                        ExerciseType.MATCHING in config.selectedTypes ->
                            "Matching exercises require at least two learning items"
                        else -> "The selected exercise mix is not compatible with this vocabulary"
                    }
                    throw ExerciseGenerationException.InvalidConfiguration(
                        message
                    )
                }
                val request = ExerciseSessionRequest(
                    config = config,
                    selectedWords = selectedWords,
                )
                val pack = ExerciseGeneratorFactory.create(
                    provider = provider,
                    apiKey = apiKey,
                    model = settingsStore.getModel(provider),
                ).generate(request)
                _generationState.value = ExerciseGenerationState.Ready(
                    pack = pack,
                    sessionJson = pack.toJsonString(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: ExerciseGenerationException) {
                _generationState.value = ExerciseGenerationState.Error(
                    error.message ?: "The provider could not generate a valid session"
                )
            } catch (_: Throwable) {
                _generationState.value = ExerciseGenerationState.Error(
                    "The session could not be generated"
                )
            }
        }
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _generationState.value = ExerciseGenerationState.Idle
    }

    fun clearSession() {
        cancelGeneration()
    }

    fun updateAnswerState(stateJson: String) {
        val ready = _generationState.value as? ExerciseGenerationState.Ready ?: return
        if (!isValidAnswerState(stateJson, ready.pack.exercises.size)) return
        _generationState.value = ready.copy(answerStateJson = stateJson)
    }

    private fun readSettings(errorMessage: String? = null): LlmSettingsUiState {
        val provider = settingsStore.provider
        return LlmSettingsUiState(
            provider = provider,
            model = settingsStore.getModel(provider),
            isConnected = settingsStore.hasApiKey(provider),
            exerciseConfig = settingsStore.getExerciseConfig(),
            errorMessage = errorMessage,
        )
    }

    private fun isValidAnswerState(raw: String, exerciseCount: Int): Boolean {
        if (raw.length !in 1..MAX_ANSWER_STATE_LENGTH) return false
        return try {
            val root = JSONObject(raw)
            if (root.length() != 3 || root.optInt("version", -1) != 1) return false
            val answers = root.optJSONArray("answers") ?: return false
            val completed = root.optJSONArray("completed") ?: return false
            if (answers.length() != exerciseCount || completed.length() != exerciseCount) {
                return false
            }
            for (index in 0 until exerciseCount) {
                if (completed.opt(index) !is Boolean) return false
                when (val answer = answers.opt(index)) {
                    null, JSONObject.NULL -> Unit
                    is Number -> {
                        val asInt = answer.toInt()
                        if (
                            answer.toDouble() != asInt.toDouble() ||
                            asInt !in -1..ExerciseConfig.MAX_EXERCISE_COUNT
                        ) return false
                    }
                    is String -> if (answer.length > MAX_WRITTEN_ANSWER_LENGTH) return false
                    is JSONArray -> {
                        if (answer.length() > 8) return false
                        for (answerIndex in 0 until answer.length()) {
                            val value = answer.opt(answerIndex)
                            if (value !is String || value.length > 3) return false
                        }
                    }
                    else -> return false
                }
            }
            true
        } catch (_: RuntimeException) {
            false
        }
    }

    private companion object {
        const val MAX_ANSWER_STATE_LENGTH = 32 * 1024
        const val MAX_WRITTEN_ANSWER_LENGTH = 500
    }
}
