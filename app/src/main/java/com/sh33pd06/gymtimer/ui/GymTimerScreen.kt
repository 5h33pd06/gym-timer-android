package com.sh33pd06.gymtimer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sh33pd06.gymtimer.*

private fun String.digitsOnly() = filter { it.isDigit() }

@Composable
fun GymTimerScreen(
    viewModel: GymTimerViewModel,
    immersive: Boolean,
    onToggleImmersive: () -> Unit,
) {
    val statusColor = AppColors.statusColor(viewModel.status, viewModel.isRedTheme)

    Box(Modifier.fillMaxSize().background(AppColors.Background)) {
        // Progress bar - mirrors #progress-container/#progress-fill.
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .align(Alignment.TopCenter)
                .background(Color(0xFF333333))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth((viewModel.progressPercent.coerceIn(0f, 100f)) / 100f)
                    .background(statusColor)
            )
        }

        // Top-right icon row - mirrors .controls-top.
        Row(
            Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircleButton("🗣️", viewModel.isVoiceOn, AppColors.Go) { viewModel.toggleVoice() }
            IconCircleButton(if (viewModel.isMuted) "🔇" else "🔊", viewModel.isMuted, AppColors.Warn) { viewModel.toggleMute() }
            IconCircleButton("🎨", viewModel.isRedTheme, AppColors.RedThemeAccent) { viewModel.toggleTheme() }
            IconCircleButton("⛶", immersive, Color.White, onClick = onToggleImmersive)
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 64.dp, bottom = 32.dp, start = 12.dp, end = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))
            ClockAndRoundsSection(viewModel)
            Spacer(Modifier.height(16.dp))
            BigDisplay(viewModel, statusColor)
            Spacer(Modifier.height(20.dp))

            if (!viewModel.controlsHidden) {
                ModeTabsRow(viewModel)
                Spacer(Modifier.height(16.dp))
            }

            when (viewModel.mode) {
                Mode.TIMER -> TimerControls(viewModel)
                Mode.TABATA -> TabataControls(viewModel)
                Mode.STOPWATCH -> StopwatchControls(viewModel)
            }
        }
    }
}

@Composable
private fun ClockAndRoundsSection(viewModel: GymTimerViewModel) {
    val clockSize = if (viewModel.clockIsMini) vw(0.05f) else vw(0.12f)
    val roundSize = vw(0.08f)
    val clockColor = Color.White.copy(alpha = 0.7f)

    // Stacked vertically rather than in a row - the web version learned the hard
    // way that a single row of round-indicator + clock + round-indicator collides
    // on narrow screens (see the mobile overlap fix); an Android phone is always
    // "narrow" by that standard, so stacking is the safe default here.
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        when (viewModel.mode) {
            Mode.TIMER -> {
                // Single "ROUND #" line under the clock, rather than Tabata's
                // two-sided SET/INT layout - Timer mode only has one counter.
                FixedWidthText(viewModel.wallClockText, clockSize, clockColor)
                FixedWidthText(viewModel.timerRoundText, roundSize, Color.White, fontWeight = FontWeight.Bold)
            }
            Mode.TABATA -> {
                FixedWidthText(viewModel.roundsLeftText, roundSize, Color.White, fontWeight = FontWeight.Bold)
                FixedWidthText(viewModel.wallClockText, clockSize, clockColor)
                FixedWidthText(viewModel.roundsRightText, roundSize, Color.White, fontWeight = FontWeight.Bold)
            }
            Mode.STOPWATCH -> {
                FixedWidthText(viewModel.wallClockText, clockSize, clockColor)
            }
        }
    }
}

