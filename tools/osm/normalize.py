"""Нормализация сырых OSM-элементов в схему приложения.

Чистые функции без сети — покрываются unit-тестами в CI.
"""

from __future__ import annotations

import re
from typing import Any, Iterable

SCHEMA_VERSION = 1

# Направление по компасу -> градусы.
COMPASS = {
    "N": 0.0, "NNE": 22.5, "NE": 45.0, "ENE": 67.5,
    "E": 90.0, "ESE": 112.5, "SE": 135.0, "SSE": 157.5,
    "S": 180.0, "SSW": 202.5, "SW": 225.0, "WSW": 247.5,
    "W": 270.0, "WNW": 292.5, "NW": 315.0, "NNW": 337.5,
}

MIN_SPEED_LIMIT = 5
MAX_SPEED_LIMIT = 200


def parse_max_speed(raw: str | None) -> int | None:
    """`60`, `60 km/h`, `50 mph` -> км/ч. Зональные значения (RU:urban) -> None."""
    if not raw:
        return None
    value = raw.strip().lower()
    match = re.match(r"^(\d+(?:\.\d+)?)\s*(km/h|kmh|kph|mph)?$", value)
    if not match:
        return None
    number = float(match.group(1))
    unit = match.group(2)
    if unit == "mph":
        number *= 1.60934
    speed = int(round(number))
    if speed < MIN_SPEED_LIMIT or speed > MAX_SPEED_LIMIT:
        return None
    return speed


def parse_direction(raw: str | None) -> float | None:
    """Числовой азимут или компасное направление. forward/backward -> None."""
    if not raw:
        return None
    value = raw.strip().upper()
    if value in COMPASS:
        return COMPASS[value]
    try:
        degrees = float(value)
    except ValueError:
        # forward/backward требуют геометрии линии, которой у нас нет.
        return None
    if degrees != degrees:  # NaN
        return None
    return degrees % 360.0


def camera_type(tags: dict[str, Any]) -> str:
    """Тип камеры. По умолчанию — контроль скорости, самый частый случай."""
    enforcement = (tags.get("enforcement") or "").strip().lower()
    if enforcement == "average_speed":
        return "AVERAGE_SPEED_START"
    if enforcement in ("traffic_signals", "red_light"):
        return "RED_LIGHT"
    if enforcement in ("maxspeed", "speed"):
        return "SPEED_CAMERA"

    raw_type = (tags.get("camera:type") or tags.get("surveillance:type") or "").lower()
    if "red_light" in raw_type or "traffic_signals" in raw_type:
        return "RED_LIGHT"

    if tags.get("highway") == "speed_camera":
        return "SPEED_CAMERA"
    return "UNKNOWN"


def is_valid_coordinate(lat: float | None, lon: float | None) -> bool:
    if lat is None or lon is None:
        return False
    if lat != lat or lon != lon:
        return False
    if not (-90.0 <= lat <= 90.0 and -180.0 <= lon <= 180.0):
        return False
    # Null Island — верный признак битых данных.
    return not (lat == 0.0 and lon == 0.0)


def element_to_camera(element: dict[str, Any]) -> dict[str, Any] | None:
    """Один элемент Overpass -> запись базы. None, если элемент непригоден."""
    osm_type = element.get("type")
    osm_id = element.get("id")
    if osm_type is None or osm_id is None:
        return None

    lat = element.get("lat")
    lon = element.get("lon")
    if lat is None or lon is None:
        center = element.get("center") or {}
        lat = center.get("lat")
        lon = center.get("lon")
    if not is_valid_coordinate(lat, lon):
        return None

    tags = element.get("tags") or {}
    kind = camera_type(tags)
    if kind == "UNKNOWN" and tags.get("type") != "enforcement":
        return None

    camera = {
        "id": f"osm:{osm_type}:{osm_id}",
        "lat": round(float(lat), 7),
        "lon": round(float(lon), 7),
        "type": kind,
        "osm_type": osm_type,
        "osm_id": int(osm_id),
    }

    speed_limit = parse_max_speed(tags.get("maxspeed"))
    if speed_limit is not None:
        camera["speed_limit"] = speed_limit

    direction = parse_direction(tags.get("direction") or tags.get("camera:direction"))
    if direction is not None:
        camera["direction"] = direction

    return camera


def normalize_elements(elements: Iterable[dict[str, Any]]) -> list[dict[str, Any]]:
    """Нормализация с удалением дубликатов по id."""
    seen: set[str] = set()
    cameras: list[dict[str, Any]] = []
    for element in elements:
        camera = element_to_camera(element)
        if camera is None:
            continue
        if camera["id"] in seen:
            continue
        seen.add(camera["id"])
        cameras.append(camera)
    cameras.sort(key=lambda c: c["id"])
    return cameras
