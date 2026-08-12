package com.sh33pd06.gymtimer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sh33pd06.gymtimer.ui.AppColors
import com.sh33pd06.gymtimer.ui.GymTimerScreen
import com.sh33pd06.gymtimer.ui.Orbitron

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            val viewModel: GymTimerViewModel = viewModel()

            // Keep the screen on while any timer is actively running - the
            // native equivalent of the web app's navigator.wakeLock use.
            LaunchedEffect(viewModel.isRunning, viewModel.stopwatchRunning) {
                if (viewModel.isRunning || viewModel.stopwatchRunning) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            GymTimerScreen(viewModel = viewModel)
        }
    }
}

/** vw-style responsive sizing: proportion of screen width, like the web app's vw units. */
@Composable
fun vw(fraction: Float): TextUnit {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return (widthDp * fraction).sp
}

@Composable
fun vwDp(fraction: Float): Dp {
    val widthDp = LocalConfiguration.current.screenWidthDp
    return (widthDp * fraction).dp
}

/**
 * Renders [text] with each digit in its own fixed-width slot so a narrow "1" doesn't
 * shift its neighbors - the native equivalent of setTimerText()/renderFixedWidthText()
 * in timer_pro.html (Orbitron isn't a monospace/tabular-figure font here either).
 */
@Composable
fun FixedWidthText(
    text: String,
    fontSize: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Black,
) {
    val digitWidth = with(LocalDensity.current) { (fontSize.toPx() * 0.72f).toDp() }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        for (ch in text) {
            val style = TextStyle(
                fontFamily = Orbitron,
                fontWeight = fontWeight,
                fontSize = fontSize,
                color = color,
                textAlign = TextAlign.Center,
            )
            if (ch.isDigit()) {
                Box(modifier = Modifier.width(digitWidth), contentAlignment = Alignment.Center) {
                    Text(text = ch.toString(), style = style, maxLines = 1)
                }
            } else {
                val shown = if (ch == ' ') " " else ch.toString()
                Text(text = shown, style = style, maxLines = 1)
            }
        }
    }
}

/**
 * Like [FixedWidthText], but picks whatever font size makes the text fill the
 * available width, rather than a fixed vw-style fraction. Used for the Timer
 * mode countdown, which should always span edge to edge regardless of whether
 * it's showing a single prep digit or "BREAK 45".
 */
@Composable
fun FillWidthFixedText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Black,
    minFontSize: Float = 32f,
    maxFontSize: Float = 260f,
) {
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val availableWidthPx = with(density) { maxWidth.toPx() }
        val referenceSp = 100f
        // Measuring the whole string's *real* glyph widths would make the
        // scale factor depend on which specific digits are showing (a "1" is
        // narrower than an "8" in this font) - even though FixedWidthText
        // renders every digit into an identical fixed-width slot regardless
        // of its value. That mismatch was making the whole display grow and
        // shrink on every tick. So width here must be computed the same way
        // FixedWidthText renders it: a constant per-digit contribution
        // (matching its 0.72*fontSize slot) plus the real measured width of
        // each non-digit character (colon, space, letters), which don't vary
        // with neighboring digits.
        val naturalWidthPx = remember(text, fontWeight, density) {
            val referenceFontSizePx = with(density) { referenceSp.sp.toPx() }
            val digitSlotPx = referenceFontSizePx * 0.72f
            var total = 0f
            for (ch in text) {
                total += if (ch.isDigit()) {
                    digitSlotPx
                } else {
                    textMeasurer.measure(
                        text = if (ch == ' ') " " else ch.toString(),
                        style = TextStyle(fontFamily = Orbitron, fontWeight = fontWeight, fontSize = referenceSp.sp),
                    ).size.width.toFloat()
                }
            }
            total.coerceAtLeast(1f)
        }
        val targetSp = (referenceSp * (availableWidthPx / naturalWidthPx)).coerceIn(minFontSize, maxFontSize)
        FixedWidthText(text = text, fontSize = targetSp.sp, color = color, fontWeight = fontWeight)
    }
}

@Composable
fun IconCircleButton(
    emoji: String,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .border(2.dp, if (active) activeColor else Color(0xFF333333), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 16.sp)
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    active: Boolean = false,
    accentBg: Color? = null,
    accentBorder: Color? = null,
    accentText: Color? = null,
) {
    val bg = when {
        active -> Color.White
        accentBg != null -> accentBg
        else -> Color(0xFF333333)
    }
    val textColor = when {
        active -> Color.Black
        accentText != null -> accentText
        else -> Color.White
    }
    val border = accentBorder ?: Color(0xFF444444)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) bg else bg.copy(alpha = 0.2f))
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = textColor,
            fontFamily = Orbitron,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun SmallField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    width: Dp? = 60.dp,
    placeholder: String? = null,
    onSubmit: (() -> Unit)? = null,
) {
    // BasicTextField rather than Material3 TextField: M3's TextField bakes in
    // generous internal padding meant for full-width fields with labels, which
    // left almost no room for text in these small ~60dp boxes (values were
    // rendering as an unreadable clipped sliver of a single digit).
    val borderColor = if (enabled) Color(0xFF444444) else Color(0xFF333333)
    // width == null means "fill whatever space the parent gave this modifier"
    // (e.g. Modifier.weight(1f) in a Row) rather than a fixed size.
    val sizeModifier = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(
        modifier = sizeModifier
            .height(44.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) Color(0xFF222222) else Color(0xFF1A1A1A))
            .border(1.dp, borderColor, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = Orbitron,
                fontSize = 16.sp,
                color = Color.White,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = if (onSubmit != null) androidx.compose.ui.text.input.ImeAction.Done else androidx.compose.ui.text.input.ImeAction.Default,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { onSubmit?.invoke() },
            ),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            decorationBox = { innerTextField ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                placeholder,
                                color = Color(0xFF666666),
                                fontFamily = Orbitron,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                            )
                        }
                        innerTextField()
                    }
                    // Explicit tap target to submit, rather than relying on the
                    // keyboard's IME action - a numeric keypad often has no
                    // visible Done/Go key on many OEM keyboards, which made the
                    // custom round-time field otherwise impossible to submit.
                    if (onSubmit != null) {
                        Text(
                            "▶",
                            color = AppColors.Go,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable(enabled = enabled) { onSubmit() }
                                .padding(start = 2.dp, end = 4.dp),
                        )
                    }
                }
            },
        )
    }
}

@Composable
fun SettingsLabel(text: String, fontSize: TextUnit = 13.sp) {
    Text(
        text,
        color = Color(0xFF888888),
        fontSize = fontSize,
        fontFamily = Orbitron,
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
    )
}
