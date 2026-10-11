# Ghostly App Dumper (Android)

A standalone Android utility in the ApkGhostly repository. It lists installed apps and exports the unpacked file entries of their installed APK packages into a ZIP that preserves folders.

## Features

- Dark Ghostly-style interface with app icons, app/package name, version, APK size and search.
- Lists user-installed apps by default; an option reveals system apps too.
- Select an app and choose **Unpack contents**. The exporter reads every installed APK/split APK as a ZIP archive and writes its files into:
  - `package/base/` for the base APK
  - `package/split_N_name/` for each split APK
- Contents can include `assets/`, `res/`, `lib/`, compiled resource tables, the package manifest and other files present in the APK.
- Adds `dump-info.txt` with package version, source parts and export counts.
- Saves to **Downloads/Ghostly APK Dumps** using Android's MediaStore, without broad storage permission.
- Runs locally on Android 10 or newer; no root required.

## Build

Requirements: JDK 17, Android SDK Platform 35, Gradle 8.8.

From the repository root:

    gradle -p app-dumper --no-daemon assembleDebug

The debug APK is created at:

    app-dumper/app/build/outputs/apk/debug/app-debug.apk

GitHub Actions builds this project and publishes the debug APK as a workflow artifact.

## Important limitations

This is an **installed APK contents export**, not a complete dump of every file used by a game. It can export files actually embedded in the installed base/split APK packages. Files inside proprietary containers such as Unreal `.pak` remain packed as-is; this utility does not decrypt them or recursively unpack custom formats.

It cannot read another application's private directories such as `/data/user/0/<package>/`, runtime memory, private saves, or game resources downloaded later into app-private storage. Android sandboxes those locations from ordinary apps without root or a separately authorized debugging workflow. On recent Android versions, `Android/data` and `Android/obb` access is also restricted.

This utility is designed for local/sideloaded use. Distribution through Google Play would require reviewing package-visibility policy.
