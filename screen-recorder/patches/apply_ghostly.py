#!/usr/bin/env python3
"""Apply Ghostly branding and AdMob App Open ads to the pinned MIT FluxRecorder source."""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
TEST_APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
ADS_SDK_VERSION = "25.5.0"

MIT_NOTICE = """MIT License

Copyright (c) 2024 Icradle Innovations Ltd

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
"""


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count > 1:
        raise RuntimeError(f"Expected one occurrence of {label}, found {count}")
    if count == 1:
        return text.replace(old, new, 1)
    if new in text:
        return text
    raise RuntimeError(f"Could not find patch target: {label}")


def main() -> None:
    if len(sys.argv) != 2:
        raise SystemExit("Usage: apply_ghostly.py <path-to-fluxrecorder-project>")

    root = Path(sys.argv[1]).resolve()
    gradle_path = root / "app/build.gradle.kts"
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    strings_path = root / "app/src/main/res/values/strings.xml"
    themes_path = root / "app/src/main/res/values/themes.xml"
    catalog_path = root / "gradle/libs.versions.toml"
    version_checks = (gradle_path, manifest_path, strings_path, themes_path, catalog_path)
    for path in version_checks:
        if not path.is_file():
            raise SystemExit(f"Pinned FluxRecorder source is incomplete; missing {path}")

    app_id = (os.environ.get("GHOSTLY_ADMOB_APP_ID") or TEST_ADMOB_APP_ID).strip()
    ad_unit_id = (os.environ.get("GHOSTLY_APP_OPEN_AD_UNIT_ID") or TEST_APP_OPEN_UNIT_ID).strip()
    if not re.fullmatch(r"ca-app-pub-\d+~\d+", app_id):
        raise SystemExit("GHOSTLY_ADMOB_APP_ID must look like ca-app-pub-1234567890123456~1234567890")
    if not re.fullmatch(r"ca-app-pub-\d+/\d+", ad_unit_id):
        raise SystemExit("GHOSTLY_APP_OPEN_AD_UNIT_ID must look like ca-app-pub-1234567890123456/1234567890")

    # Keep the original Kotlin namespace so Hilt and generated R references continue to work.
    gradle = gradle_path.read_text(encoding="utf-8")
    gradle = replace_once(gradle, 'applicationId = "com.flux.recorder"',
                          'applicationId = "ru.selteck.ghostly.recorder"', "application ID")
    gradle = replace_once(gradle, "versionCode = 6", "versionCode = 16", "version code")
    gradle = replace_once(gradle, 'versionName = "1.2.1"', 'versionName = "1.1.0"', "version name")
    if "com.google.android.gms:play-services-ads:" not in gradle:
        gradle, count = re.subn(
            r"(?m)^dependencies\s*\{\s*\n",
            'dependencies {\n    implementation("com.google.android.gms:play-services-ads:' + ADS_SDK_VERSION + '")\n',
            gradle,
            count=1,
        )
        if count != 1:
            raise RuntimeError("Could not add Google Mobile Ads dependency")
    write_text(gradle_path, gradle)

    # Google Mobile Ads 25.5.0 includes Kotlin 2.3 metadata. Upgrade the upstream
    # Kotlin/Compose compiler plugins to a compatible release before compiling.
    catalog = catalog_path.read_text(encoding="utf-8")
    catalog = replace_once(catalog, 'kotlin = "2.0.21"', 'kotlin = "2.3.21"', "Kotlin toolchain version")
    write_text(catalog_path, catalog)

    strings = strings_path.read_text(encoding="utf-8")
    strings = strings.replace(">Flux Recorder<", ">Ghostly Recorder<")
    strings = strings.replace(">Flux recorder<", ">Ghostly Recorder<")
    strings = strings.replace(">Screen Record<", ">Ghostly Record<")
    write_text(strings_path, strings)

    themes = themes_path.read_text(encoding="utf-8")
    themes = themes.replace("<item name=\"windowSplashScreenBackground\">#121212</item>",
                            "<item name=\"windowSplashScreenBackground\">#0B0B12</item>")
    themes = replace_once(
        themes,
        "<item name=\"windowSplashScreenAnimatedIcon\">@mipmap/ic_launcher</item>",
        "<item name=\"windowSplashScreenAnimatedIcon\">@drawable/ic_ghostly_mark</item>",
        "native splash icon",
    )
    write_text(themes_path, themes)

    write_text(
        root / "app/src/main/res/drawable/ic_ghostly_mark.xml",
        """<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#A78BFA"
        android:pathData="M54,9 C33,9 18,25 18,46 L18,87 L31,78 L43,87 L54,78 L65,87 L77,78 L90,87 L90,46 C90,25 75,9 54,9 Z" />
    <path
        android:fillColor="#0B0B12"
        android:pathData="M35,39 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0" />
    <path
        android:fillColor="#0B0B12"
        android:pathData="M65,39 a4,4 0 1,0 8,0 a4,4 0 1,0 -8,0" />
</vector>
""",
    )

    manifest = manifest_path.read_text(encoding="utf-8")
    for permission in ("android.permission.INTERNET", "android.permission.ACCESS_NETWORK_STATE"):
        if permission not in manifest:
            manifest = manifest.replace(
                "<application",
                f'    <uses-permission android:name="{permission}" />\n\n    <application',
                1,
            )

    application_match = re.search(r"<application\b[\s\S]*?>", manifest)
    if not application_match:
        raise RuntimeError("Could not find application tag in manifest")
    app_end = application_match.end()

    # Set Ghostly launcher artwork and embed the required upstream MIT license text in the APK.
    manifest = replace_once(manifest, 'android:icon="@mipmap/ic_launcher"',
                            'android:icon="@drawable/ic_ghostly_mark"', "app icon")
    if 'android:roundIcon="@mipmap/ic_launcher_round"' in manifest:
        manifest = replace_once(manifest, 'android:roundIcon="@mipmap/ic_launcher_round"',
                                'android:roundIcon="@drawable/ic_ghostly_mark"', "round app icon")
    application_match = re.search(r"<application\b[\s\S]*?>", manifest)
    app_end = application_match.end() if application_match else app_end
    app_meta = (
        '\n        <meta-data\n'
        '            android:name="com.google.android.gms.ads.APPLICATION_ID"\n'
        f'            android:value="{app_id}" />'
    )
    meta_pattern = re.compile(
        r'<meta-data\s+android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"'
        r'\s+android:value="[^"]+"\s*/>'
    )
    existing_meta = meta_pattern.search(manifest, app_end)
    if existing_meta:
        manifest = manifest[:existing_meta.start()] + app_meta.strip() + manifest[existing_meta.end():]
    else:
        manifest = manifest[:app_end] + app_meta + manifest[app_end:]

    # The original Activity is no longer the launcher; the startup screen shows the ad and then routes to it.
    main_marker = '<activity\n            android:name="com.flux.recorder.MainActivity"'
    main_start = manifest.find(main_marker)
    if main_start < 0:
        raise RuntimeError("Could not find FluxRecorder MainActivity in the manifest")
    main_end = manifest.find("</activity>", main_start)
    if main_end < 0:
        raise RuntimeError("MainActivity activity tag is incomplete")
    main_end += len("</activity>")
    main_block = manifest[main_start:main_end]
    main_block, filter_count = re.subn(
        r"\s*<intent-filter>\s*<action android:name=\"android\.intent\.action\.MAIN\"\s*/>\s*"
        r"<category android:name=\"android\.intent\.category\.LAUNCHER\"\s*/>\s*</intent-filter>",
        "",
        main_block,
        count=1,
    )
    if filter_count != 1 and 'android:name=".GhostlyStartActivity"' not in manifest:
        raise RuntimeError("Could not move the original launcher intent filter")
    main_block = replace_once(main_block, 'android:exported="true"',
                              'android:exported="false"', "MainActivity exported flag")
    manifest = manifest[:main_start] + main_block + manifest[main_end:]

    splash_activity = """        <activity
            android:name=".GhostlyStartActivity"
            android:exported="true"
            android:theme="@style/Theme.App.Starting"
            android:launchMode="singleTop">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

"""
    if 'android:name=".GhostlyStartActivity"' not in manifest:
        manifest = manifest[:main_start] + splash_activity + manifest[main_start:]
    write_text(manifest_path, manifest)

    write_text(
        root / "app/src/main/java/com/flux/recorder/GhostlyStartActivity.kt",
        r'''package com.flux.recorder

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd

/**
 * Ghostly startup screen. Failed/offline ad requests never prevent the recorder from opening.
 */
class GhostlyStartActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var routedToRecorder = false
    private var showingAd = false

    private val startupTimeout = Runnable {
        if (!showingAd) openRecorder()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(11, 11, 18)
        window.navigationBarColor = Color.rgb(11, 11, 18)
        setContentView(buildSplash())

        handler.postDelayed(startupTimeout, 5_000L)
        MobileAds.initialize(this) {
            handler.post {
                if (!routedToRecorder && !isFinishing) loadAndShowAd()
            }
        }
    }

    private fun buildSplash(): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(Color.rgb(11, 11, 18))

            addView(TextView(this@GhostlyStartActivity).apply {
                text = "◈"
                textSize = 52f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(167, 139, 250))
            })

            addView(TextView(this@GhostlyStartActivity).apply {
                text = "GHOSTLY"
                textSize = 28f
                typeface = Typeface.DEFAULT_BOLD
                letterSpacing = 0.12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(244, 242, 255))
            })

            addView(TextView(this@GhostlyStartActivity).apply {
                text = "SCREEN RECORDER"
                textSize = 12f
                letterSpacing = 0.18f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(167, 139, 250))
            })

            addView(ProgressBar(this@GhostlyStartActivity).apply {
                isIndeterminate = true
            }, LinearLayout.LayoutParams(48, 48).apply {
                gravity = Gravity.CENTER
                topMargin = 30
            })

            addView(TextView(this@GhostlyStartActivity).apply {
                text = "Подготавливаем рекордер…"
                textSize = 13f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(157, 156, 179))
            })
        }

    private fun loadAndShowAd() {
        if (routedToRecorder || isFinishing || isDestroyed) return
        AppOpenAd.load(
            this,
            "__GHOSTLY_APP_OPEN_AD_UNIT_ID__",
            AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    if (routedToRecorder || isFinishing || isDestroyed) return
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            showingAd = false
                            openRecorder()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            showingAd = false
                            openRecorder()
                        }
                    }
                    showingAd = true
                    try {
                        ad.show(this@GhostlyStartActivity)
                    } catch (_: Exception) {
                        showingAd = false
                        openRecorder()
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    openRecorder()
                }
            }
        )
    }

    private fun openRecorder() {
        if (routedToRecorder || showingAd) return
        routedToRecorder = true
        handler.removeCallbacks(startupTimeout)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(startupTimeout)
        super.onDestroy()
    }
}
'''.replace("__GHOSTLY_APP_OPEN_AD_UNIT_ID__", ad_unit_id),
    )

    write_text(
        root / "app/src/main/assets/third_party_notices.txt",
        "FluxRecorder source adaptation used by Ghostly Recorder\n"
        "Upstream project: https://github.com/IcradleInnovationsLtd/FluxRecorder\n"
        "Pinned commit: 4ddca4280c5a79a808f759bfe82b6802898b14ce\n\n"
        + MIT_NOTICE,
    )
    print("Ghostly adaptation applied.")
    print("  Package: ru.selteck.ghostly.recorder")
    print("  Version: 1.1.0 (code 16)")
    print(f"  AdMob app ID: {'Google test ID' if app_id == TEST_ADMOB_APP_ID else 'custom ID configured'}")
    print(f"  App Open ad unit: {'Google test ID' if ad_unit_id == TEST_APP_OPEN_UNIT_ID else 'custom ID configured'}")
    print("  Source: IcradleInnovationsLtd/FluxRecorder at pinned commit; MIT License")


if __name__ == "__main__":
    main()
