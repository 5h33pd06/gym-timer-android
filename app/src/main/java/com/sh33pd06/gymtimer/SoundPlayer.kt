package com.sh33pd06.gymtimer

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Plays the short beep/buzzer cues, mirroring `playAudio()` in timer_pro.html.
 * Uses SoundPool (not MediaPlayer) since these are short, latency-sensitive one-shots
 * that can overlap the countdown ticking every second.
 */
class SoundPlayer(context: Context) {
    private val pool = SoundPool.Builder()
        .setMaxStreams(4)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    private val beepId = pool.load(context, R.raw.beep, 1)
    private val buzzerId = pool.load(context, R.raw.buzzer, 1)

    var isMuted: Boolean = false

    fun beep() {
        if (!isMuted) pool.play(beepId, 1f, 1f, 1, 0, 1f)
    }

    fun buzzer() {
        if (!isMuted) pool.play(buzzerId, 1f, 1f, 1, 0, 1f)
    }

    fun release() {
        pool.release()
    }
}
