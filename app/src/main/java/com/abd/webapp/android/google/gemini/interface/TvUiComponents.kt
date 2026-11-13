package com.abd.webapp.android.google.gemini.`interface`

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest

private val TvFocusRingColor = Color(0xFF38BDF8)
private val TvProFocusRingColor = Color(0xFFC084FC)

/**
 * TV-optimized pill/chip with clear D-pad focus scale, glowing ring, and OK/Center support.
 */
@Composable
fun TvFocusableChip(
  selected: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  accentColor: Color = TvFocusRingColor,
  showStatusDot: Boolean = false
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.06f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "tv_chip_scale"
  )
  val borderColor by animateColorAsState(
    targetValue = when {
      isFocused -> Color.White
      selected -> accentColor.copy(alpha = 0.75f)
      else -> Color(0xFF334155)
    },
    label = "tv_chip_border"
  )
  val containerColor by animateColorAsState(
    targetValue = when {
      isFocused && selected -> accentColor.copy(alpha = 0.38f)
      isFocused -> Color(0xFF334155)
      selected -> accentColor.copy(alpha = 0.22f)
      else -> Color(0xFF1E293B)
    },
    label = "tv_chip_bg"
  )

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(16.dp),
    color = containerColor,
    border = BorderStroke(if (isFocused) 2.5.dp else 1.dp, borderColor),
    modifier = modifier
      .scale(scale)
      .minimumInteractiveComponentSize()
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (showStatusDot) {
        Box(
          modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (selected) accentColor else Color(0xFF64748B))
        )
        Spacer(modifier = Modifier.width(7.dp))
      }
      Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected || isFocused) FontWeight.Bold else FontWeight.Medium,
        color = when {
          isFocused -> Color.White
          selected -> Color(0xFFF1F5F9)
          else -> Color(0xFFCBD5E1)
        }
      )
    }
  }
}

/**
 * TV-optimized icon button with a bright D-pad focus halo and minimum 48.dp touch/focus area.
 */
@Composable
fun TvFocusableIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  tint: Color = Color(0xFFCBD5E1),
  focusedTint: Color = Color.White,
  shape: Shape = CircleShape
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isFocused && enabled) 1.12f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "tv_icon_scale"
  )
  val bgColor by animateColorAsState(
    targetValue = when {
      isFocused && enabled -> TvFocusRingColor.copy(alpha = 0.28f)
      else -> Color(0xFF1E293B).copy(alpha = 0.7f)
    },
    label = "tv_icon_bg"
  )

  Box(
    modifier = modifier
      .scale(scale)
      .minimumInteractiveComponentSize()
      .size(48.dp)
      .clip(shape)
      .background(bgColor)
      .border(
        width = if (isFocused && enabled) 2.5.dp else 1.dp,
        color = if (isFocused && enabled) TvFocusRingColor else Color(0xFF334155),
        shape = shape
      )
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = androidx.compose.material3.ripple(bounded = true),
        onClick = onClick
      ),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      tint = if (!enabled) Color(0xFF475569) else if (isFocused) focusedTint else tint,
      modifier = Modifier.size(22.dp)
    )
  }
}

/**
 * TV-optimized Suggestion Card for 10-foot D-pad selection.
 */
@Composable
fun TvSuggestionCard(
  text: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val interactionSource = remember { MutableInteractionSource() }
  val isFocused by interactionSource.collectIsFocusedAsState()

  val scale by animateFloatAsState(
    targetValue = if (isFocused) 1.03f else 1f,
    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
    label = "tv_card_scale"
  )

  Surface(
    onClick = onClick,
    interactionSource = interactionSource,
    shape = RoundedCornerShape(16.dp),
    color = if (isFocused) Color(0xFF1E3A5F) else Color(0xFF1E293B).copy(alpha = 0.78f),
    border = BorderStroke(
      width = if (isFocused) 2.5.dp else 1.dp,
      brush = if (isFocused) {
        Brush.horizontalGradient(listOf(TvFocusRingColor, TvProFocusRingColor))
      } else {
        Brush.horizontalGradient(listOf(Color(0xFF334155), Color(0xFF1E293B)))
      }
    ),
    modifier = modifier
      .scale(scale)
      .minimumInteractiveComponentSize()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 18.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = text,
        color = if (isFocused) Color.White else Color(0xFFE2E8F0),
        fontSize = 15.sp,
        fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Medium,
        lineHeight = 21.sp
      )
    }
  }
}

/**
 * TV-optimized Chat Message Bubble: focusable for D-pad Up/Down scrolling through long feeds,
 * with D-pad focusable Copy and Voice Speak controls.
 */
