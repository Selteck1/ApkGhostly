import base64
import hmac
import io
import json
import os
import zipfile
from typing import Any

import requests
from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.responses import JSONResponse
from urllib.parse import urlparse

app = FastAPI(title="Lineup Content Publisher", version="1.0.0")
GITHUB_API = "https://api.github.com"
REPO = os.getenv("LINEUP_GITHUB_REPO", "Selteck1/ApkGhostly")
BRANCH = os.getenv("LINEUP_GITHUB_BRANCH", "main")
PACK_PATH = "content/packs/community.lineup"
MAX_UPLOAD_BYTES = 90 * 1024 * 1024


def github_headers() -> dict[str, str]:
    token = os.getenv("LINEUP_GITHUB_TOKEN", "").strip()
    if not token or token.startswith("SET_"):
        raise HTTPException(status_code=503, detail="Publishing is not configured yet. Set LINEUP_GITHUB_TOKEN in Render environment variables.")
    return {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "Lineup-Content-Publisher/1.0",
    }


def check_admin(received: str | None) -> None:
    expected = os.getenv("LINEUP_ADMIN_KEY", "").strip()
    if not expected or expected.startswith("SET_"):
        raise HTTPException(status_code=503, detail="Admin publishing key is not configured on the server.")
    if received is None or not hmac.compare_digest(received, expected):
        raise HTTPException(status_code=401, detail="Invalid publishing key.")


def validate_pack(data: bytes) -> dict[str, Any]:
    if not data or len(data) > MAX_UPLOAD_BYTES:
        raise HTTPException(status_code=413, detail="Package must be between 1 byte and 90 MB.")
    try:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            entries = archive.infolist()
            total = 0
            names: set[str] = set()
            for entry in entries:
                name = entry.filename
                if name.startswith("/") or ".." in name.split("/") or "\\" in name:
                    raise HTTPException(status_code=400, detail="Unsafe path in package.")
                if entry.is_dir():
                    continue
                if name in names:
                    raise HTTPException(status_code=400, detail="Duplicate file in package.")
                names.add(name)
                total += entry.file_size
                if total > MAX_UPLOAD_BYTES:
                    raise HTTPException(status_code=413, detail="Uncompressed package is too large.")
            if "manifest.json" not in names:
                raise HTTPException(status_code=400, detail="Package does not contain manifest.json.")
            manifest = json.loads(archive.read("manifest.json").decode("utf-8"))
    except HTTPException:
        raise
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid .lineup package: " + str(exc)) from exc

    if manifest.get("format") != "lineup" or manifest.get("schemaVersion") != 1:
        raise HTTPException(status_code=400, detail="Unsupported Lineup package format.")
    blocks = manifest.get("blocks")
    if not isinstance(blocks, list) or not blocks or len(blocks) > 5000:
        raise HTTPException(status_code=400, detail="The database must contain 1–5000 blocks.")
    for block in blocks:
        if not isinstance(block, dict) or not str(block.get("title", "")).strip():
            raise HTTPException(status_code=400, detail="Every block needs a title.")
        photos = block.get("photos", [])
        if not isinstance(photos, list) or len(photos) > 12:
            raise HTTPException(status_code=400, detail="A block may contain up to 12 photos.")
        for photo in photos:
            if not isinstance(photo, dict):
                raise HTTPException(status_code=400, detail="Invalid photo description.")
            filename = str(photo.get("file", ""))
            if not filename.startswith("images/") or filename not in names:
                raise HTTPException(status_code=400, detail="A referenced image is missing from the package.")
    return manifest


def github_request(method: str, url: str, **kwargs: Any) -> requests.Response:
    try:
        response = requests.request(method, url, headers=github_headers(), timeout=25, **kwargs)
    except requests.RequestException as exc:
        raise HTTPException(status_code=502, detail=f"Could not contact GitHub: {exc}") from exc
    if response.status_code >= 500:
        raise HTTPException(status_code=502, detail=f"GitHub is temporarily unavailable (HTTP {response.status_code}).")
    return response


@app.get("/")
def root() -> dict[str, str]:
    return {"service": "Lineup Content Publisher", "health": "/health"}


@app.get("/health")
def health() -> dict[str, Any]:
    token = os.getenv("LINEUP_GITHUB_TOKEN", "").strip()
    admin = os.getenv("LINEUP_ADMIN_KEY", "").strip()
    return {
        "ok": True,
        "githubConfigured": bool(token) and not token.startswith("SET_"),
        "adminConfigured": bool(admin) and not admin.startswith("SET_"),
    }



def get_github_file(path: str) -> tuple[dict[str, Any] | None, str | None]:
    url = f"{GITHUB_API}/repos/{REPO}/contents/{path}"
    response = github_request("GET", url, params={"ref": BRANCH})
    if response.status_code == 404:
        return None, None
    if response.status_code != 200:
        raise HTTPException(status_code=502, detail=f"Could not read {path} (HTTP {response.status_code}).")
    try:
        info = response.json()
        raw = base64.b64decode(info["content"])
        return json.loads(raw.decode("utf-8")), info["sha"]
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Could not parse {path}: {exc}") from exc


