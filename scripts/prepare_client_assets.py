#!/usr/bin/env python3
"""Prepare the .lineup ZIP as an APK asset and print the required content revision."""
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS = ROOT / "content" / "packs"
CATALOG = ROOT / "content" / "catalog.json"
ASSET = ROOT / "client" / "src" / "main" / "assets" / "community.lineup"


def main():
    ASSET.parent.mkdir(parents=True, exist_ok=True)
    revision = 0
    if CATALOG.exists():
        catalog = json.loads(CATALOG.read_text(encoding="utf-8"))
        revision = max(0, int(catalog.get("revision", 0)))

    source = PACKS / "community.lineup"
    if source.exists():
        # Verify the ZIP before copying it into the APK.
        with zipfile.ZipFile(source) as archive:
            manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
        if manifest.get("format") != "lineup" or manifest.get("schemaVersion") != 1:
            raise SystemExit("content/packs/community.lineup has an unsupported format")
        ASSET.write_bytes(source.read_bytes())
    else:
        # A first-install client is buildable even before the first public database exists.
        manifest = {
            "format": "lineup",
            "schemaVersion": 1,
            "packId": "community",
            "version": 1,
            "title": "Lineup — база раскидок",
            "blocks": [],
        }
        with zipfile.ZipFile(ASSET, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("manifest.json", json.dumps(manifest, ensure_ascii=False, indent=2) + "\n")

    print(f"CONTENT_REVISION={revision}")


if __name__ == "__main__":
    main()
