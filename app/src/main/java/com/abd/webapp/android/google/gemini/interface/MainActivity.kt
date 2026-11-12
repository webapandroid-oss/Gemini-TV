package com.abd.webapp.android.google.gemini.`interface`

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.abd.webapp.android.google.gemini.`interface`.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.util.Locale

data class ChatMessage(
  val id: String = System.currentTimeMillis().toString(),
  val sender: MessageSender,
  val text: String,
  val imageUrl: String? = null,
  val isImage: Boolean = false,
  val timestamp: Long = System.currentTimeMillis()
)

enum class MessageSender {
  USER, GEMINI
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  private var isTtsReady = false

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    try {
      tts = TextToSpeech(this, this)
    } catch (_: Exception) {}

    setContent {
      MyApplicationTheme(darkTheme = true) {
        GeminiAppScreen(
          onSpeak = { text -> speakText(text) },
          onStopSpeak = { stopSpeaking() },
          activity = this
        )
      }
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isTtsReady = true
      try {
        tts?.language = Locale.getDefault()
      } catch (_: Exception) {}
    }
  }

  private fun speakText(text: String) {
    if (isTtsReady && tts != null) {
      try {
        val clean = text.replace(Regex("""[*#_`>]"""), "")
        tts?.speak(clean, TextToSpeech.QUEUE_FLUSH, null, "gemini_tts")
      } catch (_: Exception) {}
    }
  }

  private fun stopSpeaking() {
    try {
      tts?.stop()
    } catch (_: Exception) {}
  }

  override fun onDestroy() {
    try {
      tts?.stop()
      tts?.shutdown()
    } catch (_: Exception) {}
    super.onDestroy()
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GeminiAppScreen(
  onSpeak: (String) -> Unit,
  onStopSpeak: () -> Unit,
  activity: ComponentActivity
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val listState = rememberLazyListState()

  // Preferences
  val sharedPrefs = remember { context.getSharedPreferences("gemini_prefs", Context.MODE_PRIVATE) }
  var apiKey by remember {
    mutableStateOf(sharedPrefs.getString("api_key", "") ?: "")
  }
  var selectedModel by remember {
    mutableStateOf(sharedPrefs.getString("model_name", "gemini-2.0-flash") ?: "gemini-2.0-flash")
  }
  var autoSpeak by remember {
    mutableStateOf(sharedPrefs.getBoolean("auto_speak", false))
  }

  // Gemini Service instance
  val geminiService = remember {
    GeminiService {
      if (apiKey.isNotEmpty()) apiKey else {
        try {
          val buildConfigClass = Class.forName("com.example.BuildConfig")
          (buildConfigClass.getField("GEMINI_API_KEY").get(null) as? String) ?: ""
        } catch (_: Exception) { "" }
      }
    }
  }

  // UI States
  var currentMode by remember { mutableStateOf("chat") } // "chat", "image", "voice"
  var inputText by remember { mutableStateOf("") }
  var isThinking by remember { mutableStateOf(false) }
  var isSpeakingAudio by remember { mutableStateOf(false) }
  var isListeningMic by remember { mutableStateOf(false) }
  var showSettingsDialog by remember { mutableStateOf(false) }
  val messages = remember { mutableStateListOf<ChatMessage>() }

  // Speech Recognizer
  var speechRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startSpeechListening(context, { isListeningMic = it }, { text ->
        inputText = text
      })
    } else {
      Toast.makeText(context, "Microphone permission required for voice dictation", Toast.LENGTH_SHORT).show()
    }
  }

  // Send message action
  fun handleSend(promptText: String) {
    val cleanPrompt = promptText.trim()
    if (cleanPrompt.isEmpty() || isThinking) return

    val isImageQuery = currentMode == "image" ||
      cleanPrompt.contains("ন্যানো ব্যানানা", ignoreCase = true) ||
      cleanPrompt.contains("nano banana", ignoreCase = true) ||
      cleanPrompt.contains("ছবি আঁকো", ignoreCase = true) ||
      cleanPrompt.contains("draw image", ignoreCase = true) ||
      cleanPrompt.contains("generate image", ignoreCase = true)

    // Append User Message
    messages.add(ChatMessage(sender = MessageSender.USER, text = cleanPrompt))
    inputText = ""
    isThinking = true

    coroutineScope.launch {
      listState.animateScrollToItem(maxOf(0, messages.size - 1))

      // Check device actions first (Wi-Fi, Bluetooth, Alarm)
      val lower = cleanPrompt.lowercase(Locale.ROOT)
      if (lower.contains("wifi") || lower.contains("ওয়াইফাই")) {
        val enable = lower.contains("চালু") || lower.contains("on") || lower.contains("অন")
        val action = if (enable) "ACTION_WIFI_ON" else "ACTION_WIFI_OFF"
        val feedback = GeminiProcessor.executeDeviceAction(action, "", activity)
        messages.add(ChatMessage(sender = MessageSender.GEMINI, text = "⚙️ $feedback"))
        isThinking = false
        listState.animateScrollToItem(maxOf(0, messages.size - 1))
        return@launch
      } else if (lower.contains("bluetooth") || lower.contains("ব্লুটুথ")) {
        val enable = lower.contains("চালু") || lower.contains("on") || lower.contains("অন")
        val action = if (enable) "ACTION_BLUETOOTH_ON" else "ACTION_BLUETOOTH_OFF"
        val feedback = GeminiProcessor.executeDeviceAction(action, "", activity)
        messages.add(ChatMessage(sender = MessageSender.GEMINI, text = "🔵 $feedback"))
        isThinking = false
        listState.animateScrollToItem(maxOf(0, messages.size - 1))
        return@launch
      } else if (lower.contains("alarm") || lower.contains("অ্যালার্ম")) {
        val feedback = GeminiProcessor.executeDeviceAction("ACTION_SET_ALARM", cleanPrompt, activity)
        messages.add(ChatMessage(sender = MessageSender.GEMINI, text = "⏰ $feedback"))
        isThinking = false
        listState.animateScrollToItem(maxOf(0, messages.size - 1))
        return@launch
      }

      if (isImageQuery) {
        // Nano Banana Image Generation
        val imageResult = geminiService.generateNanoBananaImage(cleanPrompt)
        imageResult.onSuccess { url ->
          messages.add(
            ChatMessage(
              sender = MessageSender.GEMINI,
              text = "🍌 **ন্যানো ব্যানানা (Nano Banana) এআই ইমেজ তৈরি সম্পন্ন!**\n\nপ্রম্পট: \"$cleanPrompt\"",
              imageUrl = url,
              isImage = true
            )
          )
        }.onFailure { err ->
          messages.add(
            ChatMessage(
              sender = MessageSender.GEMINI,
              text = "⚠️ ন্যানো ব্যানানা দিয়ে ছবি তৈরি করতে সমস্যা হয়েছে: ${err.message}"
            )
          )
        }
      } else {
        // Conversational AI Generation
        val historyList = messages.map {
          Pair(if (it.sender == MessageSender.USER) "user" else "model", it.text)
        }
        val textResult = geminiService.generateContent(cleanPrompt, selectedModel, historyList)
        textResult.onSuccess { reply ->
          messages.add(ChatMessage(sender = MessageSender.GEMINI, text = reply))
          if (autoSpeak) {
            onSpeak(reply)
            isSpeakingAudio = true
          }
        }.onFailure { err ->
          messages.add(
            ChatMessage(
              sender = MessageSender.GEMINI,
              text = "⚠️ গুগল জেমিনি এআই রেসপন্স ত্রুটি: ${err.message}"
            )
          )
        }
      }

      isThinking = false
      listState.animateScrollToItem(maxOf(0, messages.size - 1))
    }
  }

  // Layout Colors
  val bgGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF060913), Color(0xFF0F172A), Color(0xFF080C16))
  )
  val geminiSparkleBrush = Brush.linearGradient(
    colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFC084FC))
  )

  Scaffold(
    modifier = Modifier
      .fillMaxSize()
      .testTag("gemini_main_screen"),
    containerColor = Color.Transparent
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(bgGradient)
        .padding(padding)
    ) {
      // 1. Top Bar
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(CircleShape)
              .background(geminiSparkleBrush),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = "Gemini Sparkle",
              tint = Color.White,
              modifier = Modifier.size(26.dp)
            )
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Google Gemini",
              fontSize = 20.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "Multimodal AI • Nano Banana Inside",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          // Status indicator
          Box(
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .background(Color(0xFF1E293B))
              .padding(horizontal = 12.dp, vertical = 6.dp)
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(8.dp)
                  .clip(CircleShape)
                  .background(Color(0xFF10B981))
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "Gemini 2.0",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE2E8F0)
              )
            }
          }

          Spacer(modifier = Modifier.width(8.dp))

          IconButton(
            onClick = { showSettingsDialog = true },
            modifier = Modifier.testTag("settings_button")
          ) {
            Icon(
              imageVector = Icons.Default.Settings,
              contentDescription = "Settings",
              tint = Color(0xFFCBD5E1)
            )
          }
        }
      }

      // 2. Mode Selector (Chat, Nano Banana Image, Voice Talk)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        FilterChip(
          selected = currentMode == "chat",
          onClick = { currentMode = "chat" },
          label = { Text("💬 Chat") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFF38BDF8).copy(alpha = 0.25f),
            selectedLabelColor = Color(0xFF38BDF8)
          ),
          modifier = Modifier.testTag("mode_chat")
        )

        FilterChip(
          selected = currentMode == "image",
          onClick = { currentMode = "image" },
          label = { Text("🍌 ন্যানো ব্যানানা (Image)") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFFBBF24).copy(alpha = 0.25f),
            selectedLabelColor = Color(0xFFFBBF24)
          ),
          modifier = Modifier.testTag("mode_image")
        )

        FilterChip(
          selected = currentMode == "voice",
          onClick = {
            currentMode = "voice"
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
              startSpeechListening(context, { isListeningMic = it }, { text -> inputText = text })
            } else {
              micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          label = { Text("🎙️ Voice Talk") },
          colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Color(0xFFA855F7).copy(alpha = 0.25f),
            selectedLabelColor = Color(0xFFA855F7)
          ),
          modifier = Modifier.testTag("mode_voice")
        )
      }

      // 3. Message Feed
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
      ) {
        if (messages.isEmpty()) {
          // Welcome Suggestions Card
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Icon(
              imageVector = Icons.Default.AutoAwesome,
              contentDescription = null,
              tint = Color(0xFF818CF8),
              modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Google Gemini এআই-তে স্বাগতম",
              fontSize = 22.sp,
              fontWeight = FontWeight.Bold,
              color = Color.White
            )
            Text(
              text = "যেকোনো বিষয়ে প্রশ্ন করুন অথবা ন্যানো ব্যানানা দিয়ে ছবি তৈরি করুন",
              fontSize = 14.sp,
              color = Color(0xFF94A3B8),
              textAlign = TextAlign.Center,
              modifier = Modifier.padding(top = 4.dp, bottom = 24.dp)
            )

            // Quick suggestion chips
            val suggestions = listOf(
              "🍌 ন্যানো ব্যানানা: নিয়ন সাইবারপাঙ্ক শহরের ছবি আঁকো",
              "🌌 ব্ল্যাক হোল কীভাবে মহাবিশ্বে কাজ করে?",
              "📶 ওয়াইফাই চালু করো",
              "⏰ সকাল ৭টায় অ্যালার্ম দাও",
              "💻 Kotlin Coroutine কীভাবে কাজ করে ব্যাখ্যা করো"
            )

            Column(
              verticalArrangement = Arrangement.spacedBy(8.dp),
              modifier = Modifier.widthIn(max = 500.dp)
            ) {
              suggestions.forEach { prompt ->
                Surface(
                  onClick = { handleSend(prompt) },
                  shape = RoundedCornerShape(14.dp),
                  color = Color(0xFF1E293B).copy(alpha = 0.7f),
                  border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Text(
                      text = prompt,
                      color = Color(0xFFE2E8F0),
                      fontSize = 14.sp,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              }
            }
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
          ) {
            items(messages, key = { it.id }) { message ->
              MessageBubble(
                message = message,
                onSpeak = { text ->
                  onSpeak(text)
                  isSpeakingAudio = true
                },
                onStopSpeak = {
                  onStopSpeak()
                  isSpeakingAudio = false
                }
              )
            }

            if (isThinking) {
              item {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF38BDF8)
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = if (currentMode == "image") "ন্যানো ব্যানানা ছবি তৈরি করছে..." else "Gemini চিন্তা করছে...",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                  )
                }
              }
            }
          }
        }
      }

      // 4. Bottom Input Dock
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.95f),
        border = CardDefaults.outlinedCardBorder().copy(brush = Brush.horizontalGradient(listOf(Color(0xFF38BDF8).copy(alpha = 0.4f), Color(0xFFC084FC).copy(alpha = 0.4f))))
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Mic Dictation Button
          IconButton(
            onClick = {
              if (isListeningMic) {
                speechRecognizer?.stopListening()
                isListeningMic = false
              } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                  startSpeechListening(context, { isListeningMic = it }, { text -> inputText = text })
                } else {
                  micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
              }
            },
            modifier = Modifier.testTag("dictate_button")
          ) {
            Icon(
              imageVector = if (isListeningMic) Icons.Default.MicOff else Icons.Default.Mic,
              contentDescription = "Voice Dictate",
              tint = if (isListeningMic) Color(0xFFEF4444) else Color(0xFF38BDF8)
            )
          }

          // Input Text Field
          TextField(
            value = inputText,
            onValueChange = { inputText = it },
            modifier = Modifier
              .weight(1f)
              .testTag("ask_gemini_input"),
            placeholder = {
              Text(
                text = if (currentMode == "image") "ন্যানো ব্যানানার জন্য ছবির বিবরণ দিন..." else "Ask Gemini anything...",
                color = Color(0xFF64748B),
                fontSize = 15.sp
              )
            },
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent,
              cursorColor = Color(0xFF38BDF8),
              focusedTextColor = Color.White,
              unfocusedTextColor = Color.White
            ),
            singleLine = true
          )

          // Send Button
          IconButton(
            onClick = { handleSend(inputText) },
            enabled = inputText.trim().isNotEmpty() && !isThinking,
            modifier = Modifier.testTag("send_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Send,
              contentDescription = "Send",
              tint = if (inputText.trim().isNotEmpty() && !isThinking) Color(0xFF38BDF8) else Color(0xFF475569)
            )
          }
        }
      }
    }
  }

  // Settings Dialog
  if (showSettingsDialog) {
    var tempKey by remember { mutableStateOf(apiKey) }
    var tempModel by remember { mutableStateOf(selectedModel) }
    var tempAutoSpeak by remember { mutableStateOf(autoSpeak) }

    AlertDialog(
      onDismissRequest = { showSettingsDialog = false },
      title = { Text("Gemini AI Settings", color = Color.White, fontWeight = FontWeight.Bold) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text("Gemini API Key (Google AI Studio):", fontSize = 13.sp, color = Color(0xFF94A3B8))
          OutlinedTextField(
            value = tempKey,
            onValueChange = { tempKey = it },
            placeholder = { Text("Enter your Gemini API key (optional)", color = Color(0xFF64748B)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("api_key_input")
          )

          Text("Model Selection:", fontSize = 13.sp, color = Color(0xFF94A3B8))
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("gemini-2.0-flash", "gemini-1.5-flash", "gemini-1.5-pro").forEach { mod ->
              FilterChip(
                selected = tempModel == mod,
                onClick = { tempModel = mod },
                label = { Text(mod.replace("gemini-", "")) }
              )
            }
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
              checked = tempAutoSpeak,
              onCheckedChange = { tempAutoSpeak = it }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Auto-read responses with Voice (TTS)", color = Color(0xFFE2E8F0), fontSize = 14.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            apiKey = tempKey.trim()
            selectedModel = tempModel
            autoSpeak = tempAutoSpeak
            sharedPrefs.edit()
              .putString("api_key", apiKey)
              .putString("model_name", selectedModel)
              .putBoolean("auto_speak", autoSpeak)
              .apply()
            showSettingsDialog = false
            Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
          }
        ) {
          Text("Save")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSettingsDialog = false }) {
          Text("Cancel")
        }
      },
      containerColor = Color(0xFF1E293B),
      textContentColor = Color(0xFFE2E8F0)
    )
  }
}