@Composable
fun TvMessageBubble(
  message: ChatMessage,
  onSpeak: (String) -> Unit,
  onStopSpeak: () -> Unit
) {
  val context = LocalContext.current
  val isUser = message.sender == MessageSender.USER
  var isPlaying by remember { mutableStateOf(false) }

  val bubbleInteractionSource = remember { MutableInteractionSource() }
  val isBubbleFocused by bubbleInteractionSource.collectIsFocusedAsState()

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
  ) {
    if (!isUser) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .background(Brush.linearGradient(listOf(TvFocusRingColor, TvProFocusRingColor))),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.AutoAwesome,
          contentDescription = "Gemini",
          tint = Color.White,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.width(12.dp))
    }

    Column(
      modifier = Modifier
        .widthIn(max = 720.dp)
        .clip(RoundedCornerShape(18.dp))
        .background(
          if (isUser) Brush.linearGradient(listOf(Color(0xFF2563EB), TvFocusRingColor))
          else Brush.linearGradient(
            if (isBubbleFocused) listOf(Color(0xFF24344D), Color(0xFF1D2B44))
            else listOf(Color(0xFF1E293B), Color(0xFF182234))
          )
        )
        .border(
          width = if (isBubbleFocused) 2.5.dp else 1.dp,
          color = when {
            isBubbleFocused -> TvFocusRingColor
            isUser -> Color(0xFF60A5FA).copy(alpha = 0.5f)
            else -> Color(0xFF334155)
          },
          shape = RoundedCornerShape(18.dp)
        )
        .focusable(interactionSource = bubbleInteractionSource)
        .padding(16.dp)
    ) {
      if (!isUser && !message.modelUsed.isNullOrBlank()) {
        Text(
          text = GeminiService.getModelDisplayName(message.modelUsed),
          fontSize = 11.sp,
          fontWeight = FontWeight.SemiBold,
          color = TvFocusRingColor,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }

      Text(
        text = message.text,
        color = Color.White,
        fontSize = 16.sp,
        lineHeight = 24.sp
      )

      if (message.isImage && message.imageUrl != null) {
        Spacer(modifier = Modifier.height(12.dp))
        AsyncImage(
          model = ImageRequest.Builder(context)
            .data(message.imageUrl)
            .crossfade(true)
            .build(),
          contentDescription = "Nano Banana Generated Image",
          contentScale = ContentScale.Crop,
          modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(12.dp))
        )
      }

      if (!isUser) {
        Spacer(modifier = Modifier.height(10.dp))
        Row(
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          TvFocusableChip(
            selected = false,
            label = "Copy",
            onClick = {
              val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
              clipboard.setPrimaryClip(ClipData.newPlainText("Gemini Response", message.text))
              Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.testTag("copy_response_button")
          )

          TvFocusableChip(
            selected = isPlaying,
            label = if (isPlaying) "Stop Voice" else "Read Aloud",
            accentColor = TvProFocusRingColor,
            onClick = {
              if (isPlaying) {
                onStopSpeak()
                isPlaying = false
              } else {
                onSpeak(message.text)
                isPlaying = true
              }
            },
            modifier = Modifier.testTag("speak_response_button")
          )

          Icon(
            imageVector = if (isPlaying) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = null,
            tint = if (isPlaying) TvProFocusRingColor else Color(0xFF94A3B8),
            modifier = Modifier.size(18.dp)
          )
          Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = null,
            tint = Color(0xFF64748B),
            modifier = Modifier.size(15.dp)
          )
        }
      }
    }
  }
}

