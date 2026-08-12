# Gym Timer (Android)

A native Android rewrite of [gym-timer](https://github.com/5h33pd06/gym-timer)'s `timer_pro.html`, built with Kotlin and Jetpack Compose.

The web version stays as-is; this is a standalone app with the same Timer / Tabata / Stopwatch functionality, plus native features a WebView can't provide well:

- **Voice Coach** via Android's native `TextToSpeech` (the web version uses the Web Speech API, which Android's WebView doesn't implement)
- **Beep/buzzer** cues via `SoundPool`
- Keep-screen-on while a timer is actively running

## Features

- **Timer**: prep countdown, work/break phases, round limits, break-duration picker, preset buttons (1m/3m/4m/5m/7m/10m/RND) or custom MM:SS, pause/resume, hides secondary controls while running (STOP/RESET stay visible)
- **Tabata**: work/rest/set-rest/prep phases, SET/INT indicators, configurable set count, intervals per set, and rest between sets
- **Stopwatch**: hundredths precision
- Fixed-width digit rendering so narrow characters (like "1") don't shift neighboring digits
- Mute, Voice Coach, and red-theme toggles; live wall clock

## Building

Requires JDK 17 and the Android SDK (`ANDROID_HOME` or `local.properties` pointing at it).

```bash
./gradlew assembleDebug
```

The debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

## Project structure

- `GymTimerViewModel.kt` — the state machine, ported from timer_pro.html's `<script>` block
- `SoundPlayer.kt` / `VoiceCoach.kt` — native audio/TTS wrappers
- `MainActivity.kt` / `ui/GymTimerScreen.kt` — Compose UI
- `ui/Theme.kt` — colors and the bundled Orbitron font family
