from agpipeline.enrich import Enricher


class FakeHttp:
    def __init__(self, responses):
        self.responses = responses
        self.calls = []

    def __call__(self, url, params=None):
        self.calls.append((url, params or {}))
        for key, body in self.responses.items():
            if key in url:
                return body
        return {}


def test_tmdb_used_for_italian_description_and_images():
    http = FakeHttp({
        "/tv/26209": {
            "overview": "Descrizione italiana.",
            "poster_path": "/p.jpg",
            "backdrop_path": "/b.jpg",
            "genres": [{"name": "Commedia"}],
            "vote_average": 7.8,
            "first_air_date": "1981-10-14",
        },
    })
    enricher = Enricher(http_get=http, tmdb_key="k")
    meta = enricher.enrich(external_ids={"tmdb_id": 26209, "mal_id": 1})

    assert meta.description_it == "Descrizione italiana."
    assert meta.poster_tall.endswith("/p.jpg")
    assert meta.poster_wide.endswith("/b.jpg")
    assert meta.genres == ["Commedia"]
    assert meta.rating == 7.8
    assert meta.year == 1981
    # TMDB requested with Italian language
    assert any(c[1].get("language") == "it-IT" for c in http.calls)


def test_falls_back_to_jikan_when_no_tmdb_id():
    http = FakeHttp({
        "/anime/1": {"data": {
            "synopsis": "English synopsis.",
            "images": {"jpg": {"large_image_url": "https://mal/p.jpg"}},
            "genres": [{"name": "Action"}],
            "score": 8.1,
            "year": 1998,
        }},
    })
    enricher = Enricher(http_get=http, tmdb_key="k")
    meta = enricher.enrich(external_ids={"mal_id": 1})

    assert meta.poster_tall == "https://mal/p.jpg"
    assert meta.genres == ["Action"]
    assert meta.rating == 8.1
    assert meta.year == 1998
    # English synopsis is acceptable fallback when no Italian source
    assert meta.description_it == "English synopsis."
