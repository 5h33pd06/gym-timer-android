package com.sh33pd06.gymtimer

/** Which of the three screens is active - mirrors the web app's `mode` variable. */
enum class Mode { TIMER, TABATA, STOPWATCH }

/** Drives the big readout's color, mirroring the web app's `status-*` body classes. */
enum class Status { READY, GO, BREAK, WARN, PAUSED }

/** Standard Timer mode's phase, mirrors `timerState.phase`. */
enum class TimerPhase { IDLE, PREP, WORK, BREAK, DONE }

/** Tabata mode's phase, mirrors `tabataState.phase`. */
enum class TabataPhase { IDLE, PREP, WORK, REST, SET_REST, DONE }

/** Font-size variant for the big readout - the web app used break-text/interval-text
 * classes; Tabata no longer shrinks (per later request), so only NORMAL/BREAK/STOPWATCH
 * remain in practice, but DisplayVariant stays generic. */
enum class DisplayVariant { NORMAL, BREAK, STOPWATCH }
