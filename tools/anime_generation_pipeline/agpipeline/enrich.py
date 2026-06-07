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
    maturity_rating: str = ""


class Enricher:
    def __init__(self, http_get: HttpGet, tmdb_key: str):
        self._http = http_get
        self._tmdb_key = tmdb_key

    def enrich(self, external_ids: dict) -> EnrichedMeta:
        meta = EnrichedMeta()
        tmdb_id = external_ids.get("tmdb_id")
        if tmdb_id:
            media_type = external_ids.get("tmdb_type", "tv")
            self._apply_tmdb(meta, int(tmdb_id), media_type)
        # Jikan fills any gaps when a MAL id is available (rarely, with the
        # TMDB-search path) and TMDB missed description/poster.
        mal_id = external_ids.get("mal_id")
        if mal_id and (not meta.description_it or not meta.poster_tall):
            self._apply_jikan(meta, int(mal_id))
        return meta

    def _apply_tmdb(self, meta: EnrichedMeta, tmdb_id: int, media_type: str) -> None:
        body = self._http(
            f"https://api.themoviedb.org/3/{media_type}/{tmdb_id}",
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
        date = body.get("first_air_date") or body.get("release_date") or ""
        if len(date) >= 4 and date[:4].isdigit():
            meta.year = int(date[:4])
        meta.maturity_rating = self._fetch_certification(tmdb_id, media_type)

    def _fetch_certification(self, tmdb_id: int, media_type: str) -> str:
        """Italian age certification, or empty string if none."""
        if media_type == "movie":
            body = self._http(
                f"https://api.themoviedb.org/3/movie/{tmdb_id}/release_dates",
                {"api_key": self._tmdb_key},
            )
            for r in body.get("results", []):
                if r.get("iso_3166_1") == "IT":
                    for rd in r.get("release_dates", []):
                        cert = (rd.get("certification") or "").strip()
                        if cert:
                            return cert
        else:
            body = self._http(
                f"https://api.themoviedb.org/3/tv/{tmdb_id}/content_ratings",
                {"api_key": self._tmdb_key},
            )
            for r in body.get("results", []):
                if r.get("iso_3166_1") == "IT":
                    cert = (r.get("rating") or "").strip()
                    if cert:
                        return cert
        return ""

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
