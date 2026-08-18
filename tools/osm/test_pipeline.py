"""Тесты pipeline. Запускаются в CI до публикации базы."""

from __future__ import annotations

import gzip
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))

import build_database
from normalize import camera_type, element_to_camera, normalize_elements, parse_direction, parse_max_speed
from overpass import build_query
from regions import RUSSIA_REGIONS
from validate import ValidationError, validate_against_previous, validate_cameras


class MaxSpeedTest(unittest.TestCase):
    def test_plain_number(self):
        self.assertEqual(60, parse_max_speed("60"))

    def test_kmh_suffix(self):
        self.assertEqual(90, parse_max_speed("90 km/h"))

    def test_mph_converted(self):
        self.assertEqual(80, parse_max_speed("50 mph"))

    def test_zone_value_ignored(self):
        self.assertIsNone(parse_max_speed("RU:urban"))

    def test_nonsense_ignored(self):
        self.assertIsNone(parse_max_speed("signals"))
        self.assertIsNone(parse_max_speed(None))
        self.assertIsNone(parse_max_speed("999"))


class DirectionTest(unittest.TestCase):
    def test_numeric(self):
        self.assertEqual(180.0, parse_direction("180"))

    def test_compass(self):
        self.assertEqual(45.0, parse_direction("NE"))

    def test_normalized(self):
        self.assertEqual(10.0, parse_direction("370"))

    def test_forward_not_supported(self):
        self.assertIsNone(parse_direction("forward"))


class CameraTypeTest(unittest.TestCase):
    def test_speed_camera(self):
        self.assertEqual("SPEED_CAMERA", camera_type({"highway": "speed_camera"}))

    def test_average_speed(self):
        self.assertEqual("AVERAGE_SPEED_START", camera_type({"enforcement": "average_speed"}))

    def test_red_light(self):
        self.assertEqual("RED_LIGHT", camera_type({"enforcement": "traffic_signals"}))

    def test_unknown(self):
        self.assertEqual("UNKNOWN", camera_type({"amenity": "cafe"}))


class NormalizeTest(unittest.TestCase):
    def setUp(self):
        raw = Path(__file__).with_name("sample_elements.json").read_text(encoding="utf-8")
        self.elements = json.loads(raw)

    def test_sample_normalization(self):
        cameras = normalize_elements(self.elements)
        ids = {c["id"] for c in cameras}
        self.assertIn("osm:node:1001", ids)
        self.assertIn("osm:way:2001", ids)
        self.assertIn("osm:relation:3001", ids)
        # Null Island и кафе не должны попасть в базу.
        self.assertNotIn("osm:node:4001", ids)
        self.assertNotIn("osm:node:5001", ids)

    def test_duplicates_removed(self):
        cameras = normalize_elements(self.elements + self.elements)
        self.assertEqual(len(cameras), len(normalize_elements(self.elements)))

    def test_zone_speed_limit_dropped(self):
        camera = next(c for c in normalize_elements(self.elements) if c["id"] == "osm:way:2001")
        self.assertNotIn("speed_limit", camera)

    def test_element_without_coordinates(self):
        self.assertIsNone(element_to_camera({"type": "node", "id": 1, "tags": {"highway": "speed_camera"}}))


class ValidateTest(unittest.TestCase):
    def valid(self):
        return [{"id": "osm:node:1", "lat": 55.0, "lon": 37.0, "type": "SPEED_CAMERA"}]

    def test_valid_passes(self):
        validate_cameras(self.valid())

    def test_empty_rejected(self):
        with self.assertRaises(ValidationError):
            validate_cameras([])

    def test_bad_coordinates_rejected(self):
        with self.assertRaises(ValidationError):
            validate_cameras([{"id": "x", "lat": 91.0, "lon": 37.0}])

    def test_duplicate_id_rejected(self):
        with self.assertRaises(ValidationError):
            validate_cameras(self.valid() + self.valid())

    def test_shrink_rejected(self):
        with self.assertRaises(ValidationError):
            validate_against_previous(new_count=100, previous_count=1000)

    def test_shrink_allowed_with_override(self):
        validate_against_previous(new_count=100, previous_count=1000, allow_shrink=True)

    def test_small_shrink_allowed(self):
        validate_against_previous(new_count=950, previous_count=1000)


