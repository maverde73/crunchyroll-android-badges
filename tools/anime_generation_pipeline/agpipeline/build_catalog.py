from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Callable

from agpipeline.age import normalize_age
from agpipeline.enrich import Enricher
from agpipeline.matching import IdMapper, best_crunchyroll_match
from agpipeline.models import Catalog, CatalogTitle
from agpipeline.sources import RawAgTitle
from agpipeline.validate import validate_catalog

MalLookup = Callable[[RawAgTitle], "int | None"]


def build_catalog(
    ag_titles: list[RawAgTitle],
    cr_catalog: list[dict],
    id_mapper: IdMapper,
    enricher: Enricher,
    mal_lookup: MalLookup,
    version: int,
    generated_at: str,
    external_ids_lookup=None,
    age_fallback=None,
) -> Catalog:
    titles: list[CatalogTitle] = []
    for raw in ag_titles:
        if external_ids_lookup is not None:
            # Direct lookup (e.g. TMDB search by title) -> external ids.
            external_ids = external_ids_lookup(raw) or {}
        else:
            mal_id = mal_lookup(raw)
            external_ids = id_mapper.external_ids(mal_id=mal_id) if mal_id else {}
        meta = enricher.enrich(external_ids) if external_ids else None
        matched_cr = best_crunchyroll_match(raw.title, raw.year, cr_catalog)

        audio = raw.audio_locales or ["it-IT"]
        subs = raw.subtitle_locales or ["it-IT"]
        assumed = not raw.audio_locales

        # Age: JustWatch (IT) -> TMDB (IT/US) -> fallback (Kitsu/Jikan), all normalized.
        maturity = (
            normalize_age(raw.age_certification)
            or normalize_age(meta.maturity_rating if meta else "")
            or (age_fallback(raw) if age_fallback else "")
        )

        titles.append(CatalogTitle(
            ag_id=raw.ag_id,
            title=raw.title,
            year=(meta.year if meta and meta.year else raw.year),
            matched_crunchyroll_id=matched_cr,
            external_ids=external_ids,
            description_it=(meta.description_it if meta else ""),
            poster_tall=(meta.poster_tall if meta else ""),
            poster_wide=(meta.poster_wide if meta else ""),
            genres=(meta.genres if meta else []),
            rating=(meta.rating if meta else None),
            audio_locales=audio,
            subtitle_locales=subs,
            languages_assumed=assumed,
            deep_link_url=raw.deep_link_url,
            maturity_rating=maturity,
        ))
    return Catalog(version=version, generated_at=generated_at, titles=titles)


def write_if_valid(catalog: Catalog, out_path: Path, min_titles: int) -> None:
    """Validate first; only overwrite out_path when the catalog is valid."""
    validate_catalog(catalog, min_titles=min_titles)  # raises -> existing file untouched
    Path(out_path).write_text(
        json.dumps(catalog.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
    )


def main() -> int:  # pragma: no cover - CI integration entrypoint
    import datetime
    import time
    import requests
    import simplejustwatchapi.justwatch as jw

    from agpipeline.sources import (
        anilist_mal_id_for,
        fetch_anime_generation_titles,
        fetch_crunchyroll_catalog,
    )

    tmdb_key = os.environ["TMDB_API_KEY"]
    out_path = Path(os.environ.get("OUTPUT_PATH", "catalog_anime_generation.json"))
    min_titles = int(os.environ.get("MIN_TITLES", "100"))

    session = requests.Session()
    session.headers.update({"User-Agent": "ag-pipeline/0.1"})

    def _request(method: str, url: str, **kw) -> dict:
        # Retry on rate limits / transient errors with simple backoff.
        for attempt in range(5):
            resp = session.request(method, url, timeout=30, **kw)
            if resp.status_code == 429:
                wait = int(resp.headers.get("Retry-After", 2 ** attempt))
                time.sleep(min(wait, 30))
                continue
            resp.raise_for_status()
            return resp.json()
        resp.raise_for_status()
        return resp.json()

    def http_get(url: str, params: dict) -> dict:
        return _request("GET", url, params=params)

    def http_post(url: str, payload: dict) -> dict:
        return _request("POST", url, json=payload)

    ag_titles = fetch_anime_generation_titles(jw)
    try:
        cr_catalog = fetch_crunchyroll_catalog(http_get)
    except Exception as e:
        # The CR browse API requires auth headers (the app gets them via a
        # WebView). Without them the fetch fails; skip CR matching for now —
        # titles become AG-only (matched_crunchyroll_id stays null).
        print(f"CR catalog fetch failed ({e}); skipping CR matching.")
        cr_catalog = []
    enricher = Enricher(http_get=http_get, tmdb_key=tmdb_key)

    from agpipeline.sources import tmdb_search_external_ids, kitsu_age_rating, jikan_age_rating

    def ext_lookup(raw: RawAgTitle):
        try:
            return tmdb_search_external_ids(http_get, tmdb_key, raw.title, raw.year)
        except Exception:
            return {}

    def age_fallback(raw: RawAgTitle) -> str:
        # Called only when JustWatch + TMDB had no certification.
        return normalize_age(kitsu_age_rating(http_get, raw.title)) \
            or normalize_age(jikan_age_rating(http_get, raw.title))

    matched = len(ag_titles)  # logged below for coverage visibility

    catalog = build_catalog(
        ag_titles=ag_titles, cr_catalog=cr_catalog, id_mapper=IdMapper([]),
        enricher=enricher, mal_lookup=lambda r: None, version=1,
        generated_at=datetime.datetime.now(datetime.timezone.utc)
            .replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        external_ids_lookup=ext_lookup,
        age_fallback=age_fallback,
    )
    # Coverage visibility — never silently truncate (spec §11).
    with_tmdb = sum(1 for t in catalog.titles if t.external_ids.get("tmdb_id"))
    matched_cr = sum(1 for t in catalog.titles if t.matched_crunchyroll_id)
    assumed_lang = sum(1 for t in catalog.titles if t.languages_assumed)
    with_age = sum(1 for t in catalog.titles if t.maturity_rating)
    print(
        f"AG titles found: {matched} | with tmdb: {with_tmdb} | "
        f"matched to CR: {matched_cr} | assumed languages: {assumed_lang} | "
        f"with age rating: {with_age}"
    )

    write_if_valid(catalog, out_path, min_titles=min_titles)
    print(f"Wrote {len(catalog.titles)} titles to {out_path}")
    return 0
