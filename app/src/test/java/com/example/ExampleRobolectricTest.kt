package com.example

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ApplicationProvider
import com.abd.webapp.android.google.gemini.`interface`.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Gemini TV", appName)
  }

  @Test
  fun `manifest declares required android tv leanback and television features`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val pm = context.packageManager
    val pkgInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_CONFIGURATIONS)
    val features = pkgInfo.reqFeatures?.associateBy { it.name } ?: emptyMap()

    val leanback = features[PackageManager.FEATURE_LEANBACK]
    assertNotNull("android.software.leanback must be declared", leanback)
    assertTrue(
      "android.software.leanback must be required=true",
      (leanback!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED) != 0
    )

    val television = features["android.hardware.type.television"]
    assertNotNull("android.hardware.type.television must be declared", television)
    assertTrue(
      "android.hardware.type.television must be required=true",
      (television!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED) != 0
    )

    val touchscreen = features[PackageManager.FEATURE_TOUCHSCREEN]
    assertNotNull("android.hardware.touchscreen must be declared", touchscreen)
    assertFalse(
      "android.hardware.touchscreen must be required=false",
      (touchscreen!!.flags and android.content.pm.FeatureInfo.FLAG_REQUIRED) != 0
    )

    val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
    val resolved = pm.queryIntentActivities(leanbackIntent, 0)
    assertTrue("MainActivity must resolve LEANBACK_LAUNCHER", resolved.isNotEmpty())
  }

  @Test
  fun `main tv screen renders model switcher and dpad controls`() {
    composeTestRule.onNodeWithTag("gemini_main_screen").assertIsDisplayed()
    composeTestRule.onNodeWithTag("model_chip_3_8").assertIsDisplayed()
    composeTestRule.onNodeWithTag("model_chip_3_8_pro").assertIsDisplayed()
    composeTestRule.onNodeWithTag("release_notes_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("ask_gemini_input").assertIsDisplayed()
    composeTestRule.onNodeWithTag("send_button").assertIsDisplayed()
  }
}
