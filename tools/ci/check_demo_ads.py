"""Проверка, что demo-блоки Яндекса не попадают в release-конфигурацию (ТЗ §26, §49).

Запуск:
    python tools/ci/check_demo_ads.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

DEMO_PATTERN = re.compile(r"demo-[a-z]+-yandex")

# Здесь demo-идентификаторы допустимы: это их определение и тесты.
ALLOWED_FILES = {
    "app/src/main/java/ru/example/roadalert/ads/AdUnits.kt",
    "tools/ci/check_demo_ads.py",
}

ALLOWED_DIR_PARTS = ("src/test/", "src/debug/", "src/androidTest/", "release/", "legal/", "tools/osm/")


def release_block(gradle_text: str) -> str:
    """Содержимое блока release { ... } из app/build.gradle.kts."""
    start = gradle_text.find("release {")
    if start == -1:
        return ""
    depth = 0
    for index in range(start, len(gradle_text)):
        char = gradle_text[index]
        if char == "{":
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0:
                return gradle_text[start:index + 1]
    return gradle_text[start:]


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    problems: list[str] = []

    gradle_file = root / "app" / "build.gradle.kts"
    if gradle_file.exists():
        text = gradle_file.read_text(encoding="utf-8")
        if DEMO_PATTERN.search(release_block(text)):
            problems.append("app/build.gradle.kts: demo-блок внутри release { }")

    patterns = ("**/*.kt", "**/*.xml", "**/*.md")
    for pattern in patterns:
        for path in root.glob(pattern):
            if not path.is_file():
                continue
            relative = path.relative_to(root).as_posix()
            if relative in ALLOWED_FILES:
                continue
            if any(part in relative for part in ALLOWED_DIR_PARTS):
                continue
            if "/build/" in relative or relative.startswith("build/"):
                continue
            try:
                content = path.read_text(encoding="utf-8")
            except (UnicodeDecodeError, OSError):
                continue
            if DEMO_PATTERN.search(content):
                problems.append(f"{relative}: demo-блок вне разрешённых мест")

    if problems:
        print("Найдены demo рекламные блоки вне debug-конфигурации:", file=sys.stderr)
        for problem in problems:
            print(f"  ✗ {problem}", file=sys.stderr)
        return 1

    print("OK: demo-блоки используются только в debug-конфигурации и тестах")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
