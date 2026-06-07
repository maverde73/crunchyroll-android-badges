from __future__ import annotations

from dataclasses import dataclass, field
from typing import Callable

TMDB_IMAGE_BASE = "https://image.tmdb.org/t/p/original"
HttpGet = Callable[[str, dict], dict]


@dataclass
class EnrichedMeta:
    description_it: str = ""
    poster_tall: str = ""
    poster_wide: str = ""
    genres: list[str] = field(default_factory=list)
    rating: float | None = None
    year: int | None = None


class Enricher:
    def __init__(self, http_get: HttpGet, tmdb_key: str):
        self._http = http_get
        self._tmdb_key = tmdb_key

    def enrich(self, external_ids: dict[str, int]) -> EnrichedMeta:
        meta = EnrichedMeta()
        tmdb_id = external_ids.get("tmdb_id")
        if tmdb_id:
            self._apply_tmdb(meta, tmdb_id)
        # Jikan fills any gaps (genres/rating/year/poster) and is the fallback
        # when TMDB is missing — common for the classic back-catalog.
        mal_id = external_ids.get("mal_id")
        if mal_id and (not meta.description_it or not meta.poster_tall):
            self._apply_jikan(meta, mal_id)
        return meta

    def _apply_tmdb(self, meta: EnrichedMeta, tmdb_id: int) -> None:
        body = self._http(
            f"https://api.themoviedb.org/3/tv/{tmdb_id}",
            {"api_key": self._tmdb_key, "language": "it-IT"},
        )
        if body.get("overview"):
            meta.description_it = body["overview"]
        if body.get("poster_path"):
            meta.poster_tall = TMDB_IMAGE_BASE + body["poster_path"]
        if body.get("backdrop_path"):
            meta.poster_wide = TMDB_IMAGE_BASE + body["backdrop_path"]
        if body.get("genres"):
            meta.genres = [g["name"] for g in body["genres"]]
        if body.get("vote_average"):
            meta.rating = float(body["vote_average"])
        air = body.get("first_air_date") or ""
        if len(air) >= 4 and air[:4].isdigit():
            meta.year = int(air[:4])

    def _apply_jikan(self, meta: EnrichedMeta, mal_id: int) -> None:
        body = self._http(f"https://api.jikan.moe/v4/anime/{mal_id}", {}).get("data", {})
        if not body:
            return
        if not meta.description_it and body.get("synopsis"):
            meta.description_it = body["synopsis"]
        if not meta.poster_tall:
            img = (body.get("images") or {}).get("jpg", {})
            meta.poster_tall = img.get("large_image_url", "") or meta.poster_tall
        if not meta.genres and body.get("genres"):
            meta.genres = [g["name"] for g in body["genres"]]
        if meta.rating is None and body.get("score") is not None:
            meta.rating = float(body["score"])
        if meta.year is None and body.get("year"):
            meta.year = int(body["year"])
