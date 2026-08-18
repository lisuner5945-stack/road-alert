"""Сборка camera_database.json.gz + metadata.json из данных OpenStreetMap.

Запуск:
    python build_database.py --output ../../database
    python build_database.py --output out --sample   # без сети, для тестов

Данные: (c) OpenStreetMap contributors, лицензия ODbL.
"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from normalize import SCHEMA_VERSION, normalize_elements
from overpass import fetch_region
from regions import RUSSIA_REGIONS
from validate import ValidationError, validate_against_previous, validate_cameras

DATABASE_FILE = "camera_database.json.gz"
METADATA_FILE = "metadata.json"
CHECKSUM_FILE = "SHA256SUMS"

PAUSE_BETWEEN_REGIONS_SECONDS = 15.0


def utc_now_iso() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_previous_metadata(output_dir: Path) -> dict[str, Any] | None:
    metadata_path = output_dir / METADATA_FILE
    if not metadata_path.exists():
        return None
    try:
        return json.loads(metadata_path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        print("! Предыдущий metadata.json повреждён — сравнение пропущено")
        return None


def collect_cameras(sample: bool) -> list[dict[str, Any]]:
    if sample:
        sample_path = Path(__file__).with_name("sample_elements.json")
        elements = json.loads(sample_path.read_text(encoding="utf-8"))
        return normalize_elements(elements)

    all_elements: list[dict[str, Any]] = []
    for index, region in enumerate(RUSSIA_REGIONS, start=1):
        print(f"[{index}/{len(RUSSIA_REGIONS)}] {region.name} ...")
        elements = fetch_region(region)
        print(f"    получено элементов: {len(elements)}")
        all_elements.extend(elements)
        if index < len(RUSSIA_REGIONS):
            time.sleep(PAUSE_BETWEEN_REGIONS_SECONDS)
    return normalize_elements(all_elements)


def build_payload(cameras: list[dict[str, Any]], generated_at: str) -> dict[str, Any]:
    return {
        "schema_version": SCHEMA_VERSION,
        "database_version": generated_at,
        "generated_at": generated_at,
        "source": "OpenStreetMap",
        "license": "ODbL",
        "cameras": cameras,
    }


def write_outputs(
    output_dir: Path,
    cameras: list[dict[str, Any]],
    download_url: str | None,
) -> dict[str, Any]:
    output_dir.mkdir(parents=True, exist_ok=True)
    generated_at = utc_now_iso()

    payload = build_payload(cameras, generated_at)
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")

    # Пишем во временный файл: пока проверки не пройдены, старая база не трогается.
    temporary = output_dir / (DATABASE_FILE + ".tmp")
    with temporary.open("wb") as handle:
        # mtime=0 -> байт-в-байт одинаковый архив при неизменных данных.
        with gzip.GzipFile(filename="", mode="wb", fileobj=handle, mtime=0) as gz:
            gz.write(raw)

    reparsed = json.loads(gzip.decompress(temporary.read_bytes()).decode("utf-8"))
    if len(reparsed["cameras"]) != len(cameras):
        temporary.unlink(missing_ok=True)
        raise ValidationError("Проверка перечитыванием архива не прошла")

    database_path = output_dir / DATABASE_FILE
    temporary.replace(database_path)

    # В metadata кладём хэш ИМЕННО того файла, который скачивает приложение.
    checksum = sha256_of(database_path)

    metadata = {
        "schema_version": SCHEMA_VERSION,
        "database_version": generated_at,
        "generated_at": generated_at,
        "source": "OpenStreetMap",
        "license": "ODbL",
        "camera_count": len(cameras),
        "sha256": checksum,
        "download_url": download_url or "",
    }
    (output_dir / METADATA_FILE).write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    (output_dir / CHECKSUM_FILE).write_text(
        f"{sha256_of(database_path)}  {DATABASE_FILE}\n",
        encoding="utf-8",
    )
    return metadata


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Сборка базы камер из OpenStreetMap")
    parser.add_argument("--output", default="database", help="каталог для публикации")
    parser.add_argument("--sample", action="store_true", help="локальные данные вместо Overpass")
    parser.add_argument(
        "--allow-shrink",
        action="store_true",
        help="разрешить резкое уменьшение числа камер (ручное подтверждение)",
    )
    parser.add_argument("--download-url", default=os.environ.get("CAMERA_DB_URL", ""))
    args = parser.parse_args(argv)

    output_dir = Path(args.output)
    previous = read_previous_metadata(output_dir)

    try:
        cameras = collect_cameras(sample=args.sample)
        validate_cameras(cameras)
        validate_against_previous(
            len(cameras),
            (previous or {}).get("camera_count"),
            allow_shrink=args.allow_shrink,
        )
        metadata = write_outputs(output_dir, cameras, args.download_url or None)
    except ValidationError as error:
        print(f"ОТМЕНА ПУБЛИКАЦИИ: {error}", file=sys.stderr)
        return 2
    except Exception as error:  # noqa: BLE001
        print(f"ОШИБКА: {error}", file=sys.stderr)
        return 1

    print(
        f"Готово: {metadata['camera_count']} камер, версия {metadata['database_version']}, "
        f"sha256 {metadata['sha256'][:16]}..."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
