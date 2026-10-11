#!/usr/bin/env python3
"""Apply Ghostly branding and test App Open ads to the pinned upstream REC source.

Upstream: https://github.com/pixl-hq/rec
Pinned commit: 0c855ef7a73111be394d5306302dd0b29245272d
Upstream license: GPL-3.0; original LICENSE and source remain intact.
"""
from __future__ import annotations

import argparse
import re
import subprocess
from pathlib import Path

UPSTREAM_COMMIT = "0c855ef7a73111be394d5306302dd0b29245272d"
TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match in {path}, got {count}")
    path.write_text(text.replace(old, new, 1), encoding="utf-8")


def regex_replace_once(path: Path, pattern: str, replacement: str, label: str) -> None:
    text = path.read_text(encoding="utf-8")
    updated, count = re.subn(pattern, replacement, text, count=1)
    if count != 1:
        raise RuntimeError(f"{label}: expected exactly one match in {path}, got {count}")
    path.write_text(updated, encoding="utf-8")


APP_OPEN_AD_SOURCE = r'''package pixl.rec.ads

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Launch-only Google App Open ads for Ghostly Recorder. Test creatives earn no revenue. */
class GhostlyAppOpenAd(private val activity: Activity) {
    private val consentInformation by lazy {
        UserMessagingPlatform.getConsentInformation(activity)
    }

    private var consentFlowStarted = false
    private var mobileAdsInitializationStarted = false
    private var loadingAd = false
    private var activityResumed = false
    private var adShownThisLaunch = false
    private var isShowingAd = false
    private var appOpenAd: AppOpenAd? = null

    fun onResume() {
        activityResumed = true
        if (!consentFlowStarted) {
            consentFlowStarted = true
            requestConsent()
        } else {
            showAdIfReady()
        }
    }

    fun onPause() {
        activityResumed = false
    }

    private fun requestConsent() {
        val params = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) Log.w(TAG, "Consent form: ${formError.message}")
                    initializeAdsIfAllowed()
                }
            },
            { requestError ->
                Log.w(TAG, "Consent update: ${requestError.message}")
                initializeAdsIfAllowed()
            }
        )
    }

    private fun initializeAdsIfAllowed() {
        if (activity.isFinishing || activity.isDestroyed || !consentInformation.canRequestAds()) return
        if (mobileAdsInitializationStarted) {
            loadAppOpenAd()
            return
        }
        mobileAdsInitializationStarted = true
        MobileAds.initialize(activity) {
            activity.runOnUiThread { loadAppOpenAd() }
        }
    }

    private fun loadAppOpenAd() {
        if (activity.isFinishing || activity.isDestroyed || loadingAd ||
            appOpenAd != null || adShownThisLaunch || !consentInformation.canRequestAds()
        ) return

        loadingAd = true
        AppOpenAd.load(
            activity,
            TEST_APP_OPEN_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    loadingAd = false
                    appOpenAd = ad
                    showAdIfReady()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    loadingAd = false
                    Log.w(TAG, "App Open ad load failed: ${error.message}")
                }
            }
        )
    }

    private fun showAdIfReady() {
        if (!activityResumed || activity.isFinishing || activity.isDestroyed ||
            isShowingAd || adShownThisLaunch
        ) return

        val ad = appOpenAd ?: return
        appOpenAd = null
        isShowingAd = true
        adShownThisLaunch = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "App Open test ad is showing")
            }

            override fun onAdDismissedFullScreenContent() {
                isShowingAd = false
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                isShowingAd = false
                Log.w(TAG, "App Open ad display failed: ${error.message}")
            }
        }
        ad.show(activity)
    }

    companion object {
        private const val TAG = "GhostlyAppOpenAd"
        // Official Google test unit. Replace with the owner's own ad unit before monetizing.
        private const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    }
}
'''


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True, type=Path)
    root = parser.parse_args().project.resolve()

    required = (
        "app/build.gradle.kts",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/pixl/rec/ui/MainActivity.kt",
        "app/src/main/res/values/strings.xml",
    )
    missing = [item for item in required if not (root / item).is_file()]
    if missing:
        raise SystemExit(f"Not a REC source tree; missing: {', '.join(missing)}")

    if (root / ".git").exists():
        current = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            capture_output=True, text=True, check=True
        ).stdout.strip()
        if current != UPSTREAM_COMMIT:
            raise SystemExit(f"REC source mismatch: expected {UPSTREAM_COMMIT}, found {current}")

    build_file = root / "app/build.gradle.kts"
    replace_once(build_file, 'applicationId = "pixl.rec"',
                 'applicationId = "ru.selteck.ghostly.recorder"', "Ghostly package ID")
    replace_once(build_file, 'val baseName = "REC-v${variant.versionName}"',
                 'val baseName = "GhostlyScreenRecorder-v${variant.versionName}"', "APK filename")
    regex_replace_once(
        build_file,
        r"(?m)^dependencies \{\s*$",
        'dependencies {\n'
        '    // Ghostly launch ads (Google test IDs until the owner configures AdMob)\n'
        '    implementation("com.google.android.gms:play-services-ads:25.5.0")\n'
        '    implementation("com.google.android.ump:user-messaging-platform:4.0.0")',
        "Ad SDK dependencies",
    )

    manifest = root / "app/src/main/AndroidManifest.xml"
    replace_once(
        manifest,
        'android:theme="@style/Theme.REC">',
        'android:theme="@style/Theme.REC">\n'
        '        <!-- Google sample AdMob App ID: test ads only; no revenue. -->\n'
        '        <meta-data\n'
        '            android:name="com.google.android.gms.ads.APPLICATION_ID"\n'
        f'            android:value="{TEST_ADMOB_APP_ID}" />',
        "AdMob App ID metadata",
    )

    strings = root / "app/src/main/res/values/strings.xml"
    replace_once(strings, '<string name="app_name">REC</string>',
                 '<string name="app_name">Ghostly Recorder</string>', "App name")
    replace_once(
        strings,
        "REC requires overlay permission to show the floating game pill controls.",
        "Ghostly Recorder needs overlay permission to show optional floating game controls.",
        "Overlay permission wording",
    )

    dashboard = root / "app/src/main/java/pixl/rec/ui/dashboard/DashboardScreen.kt"
    replace_once(dashboard, 'text = "REC",', 'text = "GHOSTLY",', "Dashboard brand")
    colors = root / "app/src/main/java/pixl/rec/ui/theme/Color.kt"
    replace_once(colors, "Color(0xFFFF0033)", "Color(0xFF8B5CF6)", "Ghostly primary color")
    replace_once(colors, "Color(0xFFFF3864)", "Color(0xFFA78BFA)", "Ghostly highlight color")

    writer = root / "app/src/main/java/pixl/rec/core/storage/MediaStoreWriter.kt"
    replace_once(
        writer, 'if (isStreamSession) "STREAM_" else "REC_"',
        'if (isStreamSession) "Ghostly_Stream_" else "Ghostly_"', "Recording filename",
    )
    replace_once(
        writer, '"${Environment.DIRECTORY_MOVIES}/PixL-REC"',
        '"${Environment.DIRECTORY_MOVIES}/Ghostly Recorder"', "Gallery output folder",
    )

    main_activity = root / "app/src/main/java/pixl/rec/ui/MainActivity.kt"
    replace_once(
        main_activity, "import pixl.rec.BuildConfig",
        "import pixl.rec.BuildConfig\nimport pixl.rec.ads.GhostlyAppOpenAd", "Ads import",
    )
    replace_once(
        main_activity,
        "    private val viewModel: DashboardViewModel by viewModels()",
        "    private val viewModel: DashboardViewModel by viewModels()\n"
        "    private lateinit var ghostlyAppOpenAd: GhostlyAppOpenAd",
        "Ad manager field",
    )
    replace_once(
        main_activity, "        super.onCreate(savedInstanceState)",
        "        super.onCreate(savedInstanceState)\n"
        "        ghostlyAppOpenAd = GhostlyAppOpenAd(this)", "Ad manager init",
    )
    activity_text = main_activity.read_text(encoding="utf-8")
    resume_anchor = "    override fun onResume() {\n        super.onResume()"
    if activity_text.count(resume_anchor) != 1:
        raise SystemExit("Expected exactly one existing MainActivity.onResume() hook.")
    activity_text = activity_text.replace(
        resume_anchor,
        resume_anchor + "\n        ghostlyAppOpenAd.onResume()",
        1,
    )
    if re.search(r"(?m)^\s*override fun onPause\(", activity_text):
        raise SystemExit("MainActivity already overrides onPause; update the lifecycle hook manually.")
    final_brace = activity_text.rfind("}")
    if final_brace < 0:
        raise SystemExit("Unable to find MainActivity closing brace.")
    activity_text = (
        activity_text[:final_brace]
        + "\n    override fun onPause() {\n"
        + "        ghostlyAppOpenAd.onPause()\n"
        + "        super.onPause()\n"
        + "    }\n"
        + activity_text[final_brace:]
    )
    main_activity.write_text(activity_text, encoding="utf-8")

    ad_file = root / "app/src/main/java/pixl/rec/ads/GhostlyAppOpenAd.kt"
    ad_file.parent.mkdir(parents=True, exist_ok=True)
    ad_file.write_text(APP_OPEN_AD_SOURCE, encoding="utf-8")

    (root / "GHOSTLY-MODIFICATIONS.md").write_text(
        "# Ghostly Recorder build\n\n"
        "This build is based on PixL REC at commit " + UPSTREAM_COMMIT + ":\n"
        "https://github.com/pixl-hq/rec\n\n"
        "Upstream license: GPL-3.0. The original LICENSE file is retained.\n"
        "Ghostly modifications: package and selected interface branding, Gallery folder, and "
        "Google Mobile Ads App Open integration.\n\n"
        "Google sample AdMob App ID and App Open unit are used for development; test ads earn "
        "no revenue. To monetize, configure the owner's own AdMob IDs and a published UMP "
        "privacy message in AdMob Privacy & messaging. The build patch is available at "
        "https://github.com/Selteck1/ApkGhostly/blob/main/screen-recorder/patch_rec_build.py\n",
        encoding="utf-8",
    )


if __name__ == "__main__":
    main()
