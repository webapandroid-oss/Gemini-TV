package com.abd.webapp.android.google.gemini.`interface`

import android.app.Activity
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(
  val id: String = "${System.currentTimeMillis()}_${System.nanoTime()}",
  val sender: MessageSender,
  val text: String,
  val imageUrl: String? = null,
  val isImage: Boolean = false,
  val modelUsed: String? = null,
  val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
  USER, GEMINI
}

data class GeminiUiState(
  val apiKey: String = "",
  val selectedModel: String = GeminiService.MODEL_GEMINI_3_8,
  val autoSpeak: Boolean = false,
  val currentMode: String = "chat",
  val messages: List<ChatMessage> = emptyList(),
  val isThinking: Boolean = false,
  val isSpeakingAudio: Boolean = false,
  val isListeningMic: Boolean = false,
  val showSettingsDialog: Boolean = false,
  val showReleaseNotesDialog: Boolean = false
)

class GeminiViewModel(application: Application) : AndroidViewModel(application) {

  private val sharedPrefs = application.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE)

  private val _uiState = MutableStateFlow(loadInitialState())
  val uiState: StateFlow<GeminiUiState> = _uiState.asStateFlow()

  private val geminiService = GeminiService {
    val currentKey = _uiState.value.apiKey
    if (currentKey.isNotEmpty()) currentKey
    else {
      try {
        val buildConfigClass = Class.forName("com.example.BuildConfig")
        (buildConfigClass.getField("GEMINI_API_KEY").get(null) as? String) ?: ""
      } catch (_: Exception) {
        ""
      }
    }
  }

  private fun loadInitialState(): GeminiUiState {
    val savedKey = sharedPrefs.getString("api_key", "") ?: ""
    val savedModel = sharedPrefs.getString("model_name", GeminiService.MODEL_GEMINI_3_8)
      ?: GeminiService.MODEL_GEMINI_3_8
    val migratedModel = GeminiService.normalizeModelId(savedModel)
    if (migratedModel != savedModel) {
      sharedPrefs.edit().putString("model_name", migratedModel).apply()
    }
    val autoSpeak = sharedPrefs.getBoolean("auto_speak", false)
    return GeminiUiState(
      apiKey = savedKey,
      selectedModel = migratedModel,
      autoSpeak = autoSpeak
    )
  }

  fun selectModel(model: String) {
    val normalized = GeminiService.normalizeModelId(model)
    sharedPrefs.edit().putString("model_name", normalized).apply()
    _uiState.update { it.copy(selectedModel = normalized) }
  }

  fun setMode(mode: String) {
    _uiState.update { it.copy(currentMode = mode) }
  }

  fun setListeningMic(listening: Boolean) {
    _uiState.update { it.copy(isListeningMic = listening) }
  }

  fun setSpeakingAudio(speaking: Boolean) {
    _uiState.update { it.copy(isSpeakingAudio = speaking) }
  }

  fun openSettings(open: Boolean) {
    _uiState.update { it.copy(showSettingsDialog = open) }
  }

  fun openReleaseNotes(open: Boolean) {
    _uiState.update { it.copy(showReleaseNotesDialog = open) }
  }

  fun saveSettings(apiKey: String, model: String, autoSpeak: Boolean) {
    val cleanKey = apiKey.trim()
    val normalized = GeminiService.normalizeModelId(model)
    sharedPrefs.edit()
      .putString("api_key", cleanKey)
      .putString("model_name", normalized)
      .putBoolean("auto_speak", autoSpeak)
      .apply()
    _uiState.update {
      it.copy(
        apiKey = cleanKey,
        selectedModel = normalized,
        autoSpeak = autoSpeak,
        showSettingsDialog = false
      )
    }
  }

  fun clearMessages() {
    _uiState.update { it.copy(messages = emptyList()) }
  }

  fun sendPrompt(
    promptText: String,
    activity: Activity,
    onAutoSpeak: (String) -> Unit
  ) {
    val cleanPrompt = promptText.trim()
    val current = _uiState.value
    if (cleanPrompt.isEmpty() || current.isThinking) return

    val isImageQuery = current.currentMode == "image" ||
      cleanPrompt.contains("ন্যানো ব্যানানা", ignoreCase = true) ||
      cleanPrompt.contains("nano banana", ignoreCase = true) ||
      cleanPrompt.contains("ছবি আঁকো", ignoreCase = true) ||
      cleanPrompt.contains("draw image", ignoreCase = true) ||
      cleanPrompt.contains("generate image", ignoreCase = true)

    val userMessage = ChatMessage(sender = MessageSender.USER, text = cleanPrompt)
    _uiState.update {
      it.copy(
        messages = it.messages + userMessage,
        isThinking = true
      )
    }

    viewModelScope.launch {
      val lower = cleanPrompt.lowercase(Locale.ROOT)
      if (lower.contains("wifi") || lower.contains("ওয়াইফাই")) {
        val enable = lower.contains("চালু") || lower.contains("on") || lower.contains("অন")
        val action = if (enable) "ACTION_WIFI_ON" else "ACTION_WIFI_OFF"
        val feedback = GeminiProcessor.executeDeviceAction(action, "", activity)
        appendGeminiReply("⚙️ $feedback", modelUsed = current.selectedModel)
        return@launch
      } else if (lower.contains("bluetooth") || lower.contains("ব্লুটুথ")) {
        val enable = lower.contains("চালু") || lower.contains("on") || lower.contains("অন")
        val action = if (enable) "ACTION_BLUETOOTH_ON" else "ACTION_BLUETOOTH_OFF"
        val feedback = GeminiProcessor.executeDeviceAction(action, "", activity)
        appendGeminiReply("🔵 $feedback", modelUsed = current.selectedModel)
        return@launch
      } else if (lower.contains("alarm") || lower.contains("অ্যালার্ম")) {
        val feedback = GeminiProcessor.executeDeviceAction("ACTION_SET_ALARM", cleanPrompt, activity)
        appendGeminiReply("⏰ $feedback", modelUsed = current.selectedModel)
        return@launch
      }

      if (isImageQuery) {
        val imageResult = geminiService.generateNanoBananaImage(cleanPrompt)
        imageResult.onSuccess { url ->
          appendGeminiReply(
            text = "🍌 **ন্যানো ব্যানানা (Nano Banana) এআই ইমেজ তৈরি সম্পন্ন!**\n\nপ্রম্পট: \"$cleanPrompt\"",
            imageUrl = url,
            isImage = true,
            modelUsed = "Nano Banana"
          )
        }.onFailure { err ->
          appendGeminiReply(
            text = "⚠️ ন্যানো ব্যানানা দিয়ে ছবি তৈরি করতে সমস্যা হয়েছে: ${err.message}",
            modelUsed = current.selectedModel
          )
        }
      } else {
        val historyList = _uiState.value.messages.map {
          Pair(if (it.sender == MessageSender.USER) "user" else "model", it.text)
        }
        val activeModel = current.selectedModel
        val textResult = geminiService.generateContent(cleanPrompt, activeModel, historyList)
        textResult.onSuccess { reply ->
          appendGeminiReply(reply, modelUsed = activeModel)
          if (_uiState.value.autoSpeak) {
            onAutoSpeak(reply)
            _uiState.update { it.copy(isSpeakingAudio = true) }
          }
        }.onFailure { err ->
          appendGeminiReply(
            text = "⚠️ গুগল জেমিনি এআই রেসপন্স ত্রুটি: ${err.message}",
            modelUsed = activeModel
          )
        }
      }
    }
  }

  private fun appendGeminiReply(
    text: String,
    imageUrl: String? = null,
    isImage: Boolean = false,
    modelUsed: String? = null
  ) {
    val replyMessage = ChatMessage(
      sender = MessageSender.GEMINI,
      text = text,
      imageUrl = imageUrl,
      isImage = isImage,
      modelUsed = modelUsed
    )
    _uiState.update {
      it.copy(
        messages = it.messages + replyMessage,
        isThinking = false
      )
    }
  }
}
