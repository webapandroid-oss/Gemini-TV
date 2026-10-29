package com.abd.webapp.android.google.gemini.`interface`

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.TimeUnit

class MainActivity : Activity(), TextToSpeech.OnInitListener {

  companion object {
    private const val TAG = "GeminiTV"
    private const val REQUEST_RECORD_AUDIO = 1001
  }

  private lateinit var webView: WebView
  private var textToSpeech: TextToSpeech? = null
  private var isTtsInitialized = false
  private var speechRecognizer: SpeechRecognizer? = null
  private val mainHandler = Handler(Looper.getMainLooper())
  private val activityScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
  private var lastBackPressTime: Long = 0

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .writeTimeout(30, TimeUnit.SECONDS)
    .retryOnConnectionFailure(true)
    .build()

  @SuppressLint("SetJavaScriptEnabled")
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    hideSystemUI()

    webView = WebView(this).apply {
      setBackgroundColor(Color.parseColor("#080C16"))
      isFocusable = true
      isFocusableInTouchMode = true
    }
    setContentView(webView)

    configureWebViewSettings()
    setupWebClients()
    setupJavaScriptInterface()

    // Initialize Text to Speech safely for voice responses
    try {
      textToSpeech = TextToSpeech(this, this)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to initialize TextToSpeech", e)
    }