class RegionFailureTest(unittest.TestCase):
    """Единичный отказ региона не должен ронять всю сборку, но и молчать нельзя."""

    def setUp(self):
        self.original_fetch = build_database.fetch_region
        self.original_sleep = build_database.time.sleep
        build_database.time.sleep = lambda _seconds: None

    def tearDown(self):
        build_database.fetch_region = self.original_fetch
        build_database.time.sleep = self.original_sleep

    def test_single_failure_tolerated(self):
        calls = {"n": 0}

        def fetch(region):
            calls["n"] += 1
            if calls["n"] == 1:
                raise RuntimeError("Overpass недоступен")
            return [
                {
                    "type": "node",
                    "id": calls["n"],
                    "lat": 55.0 + calls["n"] / 1000,
                    "lon": 37.0,
                    "tags": {"highway": "speed_camera"},
                }
            ]

        build_database.fetch_region = fetch
        cameras, failed = build_database.collect_cameras(sample=False, max_failed_regions=1)
        self.assertEqual(1, len(failed))
        self.assertGreater(len(cameras), 0)

    def test_too_many_failures_cancel_publication(self):
        def always_fail(region):
            raise RuntimeError("Overpass недоступен")

        build_database.fetch_region = always_fail
        with self.assertRaises(ValidationError):
            build_database.collect_cameras(sample=False, max_failed_regions=1)


class QueryTest(unittest.TestCase):
    def test_query_limited_to_region(self):
        region = RUSSIA_REGIONS[0]
        query = build_query(region)
        self.assertIn(region.bbox, query)
        self.assertIn("speed_camera", query)

    def test_all_regions_inside_reasonable_bounds(self):
        for region in RUSSIA_REGIONS:
            self.assertLess(region.south, region.north)
            self.assertLess(region.west, region.east)
            self.assertGreaterEqual(region.south, 40.0)
            self.assertLessEqual(region.north, 82.0)


class BuildOutputTest(unittest.TestCase):
    def test_sample_build_produces_valid_files(self):
        with tempfile.TemporaryDirectory() as directory:
            exit_code = build_database.main(["--output", directory, "--sample"])
            self.assertEqual(0, exit_code)

            output = Path(directory)
            metadata = json.loads((output / "metadata.json").read_text(encoding="utf-8"))
            payload = json.loads(
                gzip.decompress((output / "camera_database.json.gz").read_bytes()).decode("utf-8")
            )

            self.assertEqual(metadata["camera_count"], len(payload["cameras"]))
            self.assertGreater(metadata["camera_count"], 0)
            self.assertEqual("ODbL", metadata["license"])
            self.assertEqual(1, payload["schema_version"])
            self.assertTrue((output / "SHA256SUMS").exists())

    def test_build_refuses_empty_database(self):
        with tempfile.TemporaryDirectory() as directory:
            original = build_database.collect_cameras
            build_database.collect_cameras = lambda sample, max_failed_regions=0: ([], [])
            try:
                self.assertEqual(2, build_database.main(["--output", directory, "--sample"]))
            finally:
                build_database.collect_cameras = original

    def test_metadata_lists_failed_regions(self):
        with tempfile.TemporaryDirectory() as directory:
            original = build_database.collect_cameras
            stub = [{"id": "osm:node:1", "lat": 55.0, "lon": 37.0, "type": "SPEED_CAMERA"}]
            build_database.collect_cameras = (
                lambda sample, max_failed_regions=0: (stub, ["kamchatka"])
            )
            try:
                self.assertEqual(0, build_database.main(["--output", directory, "--sample"]))
                metadata = json.loads((Path(directory) / "metadata.json").read_text(encoding="utf-8"))
                self.assertEqual(["kamchatka"], metadata["failed_regions"])
            finally:
                build_database.collect_cameras = original


if __name__ == "__main__":
    unittest.main()
