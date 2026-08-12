package com.sh33pd06.gymtimer

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Port of the state machine in timer_pro.html's <script> block. Kept as one big
 * ViewModel (rather than split further) to mirror the original's single-file
 * structure and make the two easy to diff against each other.
 */
class GymTimerViewModel(application: Application) : AndroidViewModel(application) {

    private val sound = SoundPlayer(application)
    private val voice = VoiceCoach(application)

    // ================= Shared / mode ==================
    var mode by mutableStateOf(Mode.TIMER)
        private set
    var status by mutableStateOf(Status.READY)
        private set
    var isRunning by mutableStateOf(false)
        private set
    var isPaused by mutableStateOf(false)
        private set
    var controlsHidden by mutableStateOf(false)
        private set
    var stopButtonIsResume by mutableStateOf(false)
        private set

    // ================= Display ==================
    var displayText by mutableStateOf("00:00")
        private set
    var displayVariant by mutableStateOf(DisplayVariant.NORMAL)
        private set
    var roundsLeftText by mutableStateOf("R0 -")
        private set
    var roundsRightText by mutableStateOf("- R0")
        private set
    var roundsVisible by mutableStateOf(true)
        private set
    var progressPercent by mutableStateOf(100f)
        private set
    var clockIsMini by mutableStateOf(false)
        private set
    var wallClockText by mutableStateOf("00:00:00")
        private set

    // ================= Theme / audio toggles ==================
    var isRedTheme by mutableStateOf(false)
        private set
    var isMuted by mutableStateOf(false)
        private set
    var isVoiceOn by mutableStateOf(false)
        private set

    // ================= Timer settings (user-editable) ==================
    var maxRoundsInput by mutableStateOf("")
    var prepTimeInput by mutableStateOf("10")
    var breakDuration by mutableStateOf(60)
        private set
    var customRoundInput by mutableStateOf("")
    var activePresetId by mutableStateOf<String?>(null)
        private set

    // ================= Tabata settings (user-editable) ==================
    var tabWorkInput by mutableStateOf("20")
    var tabRestInput by mutableStateOf("10")
    var tabIntervalsInput by mutableStateOf("5")
    var tabRoundsInput by mutableStateOf("5")
    var tabSetRestInput by mutableStateOf("60")

    // ================= Stopwatch ==================
    var stopwatchText by mutableStateOf("00:00.00")
        private set
    var stopwatchRunning by mutableStateOf(false)
        private set

    // ================= internal (non-UI) state ==================
    private var timerPhase = TimerPhase.IDLE
    private var roundsCount = 0
    private var maxRounds = Int.MAX_VALUE
    private var totalPhaseTimeMs = 0L
    private var endTimeMs = 0L
    private var remainingMsAtPause = 0L
    private var durationSecondsFn: (() -> Int)? = null

    private var tabSet = 1
    private var tabInterval = 1
    private var tabataPhase = TabataPhase.IDLE
    private var tabWorkTime = 20
    private var tabRestTime = 10
    private var tabIntervalsPerRound = 5
    private var tabMaxSets = 5
    private var tabSetRestTime = 60

    private var stopwatchElapsedMs = 0L
    private var stopwatchStartAnchor = 0L

    private var lastSpokenSecond = -1

    private var previousMuted = false
    private var previousVoiceOn = false
    private var isTabataRun = false

    private var tickJob: Job? = null
    private var prepJob: Job? = null
    private var stopwatchJob: Job? = null

    init {
        viewModelScope.launch {
            val fmt = SimpleDateFormat("HH:mm:ss", Locale.US)
            while (isActive) {
                wallClockText = fmt.format(System.currentTimeMillis())
                delay(1000)
            }
        }
    }

    // ===========================================================
    // ---------------------- Mode switching ----------------------
    // ===========================================================

