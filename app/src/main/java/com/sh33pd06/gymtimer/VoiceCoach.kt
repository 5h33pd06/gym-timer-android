package com.sh33pd06.gymtimer

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_FLUSH
import java.util.Locale

/**
 * Wraps Android's native TextToSpeech engine for the "Voice Coach" feature.
 * The web app used the browser's Web Speech API (window.speechSynthesis), which the
 * Android WebView doesn't implement - this is the native equivalent, which is a large
 * part of why this app is a full native rewrite rather than a WebView wrapper.
 */
class VoiceCoach(context: Context) {
    private var ready = false
    var isOn: Boolean = false

    private var engine: TextToSpeech? = null

    init {
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
                ready = true
            }
        }
    }

    fun speak(text: String) {
        if (!isOn || !ready) return
        engine?.speak(text, QUEUE_FLUSH, null, "gymtimer-utterance")
    }

    fun stop() {
        engine?.stop()
    }

    fun release() {
        engine?.shutdown()
        engine = null
    }
}
