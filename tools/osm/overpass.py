"""Вежливый клиент Overpass API.

Публичные инстансы Overpass бесплатны и часто перегружены, поэтому:
  * запросы идут по одному региону;
  * между запросами обязательная пауза;
  * при ошибке — retry с экспоненциальной задержкой;
  * инстансы перебираются по кругу.
"""

from __future__ import annotations

import json
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import Any

from regions import Region

# Несколько независимых инстансов: любой из них может быть перегружен
# или отказать облачным IP (например, серверам GitHub Actions).
ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.osm.ch/api/interpreter",
    "https://overpass.osm.jp/api/interpreter",
]

USER_AGENT = "RoadAlert-camera-db/1.0 (OpenStreetMap ODbL; https://github.com/lisuner5945-stack/road-alert)"

QUERY_TEMPLATE = """
[out:json][timeout:{timeout}];
(
  node["highway"="speed_camera"]({bbox});
  way["highway"="speed_camera"]({bbox});
  node["enforcement"~"^(maxspeed|average_speed)$"]({bbox});
  relation["type"="enforcement"]["enforcement"~"^(maxspeed|average_speed)$"]({bbox});
);
out center tags;
"""


class OverpassError(RuntimeError):
    pass


def build_query(region: Region, timeout: int = 180) -> str:
    return QUERY_TEMPLATE.format(bbox=region.bbox, timeout=timeout)


def fetch_region(
    region: Region,
    *,
    attempts: int = 6,
    base_delay: float = 15.0,
    timeout: int = 180,
    opener=urllib.request.urlopen,
    sleep=time.sleep,
) -> list[dict[str, Any]]:
    """Данные одного региона. Бросает OverpassError, если все попытки провалились."""
    query = build_query(region, timeout=timeout)
    last_error: Exception | None = None

    for attempt in range(attempts):
        endpoint = ENDPOINTS[attempt % len(ENDPOINTS)]
        request = urllib.request.Request(
            endpoint,
            data=urllib.parse.urlencode({"data": query}).encode("utf-8"),
            headers={"User-Agent": USER_AGENT},
        )
        try:
            with opener(request, timeout=timeout + 60) as response:
                payload = json.loads(response.read().decode("utf-8"))
            elements = payload.get("elements")
            if elements is None:
                raise OverpassError(f"{region.name}: в ответе нет elements")
            return elements
        except Exception as error:  # noqa: BLE001 - сеть падает по-разному
            last_error = error
            # Экспоненциальная задержка с потолком: 15, 30, 60, 120, 240 секунд.
            delay = min(base_delay * (2 ** attempt), 240.0)
            print(f"  ! {region.name}: попытка {attempt + 1} не удалась ({error}); ждём {delay:.0f} с")
            sleep(delay)

    raise OverpassError(f"{region.name}: данные не получены: {last_error}")
