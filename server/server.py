#!/usr/bin/env python3
import asyncio
import json
import os
import re
import socket
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

from google.protobuf.json_format import MessageToDict
from google.protobuf.descriptor import FieldDescriptor

from Astandy import StandClient
from Astandy.generated.protos import player_message_pb2, player_stats_message_pb2

HOST = os.getenv("GHOSTLY_SERVER_HOST", "0.0.0.0")
PORT = int(os.getenv("GHOSTLY_SERVER_PORT", "8081"))
VERSION = "0.2.0"
CACHE_TTL = int(os.getenv("GHOSTLY_STATS_CACHE_TTL", "30"))
STANDOFF2_HANDSHAKE = os.getenv("STANDOFF2_HANDSHAKE", "").strip()

_cache: dict[str, tuple[float, dict[str, Any]]] = {}
_cache_lock = threading.Lock()


class ApiError(Exception):
    pass


def _json_safe_message(message: Any) -> dict[str, Any]:
    return MessageToDict(
        message,
        preserving_proto_field_name=True,
        use_integers_for_enums=False,
    )


def _normal_key(value: str) -> str:
    return re.sub(r"[^a-z0-9]+", "", value.lower())


def _find_value(data: Any, candidates: tuple[str, ...]) -> Any:
    wanted = {_normal_key(x) for x in candidates}

    if isinstance(data, dict):
        for key, value in data.items():
            if _normal_key(key) in wanted and value not in (None, ""):
                return value
        for value in data.values():
            found = _find_value(value, candidates)
            if found is not None:
                return found

    elif isinstance(data, list):
        for value in data:
            found = _find_value(value, candidates)
            if found is not None:
                return found

    return None


def _set_player_identifier(message: Any, player_id: str) -> None:
    fields = message.DESCRIPTOR.fields

    candidates = (
        "player_id",
        "playerId",
        "id",
        "uid",
        "user_id",
        "userId",
        "avatar_id",
        "avatarId",
    )

    for candidate in candidates:
        field = message.DESCRIPTOR.fields_by_name.get(candidate)
        if field is None or field.label == FieldDescriptor.LABEL_REPEATED:
            continue
        if field.type == FieldDescriptor.TYPE_STRING:
            setattr(message, candidate, player_id)
            return
        if field.type in {
            FieldDescriptor.TYPE_INT32,
            FieldDescriptor.TYPE_INT64,
            FieldDescriptor.TYPE_UINT32,
            FieldDescriptor.TYPE_UINT64,
            FieldDescriptor.TYPE_SINT32,
            FieldDescriptor.TYPE_SINT64,
            FieldDescriptor.TYPE_FIXED32,
            FieldDescriptor.TYPE_FIXED64,
            FieldDescriptor.TYPE_SFIXED32,
            FieldDescriptor.TYPE_SFIXED64,
        }:
            setattr(message, candidate, int(player_id))
            return

    scalar_fields = [
        f for f in fields
        if f.label != FieldDescriptor.LABEL_REPEATED
        and f.type in {
            FieldDescriptor.TYPE_STRING,
            FieldDescriptor.TYPE_INT32,
            FieldDescriptor.TYPE_INT64,
            FieldDescriptor.TYPE_UINT32,
            FieldDescriptor.TYPE_UINT64,
            FieldDescriptor.TYPE_SINT32,
            FieldDescriptor.TYPE_SINT64,
            FieldDescriptor.TYPE_FIXED32,
            FieldDescriptor.TYPE_FIXED64,
            FieldDescriptor.TYPE_SFIXED32,
            FieldDescriptor.TYPE_SFIXED64,
        }
    ]

    if len(scalar_fields) == 1:
        field = scalar_fields[0]
        if field.type == FieldDescriptor.TYPE_STRING:
            setattr(message, field.name, player_id)
        else:
            setattr(message, field.name, int(player_id))
        return

    raise ApiError("Не удалось определить поле ID в запросе Standoff 2")


async def _load_player(player_id: str) -> tuple[dict[str, Any], dict[str, Any]]:
    if not STANDOFF2_HANDSHAKE:
        raise ApiError(
            "STANDOFF2_HANDSHAKE не настроен. Нужен отдельный handshake тестового аккаунта."
        )

    client = StandClient(STANDOFF2_HANDSHAKE)
    await client.start()

    try:
        profile_request = player_message_pb2.GetPlayerByIdRequest()
        _set_player_identifier(profile_request, player_id)

        profile_response = await client.raw.PlayerRemoteService.getPlayerById3(
            client,
            profile_request,
        )
        profile = _json_safe_message(profile_response)

        stats_request = player_stats_message_pb2.GetPlayerStatsRequest()
        _set_player_identifier(stats_request, player_id)

        try:
            stats_response = await client.raw.PlayerStatsRemoteService.getPlayerStats2(
                client,
                stats_request,
            )
            stats = _json_safe_message(stats_response)
        except Exception as exc:
            stats = {
                "unavailable": True,
                "error": str(exc),
            }

        return profile, stats
    finally:
        await client.stop()


