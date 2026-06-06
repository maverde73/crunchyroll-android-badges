from __future__ import annotations

from agpipeline.models import Catalog


class ValidationError(Exception):
    pass


def validate_catalog(catalog: Catalog, min_titles: int) -> None:
    """Raise ValidationError if the catalog is malformed or too small to publish."""
    if len(catalog.titles) < min_titles:
        raise ValidationError(
            f"title count {len(catalog.titles)} below threshold {min_titles}"
        )

    seen: set[str] = set()
    for t in catalog.titles:
        if not t.ag_id:
            raise ValidationError("empty ag_id")
        if t.ag_id in seen:
            raise ValidationError(f"duplicate ag_id: {t.ag_id}")
        seen.add(t.ag_id)
        if not t.title:
            raise ValidationError(f"empty title for ag_id {t.ag_id}")
        if not t.audio_locales:
            raise ValidationError(f"empty audio_locales for ag_id {t.ag_id}")
