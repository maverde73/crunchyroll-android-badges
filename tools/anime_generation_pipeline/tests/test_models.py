from agpipeline.models import CatalogTitle, Catalog


def test_catalog_round_trips_to_contract_json():
    title = CatalogTitle(
        ag_id="ag-1",
        title="Lamù",
        year=1981,
        matched_crunchyroll_id=None,
        external_ids={"mal_id": 1, "anilist_id": 290, "tmdb_id": 26209},
        description_it="Una commedia romantica aliena.",
        poster_tall="https://img/tall.jpg",
        poster_wide="https://img/wide.jpg",
        genres=["Commedia", "Soprannaturale"],
        rating=7.8,
        audio_locales=["it-IT", "ja-JP"],
        subtitle_locales=["it-IT"],
        languages_assumed=False,
        deep_link_url="https://www.primevideo.com/detail/lamu",
    )
    catalog = Catalog(version=1, generated_at="2026-06-07T12:00:00Z", titles=[title])

    payload = catalog.to_dict()

    assert payload["version"] == 1
    assert payload["generated_at"] == "2026-06-07T12:00:00Z"
    assert payload["titles"][0]["ag_id"] == "ag-1"
    assert payload["titles"][0]["external_ids"]["tmdb_id"] == 26209
    assert payload["titles"][0]["languages_assumed"] is False
    # round-trip
    assert Catalog.from_dict(payload).titles[0] == title