def put_github_json(path: str, document: dict[str, Any], message: str, sha: str | None) -> None:
    url = f"{GITHUB_API}/repos/{REPO}/contents/{path}"
    payload: dict[str, Any] = {
        "message": message,
        "content": base64.b64encode((json.dumps(document, ensure_ascii=False, indent=2) + "\n").encode("utf-8")).decode("ascii"),
        "branch": BRANCH,
    }
    if sha:
        payload["sha"] = sha
    response = github_request("PUT", url, json=payload)
    if response.status_code not in (200, 201):
        try:
            detail = response.json().get("message", "GitHub rejected the update.")
        except ValueError:
            detail = "GitHub rejected the update."
        raise HTTPException(status_code=502, detail=detail)


DEFAULT_POLICY: dict[str, Any] = {
    "schemaVersion": 1,
    "minClientRevision": 0,
    "telegramUrl": "https://t.me/",
}


@app.get("/policy")
def read_policy() -> dict[str, Any]:
    document, _ = get_github_file("content/policy.json")
    policy = document if isinstance(document, dict) else dict(DEFAULT_POLICY)
    policy.setdefault("schemaVersion", 1)
    policy.setdefault("minClientRevision", 0)
    policy.setdefault("telegramUrl", "https://t.me/")
    return policy


@app.post("/policy/lock")
async def lock_previous_versions(
    request: Request,
    x_lineup_admin_key: str | None = Header(default=None),
) -> JSONResponse:
    check_admin(x_lineup_admin_key)
    catalog, catalog_sha = get_github_file("content/catalog.json")
    if not isinstance(catalog, dict) or not catalog_sha:
        raise HTTPException(status_code=409, detail="Catalog has not been initialized yet.")
    policy, policy_sha = get_github_file("content/policy.json")
    if not isinstance(policy, dict):
        policy = dict(DEFAULT_POLICY)

    revision = max(0, int(catalog.get("revision", 0)))
    minimum = max(0, int(policy.get("minClientRevision", 0)))
    if revision <= minimum:
        revision += 1
        catalog["revision"] = revision
        catalog["generatedAt"] = __import__("datetime").datetime.now(__import__("datetime").timezone.utc).replace(microsecond=0).isoformat()
        put_github_json("content/catalog.json", catalog, f"Bump Lineup revision to lock previous app versions (revision {revision})", catalog_sha)

    policy["schemaVersion"] = 1
    policy["minClientRevision"] = revision
    policy.setdefault("telegramUrl", "https://t.me/")
    put_github_json("content/policy.json", policy, f"Require Lineup client database revision {revision}", policy_sha)
    return JSONResponse({
        "ok": True,
        "minClientRevision": revision,
        "message": "Previous client revisions will show the update screen. A client APK for this revision is built by GitHub Actions.",
    })


@app.post("/policy/telegram")
async def set_telegram_update_link(
    request: Request,
    x_lineup_admin_key: str | None = Header(default=None),
) -> JSONResponse:
    check_admin(x_lineup_admin_key)
    try:
        body = await request.json()
    except Exception as exc:
        raise HTTPException(status_code=400, detail="Invalid JSON body.") from exc
    link = str(body.get("telegramUrl", "")).strip() if isinstance(body, dict) else ""
    parsed = urlparse(link)
    if len(link) > 500 or parsed.scheme != "https" or parsed.netloc.lower() not in {"t.me", "www.t.me", "telegram.me", "www.telegram.me"}:
        raise HTTPException(status_code=400, detail="Update link must be a Telegram HTTPS URL such as https://t.me/your_channel.")
    policy, sha = get_github_file("content/policy.json")
    if not isinstance(policy, dict):
        policy = dict(DEFAULT_POLICY)
    policy["schemaVersion"] = 1
    policy["minClientRevision"] = max(0, int(policy.get("minClientRevision", 0)))
    policy["telegramUrl"] = link
    put_github_json("content/policy.json", policy, "Update Lineup Telegram update link", sha)
    return JSONResponse({"ok": True, "telegramUrl": link, "message": "Telegram update link saved."})


@app.post("/publish")
async def publish(request: Request, x_lineup_admin_key: str | None = Header(default=None)) -> JSONResponse:
    check_admin(x_lineup_admin_key)
    data = await request.body()
    manifest = validate_pack(data)
    url = f"{GITHUB_API}/repos/{REPO}/contents/{PACK_PATH}"
    current = github_request("GET", url, params={"ref": BRANCH})
    if current.status_code not in (200, 404):
        raise HTTPException(status_code=502, detail=f"Could not inspect current content package (HTTP {current.status_code}).")
    payload: dict[str, Any] = {
        "message": f"Publish Lineup database package v{manifest.get('version', 1)}",
        "content": base64.b64encode(data).decode("ascii"),
        "branch": BRANCH,
    }
    if current.status_code == 200:
        try:
            payload["sha"] = current.json()["sha"]
        except (ValueError, KeyError) as exc:
            raise HTTPException(status_code=502, detail="GitHub returned unexpected package metadata.") from exc
    result = github_request("PUT", url, json=payload)
    if result.status_code not in (200, 201):
        detail = "GitHub rejected the package upload."
        try:
            detail = result.json().get("message", detail)
        except ValueError:
            pass
        raise HTTPException(status_code=502, detail=detail)
    commit = result.json().get("commit", {}).get("sha")
    return JSONResponse({
        "ok": True,
        "message": "Package committed. The content catalog workflow will publish a new mandatory revision.",
        "blocks": len(manifest["blocks"]),
        "commit": commit,
        "workflow": "Publish content catalog",
    })