@Composable
fun MessageBubble(
  message: ChatMessage,
  onSpeak: (String) -> Unit,
  onStopSpeak: () -> Unit
) {
  val context = LocalContext.current
  val isUser = message.sender == MessageSender.USER
  var isPlaying by remember { mutableStateOf(false) }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    if (!isUser) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFFC084FC)))),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = "Gemini",
          tint = Color.White,
          modifier = Modifier.size(20.dp)
        )
      }
      Spacer(modifier = Modifier.width(10.dp))
    }

    Column(
      modifier = Modifier
        .widthIn(max = 520.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(
          if (isUser) Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF38BDF8)))
          else Brush.linearGradient(listOf(Color(0xFF1E293B), Color(0xFF182234)))
        )
        .border(
          width = 1.dp,
          color = if (isUser) Color(0xFF60A5FA).copy(alpha = 0.5f) else Color(0xFF334155),
          shape = RoundedCornerShape(18.dp)
        )
        .padding(14.dp)
    ) {
      Text(
        text = message.text,
        color = Color.White,
        fontSize = 15.sp,
        lineHeight = 22.sp
      )

      if (message.isImage && message.imageUrl != null) {
        Spacer(modifier = Modifier.height(10.dp))
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(message.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = "Nano Banana Generated Image",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(12.dp))
        )
      }

      if (!isUser) {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Copy button
          IconButton(
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Response", message.text))
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ContentCopy,
              contentDescription = "Copy",
              tint = Color(0xFF94A3B8),
              modifier = Modifier.size(16.dp)
            )
          }

          // TTS Speak button
          IconButton(
            onClick = {
              if (isPlaying) {
                onStopSpeak()
                isPlaying = false
              } else {
                onSpeak(message.text)
                isPlaying = true
              }
            },
            modifier = Modifier.size(32.dp)
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
              contentDescription = "Speak",
              tint = if (isPlaying) Color(0xFF38BDF8) else Color(0xFF94A3B8),
              modifier = Modifier.size(18.dp)
            )
          }
        }
      }
    }
  }
}

private fun startSpeechListening(
  context: Context,
  onListeningState: (Boolean) -> Unit,
  onResult: (String) -> Unit
) {
  if (!SpeechRecognizer.isRecognitionAvailable(context)) {
    Toast.makeText(context, "Speech recognition not available", Toast.LENGTH_SHORT).show()
    return
  }

  val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Gemini...")
  }

  try {
    val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
    recognizer.setRecognitionListener(object : RecognitionListener {
      override fun onReadyForSpeech(params: Bundle?) { onListeningState(true) }
      override fun onBeginningOfSpeech() {}
      override fun onRmsChanged(rmsdB: Float) {}
      override fun onBufferReceived(buffer: ByteArray?) {}
      override fun onEndOfSpeech() { onListeningState(false) }
      override fun onError(error: Int) { onListeningState(false) }
      override fun onResults(results: Bundle?) {
        onListeningState(false)
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        if (!matches.isNullOrEmpty()) {
          onResult(matches[0])
        }
      }
      override fun onPartialResults(partialResults: Bundle?) {}
      override fun onEvent(eventType: Int, params: Bundle?) {}
    })
    recognizer.startListening(intent)
  } catch (e: Exception) {
    onListeningState(false)
  }
}
