# PhoneToOBS (MVP)

Android screen-to-OBS prototype using MediaProjection, RootEncoder, H.264/AAC and RTMP over USB ADB reverse.

## Build APK
Requires JDK 17 and Android SDK 35:
```powershell
cd phone-to-obs
gradle assembleDebug
```
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Windows receiver
1. Download the Windows build of MediaMTX from https://github.com/bluenviron/mediamtx/releases and place `mediamtx.exe` beside `windows/mediamtx.yml`.
2. Install Android platform-tools (ADB), connect phone by USB and enable USB debugging.
3. Run `adb devices` and approve the computer on the phone.
4. Run `adb reverse tcp:1935 tcp:1935`.
5. Start MediaMTX using the supplied config.
6. In OBS add a Media Source and use `rtmp://127.0.0.1:1935/phone`.

## Notes
- The app requests MediaProjection permission each session. Android displays a persistent foreground notification while capturing.
- Internal playback audio capture is supported on Android 10+, but individual apps can prohibit playback capture.
- 50 Mbps / 60 FPS is a requested configuration, not a guarantee: hardware codecs may reject it or the device may not sustain the frame rate.
- This is an initial MVP and requires device testing. The stream library is RootEncoder (Apache-2.0).