    // Check Audio recording permission if microphone is present
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
      != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        REQUEST_RECORD_AUDIO
      )
    }

    webView.loadUrl("file:///android_asset/index.html")
  }

  private fun hideSystemUI() {
    try {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        window.setDecorFitsSystemWindows(false)
        window.insetsController?.let { controller ->
          controller.hide(
            android.view.WindowInsets.Type.statusBars() or
              android.view.WindowInsets.Type.navigationBars()
          )
          controller.systemBarsBehavior =
            android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
      } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
          View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
      }
    } catch (e: Exception) {
      Log.w(TAG, "Could not set immersive mode", e)
    }
  }

  override fun onWindowFocusChanged(hasFocus: Boolean) {
    super.onWindowFocusChanged(hasFocus)
    if (hasFocus) {
      hideSystemUI()
      webView.requestFocus()
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebViewSettings() {
    val settings = webView.settings
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.databaseEnabled = true
    settings.mediaPlaybackRequiresUserGesture = false
    settings.allowFileAccess = true
    settings.allowContentAccess = true
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

    // Enable cross-origin requests from local assets to APIs
    try {
      val allowUniversal = settings.javaClass.getMethod(
        "setAllowUniversalAccessFromFileURLs",
        Boolean::class.javaPrimitiveType
      )
      allowUniversal.invoke(settings, true)
      val allowFile = settings.javaClass.getMethod(
        "setAllowFileAccessFromFileURLs",
        Boolean::class.javaPrimitiveType
      )
      allowFile.invoke(settings, true)
    } catch (e: Exception) {
      Log.w(TAG, "Could not set universal access", e)
    }

    settings.useWideViewPort = true
    settings.loadWithOverviewMode = true
    settings.displayZoomControls = false
    settings.builtInZoomControls = false
    settings.setSupportZoom(false)
    settings.cacheMode = WebSettings.LOAD_DEFAULT
  }

  private fun setupWebClients() {
    webView.webViewClient = object : WebViewClient() {
      override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
      ) {
        super.onReceivedError(view, request, error)
        Log.e(TAG, "WebView error: ${error?.description}")
      }

      override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        Log.d(TAG, "Page loaded successfully: $url")
      }

      override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?
      ): Boolean {
        // Prevent fatal TV crash when TV OS Low-Memory Killer kills WebView renderer
        Log.e(TAG, "WebView render process gone (OOM recovery)")
        try {
          view?.destroy()
        } catch (e: Exception) {
          Log.w(TAG, "Error cleaning gone webview", e)
        }
        recreate()
        return true
      }
    }

    webView.webChromeClient = object : WebChromeClient() {
      override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
        Log.d(TAG, "JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} -- line ${consoleMessage?.lineNumber()}")
        return true
      }

      override fun onPermissionRequest(request: PermissionRequest?) {
        mainHandler.post {
          try {
            request?.grant(request.resources)
          } catch (e: Exception) {
            Log.w(TAG, "Error granting web permission", e)
          }
        }
      }
    }
  }

  private fun setupJavaScriptInterface() {
    webView.addJavascriptInterface(object : Any() {

      @JavascriptInterface
      fun getBuildConfigApiKey(): String {
        return try {
          val buildConfigClass = try {
            Class.forName("com.example.BuildConfig")
          } catch (e1: Exception) {
            try {
              Class.forName("com.abd.webapp.android.google.gemini.interface.BuildConfig")
            } catch (e2: Exception) {
              null
            }
          }
          val key = buildConfigClass?.getField("GEMINI_API_KEY")?.get(null) as? String
          if (key != null && key.isNotEmpty() && key != "MY_GEMINI_API_KEY") key else ""
        } catch (e: Exception) {
          ""
        }
      }

      @JavascriptInterface
      fun getProcessorEngineInfo(): String {
        return GeminiProcessor.getProcessorInfo()
      }

      @JavascriptInterface
      fun processNativeText(text: String): String {
        return GeminiProcessor.processResponse(text)
      }

      @JavascriptInterface
      fun getNativeThemeColor(palette: String): String {
        return GeminiProcessor.getThemeColors(palette)
      }

      @JavascriptInterface
      fun showToast(message: String) {
        mainHandler.post {
          if (!isFinishing && !isDestroyed) {
            Toast.makeText(this@MainActivity, message, Toast.LENGTH_SHORT).show()
          }
        }
      }

      @JavascriptInterface
      fun startDictation() {
        mainHandler.post {
          startSpeechRecognition()
        }
      }

      @JavascriptInterface
      fun stopDictation() {
        mainHandler.post {
          try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
          } catch (e: Exception) {
            Log.e(TAG, "Error stopping recognizer", e)
          }
        }
      }

      @JavascriptInterface
      fun speakText(text: String, languageCode: String) {
        mainHandler.post {
          speakUtterance(text, languageCode)
        }
      }

      @JavascriptInterface
      fun stopSpeaking() {
        mainHandler.post {
          try {
            textToSpeech?.stop()
          } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS", e)
          }
          evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
        }
      }

      @JavascriptInterface
      fun isSpeaking(): Boolean {
        return try {
          textToSpeech?.isSpeaking ?: false
        } catch (e: Exception) {
          false
        }
      }

      @JavascriptInterface
      fun makeGeminiRequest(
        url: String,
        method: String,
        headersJson: String,
        jsonBody: String,
        callbackId: String
      ) {
        activityScope.launch(Dispatchers.IO) {
          try {
            val reqBuilder = Request.Builder().url(url)
            if (headersJson.isNotEmpty()) {
              try {
                val headersObj = JSONObject(headersJson)
                val keys = headersObj.keys()
                while (keys.hasNext()) {
                  val key = keys.next()
                  reqBuilder.addHeader(key, headersObj.getString(key))
                }
              } catch (e: Exception) {
                Log.w(TAG, "Header parse error", e)
              }
            }

            if (method.equals("POST", ignoreCase = true)) {
              val mediaType = "application/json; charset=utf-8".toMediaTypeOrNull()
              reqBuilder.post(jsonBody.toRequestBody(mediaType))
            } else {
              reqBuilder.get()
            }

            val call = okHttpClient.newCall(reqBuilder.build())
            val response = call.execute()
            response.use { resp ->
              val responseBody = resp.body?.string() ?: ""
              val statusCode = resp.code

              withContext(Dispatchers.Main) {
                if (!isFinishing && !isDestroyed) {
                  try {
                    val base64Data = android.util.Base64.encodeToString(
                      responseBody.toByteArray(Charsets.UTF_8),
                      android.util.Base64.NO_WRAP
                    )
                    evaluateJs("try { if (window.onNativeResponseBase64) { window.onNativeResponseBase64('$callbackId', $statusCode, '$base64Data'); } else if (window.onNativeResponse) { window.onNativeResponse('$callbackId', $statusCode, ${JSONObject.quote(responseBody)}); } } catch (e) { console.error('Bridge dispatch error', e); }")
                  } catch (e: Exception) {
                    Log.e(TAG, "Error evaluating response in JS", e)
                    val errMsg = JSONObject.quote(e.message ?: "Response parse error")
                    evaluateJs("window.onNativeError && window.onNativeError('$callbackId', $errMsg)")
                  }
                }
              }
            }
          } catch (e: Exception) {
            Log.e(TAG, "Native request failed", e)
            withContext(Dispatchers.Main) {
              if (!isFinishing && !isDestroyed) {
                val errMsg = JSONObject.quote(e.message ?: "Network error connecting to Gemini")
                evaluateJs("window.onNativeError && window.onNativeError('$callbackId', $errMsg)")
              }
            }
          }
        }
      }
    }, "Android")
  }

  private fun startSpeechRecognition() {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
      != PackageManager.PERMISSION_GRANTED
    ) {
      ActivityCompat.requestPermissions(
        this,
        arrayOf(Manifest.permission.RECORD_AUDIO),
        REQUEST_RECORD_AUDIO
      )
      evaluateJs("window.onDictationError && window.onDictationError('Microphone permission required')")
      return
    }

    if (!SpeechRecognizer.isRecognitionAvailable(this)) {
      Log.w(TAG, "Speech recognition service not reported as available by package manager")
    }

    try {
      try {
        speechRecognizer?.stopListening()
        speechRecognizer?.cancel()
        speechRecognizer?.destroy()
      } catch (e: Exception) {
        Log.w(TAG, "Error cleaning existing recognizer", e)
      }
      speechRecognizer = null

      val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
      if (recognizer == null) {
        evaluateJs("window.onDictationError && window.onDictationError('Voice recognition service is unavailable')")
        return
      }

      speechRecognizer = recognizer
      recognizer.setRecognitionListener(object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
          evaluateJs("window.onDictationState && window.onDictationState('listening')")
        }

        override fun onBeginningOfSpeech() {
          evaluateJs("window.onDictationState && window.onDictationState('speaking')")
        }

        override fun onRmsChanged(rmsdB: Float) {
          evaluateJs("window.onDictationRms && window.onDictationRms($rmsdB)")
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
          evaluateJs("window.onDictationState && window.onDictationState('processing')")
        }

        override fun onError(error: Int) {
          val errorMsg = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
            SpeechRecognizer.ERROR_CLIENT -> "Client error"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required"
            SpeechRecognizer.ERROR_NETWORK -> "Network error during recognition"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service busy"
            SpeechRecognizer.ERROR_SERVER -> "Server error"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
            else -> "Speech error ($error)"
          }
          Log.w(TAG, "SpeechRecognizer error: $errorMsg ($error)")
          try {
            speechRecognizer?.cancel()
          } catch (e: Exception) {
            Log.w(TAG, "Error canceling recognizer on error", e)
          }
          val safeError = JSONObject.quote(errorMsg)
          evaluateJs("window.onDictationError && window.onDictationError($safeError)")
        }

        override fun onResults(results: Bundle?) {
          val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val recognizedText = matches?.firstOrNull() ?: ""
          try {
            speechRecognizer?.stopListening()
          } catch (e: Exception) {
            Log.w(TAG, "Error stopping recognizer on results", e)
          }
          val escaped = JSONObject.quote(recognizedText)
          evaluateJs("window.onDictationResult && window.onDictationResult($escaped)")
        }

        override fun onPartialResults(partialResults: Bundle?) {
          val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
          val partialText = matches?.firstOrNull() ?: ""
          if (partialText.isNotEmpty()) {
            val escaped = JSONObject.quote(partialText)
            evaluateJs("window.onDictationPartialResult && window.onDictationPartialResult($escaped)")
          }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
      })

      val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
      }
      recognizer.startListening(intent)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to start speech recognition", e)
      val err = JSONObject.quote(e.message ?: "Failed to start listening")
      evaluateJs("window.onDictationError && window.onDictationError($err)")
    }
  }

  private fun speakUtterance(text: String, languageCode: String) {
    if (!isTtsInitialized || textToSpeech == null) {
      Log.w(TAG, "TTS not initialized yet")
      evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
      return
    }

    try {
      // Safe length to avoid Android TTS buffer overflow
      val safeText = if (text.length > 2500) text.substring(0, 2500) else text
      val locale = if (languageCode.isNotEmpty()) {
        try { Locale.forLanguageTag(languageCode) } catch (e: Exception) { Locale.getDefault() }
      } else {
        Locale.getDefault()
      }

      try {
        val result = textToSpeech?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
          textToSpeech?.language = Locale.US
        }
      } catch (e: Exception) {
        Log.w(TAG, "Language setting fallback to default", e)
      }

      textToSpeech?.stop()

      val utteranceId = "GeminiUtterance_${System.currentTimeMillis()}"
      textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
          evaluateJs("window.onSpeechStatus && window.onSpeechStatus(true)")
        }

        override fun onDone(utteranceId: String?) {
          evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
          evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
          evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
        }
      })

      val speakResult = textToSpeech?.speak(safeText, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
      if (speakResult != TextToSpeech.SUCCESS) {
        Log.w(TAG, "TTS speak failed with code: $speakResult")
        evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to speak text", e)
      evaluateJs("window.onSpeechStatus && window.onSpeechStatus(false)")
    }
  }

  override fun onInit(status: Int) {
    if (status == TextToSpeech.SUCCESS) {
      isTtsInitialized = true
      try {
        textToSpeech?.setSpeechRate(1.0f)
        textToSpeech?.setPitch(1.0f)
      } catch (e: Exception) {
        Log.w(TAG, "TTS rate/pitch config error", e)
      }
      Log.d(TAG, "TextToSpeech initialized successfully")
    } else {
      Log.w(TAG, "TextToSpeech initialization failed with code $status")
    }
  }

  private fun evaluateJs(script: String) {
    if (isFinishing || isDestroyed) return
    mainHandler.post {
      if (!isFinishing && !isDestroyed) {
        try {
          webView.evaluateJavascript(script, null)
        } catch (e: Exception) {
          Log.w(TAG, "evaluateJavascript failed", e)
        }
      }
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    when (keyCode) {
      KeyEvent.KEYCODE_BACK -> {
        val now = System.currentTimeMillis()
        // Double back press protection: exits immediately if pressed twice within 1.5s
        if (now - lastBackPressTime < 1500) {
          finish()
          return true
        }
        lastBackPressTime = now

        webView.evaluateJavascript("window.handleTvBackPress && window.handleTvBackPress()") { result ->
          if (result == null || result == "false" || result == "\"false\"") {
            if (webView.canGoBack()) {
              webView.goBack()
            } else {
              finish()
            }
          }
        }
        return true
      }
      KeyEvent.KEYCODE_MEDIA_RECORD,
      KeyEvent.KEYCODE_VOICE_ASSIST,
      KeyEvent.KEYCODE_SEARCH -> {
        evaluateJs("window.triggerVoiceFromRemote && window.triggerVoiceFromRemote()")
        return true
      }
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onPause() {
    super.onPause()
    try {
      speechRecognizer?.stopListening()
      speechRecognizer?.cancel()
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping recognizer on pause", e)
    }
    try {
      textToSpeech?.stop()
    } catch (e: Exception) {
      Log.w(TAG, "Error stopping TTS on pause", e)
    }
    try {
      webView.onPause()
      webView.pauseTimers()
    } catch (e: Exception) {
      Log.w(TAG, "Error pausing webview", e)
    }
  }

  override fun onResume() {
    super.onResume()
    try {
      webView.onResume()
      webView.resumeTimers()
    } catch (e: Exception) {
      Log.w(TAG, "Error resuming webview", e)
    }
    hideSystemUI()
  }

  override fun onRequestPermissionsResult(
    requestCode: Int,
    permissions: Array<out String>,
    grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    if (requestCode == REQUEST_RECORD_AUDIO) {
      if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        Log.d(TAG, "Audio permission granted")
        evaluateJs("window.onDictationPermissionGranted && window.onDictationPermissionGranted()")
      } else {
        Log.w(TAG, "Audio permission denied")
        evaluateJs("window.onDictationError && window.onDictationError('Microphone permission was denied')")
      }
    }
  }

  override fun onDestroy() {
    activityScope.cancel()
    try {
      speechRecognizer?.destroy()
      speechRecognizer = null
    } catch (e: Exception) {
      Log.e(TAG, "Error destroying speech recognizer", e)
    }
    try {
      textToSpeech?.stop()
      textToSpeech?.shutdown()
      textToSpeech = null
    } catch (e: Exception) {
      Log.e(TAG, "Error shutting down TTS", e)
    }
    try {
      webView.removeJavascriptInterface("Android")
      webView.stopLoading()
      webView.clearHistory()
      webView.removeAllViews()
      webView.destroy()
    } catch (e: Exception) {
      Log.e(TAG, "Error destroying webView", e)
    }
    super.onDestroy()
  }
}
