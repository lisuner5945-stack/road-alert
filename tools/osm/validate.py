"""Проверки, без которых новая база не имеет права уехать пользователям."""

from __future__ import annotations

from typing import Any

from normalize import is_valid_coordinate

# Резкое падение количества камер почти всегда означает сбой источника,
# а не то, что камеры реально исчезли.
MAX_SHRINK_RATIO = 0.40


class ValidationError(RuntimeError):
    pass


def validate_cameras(cameras: list[dict[str, Any]]) -> None:
    if not cameras:
        raise ValidationError("В базе ноль камер — публикация отменена")

    ids: set[str] = set()
    for camera in cameras:
        if not is_valid_coordinate(camera.get("lat"), camera.get("lon")):
            raise ValidationError(f"Невалидные координаты: {camera}")
        camera_id = camera.get("id")
        if not camera_id:
            raise ValidationError(f"Камера без id: {camera}")
        if camera_id in ids:
            raise ValidationError(f"Дубликат id: {camera_id}")
        ids.add(camera_id)

        speed_limit = camera.get("speed_limit")
        if speed_limit is not None and not (5 <= speed_limit <= 200):
            raise ValidationError(f"Неправдоподобное ограничение: {camera}")

        direction = camera.get("direction")
        if direction is not None and not (0.0 <= direction < 360.0):
            raise ValidationError(f"Направление вне диапазона: {camera}")


def validate_against_previous(
    new_count: int,
    previous_count: int | None,
    *,
    allow_shrink: bool = False,
) -> None:
    """Не даём подменить рабочую базу подозрительно маленькой (ТЗ §10)."""
    if previous_count is None or previous_count == 0:
        return
    if allow_shrink:
        return
    shrink = (previous_count - new_count) / previous_count
    if shrink > MAX_SHRINK_RATIO:
        raise ValidationError(
            f"Количество камер упало с {previous_count} до {new_count} "
            f"({shrink:.0%}). Публикация отменена; для ручного подтверждения "
            f"используйте allow_shrink."
        )
