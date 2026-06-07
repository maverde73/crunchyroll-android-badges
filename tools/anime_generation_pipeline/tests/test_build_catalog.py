from pathlib import Path
from agpipeline.matching import IdMapper
from agpipeline.enrich import Enricher
from agpipeline.sources import RawAgTitle
from agpipeline.build_catalog import build_catalog

FIX = Path(__file__).parent / "fixtures" / "anime-list-sample.json"


class FakeHttp:
    def __call__(self, url, params=None):
        if "/tv/26209" in url:
            return {"overview": "Desc IT", "poster_path": "/p.jpg",
                    "backdrop_path": "/b.jpg", "genres": [{"name": "Commedia"}],
                    "vote_average": 7.8, "first_air_date": "1981-10-14"}
        return {}


def test_build_merges_match_and_enrichment():
    ag = [RawAgTitle("ts1", "Lamù", 1981, ["it-IT", "ja-JP"], ["it-IT"], "https://pv/lamu")]
    cr = [{"id": "GRLAMU", "title": "Lamu", "year": 1981}]
    mapper = IdMapper.from_file(FIX)
    enricher = Enricher(http_get=FakeHttp(), tmdb_key="k")

    catalog = build_catalog(
        ag_titles=ag, cr_catalog=cr, id_mapper=mapper, enricher=enricher,
        mal_lookup=lambda t: 1,  # Lamù -> mal_id 1 (maps to tmdb 26209)
        version=1, generated_at="2026-06-07T12:00:00Z",
    )

    assert len(catalog.titles) == 1
    t = catalog.titles[0]
    assert t.ag_id == "ts1"
    assert t.matched_crunchyroll_id == "GRLAMU"      # fuzzy "Lamù"~"Lamu" + year
    assert t.external_ids["tmdb_id"] == 26209
    assert t.description_it == "Desc IT"
    assert t.poster_tall.endswith("/p.jpg")
    assert t.audio_locales == ["it-IT", "ja-JP"]
    assert t.languages_assumed is False


def test_build_uses_language_fallback_when_offer_has_no_languages():
    ag = [RawAgTitle("ts9", "Sconosciuto", 2000, [], [], "https://pv/x")]
    catalog = build_catalog(
        ag_titles=ag, cr_catalog=[], id_mapper=IdMapper([]),
        enricher=Enricher(http_get=FakeHttp(), tmdb_key="k"),
        mal_lookup=lambda t: None, version=1, generated_at="2026-06-07T12:00:00Z",
    )
    t = catalog.titles[0]
    assert t.matched_crunchyroll_id is None
    assert t.audio_locales == ["it-IT"]
    assert t.subtitle_locales == ["it-IT"]
    assert t.languages_assumed is True