    fun switchMode(newMode: Mode) {
        mode = newMode
        stopTimer()
        stopStopwatch()
        resetDisplayForMode()

        clockIsMini = newMode == Mode.TABATA
        roundsVisible = newMode != Mode.STOPWATCH
        controlsHidden = false

        if (newMode == Mode.STOPWATCH) {
            displayVariant = DisplayVariant.STOPWATCH
            // Render whatever elapsed time is still retained from before
            // (e.g. paused, then switched away and back), rather than the
            // web version's quirk of visually resetting to 0 on re-entry
            // while secretly resuming from the old value on Start.
            renderStopwatch(stopwatchElapsedMs)
        }
    }

    private fun resetDisplayForMode() {
        roundsCount = 0
        if (mode == Mode.TABATA) {
            roundsLeftText = "SET"
            roundsRightText = "INT"
        } else {
            updateRoundsDisplay(0)
        }
        renderDisplay("00:00")
        displayVariant = DisplayVariant.NORMAL
        status = Status.READY
        isRunning = false
        progressPercent = 100f
    }

    private fun updateRoundsDisplay(count: Int) {
        roundsLeftText = "R$count -"
        roundsRightText = "- R$count"
    }

    private fun renderDisplay(text: String) {
        displayText = text
    }

    // ===========================================================
    // ------------------- Toggles / settings ----------------------
    // ===========================================================

    fun toggleTheme() { isRedTheme = !isRedTheme }

    fun toggleMute() { sound.isMuted = !sound.isMuted; isMuted = sound.isMuted }

    fun toggleVoice() {
        voice.isOn = !voice.isOn
        isVoiceOn = voice.isOn
        if (isVoiceOn && !isTabataRun) voice.speak("Voice On") else if (!isVoiceOn) voice.stop()
    }

    fun updateBreakDuration(seconds: Int) { breakDuration = seconds }

    // ===========================================================
    // ----------------- STANDARD TIMER LOGIC -----------------------
    // ===========================================================

    fun handleTimerPreset(minutes: Int, presetId: String) {
        if (isRunning) return
        activePresetId = presetId
        startTimerLogic { minutes * 60 }
    }

    fun handleRandomPreset(presetId: String) {
        if (isRunning) return
        activePresetId = presetId
        val options = intArrayOf(1, 3, 4, 5, 7, 10)
        startTimerLogic { options.random() * 60 }
    }

    fun handleCustomRoundSubmit() {
        val parts = customRoundInput.split(":")
        if (parts.size != 2) return
        val mins = parts[0].toIntOrNull() ?: 0
        val secs = parts[1].toIntOrNull() ?: 0
        if (mins + secs <= 0) return
        activePresetId = null
        startTimerLogic { mins * 60 + secs }
    }

    fun onCustomRoundInputChange(raw: String) {
        var digits = raw.filter { it.isDigit() }
        if (digits.length > 4) digits = digits.take(4)
        customRoundInput = if (digits.length > 2) {
            digits.substring(0, 2) + ":" + digits.substring(2)
        } else digits
    }