def _normalize_player(player_id: str, profile: dict[str, Any], stats: dict[str, Any]) -> dict[str, Any]:
    matches = _find_value(stats, (
        "matches", "match_count", "games", "game_count", "total_matches",
        "played_matches", "matches_played",
    ))
    wins = _find_value(stats, (
        "wins", "win_count", "victories", "victory_count",
    ))
    kills = _find_value(stats, ("kills", "kill_count", "total_kills"))
    deaths = _find_value(stats, ("deaths", "death_count", "total_deaths"))
    assists = _find_value(stats, ("assists", "assist_count", "total_assists"))
    accuracy = _find_value(stats, ("accuracy", "accuracy_percent", "accuracyPercentage"))
    rating = _find_value(stats, ("rating", "mmr", "rank_rating", "rankRating", "elo"))
    level = _find_value(profile, ("level", "player_level", "playerLevel"))
    nickname = _find_value(profile, ("name", "nickname", "username", "player_name", "playerName"))

    kd = None
    if isinstance(kills, (int, float)) and isinstance(deaths, (int, float)) and deaths:
        kd = round(kills / deaths, 2)

    win_rate = None
    if isinstance(wins, (int, float)) and isinstance(matches, (int, float)) and matches:
        win_rate = round(wins / matches * 100, 1)

    return {
        "id": player_id,
        "nickname": nickname,
        "level": level,
        "rating": rating,
        "matches": matches,
        "wins": wins,
        "win_rate": win_rate,
        "kills": kills,
        "deaths": deaths,
        "assists": assists,
        "kd": kd,
        "accuracy": accuracy,
        "profile_raw": profile,
        "stats_raw": stats,
    }


def get_player(player_id: str) -> dict[str, Any]:
    player_id = player_id.strip()
    if not player_id.isdigit():
        raise ApiError("ID игрока должен содержать только цифры")
    if len(player_id) < 3 or len(player_id) > 20:
        raise ApiError("Некорректная длина ID игрока")

    now = time.time()
    with _cache_lock:
        cached = _cache.get(player_id)
        if cached and now - cached[0] < CACHE_TTL:
            result = dict(cached[1])
            result["cached"] = True
            return result

    profile, stats = asyncio.run(_load_player(player_id))
    result = _normalize_player(player_id, profile, stats)
    result["cached"] = False

    with _cache_lock:
        _cache[player_id] = (now, result)

    return result


class Handler(BaseHTTPRequestHandler):
    def _json(self, status: int, payload: dict[str, Any]) -> None:
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        if self.path == "/api/health":
            self._json(200, {
                "ok": True,
                "service": "ghostly-api",
                "version": VERSION,
                "server_time": datetime.now(timezone.utc).isoformat(),
                "hostname": socket.gethostname(),
                "standoff2_api": bool(STANDOFF2_HANDSHAKE),
            })
            return

        prefix = "/api/player/"
        if self.path.startswith(prefix):
            player_id = self.path[len(prefix):].split("?", 1)[0]
            try:
                result = get_player(player_id)
                self._json(200, {
                    "ok": True,
                    "source": "standoff2-rpc",
                    "data": result,
                })
            except ApiError as exc:
                self._json(400, {"ok": False, "error": str(exc)})
            except Exception as exc:
                print(f"[Ghostly API] Standoff 2 error: {exc}")
                self._json(502, {
                    "ok": False,
                    "error": "Не удалось получить данные из API Standoff 2",
                })
            return

        if self.path == "/":
            self._json(200, {
                "ok": True,
                "service": "ghostly-api",
                "message": "Ghostly Standoff 2 API is running",
            })
            return

        self._json(404, {"ok": False, "error": "not_found"})

    def log_message(self, fmt: str, *args: Any) -> None:
        print("[Ghostly API] " + (fmt % args))


if __name__ == "__main__":
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Ghostly API listening on http://{HOST}:{PORT}")
    print("Standoff 2 API: " + ("configured" if STANDOFF2_HANDSHAKE else "NOT configured"))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("Ghostly API stopped.")
    finally:
        server.server_close()
