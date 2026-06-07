from __future__ import annotations

from dataclasses import dataclass

# JustWatch returns 2-letter codes; the app/contract use BCP-47-ish locale tags.
_LANG_TO_LOCALE = {"it": "it-IT", "ja": "ja-JP", "en": "en-US"}


def _to_locale(code: str) -> str:
    return _LANG_TO_LOCALE.get(code, code)


@dataclass(frozen=True)
class RawAgTitle:
    ag_id: str
    title: str
    year: int | None
    audio_locales: list[str]
    subtitle_locales: list[str]
    deep_link_url: str


def parse_justwatch_offers(entries: list[dict], provider: str) -> list[RawAgTitle]:
    """Keep only entries that have an offer from the given JustWatch provider."""
    out: list[RawAgTitle] = []
    for e in entries:
        offer = next(
            (o for o in e.get("offers", [])
             if (o.get("package") or {}).get("technical_name") == provider),
            None,
        )
        if offer is None:
            continue
        out.append(RawAgTitle(
            ag_id=e["entry_id"],
            title=e["title"],
            year=e.get("release_year"),
            audio_locales=[_to_locale(c) for c in offer.get("audio_languages") or []],
            subtitle_locales=[_to_locale(c) for c in offer.get("subtitle_languages") or []],
            deep_link_url=offer.get("url", ""),
        ))
    return out


def parse_crunchyroll_browse(pages: list[dict]) -> list[dict]:
    """Flatten Crunchyroll browse pages into {id, title, year} rows."""
    out: list[dict] = []
    for page in pages:
        for item in page.get("data", []):
            meta = item.get("series_metadata") or {}
            out.append({
                "id": item["id"],
                "title": item["title"],
                "year": meta.get("series_launch_year"),
            })
    return out


def fetch_crunchyroll_catalog(http_get, page_size: int = 36, max_pages: int = 200) -> list[dict]:
    pages: list[dict] = []
    for i in range(max_pages):
        body = http_get(
            "https://www.crunchyroll.com/content/v2/discover/browse",
            {"n": page_size, "start": i * page_size, "sort_by": "alphabetical"},
        )
        if not body.get("data"):
            break
        pages.append(body)
    return parse_crunchyroll_browse(pages)


# --- Live wrappers (verified against simple-justwatch-python-api 0.16) ---
# The Anime Generation channel is the JustWatch provider with technical_name
# "amazonanimegeneration" and 3-letter short_name "aag". Enumeration uses
# popular(providers=[short_name]) which (unlike the technical_name) actually
# filters server-side; ~281 titles are returned for IT.
AG_PROVIDER = "amazonanimegeneration"
AG_SHORT_NAME = "aag"


def _media_to_entry(media) -> dict:
    """Convert a simple-justwatch MediaEntry into the dict shape parsers expect."""
    offers = [
        {
            "package": {"technical_name": getattr(getattr(o, "package", None), "technical_name", "")},
            "audio_languages": list(getattr(o, "audio_languages", []) or []),
            "subtitle_languages": list(getattr(o, "subtitle_languages", []) or []),
            "url": getattr(o, "url", "") or "",
        }
        for o in (getattr(media, "offers", []) or [])
    ]
    return {
        "entry_id": getattr(media, "entry_id", ""),
        "title": getattr(media, "title", ""),
        "release_year": getattr(media, "release_year", None),
        "offers": offers,
    }


def fetch_anime_generation_titles(
    jw, country: str = "IT", language: str = "it", page_size: int = 100, max_pages: int = 12
) -> list["RawAgTitle"]:
    """Enumerate the full Anime Generation catalog via JustWatch `popular`.

    `jw` is the simplejustwatchapi.justwatch module. We resolve the provider's
    3-letter short_name, then page through popular(providers=[short]) until the
    catalog is exhausted, and keep only entries carrying the AG offer.
    """
    short = next(
        (getattr(p, "short_name", None) for p in jw.providers(country)
         if getattr(p, "technical_name", "") == AG_PROVIDER),
        None,
    ) or AG_SHORT_NAME

    seen: dict[str, object] = {}
    for i in range(max_pages):
        batch = jw.popular(country, language, page_size, True, i * page_size, providers=[short])
        if not batch:
            break
        for m in batch:
            seen[getattr(m, "entry_id", "")] = m
        if len(batch) < page_size:
            break

    entries = [_media_to_entry(m) for m in seen.values()]
    return parse_justwatch_offers(entries, provider=AG_PROVIDER)


def tmdb_search_external_ids(http_get, tmdb_key: str, title: str, year: int | None) -> dict[str, int]:
    """Resolve a title (+year) to a TMDB id via TMDB's own search (it-IT).

    More robust than the AniList->Fribb->TMDB chain: one call, returns the best
    TV match. The Enricher then fetches full it-IT details by that id.
    """
    params = {"api_key": tmdb_key, "language": "it-IT", "query": title}
    if year:
        params["first_air_date_year"] = year
    body = http_get("https://api.themoviedb.org/3/search/tv", params)
    results = body.get("results") or []
    if not results:
        return {}
    return {"tmdb_id": int(results[0]["id"])}


def anilist_mal_id_for(http_post, title: str, year: int | None) -> int | None:
    """Resolve a title+year to a MAL id via the AniList GraphQL search.

    Returns idMal so the IdMapper can join to TMDB/Kitsu. Best-effort: returns
    None when AniList has no confident match (the orchestrator then emits a
    title with empty external_ids, which the app still shows minimally).
    """
    query = """
    query ($search: String, $year: FuzzyDateInt) {
      Media(search: $search, type: ANIME, startDate_greater: $year) { idMal }
    }
    """
    start = (year * 10000) if year else None
    body = http_post(
        "https://graphql.anilist.co",
        {"query": query, "variables": {"search": title, "year": start}},
    )
    return (((body or {}).get("data") or {}).get("Media") or {}).get("idMal")