    private fun startTimerLogic(durationFn: () -> Int) {
        if (isRunning) return
        isRunning = true
        isPaused = false
        controlsHidden = true

        timerPhase = TimerPhase.PREP
        roundsCount = 0
        durationSecondsFn = durationFn

        maxRounds = maxRoundsInput.toIntOrNull() ?: Int.MAX_VALUE

        var countdown = prepTimeInput.toIntOrNull() ?: 10
        renderDisplay(countdown.toString())
        status = Status.READY
        displayVariant = DisplayVariant.NORMAL
        progressPercent = 100f

        if (countdown <= 0) {
            timerPhase = TimerPhase.WORK
            roundsCount = 1
            runTimerStep(false)
            return
        }

        val countdownEndTime = System.currentTimeMillis() + countdown * 1000L
        prepJob?.cancel()
        prepJob = viewModelScope.launch {
            var shown = countdown
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = ceilSeconds(countdownEndTime - now)
                if (remaining != shown) {
                    shown = remaining
                    renderDisplay(max(shown, 0).toString())
                    if (shown in 1..3) sound.beep()
                }
                if (remaining <= 0) {
                    sound.buzzer()
                    timerPhase = TimerPhase.WORK
                    roundsCount = 1
                    runTimerStep(false)
                    return@launch
                }
                delay(50)
            }
        }
    }

    private fun runTimerStep(isResume: Boolean) {
        var durationSeconds = 0

        if (!isResume) {
            when (timerPhase) {
                TimerPhase.WORK -> {
                    updateRoundsDisplay(roundsCount)
                    status = Status.GO
                    voice.speak("Round $roundsCount")
                    durationSeconds = durationSecondsFn?.invoke() ?: 60
                }
                TimerPhase.BREAK -> {
                    status = Status.BREAK
                    voice.speak("Rest")
                    durationSeconds = breakDuration
                }
                else -> {}
            }
            totalPhaseTimeMs = durationSeconds * 1000L
            remainingMsAtPause = totalPhaseTimeMs
        } else {
            status = if (timerPhase == TimerPhase.WORK) Status.GO else Status.BREAK
        }

        endTimeMs = System.currentTimeMillis() + remainingMsAtPause
        lastSpokenSecond = -1

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMs = endTimeMs - now
                val timeLeftCeil = ceilSeconds(remainingMs)

                val minutes = timeLeftCeil / 60
                val seconds = timeLeftCeil % 60

                var percent = (remainingMs.toFloat() / totalPhaseTimeMs.toFloat()) * 100f
                if (percent < 0f) percent = 0f
                progressPercent = percent

                if (timerPhase == TimerPhase.BREAK) {
                    renderDisplay("BREAK $seconds")
                    displayVariant = DisplayVariant.BREAK
                    status = if (timeLeftCeil <= 5) Status.WARN else Status.BREAK
                } else {
                    renderDisplay("$minutes:${if (seconds < 10) "0$seconds" else "$seconds"}")
                    displayVariant = DisplayVariant.NORMAL
                    status = if (timeLeftCeil <= 10) Status.WARN else Status.GO
                }

                if (timeLeftCeil > 0 && timeLeftCeil != lastSpokenSecond) {
                    val msIntoSecond = remainingMs % 1000
                    if (msIntoSecond in 801..999) {
                        lastSpokenSecond = timeLeftCeil
                        if (timeLeftCeil <= 5) sound.beep()
                        if (timeLeftCeil == 10 && timerPhase == TimerPhase.WORK) {
                            voice.speak("Ten Seconds Remaining")
                        }
                    }
                }

                if (timeLeftCeil <= 0) {
                    sound.buzzer()
                    lastSpokenSecond = -1
                    transitionTimerPhase()
                    return@launch
                }
                delay(30)
            }
        }
    }

    private fun transitionTimerPhase() {
        when (timerPhase) {
            TimerPhase.WORK -> {
                if (roundsCount >= maxRounds) {
                    stopTimer()
                    renderDisplay("DONE")
                    voice.speak("Workout Complete")
                    return
                }
                if (breakDuration > 0) {
                    timerPhase = TimerPhase.BREAK
                } else {
                    roundsCount++
                    timerPhase = TimerPhase.WORK
                }
            }
            TimerPhase.BREAK -> {
                roundsCount++
                timerPhase = TimerPhase.WORK
            }
            else -> {}
        }
        runTimerStep(false)
    }

    fun handleTimerStopButton() {
        if (timerPhase == TimerPhase.PREP) {
            stopTimer()
            return
        }
        if (!isPaused) {
            tickJob?.cancel()
            remainingMsAtPause = endTimeMs - System.currentTimeMillis()
            isPaused = true
            status = Status.PAUSED
            stopButtonIsResume = true
            controlsHidden = false
        } else {
            isPaused = false
            stopButtonIsResume = false
            controlsHidden = true
            runTimerStep(true)
        }
    }

    fun resetTimer() {
        stopTimer()
        stopStopwatch()
        resetDisplayForMode()
        activePresetId = null
        prepTimeInput = "10"
        maxRoundsInput = ""
        breakDuration = 60
    }

    // ===========================================================
    // ------------------------ TABATA LOGIC -------------------------
    // ===========================================================

    fun startTabata() {
        if (isRunning) return
        isRunning = true
        isPaused = false
        isTabataRun = true
        controlsHidden = true

        previousMuted = sound.isMuted
        previousVoiceOn = voice.isOn
        sound.isMuted = true
        isMuted = true
        voice.isOn = true
        isVoiceOn = true

        tabWorkTime = tabWorkInput.toIntOrNull() ?: 20
        tabRestTime = tabRestInput.toIntOrNull() ?: 10
        tabIntervalsPerRound = tabIntervalsInput.toIntOrNull() ?: 5
        tabMaxSets = tabRoundsInput.toIntOrNull() ?: 5
        tabSetRestTime = tabSetRestInput.toIntOrNull() ?: 60

        tabSet = 1
        tabInterval = 1
        tabataPhase = TabataPhase.PREP

        var countdown = 10
        renderDisplay(countdown.toString())
        status = Status.READY
        displayVariant = DisplayVariant.NORMAL
        progressPercent = 100f

        val countdownEndTime = System.currentTimeMillis() + countdown * 1000L
        var lastPrepSpoken = -1
        prepJob?.cancel()
        prepJob = viewModelScope.launch {
            var shown = countdown
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = ceilSeconds(countdownEndTime - now)
                if (remaining != shown) {
                    shown = remaining
                    renderDisplay(max(shown, 0).toString())
                }
                if (remaining in 1..5 && remaining != lastPrepSpoken) {
                    voice.speak(remaining.toString())
                    lastPrepSpoken = remaining
                }
                if (remaining <= 0) {
                    tabataPhase = TabataPhase.WORK
                    runTabataStep(false)
                    return@launch
                }
                delay(50)
            }
        }
    }

    private fun updateTabataDisplay() {
        roundsLeftText = "SET $tabSet/$tabMaxSets"
        roundsRightText = if (tabataPhase == TabataPhase.SET_REST) "REST" else "INT $tabInterval/$tabIntervalsPerRound"
    }

    private fun runTabataStep(isResume: Boolean) {
        var durationSeconds = 0
        var speech = ""
        updateTabataDisplay()

        if (!isResume) {
            when (tabataPhase) {
                TabataPhase.WORK -> {
                    durationSeconds = tabWorkTime
                    status = Status.GO
                    speech = if (tabInterval == 1) "Set $tabSet. Go." else "Go"
                }
                TabataPhase.REST -> {
                    durationSeconds = tabRestTime
                    status = Status.BREAK
                    speech = "Rest"
                }
                TabataPhase.SET_REST -> {
                    durationSeconds = tabSetRestTime
                    status = Status.BREAK
                    speech = "Set complete. Rest."
                }
                else -> {}
            }
            if (speech.isNotEmpty()) voice.speak(speech)
            totalPhaseTimeMs = durationSeconds * 1000L
            remainingMsAtPause = totalPhaseTimeMs
        } else {
            status = if (tabataPhase == TabataPhase.WORK) Status.GO else Status.BREAK
        }

        endTimeMs = System.currentTimeMillis() + remainingMsAtPause
        lastSpokenSecond = -1

        tickJob?.cancel()
        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remainingMs = endTimeMs - now
                val timeLeftCeil = ceilSeconds(remainingMs)

                if (tabataPhase == TabataPhase.SET_REST || tabataPhase == TabataPhase.REST) {
                    renderDisplay("REST $timeLeftCeil")
                } else {
                    renderDisplay("$timeLeftCeil")
                }
                // Tabata's countdown stays full-size (no break-text shrink), per request.
                displayVariant = DisplayVariant.NORMAL

                var percent = (remainingMs.toFloat() / totalPhaseTimeMs.toFloat()) * 100f
                if (percent < 0f) percent = 0f
                progressPercent = percent

                if (timeLeftCeil > 0 && timeLeftCeil != lastSpokenSecond && timeLeftCeil <= 5) {
                    voice.speak(timeLeftCeil.toString())
                    lastSpokenSecond = timeLeftCeil
                }

                if (timeLeftCeil <= 0) {
                    lastSpokenSecond = -1
                    transitionTabataPhase()
                    return@launch
                }
                delay(30)
            }
        }
    }

    private fun transitionTabataPhase() {
        when (tabataPhase) {
            TabataPhase.WORK -> {
                if (tabInterval < tabIntervalsPerRound) {
                    tabataPhase = TabataPhase.REST
                } else if (tabSet < tabMaxSets) {
                    tabataPhase = TabataPhase.SET_REST
                } else {
                    stopTimer()
                    renderDisplay("DONE")
                    voice.speak("Workout Complete")
                    return
                }
            }
            TabataPhase.REST -> {
                tabInterval++
                tabataPhase = TabataPhase.WORK
            }
            TabataPhase.SET_REST -> {
                tabSet++
                tabInterval = 1
                tabataPhase = TabataPhase.WORK
            }
            else -> {}
        }
        runTabataStep(false)
    }

    fun handleTabataStopButton() {
        if (tabataPhase == TabataPhase.PREP) {
            stopTimer()
            return
        }
        if (!isPaused) {
            tickJob?.cancel()
            remainingMsAtPause = endTimeMs - System.currentTimeMillis()
            isPaused = true
            status = Status.PAUSED
            stopButtonIsResume = true
            controlsHidden = false
        } else {
            isPaused = false
            stopButtonIsResume = false
            controlsHidden = true
            runTabataStep(true)
        }
    }

    // ===========================================================
    // ------------------- Shared stop / reset ------------------------
    // ===========================================================

    private fun stopTimer() {
        prepJob?.cancel()
        tickJob?.cancel()
        isRunning = false
        isPaused = false
        stopButtonIsResume = false

        controlsHidden = false

        if (isTabataRun) {
            sound.isMuted = previousMuted
            isMuted = previousMuted
            voice.isOn = previousVoiceOn
            isVoiceOn = previousVoiceOn
            isTabataRun = false
        }

        renderDisplay("STOP")
        status = Status.READY
        progressPercent = 100f
        activePresetId = null
        timerPhase = TimerPhase.IDLE
        tabataPhase = TabataPhase.IDLE
    }

    // ===========================================================
    // ------------------------ STOPWATCH -----------------------------
    // ===========================================================

    fun startStopwatch() {
        if (stopwatchRunning) return
        stopwatchRunning = true
        status = Status.GO
        progressPercent = 100f

        stopwatchStartAnchor = System.currentTimeMillis() - stopwatchElapsedMs
        stopwatchJob?.cancel()
        stopwatchJob = viewModelScope.launch {
            while (isActive) {
                stopwatchElapsedMs = System.currentTimeMillis() - stopwatchStartAnchor
                renderStopwatch(stopwatchElapsedMs)
                delay(10)
            }
        }
    }

    fun stopStopwatch() {
        if (!stopwatchRunning) return
        stopwatchJob?.cancel()
        stopwatchRunning = false
        status = Status.READY
    }

    fun resetStopwatch() {
        stopStopwatch()
        stopwatchElapsedMs = 0
        renderStopwatch(0)
    }

    private fun renderStopwatch(elapsedMs: Long) {
        val minutes = elapsedMs / 60000
        val seconds = (elapsedMs % 60000) / 1000
        val hundredths = (elapsedMs % 1000) / 10
        stopwatchText = "${pad(minutes)}:${pad(seconds)}.${pad(hundredths)}"
    }

    private fun pad(n: Long): String = n.toString().padStart(2, '0')

    // ===========================================================

    private fun ceilSeconds(ms: Long): Int = ceil(ms / 1000.0).toInt()

    override fun onCleared() {
        prepJob?.cancel()
        tickJob?.cancel()
        stopwatchJob?.cancel()
        sound.release()
        voice.release()
    }
}
