#!/usr/bin/env python3
"""Regenerate the static catalog and SHA-256 hashes from .lineup ZIPs."""
import hashlib, json, zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS = ROOT / "content" / "packs"
OUTPUT = ROOT / "content" / "catalog.json"


def main():
    entries, seen = [], set()
    for path in sorted(PACKS.glob("*.lineup")):
        raw = path.read_bytes()
        try:
            with zipfile.ZipFile(path) as archive:
                manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
        except (KeyError, json.JSONDecodeError, zipfile.BadZipFile) as exc:
            raise SystemExit(f"Invalid package {path.name}: {exc}") from exc
        if manifest.get("format") != "lineup" or manifest.get("schemaVersion") != 1:
            raise SystemExit(f"Unsupported package format: {path.name}")
        pack_id = path.stem
        if pack_id in seen:
            raise SystemExit(f"Duplicate package ID: {pack_id}")
        seen.add(pack_id)
        entries.append({
            "id": pack_id, "title": str(manifest.get("title") or path.stem),
            "version": int(manifest.get("version") or 1),
            "file": f"content/packs/{path.name}",
            "sha256": hashlib.sha256(raw).hexdigest(), "size": len(raw),
        })
    doc = {"schemaVersion": 1, "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(), "packs": entries}
    OUTPUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote catalog with {len(entries)} package(s)")


if __name__ == "__main__":
    main()
