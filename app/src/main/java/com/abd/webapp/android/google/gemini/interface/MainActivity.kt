package com.abd.webapp.android.google.gemini.`interface`

import android.Manifest
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
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abd.webapp.android.google.gemini.`interface`.ui.theme.MyApplicationTheme
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

  private var tts: TextToSpeech? = null
  private var isTtsReady = false

  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
    )
    super.onCreate(savedInstanceState)
    try {
      tts = TextToSpeech(this, this)
    } catch (_: Exception) {}

    setContent {
      MyApplicationTheme(darkTheme = true) {
        val viewModel: GeminiViewModel = viewModel()
        GeminiTvScreen(
          viewModel = viewModel,
          onSpeak = { text -> speakText(text) },
          onStopSpeak = { stopSpeaking() },
          activity = this
        )
      }
    }
  }

  override fun onStop() {
    // Pause TTS audio playback when Android TV enters standby / low-power mode
    stopSpeaking()
    super.onStop()
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

@Composable
fun GeminiTvScreen(
  viewModel: GeminiViewModel,
  onSpeak: (String) -> Unit,
  onStopSpeak: () -> Unit,
  activity: ComponentActivity
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val listState = rememberLazyListState()

  var inputValue by remember { mutableStateOf(TextFieldValue("")) }
  var isInputFocused by remember { mutableStateOf(false) }
  val initialFocusRequester = remember { FocusRequester() }
  val inputFocusRequester = remember { FocusRequester() }

  // Request initial TV remote focus on launch so the user sees an active D-pad cursor immediately
  LaunchedEffect(Unit) {
    try {
      initialFocusRequester.requestFocus()
    } catch (_: Exception) {}
  }

  // Keep latest message scrolled into view
  LaunchedEffect(uiState.messages.size, uiState.isThinking) {
    if (uiState.messages.isNotEmpty()) {
      listState.animateScrollToItem(uiState.messages.lastIndex)
    }
  }

  // Handle TV Remote Back button cleanly across modes
  BackHandler(enabled = uiState.currentMode != "chat") {
    viewModel.setMode("chat")
  }

  val micPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    if (isGranted) {
      startSpeechListening(
        context = context,
        onListeningState = { viewModel.setListeningMic(it) },
        onResult = { text ->
          inputValue = TextFieldValue(text, TextRange(text.length))
        }
      )
    } else {
      Toast.makeText(context, "Microphone permission required for voice dictation", Toast.LENGTH_SHORT).show()
    }
  }

  fun submitPrompt(prompt: String) {
    val clean = prompt.trim()
    if (clean.isEmpty() || uiState.isThinking) return
    inputValue = TextFieldValue("")
    viewModel.sendPrompt(clean, activity, onSpeak)
  }

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
    containerColor = Color.Transparent,
    contentWindowInsets = WindowInsets.safeDrawing
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .background(bgGradient)
        .padding(padding)
        // TV overscan-safe margins
        .padding(horizontal = 24.dp, vertical = 10.dp)
    ) {
      // 1. TV Top Bar (Horizontal D-pad Left/Right traversal)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(46.dp)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Google Gemini TV",
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
              Spacer(modifier = Modifier.width(10.dp))
              TvFocusableChip(
                selected = true,
                label = "v${GeminiService.APP_SEMVER}",
                accentColor = Color(0xFF38BDF8),
                onClick = { viewModel.openReleaseNotes(true) },
                modifier = Modifier.testTag("release_notes_button")
              )
            }
            Text(
              text = "Gemini 3.8 & 3.8 Pro • Android TV D-Pad Optimized",
              fontSize = 12.sp,
              color = Color(0xFF94A3B8)
            )
          }
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          val isPro = uiState.selectedModel == GeminiService.MODEL_GEMINI_3_8_PRO

          TvFocusableChip(
            selected = !isPro,
            label = "3.8",
            accentColor = Color(0xFF38BDF8),
            showStatusDot = true,
            onClick = { viewModel.selectModel(GeminiService.MODEL_GEMINI_3_8) },
            modifier = Modifier.testTag("model_chip_3_8")
          )

          TvFocusableChip(
            selected = isPro,
            label = "3.8 Pro",
            accentColor = Color(0xFFC084FC),
            showStatusDot = true,
            onClick = { viewModel.selectModel(GeminiService.MODEL_GEMINI_3_8_PRO) },
            modifier = Modifier.testTag("model_chip_3_8_pro")
          )

          TvFocusableIconButton(
            icon = Icons.Default.NewReleases,
            contentDescription = "What's New in v3.0.0",
            tint = Color(0xFF38BDF8),
            onClick = { viewModel.openReleaseNotes(true) },
            modifier = Modifier.testTag("changelog_icon_button")
          )

          TvFocusableIconButton(
            icon = Icons.Default.Settings,
            contentDescription = "Settings",
            tint = Color(0xFFCBD5E1),
            onClick = { viewModel.openSettings(true) },
            modifier = Modifier.testTag("settings_button")
          )
        }
      }

      // 2. Mode Selector Row (Chat, Nano Banana Image, Voice Talk, Clear)
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        TvFocusableChip(
          selected = uiState.currentMode == "chat",
          label = "💬 Chat",
          accentColor = Color(0xFF38BDF8),
          onClick = { viewModel.setMode("chat") },
          modifier = Modifier.testTag("mode_chat")
        )

        TvFocusableChip(
          selected = uiState.currentMode == "image",
          label = "🍌 ন্যানো ব্যানানা (Image)",
          accentColor = Color(0xFFFBBF24),
          onClick = { viewModel.setMode("image") },
          modifier = Modifier.testTag("mode_image")
        )

        TvFocusableChip(
          selected = uiState.currentMode == "voice",
          label = "🎙️ Voice Talk",
          accentColor = Color(0xFFA855F7),
          onClick = {
            viewModel.setMode("voice")
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
              startSpeechListening(
                context = context,
                onListeningState = { viewModel.setListeningMic(it) },
                onResult = { text -> inputValue = TextFieldValue(text, TextRange(text.length)) }
              )
            } else {
              micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          },
          modifier = Modifier.testTag("mode_voice")
        )

        if (uiState.messages.isNotEmpty()) {
          Spacer(modifier = Modifier.weight(1f))
          TvFocusableChip(
            selected = false,
            label = "Clear Chat",
            accentColor = Color(0xFFEF4444),
            onClick = { viewModel.clearMessages() },
            modifier = Modifier.testTag("clear_chat_button")
          )
        }
      }

      // 3. Main Content Area (TV Suggestion Grid or Scrollable Chat Feed)
      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth()
      ) {
        if (uiState.messages.isEmpty()) {
          val suggestions = listOf(
            "🍌 ন্যানো ব্যানানা: নিয়ন সাইবারপাঙ্ক শহরের ছবি আঁকো",
            "🌌 ব্ল্যাক হোল কীভাবে মহাবিশ্বে কাজ করে?",
            "📶 ওয়াইফাই চালু করো",
            "⏰ সকাল ৭টায় অ্যালার্ম দাও",
            "💻 Kotlin Coroutine কীভাবে কাজ করে ব্যাখ্যা করো",
            "🚀 Explain MVVM architecture in Android with Kotlin"
          )

          BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideTv = maxWidth >= 640.dp
            Column(
              modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.Center,
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(48.dp)
              )
              Spacer(modifier = Modifier.height(8.dp))
              Text(
                text = "Google Gemini 3.8 & 3.8 Pro-তে স্বাগতম",
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
              )
              Text(
                text = "v${GeminiService.APP_SEMVER} • রিমোটের ডি-প্যাড দিয়ে কার্ড বেছে নিয়ে OK চাপুন অথবা নিচে লিখুন",
                fontSize = 14.sp,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
              )

              if (isWideTv) {
                // 2-Column TV Grid for effortless D-pad Up/Down/Left/Right selection
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.widthIn(max = 920.dp)
                ) {
                  suggestions.chunked(2).forEachIndexed { rowIdx, pair ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                      pair.forEachIndexed { colIdx, prompt ->
                        val cardModifier = Modifier
                          .weight(1f)
                          .let {
                            if (rowIdx == 0 && colIdx == 0) it.focusRequester(initialFocusRequester) else it
                          }
                        TvSuggestionCard(
                          text = prompt,
                          onClick = { submitPrompt(prompt) },
                          modifier = cardModifier
                        )
                      }
                      if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                      }
                    }
                  }
                }
              } else {
                Column(
                  verticalArrangement = Arrangement.spacedBy(10.dp),
                  modifier = Modifier.widthIn(max = 540.dp)
                ) {
                  suggestions.forEachIndexed { index, prompt ->
                    TvSuggestionCard(
                      text = prompt,
                      onClick = { submitPrompt(prompt) },
                      modifier = Modifier
                        .fillMaxWidth()
                        .let { if (index == 0) it.focusRequester(initialFocusRequester) else it }
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
            contentPadding = PaddingValues(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            items(uiState.messages, key = { it.id }) { message ->
              TvMessageBubble(
                message = message,
                onSpeak = { text ->
                  onSpeak(text)
                  viewModel.setSpeakingAudio(true)
                },
                onStopSpeak = {
                  onStopSpeak()
                  viewModel.setSpeakingAudio(false)
                }
              )
            }

            if (uiState.isThinking) {
              item {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.85f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color(0xFF38BDF8)
                  )
                  Spacer(modifier = Modifier.width(12.dp))
                  Text(
                    text = if (uiState.currentMode == "image") {
                      "ন্যানো ব্যানানা ছবি তৈরি করছে..."
                    } else {
                      "${GeminiService.getModelDisplayName(uiState.selectedModel)} চিন্তা করছে..."
                    },
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                  )
                }
              }
            }
          }
        }
      }

      // 4. TV Bottom Input Dock (with D-pad Up/Down/Left/Right escape & OK submit)
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF1E293B).copy(alpha = 0.96f),
        border = androidx.compose.foundation.BorderStroke(
          width = if (isInputFocused) 2.5.dp else 1.dp,
          brush = Brush.horizontalGradient(
            if (isInputFocused) listOf(Color(0xFF38BDF8), Color(0xFFC084FC))
            else listOf(Color(0xFF38BDF8).copy(alpha = 0.35f), Color(0xFFC084FC).copy(alpha = 0.35f))
          )
        )
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TvFocusableIconButton(
            icon = if (uiState.isListeningMic) Icons.Default.MicOff else Icons.Default.Mic,
            contentDescription = "Voice Dictate",
            tint = if (uiState.isListeningMic) Color(0xFFEF4444) else Color(0xFF38BDF8),
            onClick = {
              if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                startSpeechListening(
                  context = context,
                  onListeningState = { viewModel.setListeningMic(it) },
                  onResult = { text -> inputValue = TextFieldValue(text, TextRange(text.length)) }
                )
              } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
              }
            },
            modifier = Modifier.testTag("dictate_button")
          )

          Spacer(modifier = Modifier.width(8.dp))

          TextField(
            value = inputValue,
            onValueChange = { inputValue = it },
            modifier = Modifier
              .weight(1f)
              .focusRequester(inputFocusRequester)
              .onFocusChanged { isInputFocused = it.isFocused }
              .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                  when (event.key) {
                    Key.DirectionUp -> {
                      focusManager.moveFocus(FocusDirection.Up)
                      true
                    }
                    Key.DirectionDown -> {
                      focusManager.moveFocus(FocusDirection.Down)
                      true
                    }
                    Key.DirectionLeft -> {
                      if (inputValue.text.isEmpty() || inputValue.selection.start == 0) {
                        focusManager.moveFocus(FocusDirection.Left)
                        true
                      } else false
                    }
                    Key.DirectionRight -> {
                      if (inputValue.text.isEmpty() || inputValue.selection.end == inputValue.text.length) {
                        focusManager.moveFocus(FocusDirection.Right)
                        true
                      } else false
                    }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                      if (inputValue.text.isNotBlank()) {
                        submitPrompt(inputValue.text)
                      } else {
                        keyboardController?.show()
                      }
                      true
                    }
                    else -> false
                  }
                } else false
              }
              .testTag("ask_gemini_input"),
            placeholder = {
              Text(
                text = if (uiState.currentMode == "image") {
                  "ন্যানো ব্যানানার জন্য ছবির বিবরণ দিন..."
                } else {
                  "Ask Gemini 3.8 / 3.8 Pro anything (Press OK to type)..."
                },
                color = Color(0xFF64748B),
                fontSize = 15.sp
              )
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { submitPrompt(inputValue.text) }),
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

          Spacer(modifier = Modifier.width(8.dp))

          TvFocusableIconButton(
            icon = Icons.AutoMirrored.Filled.Send,
            contentDescription = "Send",
            enabled = inputValue.text.trim().isNotEmpty() && !uiState.isThinking,
            tint = Color(0xFF38BDF8),
            onClick = { submitPrompt(inputValue.text) },
            modifier = Modifier.testTag("send_button")
          )
        }
      }

      // 5. Subtle TV Remote Legend
      Text(
        text = "D-Pad: Up / Down / Left / Right  •  OK: Select  •  Back: Return / Exit",
        fontSize = 11.sp,
        color = Color(0xFF64748B),
        textAlign = TextAlign.Center,
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 6.dp)
      )
    }
  }

  if (uiState.showSettingsDialog) {
    TvSettingsDialog(
      initialApiKey = uiState.apiKey,
      initialModel = uiState.selectedModel,
      initialAutoSpeak = uiState.autoSpeak,
      onSave = { key, model, speak ->
        viewModel.saveSettings(key, model, speak)
        Toast.makeText(context, "Settings saved!", Toast.LENGTH_SHORT).show()
      },
      onOpenReleaseNotes = {
        viewModel.openSettings(false)
        viewModel.openReleaseNotes(true)
      },
      onDismiss = { viewModel.openSettings(false) }
    )
  }

  if (uiState.showReleaseNotesDialog) {
    TvReleaseNotesDialog(
      onDismiss = { viewModel.openReleaseNotes(false) }
    )
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
    putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Gemini TV...")
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
  } catch (_: Exception) {
    onListeningState(false)
  }
}
