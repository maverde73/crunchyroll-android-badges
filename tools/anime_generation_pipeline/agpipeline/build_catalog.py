from __future__ import annotations

import json
import os
from pathlib import Path
from typing import Callable

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
) -> Catalog:
    titles: list[CatalogTitle] = []
    for raw in ag_titles:
        mal_id = mal_lookup(raw)
        external_ids = id_mapper.external_ids(mal_id=mal_id) if mal_id else {}
        meta = enricher.enrich(external_ids) if external_ids else None
        matched_cr = best_crunchyroll_match(raw.title, raw.year, cr_catalog)

        audio = raw.audio_locales or ["it-IT"]
        subs = raw.subtitle_locales or ["it-IT"]
        assumed = not raw.audio_locales

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
    import requests
    from simplejustwatchapi.justwatch import search  # provided by the JustWatch lib

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

    def http_get(url: str, params: dict) -> dict:
        resp = session.get(url, params=params, timeout=30)
        resp.raise_for_status()
        return resp.json()

    def http_post(url: str, payload: dict) -> dict:
        resp = session.post(url, json=payload, timeout=30)
        resp.raise_for_status()
        return resp.json()

    ag_titles = fetch_anime_generation_titles(search)
    cr_catalog = fetch_crunchyroll_catalog(http_get)
    id_mapper = IdMapper.from_file(
        Path(os.environ.get("ANIME_LISTS_PATH", "anime-list-full.json"))
    )
    enricher = Enricher(http_get=http_get, tmdb_key=tmdb_key)

    matched = sum(1 for t in ag_titles)  # logged below for coverage visibility

    def mal_lookup(raw: RawAgTitle):
        return anilist_mal_id_for(http_post, raw.title, raw.year)

    catalog = build_catalog(
        ag_titles=ag_titles, cr_catalog=cr_catalog, id_mapper=id_mapper,
        enricher=enricher, mal_lookup=mal_lookup, version=1,
        generated_at=datetime.datetime.now(datetime.timezone.utc)
            .replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    )
    # Coverage visibility — never silently truncate (spec §11).
    with_tmdb = sum(1 for t in catalog.titles if t.external_ids.get("tmdb_id"))
    matched_cr = sum(1 for t in catalog.titles if t.matched_crunchyroll_id)
    assumed_lang = sum(1 for t in catalog.titles if t.languages_assumed)
    print(
        f"AG titles found: {matched} | with tmdb: {with_tmdb} | "
        f"matched to CR: {matched_cr} | assumed languages: {assumed_lang}"
    )

    write_if_valid(catalog, out_path, min_titles=min_titles)
    print(f"Wrote {len(catalog.titles)} titles to {out_path}")
    return 0
