import pytest
from agpipeline.models import Catalog, CatalogTitle
from agpipeline.validate import validate_catalog, ValidationError


def _title(ag_id: str) -> CatalogTitle:
    return CatalogTitle(
        ag_id=ag_id, title="T", year=2000, matched_crunchyroll_id=None,
        external_ids={}, description_it="d", poster_tall="u", poster_wide="u",
        genres=[], rating=None, audio_locales=["it-IT"], subtitle_locales=["it-IT"],
        languages_assumed=True, deep_link_url="https://x",
    )


def test_valid_catalog_passes():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title(str(i)) for i in range(50)])
    validate_catalog(cat, min_titles=10)  # no raise


def test_below_threshold_raises():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title("1")])
    with pytest.raises(ValidationError, match="below threshold"):
        validate_catalog(cat, min_titles=10)


def test_duplicate_ag_id_raises():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title("dup"), _title("dup")])
    with pytest.raises(ValidationError, match="duplicate ag_id"):
        validate_catalog(cat, min_titles=1)


def test_missing_title_field_raises():
    bad = _title("1")
    object.__setattr__(bad, "title", "")
    cat = Catalog(1, "2026-06-07T12:00:00Z", [bad])
    with pytest.raises(ValidationError, match="empty title"):
        validate_catalog(cat, min_titles=1)
