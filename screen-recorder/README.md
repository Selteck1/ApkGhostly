# Ghostly Screen Recorder (Android)

This build uses the recording engine and application source from the open-source **REC** Android project by PixL, pinned to commit `0c855ef7a73111be394d5306302dd0b29245272d`. The build workflow applies Ghostly branding and adds a Google App Open ad at app launch.

- Upstream source: https://github.com/pixl-hq/rec
- Upstream license: GPL-3.0 (the upstream LICENSE is retained in the source checkout).
- Ghostly modifications/build patch: `screen-recorder/patch_rec_build.py`
- Package ID: `ru.selteck.ghostly.recorder` (debug builds use `.debug`).

## Recording capabilities

The upstream engine uses a hardware `MediaCodec` surface pipeline and supports internal playback-audio capture where Android and the captured app permit it, along with microphone capture, advanced codec/bitrate settings, overlays, a recording library, and optional replay/streaming features. Actual resolution, frame rate, audio routes, and features depend on the Android version and device encoder. No particular FPS is guaranteed on every phone.

Files are saved to Gallery under Movies/Ghostly Recorder after applying the Ghostly output-folder patch.

## Advertising status

The APK currently uses Google's **official test AdMob App ID and test App Open ad unit**. This is intentional for the first build: test ads do **not** generate income. To earn revenue, the app owner must replace both IDs with their own AdMob App ID and App Open unit, publish an appropriate privacy/consent message in AdMob Privacy & messaging, then rebuild and verify the production configuration. The UMP SDK consent flow is included, but a live consent message must be configured in the owner's AdMob account.

Google's test identifiers and guidance:
- https://developers.google.com/admob/android/quick-start
- https://developers.google.com/admob/android/app-open
- https://developers.google.com/admob/android/privacy

## Build

The CI workflow checks out the pinned upstream REC source, applies the tracked Ghostly patch, builds a debug APK, and publishes it as a GitHub Actions artifact. You can start the workflow manually from the Actions tab or let it run after a relevant push/PR.

For GPL-3.0 compliance and reproducibility, the upstream source URL and exact commit are recorded in this README and the generated `GHOSTLY-MODIFICATIONS.md`; the build patch is included in this repository. The APK is a modified build of REC, not a decompiled or copied RECGO APK.
