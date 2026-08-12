package com.sh33pd06.gymtimer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.sh33pd06.gymtimer.R

/** Mirrors the CSS custom properties in timer_pro.html's :root. */
object AppColors {
    val Ready = Color.White
    val Go = Color(0xFF00FF00)
    val Break = Color(0xFF00CCFF)
    val Warn = Color(0xFFFF0000)
    val Paused = Color(0xFFFFFF00)
    val Background = Color.Black

    // Red theme overrides - every status collapses to red, matching the web app.
    val RedThemeAccent = Color(0xFFFF0000)

    fun statusColor(status: com.sh33pd06.gymtimer.Status, isRedTheme: Boolean): Color {
        if (isRedTheme) return RedThemeAccent
        return when (status) {
            com.sh33pd06.gymtimer.Status.READY -> Ready
            com.sh33pd06.gymtimer.Status.GO -> Go
            com.sh33pd06.gymtimer.Status.BREAK -> Break
            com.sh33pd06.gymtimer.Status.WARN -> Warn
            com.sh33pd06.gymtimer.Status.PAUSED -> Paused
        }
    }
}

val Orbitron: FontFamily = FontFamily(
    Font(R.font.orbitron_medium, FontWeight.Medium),
    Font(R.font.orbitron_bold, FontWeight.Bold),
    Font(R.font.orbitron_black, FontWeight.Black),
)