@Composable
fun TvSettingsDialog(
  initialApiKey: String,
  initialModel: String,
  initialAutoSpeak: Boolean,
  onSave: (apiKey: String, model: String, autoSpeak: Boolean) -> Unit,
  onOpenReleaseNotes: () -> Unit,
  onDismiss: () -> Unit
) {
  var tempKey by remember { mutableStateOf(initialApiKey) }
  var tempModel by remember { mutableStateOf(initialModel) }
  var tempAutoSpeak by remember { mutableStateOf(initialAutoSpeak) }
  val focusManager = LocalFocusManager.current
  val initialFocus = remember { FocusRequester() }

  LaunchedEffect(Unit) {
    try {
      initialFocus.requestFocus()
    } catch (_: Exception) {}
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(
        "Gemini TV Settings • v${GeminiService.APP_SEMVER}",
        color = Color.White,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
      ) {
        Text("Gemini API Key (Google AI Studio):", fontSize = 13.sp, color = Color(0xFF94A3B8))
        OutlinedTextField(
          value = tempKey,
          onValueChange = { tempKey = it },
          placeholder = { Text("Enter your Gemini API key (optional)", color = Color(0xFF64748B)) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { focusManager.moveFocus(FocusDirection.Down) }),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TvFocusRingColor,
            unfocusedBorderColor = Color(0xFF475569),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White
          ),
          modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
              if (event.type == KeyEventType.KeyDown) {
                when (event.key) {
                  Key.DirectionDown -> {
                    focusManager.moveFocus(FocusDirection.Down)
                    true
                  }
                  Key.DirectionUp -> {
                    focusManager.moveFocus(FocusDirection.Up)
                    true
                  }
                  else -> false
                }
              } else false
            }
            .testTag("api_key_input")
        )

        Text("Model Selection (Latest Gemini 3.8 Series):", fontSize = 13.sp, color = Color(0xFF94A3B8))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          TvFocusableChip(
            selected = tempModel == GeminiService.MODEL_GEMINI_3_8,
            label = "Gemini 3.8",
            accentColor = TvFocusRingColor,
            showStatusDot = true,
            onClick = { tempModel = GeminiService.MODEL_GEMINI_3_8 },
            modifier = Modifier.focusRequester(initialFocus)
          )
          TvFocusableChip(
            selected = tempModel == GeminiService.MODEL_GEMINI_3_8_PRO,
            label = "Gemini 3.8 Pro",
            accentColor = TvProFocusRingColor,
            showStatusDot = true,
            onClick = { tempModel = GeminiService.MODEL_GEMINI_3_8_PRO }
          )
        }

        val checkboxInteraction = remember { MutableInteractionSource() }
        val isCheckboxFocused by checkboxInteraction.collectIsFocusedAsState()
        Row(
          verticalAlignment = Alignment.CenterVertically,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCheckboxFocused) Color(0xFF334155) else Color.Transparent)
            .border(
              width = if (isCheckboxFocused) 2.dp else 0.dp,
              color = if (isCheckboxFocused) TvFocusRingColor else Color.Transparent,
              shape = RoundedCornerShape(12.dp)
            )
            .clickable(
              interactionSource = checkboxInteraction,
              indication = androidx.compose.material3.ripple()
            ) { tempAutoSpeak = !tempAutoSpeak }
            .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
          Checkbox(
            checked = tempAutoSpeak,
            onCheckedChange = null
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Auto-read responses with Voice (TTS)", color = Color(0xFFE2E8F0), fontSize = 14.sp)
        }

        TvFocusableChip(
          selected = false,
          label = "View Release Notes (v${GeminiService.APP_SEMVER})",
          onClick = onOpenReleaseNotes
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onSave(tempKey, tempModel, tempAutoSpeak) },
        colors = ButtonDefaults.buttonColors(containerColor = TvFocusRingColor, contentColor = Color(0xFF060913)),
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Text("Save", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.minimumInteractiveComponentSize()
      ) {
        Text("Cancel", color = Color(0xFFCBD5E1))
      }
    },
    containerColor = Color(0xFF1E293B),
    textContentColor = Color(0xFFE2E8F0)
  )
}

@Composable
fun TvReleaseNotesDialog(
  onDismiss: () -> Unit
) {
  val okFocusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) {
    try {
      okFocusRequester.requestFocus()
    } catch (_: Exception) {}
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.NewReleases,
          contentDescription = null,
          tint = TvFocusRingColor,
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "রিলিজ নোটস • v${GeminiService.APP_SEMVER}",
          color = Color.White,
          fontWeight = FontWeight.Bold
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Text(
          text = "সেমান্টিক ভার্সনিং: 3.0.0 (Build 6) • Android TV Edition",
          fontSize = 13.sp,
          fontWeight = FontWeight.SemiBold,
          color = TvFocusRingColor
        )
        Text(
          text = "• সরাসরি জেমিনি ৩.৮ এবং ৩.৮ প্রো ইন্টিগ্রেশন: দ্রুত উত্তর ও জটিল রিজনিংয়ের জন্য gemini-3.8-flash এবং gemini-3.8-pro (৮,১৯২ টোকেন পর্যন্ত)।\n" +
            "• Android TV ও Leanback অপ্টিমাইজেশন: ম্যানিফেস্টে leanback ও television ফিচার এবং touchscreen optional করা হয়েছে।\n" +
            "• সম্পূর্ণ D-Pad রিমোট নেভিগেশন: Up / Down / Left / Right / OK এবং Back বাটনের জন্য হাই-কনট্রাস্ট ফোকাস রিং ও রিমোট-ফ্রেন্ডলি ইনপুট।\n" +
            "• 64-bit ও 32-bit রিলিজ ভ্যারিয়েন্ট: arm64-v8a এবং armeabi-v7a Android TV-র জন্য আলাদা রিলিজ APK বিল্ড।\n" +
            "• লাইভ মডেল সুইচার ও বর্ধিত ১২-টার্ন কনটেক্সট মেমরি।",
          fontSize = 14.sp,
          lineHeight = 22.sp,
          color = Color(0xFFE2E8F0)
        )
      }
    },
    confirmButton = {
      Button(
        onClick = onDismiss,
        colors = ButtonDefaults.buttonColors(containerColor = TvFocusRingColor, contentColor = Color(0xFF060913)),
        modifier = Modifier
          .focusRequester(okFocusRequester)
          .minimumInteractiveComponentSize()
      ) {
        Text("ঠিক আছে", fontWeight = FontWeight.Bold)
      }
    },
    containerColor = Color(0xFF1E293B),
    textContentColor = Color(0xFFE2E8F0)
  )
}
