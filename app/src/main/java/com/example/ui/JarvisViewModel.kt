package com.example.ui

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.ai.ActionPlan
import com.example.ai.LocalCommandEngine
import com.example.data.AutomationRule
import com.example.data.ExecutionLog
import com.example.data.JarvisRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.*

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING
}

data class MessageItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val plan: ActionPlan? = null,
    val timestamp: Long = System.currentTimeMillis()
)

data class JarvisUiState(
    val messages: List<MessageItem> = emptyList(),
    val voiceState: VoiceState = VoiceState.IDLE,
    val isWakeWordActive: Boolean = true,
    val rules: List<AutomationRule> = emptyList(),
    val logs: List<ExecutionLog> = emptyList(),
    val isAccessibilityEnabled: Boolean = false,
    val isNotificationListenerEnabled: Boolean = false
)

class JarvisViewModel(
    private val repository: JarvisRepository,
    private val commandEngine: LocalCommandEngine,
    private val context: Context
) : ViewModel(), TextToSpeech.OnInitListener {

    private val _uiState = MutableStateFlow(JarvisUiState())
    val uiState: StateFlow<JarvisUiState> = _uiState.asStateFlow()

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    init {
        tts = TextToSpeech(context, this)

        viewModelScope.launch {
            repository.allRules.collect { rules ->
                _uiState.update { it.copy(rules = rules) }
            }
        }

        viewModelScope.launch {
            repository.recentLogs.collect { logs ->
                _uiState.update { it.copy(logs = logs) }
            }
        }

        // Welcome greeting
        addSystemMessage("JARVIS-X online. Systems operational. Give a command or tap the microphone.")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            isTtsReady = true
        }
    }

    fun speak(text: String) {
        if (isTtsReady && text.isNotBlank()) {
            _uiState.update { it.copy(voiceState = VoiceState.SPEAKING) }
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "JARVIS_TTS_ID")
            viewModelScope.launch {
                kotlinx.coroutines.delay(2000)
                _uiState.update { it.copy(voiceState = VoiceState.IDLE) }
            }
        }
    }

    fun processUserCommand(inputPrompt: String) {
        if (inputPrompt.isBlank()) return

        val userMessage = MessageItem(text = inputPrompt, isUser = true)
        _uiState.update {
            it.copy(
                messages = it.messages + userMessage,
                voiceState = VoiceState.PROCESSING
            )
        }

        viewModelScope.launch {
            val plan = commandEngine.parseAndExecute(inputPrompt)

            val botMessage = MessageItem(
                text = plan.speechResponse,
                isUser = false,
                plan = plan
            )

            _uiState.update {
                it.copy(
                    messages = it.messages + botMessage,
                    voiceState = VoiceState.IDLE
                )
            }

            speak(plan.speechResponse)
        }
    }

    fun toggleRule(rule: AutomationRule) {
        viewModelScope.launch {
            repository.updateRule(rule.copy(isEnabled = !rule.isEnabled))
        }
    }

    fun deleteRule(ruleId: Long) {
        viewModelScope.launch {
            repository.deleteRuleById(ruleId)
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
        }
    }

    fun toggleWakeWord(active: Boolean) {
        _uiState.update { it.copy(isWakeWordActive = active) }
    }

    fun setVoiceState(state: VoiceState) {
        _uiState.update { it.copy(voiceState = state) }
    }

    fun updatePermissionStatuses(accessibilityEnabled: Boolean, notifEnabled: Boolean) {
        _uiState.update {
            it.copy(
                isAccessibilityEnabled = accessibilityEnabled,
                isNotificationListenerEnabled = notifEnabled
            )
        }
    }

    private fun addSystemMessage(text: String) {
        val sysMsg = MessageItem(text = text, isUser = false)
        _uiState.update { it.copy(messages = it.messages + sysMsg) }
    }

    override fun onCleared() {
        super.onCleared()
        tts?.stop()
        tts?.shutdown()
    }
}

class JarvisViewModelFactory(
    private val repository: JarvisRepository,
    private val commandEngine: LocalCommandEngine,
    private val context: Context
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(JarvisViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return JarvisViewModel(repository, commandEngine, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