@Composable
private fun BigDisplay(viewModel: GymTimerViewModel, color: Color) {
    val text = if (viewModel.mode == Mode.STOPWATCH) viewModel.stopwatchText else viewModel.displayText

    if (viewModel.mode == Mode.TIMER || viewModel.mode == Mode.TABATA) {
        // Timer and Tabata modes: always fill the screen width, regardless of
        // whether the text is a single prep digit or something longer like
        // "BREAK 45"/"REST 45" - the font size adapts instead of using a
        // fixed vw fraction, while still keeping every digit a fixed width
        // (FillWidthFixedText -> FixedWidthText underneath) so the display
        // doesn't jitter as the digits themselves change.
        FillWidthFixedText(
            text = text,
            color = color,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        )
    } else {
        val fraction = when (viewModel.displayVariant) {
            DisplayVariant.NORMAL -> 0.20f
            DisplayVariant.BREAK -> 0.14f
            DisplayVariant.STOPWATCH -> 0.13f
        }
        FixedWidthText(
            text = text,
            fontSize = vw(fraction),
            color = color,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

@Composable
private fun ModeTabsRow(viewModel: GymTimerViewModel) {
    val locked = viewModel.isRunning || viewModel.stopwatchRunning
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton("TIMER", { viewModel.switchMode(Mode.TIMER) }, active = viewModel.mode == Mode.TIMER, enabled = !locked)
        PillButton("TABATA", { viewModel.switchMode(Mode.TABATA) }, active = viewModel.mode == Mode.TABATA, enabled = !locked)
        PillButton("STOPWATCH", { viewModel.switchMode(Mode.STOPWATCH) }, active = viewModel.mode == Mode.STOPWATCH, enabled = !locked)
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SettingsLabel(label)
        Spacer(Modifier.height(4.dp))
        SmallField(value, onValueChange, enabled = enabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimerControls(viewModel: GymTimerViewModel) {
    val enabled = !viewModel.isRunning
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (!viewModel.controlsHidden) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("ROUNDS (blank=∞)", viewModel.maxRoundsInput, { viewModel.maxRoundsInput = it.digitsOnly() }, enabled)
                LabeledField("PREP (s)", viewModel.prepTimeInput, { viewModel.prepTimeInput = it.digitsOnly() }, enabled)
            }
        }

        if (!viewModel.controlsHidden) {
            // 4-and-4 grid, evenly spaced: [MM:SS, 1m, 3m, 4m] / [5m, 7m, 10m, RND].
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallField(
                        value = viewModel.customRoundInput,
                        onValueChange = viewModel::onCustomRoundInputChange,
                        enabled = enabled,
                        width = null,
                        placeholder = "MM:SS",
                        onSubmit = viewModel::handleCustomRoundSubmit,
                        modifier = Modifier.weight(1f),
                    )
                    for (m in intArrayOf(1, 3, 4)) {
                        val id = "btn-${m}m"
                        PillButton(
                            "${m}m", { viewModel.handleTimerPreset(m, id) },
                            enabled = enabled, active = viewModel.activePresetId == id,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (m in intArrayOf(5, 7, 10)) {
                        val id = "btn-${m}m"
                        PillButton(
                            "${m}m", { viewModel.handleTimerPreset(m, id) },
                            enabled = enabled, active = viewModel.activePresetId == id,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    PillButton(
                        "RND", { viewModel.handleRandomPreset("btn-rnd") },
                        enabled = enabled, active = viewModel.activePresetId == "btn-rnd",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (!viewModel.controlsHidden) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                SettingsLabel("BREAK DURATION")
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (s in intArrayOf(0, 10, 45, 60)) {
                        PillButton("${s}s", { viewModel.updateBreakDuration(s) }, active = viewModel.breakDuration == s)
                    }
                }
            }
        }

        StopResetRow(
            isRunning = viewModel.isRunning,
            isResume = viewModel.stopButtonIsResume,
            onStop = viewModel::handleTimerStopButton,
            onReset = viewModel::resetTimer,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TabataControls(viewModel: GymTimerViewModel) {
    val enabled = !viewModel.isRunning
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (!viewModel.controlsHidden) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabeledField("WORK (s)", viewModel.tabWorkInput, { viewModel.tabWorkInput = it.digitsOnly() }, enabled)
                LabeledField("REST (s)", viewModel.tabRestInput, { viewModel.tabRestInput = it.digitsOnly() }, enabled)
                LabeledField("INT/SET", viewModel.tabIntervalsInput, { viewModel.tabIntervalsInput = it.digitsOnly() }, enabled)
                LabeledField("SETS", viewModel.tabRoundsInput, { viewModel.tabRoundsInput = it.digitsOnly() }, enabled)
                LabeledField("SET REST (s)", viewModel.tabSetRestInput, { viewModel.tabSetRestInput = it.digitsOnly() }, enabled)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                "START TABATA",
                { viewModel.startTabata() },
                enabled = enabled,
                accentBg = Color(0xFF554400),
                accentBorder = Color(0xFF886600),
                accentText = Color(0xFFFFCC00),
            )
            StopButton(isRunning = viewModel.isRunning, isResume = viewModel.stopButtonIsResume, onClick = viewModel::handleTabataStopButton)
            ResetButton(onClick = viewModel::resetTimer)
        }
    }
}

@Composable
private fun StopwatchControls(viewModel: GymTimerViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PillButton(
            "START",
            viewModel::startStopwatch,
            enabled = !viewModel.stopwatchRunning,
            accentBg = Color(0xFF003300),
            accentBorder = Color(0xFF005500),
            accentText = AppColors.Go,
        )
        PillButton(
            "STOP",
            viewModel::stopStopwatch,
            enabled = viewModel.stopwatchRunning,
            accentBg = Color(0xFF330000),
            accentBorder = Color(0xFF550000),
            accentText = AppColors.Warn,
        )
        ResetButton(onClick = viewModel::resetStopwatch)
    }
}

@Composable
private fun StopResetRow(isRunning: Boolean, isResume: Boolean, onStop: () -> Unit, onReset: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StopButton(isRunning, isResume, onStop)
        ResetButton(onReset)
    }
}

@Composable
private fun StopButton(isRunning: Boolean, isResume: Boolean, onClick: () -> Unit) {
    PillButton(
        if (isResume) "RESUME" else "STOP",
        onClick,
        enabled = isRunning,
        accentBg = if (isResume) Color(0xFF003300) else Color(0xFF330000),
        accentBorder = if (isResume) Color(0xFF005500) else Color(0xFF550000),
        accentText = if (isResume) AppColors.Go else AppColors.Warn,
    )
}

@Composable
private fun ResetButton(onClick: () -> Unit) {
    PillButton("RESET", onClick)
}
