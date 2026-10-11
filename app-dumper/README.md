# Ghostly App Dumper (Android)

A standalone Android utility in the ApkGhostly repository. It lists installed apps, lets you select one, and exports the installed APK package files to local Downloads.

## Features

- Dark Ghostly-style interface with app icons, app/package name, version, APK size and search.
- Lists user-installed apps by default; an option reveals system apps too.
- Single-APK apps are exported as `.apk`.
- Apps installed as multiple APK splits are exported as a `.zip` containing `base.apk`, the split APK files and a `dump-info.txt`.
- Files are saved to **Downloads/Ghostly APK Dumps** using Android's MediaStore, without requesting broad file-storage permissions.
- Runs locally on Android 10 or newer; no root required.

## Build

Requirements: JDK 17, Android SDK Platform 35, Gradle 8.8.

From the repository root:

    gradle -p app-dumper --no-daemon assembleDebug

The debug APK is created at:

    app-dumper/app/build/outputs/apk/debug/app-debug.apk

GitHub Actions builds this project and publishes the debug APK as a workflow artifact.

## Scope and limitations

This exports the installed app's APK package files, not a full filesystem or memory dump. It does not access an app's private data directory, process memory, game saves, runtime-loaded assets or obb/data folders. Many Play-installed apps use split APKs; those are bundled into a ZIP and need to be installed with a split-aware installer if you intend to restore them. Package visibility is declared for this sideloaded utility; distribution through Google Play would require reviewing its package-visibility policy.
