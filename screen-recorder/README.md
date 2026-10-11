# Ghostly Screen Recorder (Android)

A separate Android application inside the ApkGhostly repository. It does not change the existing Forge/Minecraft build at the repository root.

## Features

- Dark Ghostly-style interface.
- Screen recording through Android MediaProjection with an MP4/H.264 MediaRecorder pipeline, designed to use the device's system video encoder rather than render a software overlay.
- Resolution presets: 480p, 720p, 1080p and 1440p/2K. Presets use the short edge and preserve the phone's aspect ratio, so landscape capture is sized correctly even when the recorder UI itself is portrait.
- Orientation selection: landscape for games, portrait for vertical video, or current display orientation.
- Requested frame-rate presets: 30, 60, 90 and 120 FPS. The app checks the available hardware encoder and falls back to a supported rate or resolution when possible.
- Adjustable video bitrate from 4 to 50 Mbit/s, with device limits respected.
- Optional hardware H.265/HEVC encoding for better compression/retained detail at similar bitrates; H.264 is used when HEVC is unavailable or cannot maintain the requested frame rate.
- Optional microphone audio; recording can be completely silent.
- Output saved to Gallery → Movies/Ghostly Recorder.
- Foreground notification with a stop action.
- Quick Settings tile beside controls such as Wi-Fi/Hotspot; Android 13+ asks whether to add it, while older systems require adding it from the edit button.

## Build

Requirements: JDK 17, Android SDK Platform 35, and Gradle 8.7 or newer.

From the repository root:

    gradle -p screen-recorder --no-daemon assembleDebug

The debug APK is created at:

    screen-recorder/app/build/outputs/apk/debug/app-debug.apk

A GitHub Actions workflow builds the APK and uploads it as a downloadable artifact after changes to this directory.

## Performance notes

The app intentionally avoids a live preview, floating controls and effects while recording. This reduces extra work, but no screen recorder can promise zero FPS loss on every phone: video capture, encoding, memory bandwidth, thermals and the game all compete for resources. For demanding games, start with 1080p/60 FPS and 15–25 Mbit/s; for 1080p/90 FPS try 25–35 Mbit/s; for 2K try 35–50 Mbit/s only if the phone remains smooth.

The 1440p/2K and 90/120 FPS options are requests, not guarantees. The recorder reports actual encoded dimensions/FPS, codec and bitrate. If the encoder cannot support a requested mode, it falls back to a supported size/rate when possible. A phone's physical screen resolution remains a limit on real scene detail; upscaling above it cannot recover missing details.

Audio limitation: this first version records the microphone, not direct Android/game playback audio. Turn off the microphone switch for a silent recording. Direct internal-audio capture would need a separate playback-capture and audio/video muxing pipeline and is not claimed as supported here.

Android 10 (API 29) or newer is required. Android shows its standard screen-capture consent dialog each time a new recording starts.
