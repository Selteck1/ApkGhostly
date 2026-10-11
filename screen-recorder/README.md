# Ghostly Screen Recorder (REC engine edition)

This build of **Ghostly Screen Recorder** uses the recording engine and application source from [pixl-hq/rec](https://github.com/pixl-hq/rec), pinned to commit `0c855ef7a73111be394d5306302dd0b29245272d`. The upstream project is licensed under **GNU GPL v3**; its complete source and LICENSE are available in the `screen-recorder/rec-upstream` Git submodule. The Ghostly adaptation patch is maintained in `screen-recorder/patches/apply_ghostly.py`.

## What changes in this build

- Keeps the upstream MediaCodec-based hardware recording engine, internal game-audio capture where Android/app policy permits it, microphone capture/mixing, recording vault, settings, floating controls and replay features.
- Changes the installed application ID to `ru.selteck.ghostly.recorder` and launcher name to **Ghostly Recorder**.
- Shows a Google AdMob **App Open** ad from a branded startup screen. The build defaults to official Google test IDs; test ads do not earn revenue.
- Uses a bounded startup wait: if an ad fails to load, the app opens normally. The recorder is not blocked by a network failure.

## AdMob configuration

For safety, the build uses Google's official test IDs until you add your own values. To serve real ads, configure these **repository Actions variables** on GitHub:

- `GHOSTLY_ADMOB_APP_ID` — your AdMob app ID (`ca-app-pub-…~…`)
- `GHOSTLY_APP_OPEN_AD_UNIT_ID` — your App Open ad unit ID (`ca-app-pub-…/…`)

Do not use live ad IDs for development/testing. Review applicable consent/privacy requirements before distributing a monetized build.

## Build

Clone the repository with submodules, then apply the branding/ad patch and build the upstream-derived app:

```bash
git clone --recurse-submodules https://github.com/Selteck1/ApkGhostly.git
python3 screen-recorder/patches/apply_ghostly.py screen-recorder/rec-upstream
gradle -p screen-recorder/rec-upstream --no-daemon assembleDebug
```

The APK is created under `screen-recorder/rec-upstream/app/build/outputs/apk/debug/`. GitHub Actions runs the same patch/build flow and uploads the APK artifact.

The previous Ghostly implementation remains in `screen-recorder/app` as a fallback reference; the current GitHub Actions artifact is built from the pinned REC-derived project.
