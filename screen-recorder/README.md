# Ghostly Screen Recorder — FluxRecorder engine

Ghostly Recorder is built from [FluxRecorder](https://github.com/IcradleInnovationsLtd/FluxRecorder), pinned to commit `4ddca4280c5a79a808f759bfe82b6802898b14ce`. FluxRecorder is distributed under the **MIT License**. The upstream source and its license are kept in the `screen-recorder/rec-upstream` Git submodule; the Ghostly branding and startup-adaptation patch is `screen-recorder/patches/apply_ghostly.py`.

## Included functionality

This engine brings the Compose UI and recording pipeline from FluxRecorder, including hardware-accelerated recording, configurable capture quality, internal playback audio where supported by Android and by the captured app, microphone recording, recording library, quick controls and floating controls. Features can vary by device and Android version; no fixed FPS is guaranteed on every phone.

The package ID is `ru.selteck.ghostly.recorder`, and the launcher name is **Ghostly Recorder**. At launch, a branded screen attempts to show a Google AdMob App Open ad. If the ad fails to load, the recorder opens without waiting indefinitely.

## Ads

The CI build uses Google's official **test App Open ad** and test App ID by default. Test ads do not generate revenue. Google advises using test IDs during development to avoid invalid traffic or account restrictions: https://developers.google.com/admob/android/test-ads

To configure your own AdMob IDs for a monetized build, add these **Actions variables** in your GitHub repository:

- `GHOSTLY_ADMOB_APP_ID`: app ID like `ca-app-pub-1234567890123456~1234567890`
- `GHOSTLY_APP_OPEN_AD_UNIT_ID`: App Open unit ID like `ca-app-pub-1234567890123456/1234567890`

Before enabling live ads, configure the correct consent/privacy flow for the regions in which you distribute the app (including EEA/UK where applicable). Never click production ads while testing.

## Build locally

```bash
git clone --recurse-submodules https://github.com/Selteck1/ApkGhostly.git
python3 screen-recorder/patches/apply_ghostly.py screen-recorder/rec-upstream
gradle -p screen-recorder/rec-upstream --no-daemon assembleDebug
```

The debug APK will be in `screen-recorder/rec-upstream/app/build/outputs/apk/debug/`. GitHub Actions builds and uploads the APK artifact automatically on changes to the recorder project.

The original Ghostly implementation remains in `screen-recorder/app` as a fallback reference. This derivative project keeps the upstream MIT license notice in the built APK and the complete upstream source in the pinned submodule.
