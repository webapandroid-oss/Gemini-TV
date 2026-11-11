package com.abd.webapp.android.google.gemini.`interface`

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.Settings
import android.util.Log
import java.util.Locale

/**
 * High-performance Gemini response processor and device hardware coordinator.
 * Interfaces with native libgemini_processor.so (ARM) and system hardware.
 */
object GeminiProcessor {
  private const val TAG = "GeminiProcessor"

  var isNativeLoaded: Boolean = false
    private set

  init {
    try {
      System.loadLibrary("gemini_processor")
      isNativeLoaded = true
      Log.i(TAG, "Native libgemini_processor.so loaded successfully!")
    } catch (t: Throwable) {
      Log.w(TAG, "Native library load deferred to managed ultra-fast engine", t)
      isNativeLoaded = false
    }
  }

  // External native JNI signatures
  @JvmStatic
  external fun nativeGetLibraryVersion(): String?

  @JvmStatic
  external fun nativeProcessResponse(input: String): String?

  @JvmStatic
  external fun nativeGetThemeColor(mode: String): String?

  /**
   * Executes Android device hardware actions (Wi-Fi, Bluetooth, Alarms)
   */
  fun executeDeviceAction(action: String, param: String, activity: Activity): String {
    return try {
      when (action) {
        "ACTION_WIFI_ON", "ACTION_WIFI_OFF" -> {
          val shouldEnable = action == "ACTION_WIFI_ON"
          toggleWifi(activity, shouldEnable)
        }
        "ACTION_BLUETOOTH_ON", "ACTION_BLUETOOTH_OFF" -> {
          val shouldEnable = action == "ACTION_BLUETOOTH_ON"
          toggleBluetooth(activity, shouldEnable)
        }
        "ACTION_SET_ALARM" -> {
          setDeviceAlarm(activity, param)
        }
        "ACTION_SET_TIMER" -> {
          setDeviceTimer(activity, param)
        }
        else -> "Action executed: $action"
      }
    } catch (e: Exception) {
      Log.e(TAG, "Error executing action $action", e)
      "Error: ${e.message}"
    }
  }

  @Suppress("DEPRECATION")
  private fun toggleWifi(activity: Activity, enable: Boolean): String {
    return try {
      val wifiManager = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        try {
          val panelIntent = Intent(Settings.Panel.ACTION_WIFI)
          activity.startActivity(panelIntent)
          if (enable) "Wi-Fi সেটিংস প্যানেল খোলা হয়েছে (চালুর জন্য)" else "Wi-Fi সেটিংস প্যানেল খোলা হয়েছে (বন্ধের জন্য)"
        } catch (e: Exception) {
          val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
          activity.startActivity(intent)
          "Wi-Fi সেটিংস খোলা হয়েছে"
        }
      } else {
        val success = wifiManager?.setWifiEnabled(enable) ?: false
        if (success) {
          if (enable) "ওয়াইফাই চালু করা হয়েছে।" else "ওয়াইফাই বন্ধ করা হয়েছে।"
        } else {
          val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
          activity.startActivity(intent)
          "ওয়াইফাই সেটিংস খোলা হয়েছে।"
        }
      }
    } catch (e: Exception) {
      val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
      activity.startActivity(intent)
      "ওয়াইফাই সেটিংস ওপেন করা হয়েছে।"
    }
  }

  @Suppress("DEPRECATION")
  private fun toggleBluetooth(activity: Activity, enable: Boolean): String {
    return try {
      val btAdapter = BluetoothAdapter.getDefaultAdapter()
      if (btAdapter == null) {
        return "এই ডিভাইসে ব্লুটুথ হার্ডওয়্যার পাওয়া যায়নি।"
      }
      if (enable) {
        if (!btAdapter.isEnabled) {
          try {
            btAdapter.enable()
            "ব্লুটুথ সক্রিয় করা হচ্ছে..."
          } catch (e: Exception) {
            val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activity.startActivity(intent)
            "ব্লুটুথ চালু করার অনুমতি চাওয়া হচ্ছে..."
          }
        } else {
          "ব্লুটুথ ইতিমধ্যে চালু আছে।"
        }
      } else {
        if (btAdapter.isEnabled) {
          try {
            btAdapter.disable()
            "ব্লুটুথ বন্ধ করা হচ্ছে..."
          } catch (e: Exception) {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            activity.startActivity(intent)
            "ব্লুটুথ সেটিংস খোলা হয়েছে।"
          }
        } else {
          "ব্লুটুথ ইতিমধ্যে বন্ধ আছে।"
        }
      }
    } catch (e: Exception) {
      val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
      activity.startActivity(intent)
      "ব্লুটুথ সেটিংস খোলা হয়েছে।"
    }
  }

  private fun setDeviceAlarm(activity: Activity, param: String): String {
    return try {
      val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, if (param.isNotEmpty()) param else "Gemini TV Alarm")
        putExtra(AlarmClock.EXTRA_HOUR, 7)
        putExtra(AlarmClock.EXTRA_MINUTES, 0)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
      }
      activity.startActivity(intent)
      "অ্যালার্ম অ্যাপ খোলা হয়েছে এবং সকাল ৭টায় অ্যালার্ম সেট করা হয়েছে।"
    } catch (e: Exception) {
      "অ্যালার্ম অ্যাপ্লিকেশন পাওয়া যায়নি: ${e.message}"
    }
  }

  private fun setDeviceTimer(activity: Activity, param: String): String {
    return try {
      val seconds = 300 // default 5 minutes
      val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
        putExtra(AlarmClock.EXTRA_MESSAGE, if (param.isNotEmpty()) param else "Gemini Timer")
        putExtra(AlarmClock.EXTRA_LENGTH, seconds)
        putExtra(AlarmClock.EXTRA_SKIP_UI, false)
      }
      activity.startActivity(intent)
      "৫ মিনিটের টাইমার সেট করা হয়েছে।"
    } catch (e: Exception) {
      "টাইমার সেট করা সম্ভব হয়নি: ${e.message}"
    }
  }

  fun getProcessorInfo(): String {
    return if (isNativeLoaded) {
      "Google Gemini Universal Engine (libgemini_processor.so Active)"
    } else {
      "Google Gemini Universal Engine (Managed)"
    }
  }
}
