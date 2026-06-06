from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path

from rapidfuzz import fuzz


def normalize_title(title: str) -> str:
    """Lowercase, strip accents and punctuation, collapse whitespace."""
    decomposed = unicodedata.normalize("NFKD", title)
    no_accents = "".join(c for c in decomposed if not unicodedata.combining(c))
    cleaned = re.sub(r"[^a-z0-9 ]+", " ", no_accents.lower())
    return re.sub(r"\s+", " ", cleaned).strip()


def best_crunchyroll_match(
    ag_title: str,
    ag_year: int | None,
    cr_catalog: list[dict],
    min_score: int = 90,
    max_year_delta: int = 1,
) -> str | None:
    """Return the Crunchyroll id of the best fuzzy match, or None.

    Conservative: requires both a high token-sort ratio AND a year within
    max_year_delta (when both years are known) to avoid false positives.
    """
    target = normalize_title(ag_title)
    best_id: str | None = None
    best_score = 0.0
    for entry in cr_catalog:
        score = fuzz.token_sort_ratio(target, normalize_title(entry["title"]))
        if score < min_score or score <= best_score:
            continue
        cr_year = entry.get("year")
        if ag_year is not None and cr_year is not None and abs(cr_year - ag_year) > max_year_delta:
            continue
        best_score = score
        best_id = entry["id"]
    return best_id


class IdMapper:
    """Cross-database ID join backed by Fribb/anime-lists 'anime-list-full.json'."""

    def __init__(self, records: list[dict]):
        self._by_mal: dict[int, dict] = {}
        self._by_anilist: dict[int, dict] = {}
        for r in records:
            if r.get("mal_id") is not None:
                self._by_mal[int(r["mal_id"])] = r
            if r.get("anilist_id") is not None:
                self._by_anilist[int(r["anilist_id"])] = r

    @staticmethod
    def from_file(path: Path) -> "IdMapper":
        return IdMapper(json.loads(Path(path).read_text(encoding="utf-8")))

    def external_ids(self, mal_id: int | None = None, anilist_id: int | None = None) -> dict[str, int]:
        record = None
        if mal_id is not None:
            record = self._by_mal.get(int(mal_id))
        if record is None and anilist_id is not None:
            record = self._by_anilist.get(int(anilist_id))
        if record is None:
            return {}
        out: dict[str, int] = {}
        if record.get("mal_id") is not None:
            out["mal_id"] = int(record["mal_id"])
        if record.get("anilist_id") is not None:
            out["anilist_id"] = int(record["anilist_id"])
        tmdb = record.get("themoviedb_id")
        if isinstance(tmdb, dict) and "tv" in tmdb:
            out["tmdb_id"] = int(tmdb["tv"])
        elif isinstance(tmdb, dict) and "movie" in tmdb:
            out["tmdb_id"] = int(tmdb["movie"])
        elif isinstance(tmdb, int):
            out["tmdb_id"] = tmdb
        return out
