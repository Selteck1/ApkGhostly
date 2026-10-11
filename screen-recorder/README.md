# Ghostly Screen Recorder (Android)

A separate Android application inside the ApkGhostly repository. It does not change the existing Forge/Minecraft build at the repository root.

## Features

- Dark Ghostly-style interface.
- Screen recording through Android MediaProjection with an MP4/H.264 MediaRecorder pipeline, designed to use the device's system video encoder rather than render a software overlay.
- Resolution presets: 480p, 720p, 1080p and 1440p/2K.
- Requested frame-rate presets: 30, 60, 90 and 120 FPS. The app checks the available AVC encoder and falls back to a supported rate or resolution when possible.
- Adjustable video bitrate from 2 to 12 Mbit/s.
- Optional microphone audio; recording can be completely silent.
- Output saved to Gallery → Movies/Ghostly Recorder.
- Foreground notification with a stop action.

## Build

Requirements: JDK 17, Android SDK Platform 35, and Gradle 8.7 or newer.

From the repository root:

    gradle -p screen-recorder --no-daemon assembleDebug

The debug APK is created at:

    screen-recorder/app/build/outputs/apk/debug/app-debug.apk

A GitHub Actions workflow builds the APK and uploads it as a downloadable artifact after changes to this directory.

## Performance notes

The app intentionally avoids a live preview, floating controls and effects while recording. This reduces extra work, but no screen recorder can promise zero FPS loss on every phone: video capture, encoding, memory bandwidth, thermals and the game all compete for resources. For demanding games, start with 720p/60 FPS and 4–6 Mbit/s; increase quality after testing.

The 1440p/2K and 90/120 FPS options are requests, not guarantees. The actual dimensions and selected FPS are displayed when recording starts and may be lowered if the detected AVC encoder does not support the requested mode.

Audio limitation: this first version records the microphone, not direct Android/game playback audio. Turn off the microphone switch for a silent recording. Direct internal-audio capture would need a separate playback-capture and audio/video muxing pipeline and is not claimed as supported here.

Android 10 (API 29) or newer is required. Android shows its standard screen-capture consent dialog each time a new recording starts.
