#!/usr/bin/env python3
"""Apply the Ghostly downstream patch to the pinned pixl-hq/rec Android project.

The upstream source is GPL-3.0. This script is part of the corresponding source
for this derivative build. It runs in CI after checkout of the pinned submodule.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"
TEST_APP_OPEN_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
ADS_SDK_VERSION = "25.5.0"


def write_text(path: Path, content: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding="utf-8")


def require_replace_once(text: str, old: str, new: str, label: str) -> str:
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
        raise SystemExit("Usage: apply_ghostly.py <path-to-pinned-rec-project>")

    root = Path(sys.argv[1]).resolve()
    gradle_path = root / "app/build.gradle.kts"
    manifest_path = root / "app/src/main/AndroidManifest.xml"
    strings_path = root / "app/src/main/res/values/strings.xml"
    version_path = root / "version.properties"
    splash_path = root / "app/src/main/res/drawable/splash_background.xml"

    for path in (gradle_path, manifest_path, strings_path, version_path):
        if not path.is_file():
            raise SystemExit(f"Pinned REC source is incomplete; missing {path}")

    app_id = (os.environ.get("GHOSTLY_ADMOB_APP_ID") or TEST_ADMOB_APP_ID).strip()
    ad_unit_id = (os.environ.get("GHOSTLY_APP_OPEN_AD_UNIT_ID") or TEST_APP_OPEN_UNIT_ID).strip()
    if not re.fullmatch(r"ca-app-pub-\d+~\d+", app_id):
        raise SystemExit("GHOSTLY_ADMOB_APP_ID must look like ca-app-pub-1234567890123456~1234567890")
    if not re.fullmatch(r"ca-app-pub-\d+/\d+", ad_unit_id):
        raise SystemExit("GHOSTLY_APP_OPEN_AD_UNIT_ID must look like ca-app-pub-1234567890123456/1234567890")

    gradle = gradle_path.read_text(encoding="utf-8")
    gradle = require_replace_once(gradle, 'applicationId = "pixl.rec"',
                                  'applicationId = "ru.selteck.ghostly.recorder"',
                                  "application ID")
    if 'com.google.android.gms:play-services-ads:' not in gradle:
        gradle, count = re.subn(
            r"(?m)^dependencies\s*\{\s*\n",
            'dependencies {\n    implementation("com.google.android.gms:play-services-ads:' + ADS_SDK_VERSION + '")\n',
            gradle,
            count=1,
        )
        if count != 1:
            raise RuntimeError("Could not add Google Mobile Ads SDK to app dependencies")
    write_text(gradle_path, gradle)

    # Keep the new package updatable over Ghostly Screen Recorder 1.0.0.
    versions = version_path.read_text(encoding="utf-8")
    versions, name_count = re.subn(r"(?m)^VERSION_NAME=.*$", "VERSION_NAME=1.1.0", versions, count=1)
    versions, code_count = re.subn(r"(?m)^VERSION_CODE=.*$", "VERSION_CODE=16", versions, count=1)
    if name_count != 1 or code_count != 1:
        raise RuntimeError("Could not set Ghostly app version")
    write_text(version_path, versions)

    strings = strings_path.read_text(encoding="utf-8")
    strings = require_replace_once(strings, '<string name="app_name">REC</string>',
                                   '<string name="app_name">Ghostly Recorder</string>',
                                   "launcher label")
    strings = strings.replace(
        "REC requires overlay permission to show the floating game pill controls.",
        "Ghostly Recorder needs overlay permission to show floating game controls."
    )
    write_text(strings_path, strings)

    manifest = manifest_path.read_text(encoding="utf-8")
    application_match = re.search(r"<application\b[\s\S]*?>", manifest)
    if not application_match:
        raise RuntimeError("Could not find the application element in AndroidManifest.xml")
    app_tag_end = application_match.end()
    app_meta_pattern = re.compile(
        r'<meta-data\s+android:name="com\.google\.android\.gms\.ads\.APPLICATION_ID"'
        r'\s+android:value="[^"]+"\s*/>'
    )
    app_meta = (
        '        <meta-data\n'
        '            android:name="com.google.android.gms.ads.APPLICATION_ID"\n'
        f'            android:value="{app_id}" />'
    )
    existing_meta = app_meta_pattern.search(manifest, app_tag_end)
    if existing_meta:
        manifest = manifest[:existing_meta.start()] + app_meta + manifest[existing_meta.end():]
    else:
        manifest = manifest[:app_tag_end] + "\n" + app_meta + manifest[app_tag_end:]

    # Move the launcher entry point to a branded startup screen. It shows an
    # App Open ad from that loading screen or continues after a bounded timeout.
    main_marker = '<activity\n            android:name=".ui.MainActivity"'
    main_start = manifest.find(main_marker)
    if main_start < 0:
        raise RuntimeError("Could not find MainActivity in AndroidManifest.xml")
    main_end = manifest.find("</activity>", main_start)
    if main_end < 0:
        raise RuntimeError("MainActivity activity tag is incomplete")
    main_end += len("</activity>")
    main_block = manifest[main_start:main_end]
    main_block, _ = re.subn(
        r"\s*<intent-filter>\s*<action android:name=\"android\.intent\.action\.MAIN\"\s*/>\s*"
        r"<category android:name=\"android\.intent\.category\.LAUNCHER\"\s*/>\s*</intent-filter>",
        "",
        main_block,
        count=1,
    )
    main_block = require_replace_once(main_block, 'android:exported="true"',
                                      'android:exported="false"', "MainActivity exported flag")
    manifest = manifest[:main_start] + main_block + manifest[main_end:]

    splash_activity = """        <activity
            android:name=".ui.GhostlyStartActivity"
            android:exported="true"
            android:theme="@style/Theme.REC.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

"""
    if 'android:name=".ui.GhostlyStartActivity"' not in manifest:
        manifest = manifest[:main_start] + splash_activity + manifest[main_start:]
    write_text(manifest_path, manifest)

    # Avoid showing upstream brand artwork during the native cold-start frame.
    write_text(
        splash_path,
        """<?xml version="1.0" encoding="utf-8"?>
<layer-list xmlns:android="http://schemas.android.com/apk/res/android">
    <item android:drawable="@color/obsidian_background" />
</layer-list>
""",
    )

    start_activity = root / "app/src/main/java/pixl/rec/ui/GhostlyStartActivity.kt"
    write_text(
        start_activity,
        r'''package pixl.rec.ui

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
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback

/**
 * Branded loading screen for Google App Open ads.
 * Failed/offline requests never prevent the recorder from opening.
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
            if (!routedToRecorder && !isFinishing) loadAndShowAd()
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
            object : AppOpenAdLoadCallback() {
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

    print("Ghostly adaptation applied.")
    print("  Package: ru.selteck.ghostly.recorder")
    print("  Version: 1.1.0 (code 16)")
    print(f"  AdMob app ID: {'Google test ID' if app_id == TEST_ADMOB_APP_ID else 'custom ID configured'}")
    print(f"  App Open ad unit: {'Google test ID' if ad_unit_id == TEST_APP_OPEN_UNIT_ID else 'custom ID configured'}")
    print("  Source: pixl-hq/rec at pinned commit; GPL-3.0")


if __name__ == "__main__":
    main()
