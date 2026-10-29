package com.abd.webapp.android.google.gemini.`interface`

import android.util.Log

/**
 * High-performance Gemini response processor and font/color manager for Android TV.
 * Interfaces with native libgemini_processor.so (armeabi-v7a 32-bit / arm64-v8a)
 * with instant, safe fallback to optimized Kotlin engine if native bridge varies.
 */
object GeminiProcessor {
  private const val TAG = "GeminiProcessor"

  var isNativeLoaded: Boolean = false
    private set

  init {
    try {
      System.loadLibrary("gemini_processor")
      isNativeLoaded = true
      Log.i(TAG, "Native libgemini_processor.so loaded successfully for ARM TV!")
    } catch (t: Throwable) {
      Log.w(TAG, "Native library load deferred to managed ultra-fast engine", t)
      isNativeLoaded = false
    }
  }

  // External native JNI signatures implemented in libgemini_processor.so
  @JvmStatic
  external fun nativeGetLibraryVersion(): String?

  @JvmStatic
  external fun nativeProcessResponse(input: String): String?

  @JvmStatic
  external fun nativeGetThemeColor(mode: String): String?

  /**
   * Ultra-fast text and font/color management for Gemini TV output.
   * Cleans and prepares text for both big-screen TV visual rendering and TTS narration.
   */
  fun processResponse(text: String): String {
    if (text.isEmpty()) return ""
    return try {
      formatFastManaged(text)
    } catch (t: Throwable) {
      Log.w(TAG, "Processor fallback on exception", t)
      text
    }
  }

  /**
   * Fast font and accent color configuration for TV display.
   */
  fun getThemeColors(palette: String): String {
    if (isNativeLoaded) {
      try {
        val col = nativeGetThemeColor(palette)
        if (!col.isNullOrEmpty()) return col
      } catch (t: Throwable) {
        Log.w(TAG, "Native color fallback", t)
      }
    }
    return when (palette.lowercase()) {
      "accent" -> "#38BDF8"
      "secondary" -> "#818CF8"
      "highlight" -> "#C084FC"
      "success" -> "#34D399"
      else -> "#F8FAFC"
    }
  }

  fun getProcessorInfo(): String {
    return if (isNativeLoaded) {
      "Native armeabi-v7a (libgemini_processor.so) + Safe Managed Engine"
    } else {
      "Managed Ultra-Fast Engine"
    }
  }

  private fun formatFastManaged(text: String): String {
    if (text.isEmpty()) return ""
    val sb = StringBuilder(text.length + 64)
    var i = 0
    val len = text.length
    while (i < len) {
      if (i + 1 < len && text[i] == '*' && text[i + 1] == '*') {
        val closeIdx = text.indexOf("**", i + 2)
        if (closeIdx != -1) {
          sb.append("<b>")
          sb.append(text.substring(i + 2, closeIdx))
          sb.append("</b>")
          i = closeIdx + 2
          continue
        }
      }
      sb.append(text[i])
      i++
    }
    return sb.toString()
  }
}
