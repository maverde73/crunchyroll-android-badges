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


# --- CI-only live wrappers (exercised by workflow_dispatch, not unit tests) ---
# These convert the simple-justwatch-python-api objects into the dict shape the
# tested pure parsers expect. Verify the attribute names against the installed
# library version on first CI run (spec §11 open question).
AG_PROVIDER = "anime_generation_amazon_channel"


def fetch_anime_generation_titles(jw_search, country: str = "IT", language: str = "it") -> list["RawAgTitle"]:
    """Enumerate Anime Generation titles via JustWatch search results.

    jw_search is simplejustwatchapi.justwatch.search. We page through a broad
    query and keep only entries carrying an AG offer. (If the library exposes a
    provider-listing call in your version, prefer it; the parser is the same.)
    """
    entries: list[dict] = []
    results = jw_search("anime", country, language, 200, True)  # (query, country, lang, count, best_only)
    for media in results:
        offers = [
            {
                "package": {"technical_name": getattr(o.package, "technical_name", "")},
                "audio_languages": list(getattr(o, "audio_languages", []) or []),
                "subtitle_languages": list(getattr(o, "subtitle_languages", []) or []),
                "url": getattr(o, "url", ""),
            }
            for o in (getattr(media, "offers", []) or [])
        ]
        entries.append({
            "entry_id": getattr(media, "entry_id", ""),
            "title": getattr(media, "title", ""),
            "release_year": getattr(media, "release_year", None),
            "offers": offers,
        })
    return parse_justwatch_offers(entries, provider=AG_PROVIDER)


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
