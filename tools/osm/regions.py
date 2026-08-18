"""Разбиение территории РФ на прямоугольники для вежливых запросов к Overpass.

Один огромный запрос на всю страну публичные инстансы Overpass не выдерживают,
поэтому территория режется на куски, между которыми делается пауза.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Region:
    """Прямоугольник в порядке Overpass: (south, west, north, east)."""

    name: str
    south: float
    west: float
    north: float
    east: float

    @property
    def bbox(self) -> str:
        return f"{self.south},{self.west},{self.north},{self.east}"


# Покрытие РФ. Куски намеренно неравные: там, где камер много (европейская
# часть), прямоугольники мельче, за Уралом — крупнее.
RUSSIA_REGIONS: list[Region] = [
    Region("northwest", 55.0, 19.5, 70.0, 34.0),
    Region("murmansk", 65.0, 28.0, 70.5, 44.0),
    Region("central-west", 53.0, 30.0, 59.0, 38.0),
    Region("moscow", 54.2, 35.0, 57.2, 41.0),
    Region("central-east", 53.0, 38.0, 59.0, 47.0),
    Region("north", 59.0, 34.0, 68.0, 52.0),
    Region("volga-north", 53.0, 44.0, 59.0, 54.0),
    Region("volga-south", 47.0, 40.0, 53.0, 50.0),
    Region("south", 41.0, 36.0, 47.0, 46.0),
    Region("caucasus", 41.0, 43.0, 47.0, 50.0),
    Region("ural-south", 50.0, 50.0, 58.0, 62.0),
    Region("ural-north", 58.0, 52.0, 68.0, 68.0),
    Region("siberia-west", 49.0, 62.0, 58.0, 80.0),
    Region("siberia-west-north", 58.0, 62.0, 73.0, 85.0),
    Region("siberia-central", 49.0, 80.0, 58.0, 100.0),
    Region("siberia-central-north", 58.0, 85.0, 75.0, 110.0),
    Region("siberia-east", 49.0, 100.0, 60.0, 120.0),
    Region("yakutia", 55.0, 110.0, 73.0, 140.0),
    Region("far-east-south", 42.0, 120.0, 55.0, 140.0),
    Region("far-east", 42.0, 140.0, 62.0, 160.0),
    Region("kamchatka", 50.0, 155.0, 67.0, 180.0),
    Region("kaliningrad", 54.2, 19.0, 55.5, 23.0),
]
