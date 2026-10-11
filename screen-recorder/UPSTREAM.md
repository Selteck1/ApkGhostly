# Upstream recorder source and modifications

Ghostly Screen Recorder's current CI APK is built from the public PixL REC project:
https://github.com/pixl-hq/rec

Pinned upstream commit: `0c855ef7a73111be394d5306302dd0b29245272d`

License: GPL-3.0. The upstream source tree and its LICENSE are retained during build. This repository contains the reproducible patch script at [patch_rec_build.py](patch_rec_build.py), which changes the package/display branding and Gallery destination and adds the Google Mobile Ads SDK + UMP consent flow with Google's test App Open ad IDs.

The test IDs are for development only and do not generate revenue. Before a public monetized release, replace them with the app owner's AdMob identifiers, publish a consent message in AdMob Privacy & messaging, and redistribute the corresponding source and license notices.
