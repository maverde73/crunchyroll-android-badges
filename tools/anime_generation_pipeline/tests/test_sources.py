from agpipeline.sources import RawAgTitle, parse_justwatch_offers, parse_crunchyroll_browse


def test_parse_justwatch_offers_filters_to_anime_generation_provider():
    # Shape mirrors simple-justwatch-python-api MediaEntry/Offer objects as dicts.
    entries = [
        {
            "entry_id": "ts1", "title": "Lamù", "release_year": 1981,
            "offers": [
                {"package": {"technical_name": "anime_generation_amazon_channel"},
                 "audio_languages": ["it", "ja"], "subtitle_languages": ["it"],
                 "url": "https://www.primevideo.com/detail/lamu"},
                {"package": {"technical_name": "netflix"},
                 "audio_languages": ["ja"], "subtitle_languages": ["en"], "url": "x"},
            ],
        },
        {
            "entry_id": "ts2", "title": "Solo Su Altrove", "release_year": 2020,
            "offers": [{"package": {"technical_name": "netflix"}, "audio_languages": [], "subtitle_languages": [], "url": "y"}],
        },
    ]
    titles = parse_justwatch_offers(entries, provider="anime_generation_amazon_channel")

    assert len(titles) == 1
    t = titles[0]
    assert t == RawAgTitle(
        ag_id="ts1", title="Lamù", year=1981,
        audio_locales=["it-IT", "ja-JP"], subtitle_locales=["it-IT"],
        deep_link_url="https://www.primevideo.com/detail/lamu",
    )


def test_parse_crunchyroll_browse_extracts_id_title_year():
    body = {"data": [
        {"id": "GR1", "title": "Naruto",
         "series_metadata": {"series_launch_year": 2002}},
        {"id": "GM1", "title": "A Movie", "series_metadata": None},
    ]}
    out = parse_crunchyroll_browse([body])
    assert {"id": "GR1", "title": "Naruto", "year": 2002} in out
    # movie without metadata still included with year=None
    assert {"id": "GM1", "title": "A Movie", "year": None} in out
