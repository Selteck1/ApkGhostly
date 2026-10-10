#!/usr/bin/env python3
"""Regenerate the content catalog and increment the required revision only when packs change."""
import hashlib
import json
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
PACKS = ROOT / "content" / "packs"
OUTPUT = ROOT / "content" / "catalog.json"


def read_previous():
    if not OUTPUT.exists():
        return {"revision": 0, "packs": []}
    try:
        old = json.loads(OUTPUT.read_text(encoding="utf-8"))
        return {"revision": max(0, int(old.get("revision", 0))), "packs": old.get("packs", [])}
    except (OSError, ValueError, TypeError, json.JSONDecodeError):
        return {"revision": 0, "packs": []}


def main():
    previous = read_previous()
    entries = []
    seen = set()
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
        if not pack_id or not all(ch.isalnum() or ch in "_-" for ch in pack_id):
            raise SystemExit(f"Package name must use letters, digits, underscore or hyphen: {path.name}")
        if pack_id in seen:
            raise SystemExit(f"Duplicate package ID: {pack_id}")
        seen.add(pack_id)
        entries.append({
            "id": pack_id,
            "title": str(manifest.get("title") or path.stem),
            "version": int(manifest.get("version") or 1),
            "file": f"content/packs/{path.name}",
            "sha256": hashlib.sha256(raw).hexdigest(),
            "size": len(raw),
        })

    # A manual workflow rerun should not force every device to redownload the same base.
    # Increment only if an actual package was added, removed, or changed.
    old_pack_state = [
        {key: item.get(key) for key in ("id", "title", "version", "file", "sha256", "size")}
        for item in previous["packs"] if isinstance(item, dict)
    ]
    new_pack_state = [
        {key: item.get(key) for key in ("id", "title", "version", "file", "sha256", "size")}
        for item in entries
    ]
    revision = previous["revision"] + 1 if old_pack_state != new_pack_state else previous["revision"]

    doc = {
        "schemaVersion": 1,
        "revision": revision,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat(),
        "packs": entries,
    }
    OUTPUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote catalog revision {revision} with {len(entries)} package(s)")


if __name__ == "__main__":
    main()
