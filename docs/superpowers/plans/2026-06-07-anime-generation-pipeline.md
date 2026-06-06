# Anime Generation — Offline Catalog Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an offline Python pipeline that produces a validated `catalog_anime_generation.json`, scheduled by GitHub Actions, to be consumed by the Android app.

**Architecture:** A standalone Python package under `tools/anime_generation_pipeline/`. It fetches the Anime Generation title list from JustWatch and the Crunchyroll public catalog, matches the two (fuzzy title+year anchor → deterministic ID join via Fribb/anime-lists), enriches each title (TMDB it-IT + Jikan fallback), validates the result against a schema + minimum-count threshold, and only then overwrites the published JSON. Network-touching modules take injectable clients so they are unit-testable with mocks.

**Tech Stack:** Python 3.11, `simple-justwatch-python-api`, `requests`, `rapidfuzz`, `pytest`. CI: GitHub Actions (`on: schedule`).

**Contract:** This plan produces the JSON defined in `docs/superpowers/specs/2026-06-07-anime-generation-multiplatform-design.md` §5. The Android app plan (`2026-06-07-anime-generation-app-integration.md`) consumes the same contract.

---

## File Structure

- Create: `tools/anime_generation_pipeline/pyproject.toml` — package + deps + pytest config
- Create: `tools/anime_generation_pipeline/agpipeline/__init__.py`
- Create: `tools/anime_generation_pipeline/agpipeline/models.py` — contract dataclasses + JSON (de)serialization
- Create: `tools/anime_generation_pipeline/agpipeline/validate.py` — schema + threshold validation
- Create: `tools/anime_generation_pipeline/agpipeline/matching.py` — title normalization, fuzzy anchor, Fribb ID join
- Create: `tools/anime_generation_pipeline/agpipeline/enrich.py` — TMDB + Jikan enrichment (injectable HTTP)
- Create: `tools/anime_generation_pipeline/agpipeline/sources.py` — JustWatch (AG) + Crunchyroll catalog fetch (injectable)
- Create: `tools/anime_generation_pipeline/agpipeline/build_catalog.py` — orchestrator + CLI entrypoint
- Create: `tools/anime_generation_pipeline/tests/...` — pytest tests + fixtures
- Create: `.github/workflows/anime-generation-catalog.yml` — scheduled CI

Each module has one responsibility and pure functions where possible; only `sources.py` and `enrich.py` do I/O, and both accept an injected client so tests never hit the network.

---

## Task 1: Project scaffold + dependencies

**Files:**
- Create: `tools/anime_generation_pipeline/pyproject.toml`
- Create: `tools/anime_generation_pipeline/agpipeline/__init__.py`
- Create: `tools/anime_generation_pipeline/tests/__init__.py`
- Test: `tools/anime_generation_pipeline/tests/test_smoke.py`

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_smoke.py`:
```python
def test_package_imports():
    import agpipeline
    assert agpipeline.__version__ == "0.1.0"
```

- [ ] **Step 2: Create pyproject.toml**

`tools/anime_generation_pipeline/pyproject.toml`:
```toml
[project]
name = "agpipeline"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "simple-justwatch-python-api>=0.16",
    "requests>=2.31",
    "rapidfuzz>=3.6",
]

[project.optional-dependencies]
dev = ["pytest>=8.0"]

[build-system]
requires = ["setuptools>=68"]
build-backend = "setuptools.build_meta"

[tool.setuptools]
packages = ["agpipeline"]

[tool.pytest.ini_options]
testpaths = ["tests"]
```

- [ ] **Step 3: Create the package marker**

`tools/anime_generation_pipeline/agpipeline/__init__.py`:
```python
__version__ = "0.1.0"
```

Create empty `tools/anime_generation_pipeline/tests/__init__.py`.

- [ ] **Step 4: Install and run the test**

Run:
```bash
cd tools/anime_generation_pipeline && python -m venv .venv && . .venv/bin/activate && pip install -e ".[dev]" && pytest -q
```
Expected: 1 passed.

- [ ] **Step 5: Commit**

```bash
git add tools/anime_generation_pipeline/pyproject.toml tools/anime_generation_pipeline/agpipeline/__init__.py tools/anime_generation_pipeline/tests/__init__.py tools/anime_generation_pipeline/tests/test_smoke.py
git commit -m "chore(pipeline): scaffold anime generation pipeline package"
```

---

## Task 2: Contract models (JSON serialization)

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/models.py`
- Test: `tools/anime_generation_pipeline/tests/test_models.py`

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_models.py`:
```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_models.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.models'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/models.py`:
```python
from __future__ import annotations

from dataclasses import dataclass, field, asdict


@dataclass(frozen=True)
class CatalogTitle:
    ag_id: str
    title: str
    year: int | None
    matched_crunchyroll_id: str | None
    external_ids: dict[str, int]
    description_it: str
    poster_tall: str
    poster_wide: str
    genres: list[str]
    rating: float | None
    audio_locales: list[str]
    subtitle_locales: list[str]
    languages_assumed: bool
    deep_link_url: str

    def to_dict(self) -> dict:
        return asdict(self)

    @staticmethod
    def from_dict(d: dict) -> "CatalogTitle":
        return CatalogTitle(
            ag_id=d["ag_id"],
            title=d["title"],
            year=d.get("year"),
            matched_crunchyroll_id=d.get("matched_crunchyroll_id"),
            external_ids=dict(d.get("external_ids") or {}),
            description_it=d.get("description_it", ""),
            poster_tall=d.get("poster_tall", ""),
            poster_wide=d.get("poster_wide", ""),
            genres=list(d.get("genres") or []),
            rating=d.get("rating"),
            audio_locales=list(d.get("audio_locales") or []),
            subtitle_locales=list(d.get("subtitle_locales") or []),
            languages_assumed=bool(d.get("languages_assumed", False)),
            deep_link_url=d.get("deep_link_url", ""),
        )


@dataclass
class Catalog:
    version: int
    generated_at: str
    titles: list[CatalogTitle] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "version": self.version,
            "generated_at": self.generated_at,
            "titles": [t.to_dict() for t in self.titles],
        }

    @staticmethod
    def from_dict(d: dict) -> "Catalog":
        return Catalog(
            version=int(d["version"]),
            generated_at=d["generated_at"],
            titles=[CatalogTitle.from_dict(t) for t in d.get("titles", [])],
        )
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_models.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/models.py tests/test_models.py
git commit -m "feat(pipeline): add catalog contract models with json round-trip"
```

---

## Task 3: Output validation (schema + minimum-count threshold)

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/validate.py`
- Test: `tools/anime_generation_pipeline/tests/test_validate.py`

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_validate.py`:
```python
import pytest
from agpipeline.models import Catalog, CatalogTitle
from agpipeline.validate import validate_catalog, ValidationError


def _title(ag_id: str) -> CatalogTitle:
    return CatalogTitle(
        ag_id=ag_id, title="T", year=2000, matched_crunchyroll_id=None,
        external_ids={}, description_it="d", poster_tall="u", poster_wide="u",
        genres=[], rating=None, audio_locales=["it-IT"], subtitle_locales=["it-IT"],
        languages_assumed=True, deep_link_url="https://x",
    )


def test_valid_catalog_passes():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title(str(i)) for i in range(50)])
    validate_catalog(cat, min_titles=10)  # no raise


def test_below_threshold_raises():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title("1")])
    with pytest.raises(ValidationError, match="below threshold"):
        validate_catalog(cat, min_titles=10)


def test_duplicate_ag_id_raises():
    cat = Catalog(1, "2026-06-07T12:00:00Z", [_title("dup"), _title("dup")])
    with pytest.raises(ValidationError, match="duplicate ag_id"):
        validate_catalog(cat, min_titles=1)


def test_missing_title_field_raises():
    bad = _title("1")
    object.__setattr__(bad, "title", "")
    cat = Catalog(1, "2026-06-07T12:00:00Z", [bad])
    with pytest.raises(ValidationError, match="empty title"):
        validate_catalog(cat, min_titles=1)
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_validate.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.validate'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/validate.py`:
```python
from __future__ import annotations

from agpipeline.models import Catalog


class ValidationError(Exception):
    pass


def validate_catalog(catalog: Catalog, min_titles: int) -> None:
    """Raise ValidationError if the catalog is malformed or too small to publish."""
    if len(catalog.titles) < min_titles:
        raise ValidationError(
            f"title count {len(catalog.titles)} below threshold {min_titles}"
        )

    seen: set[str] = set()
    for t in catalog.titles:
        if not t.ag_id:
            raise ValidationError("empty ag_id")
        if t.ag_id in seen:
            raise ValidationError(f"duplicate ag_id: {t.ag_id}")
        seen.add(t.ag_id)
        if not t.title:
            raise ValidationError(f"empty title for ag_id {t.ag_id}")
        if not t.audio_locales:
            raise ValidationError(f"empty audio_locales for ag_id {t.ag_id}")
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_validate.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/validate.py tests/test_validate.py
git commit -m "feat(pipeline): add catalog validation with threshold and dedup"
```

---

## Task 4: Title matching — normalization + fuzzy anchor

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/matching.py`
- Test: `tools/anime_generation_pipeline/tests/test_matching.py`

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_matching.py`:
```python
from agpipeline.matching import normalize_title, best_crunchyroll_match


def test_normalize_strips_punctuation_and_lowercases():
    assert normalize_title("Lamù: Beautiful Dreamer!") == "lamu beautiful dreamer"
    assert normalize_title("NARUTO  -ナルト-") == "naruto"


def test_best_match_returns_id_when_title_and_year_align():
    cr = [
        {"id": "GR1", "title": "Naruto", "year": 2002},
        {"id": "GR2", "title": "Bleach", "year": 2004},
    ]
    match = best_crunchyroll_match("Naruto", 2002, cr, min_score=90)
    assert match == "GR1"


def test_best_match_rejects_low_score():
    cr = [{"id": "GR1", "title": "Naruto", "year": 2002}]
    assert best_crunchyroll_match("One Piece", 1999, cr, min_score=90) is None


def test_best_match_rejects_year_mismatch_even_if_title_close():
    cr = [{"id": "GR1", "title": "Hunter x Hunter", "year": 1999}]
    # remake exists in 2011 — must not collide with the 1999 entry
    assert best_crunchyroll_match("Hunter x Hunter", 2011, cr, min_score=90, max_year_delta=1) is None
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_matching.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.matching'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/matching.py`:
```python
from __future__ import annotations

import re
import unicodedata

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_matching.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/matching.py tests/test_matching.py
git commit -m "feat(pipeline): add title normalization and conservative CR fuzzy match"
```

---

## Task 5: ID join via Fribb/anime-lists mapping

**Files:**
- Modify: `tools/anime_generation_pipeline/agpipeline/matching.py`
- Test: `tools/anime_generation_pipeline/tests/test_id_mapping.py`
- Test fixture: `tools/anime_generation_pipeline/tests/fixtures/anime-list-sample.json`

- [ ] **Step 1: Create the fixture**

`tools/anime_generation_pipeline/tests/fixtures/anime-list-sample.json`:
```json
[
  {"mal_id": 1, "anilist_id": 290, "kitsu_id": 265, "themoviedb_id": {"tv": 26209}},
  {"mal_id": 20, "anilist_id": 20, "kitsu_id": 11, "themoviedb_id": {"tv": 31910}}
]
```

- [ ] **Step 2: Write the failing test**

`tools/anime_generation_pipeline/tests/test_id_mapping.py`:
```python
from pathlib import Path
from agpipeline.matching import IdMapper

FIX = Path(__file__).parent / "fixtures" / "anime-list-sample.json"


def test_external_ids_from_mal_id():
    mapper = IdMapper.from_file(FIX)
    ids = mapper.external_ids(mal_id=1)
    assert ids == {"mal_id": 1, "anilist_id": 290, "tmdb_id": 26209}


def test_external_ids_from_anilist_id():
    mapper = IdMapper.from_file(FIX)
    ids = mapper.external_ids(anilist_id=20)
    assert ids["mal_id"] == 20 and ids["tmdb_id"] == 31910


def test_unknown_returns_empty():
    mapper = IdMapper.from_file(FIX)
    assert mapper.external_ids(mal_id=99999) == {}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `pytest tests/test_id_mapping.py -q`
Expected: FAIL with `ImportError: cannot import name 'IdMapper'`

- [ ] **Step 4: Append implementation to matching.py**

Add to `tools/anime_generation_pipeline/agpipeline/matching.py`:
```python
import json
from pathlib import Path


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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `pytest tests/test_id_mapping.py -q`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agpipeline/matching.py tests/test_id_mapping.py tests/fixtures/anime-list-sample.json
git commit -m "feat(pipeline): add cross-database id mapper (Fribb anime-lists)"
```

---

## Task 6: Metadata enrichment (TMDB it-IT + Jikan fallback)

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/enrich.py`
- Test: `tools/anime_generation_pipeline/tests/test_enrich.py`

The enricher takes an injectable `http_get(url, params) -> dict` so tests never hit the network.

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_enrich.py`:
```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_enrich.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.enrich'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/enrich.py`:
```python
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_enrich.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/enrich.py tests/test_enrich.py
git commit -m "feat(pipeline): add TMDB it-IT enrichment with Jikan fallback"
```

---

## Task 7: Sources (JustWatch AG provider + Crunchyroll catalog)

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/sources.py`
- Test: `tools/anime_generation_pipeline/tests/test_sources.py`

Both source functions accept injected clients so tests run offline. `fetch_anime_generation_titles` takes a `justwatch_client` exposing `search`/`offers_for_countries` as in `simple-justwatch-python-api`; `fetch_crunchyroll_catalog` takes an injected `http_get`.

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_sources.py`:
```python
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_sources.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.sources'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/sources.py`:
```python
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
```

Note: live fetch wrappers (`fetch_anime_generation_titles`, `fetch_crunchyroll_catalog`) call the JustWatch library / CR browse API and then delegate to these pure parsers. They are exercised by the CI run, not unit tests, because they touch the network. Add them as thin wrappers:
```python
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
    query set and keep only entries carrying an AG offer. (If the library exposes
    a provider-listing call in your version, prefer it; the parser is the same.)
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


def anilist_mal_id_for(http_get, title: str, year: int | None) -> int | None:
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
    body = http_get(
        "https://graphql.anilist.co",
        {"query": query, "variables": {"search": title, "year": start}},
    )
    return (((body or {}).get("data") or {}).get("Media") or {}).get("idMal")
```

Note: `anilist_mal_id_for` uses a POST in practice; if `http_get` is GET-only, add a small `http_post(url, json)` helper in `main` and pass it here. Keep the conversion logic in this function so the orchestrator stays unit-testable via the `mal_lookup` injection.

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_sources.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/sources.py tests/test_sources.py
git commit -m "feat(pipeline): add JustWatch AG and Crunchyroll browse parsers"
```

---

## Task 8: Orchestrator — build the catalog (golden-file test)

**Files:**
- Create: `tools/anime_generation_pipeline/agpipeline/build_catalog.py`
- Test: `tools/anime_generation_pipeline/tests/test_build_catalog.py`

The orchestrator is a pure function `build_catalog(ag_titles, cr_catalog, id_mapper, enricher, mal_lookup, version, generated_at)` so it is fully testable with fakes. `mal_lookup(RawAgTitle) -> int|None` resolves an AG title to a MAL id (in production via an AniList/Jikan search; in tests a dict).

- [ ] **Step 1: Write the failing test**

`tools/anime_generation_pipeline/tests/test_build_catalog.py`:
```python
from pathlib import Path
from agpipeline.matching import IdMapper
from agpipeline.enrich import Enricher
from agpipeline.sources import RawAgTitle
from agpipeline.build_catalog import build_catalog

FIX = Path(__file__).parent / "fixtures" / "anime-list-sample.json"


class FakeHttp:
    def __call__(self, url, params=None):
        if "/tv/26209" in url:
            return {"overview": "Desc IT", "poster_path": "/p.jpg",
                    "backdrop_path": "/b.jpg", "genres": [{"name": "Commedia"}],
                    "vote_average": 7.8, "first_air_date": "1981-10-14"}
        return {}


def test_build_merges_match_and_enrichment():
    ag = [RawAgTitle("ts1", "Lamù", 1981, ["it-IT", "ja-JP"], ["it-IT"], "https://pv/lamu")]
    cr = [{"id": "GRLAMU", "title": "Lamu", "year": 1981}]
    mapper = IdMapper.from_file(FIX)
    enricher = Enricher(http_get=FakeHttp(), tmdb_key="k")

    catalog = build_catalog(
        ag_titles=ag, cr_catalog=cr, id_mapper=mapper, enricher=enricher,
        mal_lookup=lambda t: 1,  # Lamù -> mal_id 1 (maps to tmdb 26209)
        version=1, generated_at="2026-06-07T12:00:00Z",
    )

    assert len(catalog.titles) == 1
    t = catalog.titles[0]
    assert t.ag_id == "ts1"
    assert t.matched_crunchyroll_id == "GRLAMU"      # fuzzy "Lamù"~"Lamu" + year
    assert t.external_ids["tmdb_id"] == 26209
    assert t.description_it == "Desc IT"
    assert t.poster_tall.endswith("/p.jpg")
    assert t.audio_locales == ["it-IT", "ja-JP"]
    assert t.languages_assumed is False


def test_build_uses_language_fallback_when_offer_has_no_languages():
    ag = [RawAgTitle("ts9", "Sconosciuto", 2000, [], [], "https://pv/x")]
    catalog = build_catalog(
        ag_titles=ag, cr_catalog=[], id_mapper=IdMapper([]),
        enricher=Enricher(http_get=FakeHttp(), tmdb_key="k"),
        mal_lookup=lambda t: None, version=1, generated_at="2026-06-07T12:00:00Z",
    )
    t = catalog.titles[0]
    assert t.matched_crunchyroll_id is None
    assert t.audio_locales == ["it-IT"]
    assert t.subtitle_locales == ["it-IT"]
    assert t.languages_assumed is True
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_build_catalog.py -q`
Expected: FAIL with `ModuleNotFoundError: No module named 'agpipeline.build_catalog'`

- [ ] **Step 3: Write minimal implementation**

`tools/anime_generation_pipeline/agpipeline/build_catalog.py`:
```python
from __future__ import annotations

from typing import Callable

from agpipeline.enrich import Enricher
from agpipeline.matching import IdMapper, best_crunchyroll_match
from agpipeline.models import Catalog, CatalogTitle
from agpipeline.sources import RawAgTitle

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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_build_catalog.py -q`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agpipeline/build_catalog.py tests/test_build_catalog.py
git commit -m "feat(pipeline): add catalog orchestrator merging match + enrichment"
```

---

## Task 9: CLI entrypoint — fetch, build, validate, write only if valid

**Files:**
- Modify: `tools/anime_generation_pipeline/agpipeline/build_catalog.py` (add `main`)
- Modify: `tools/anime_generation_pipeline/pyproject.toml` (add console script)
- Test: `tools/anime_generation_pipeline/tests/test_cli_write.py`

- [ ] **Step 1: Write the failing test (write-guard behavior)**

`tools/anime_generation_pipeline/tests/test_cli_write.py`:
```python
from pathlib import Path
import json
import pytest
from agpipeline.models import Catalog, CatalogTitle
from agpipeline.build_catalog import write_if_valid
from agpipeline.validate import ValidationError


def _catalog(n):
    titles = [CatalogTitle(f"a{i}", "T", 2000, None, {}, "d", "u", "u", [], None,
                           ["it-IT"], ["it-IT"], True, "https://x") for i in range(n)]
    return Catalog(1, "2026-06-07T12:00:00Z", titles)


def test_writes_when_valid(tmp_path):
    out = tmp_path / "catalog.json"
    write_if_valid(_catalog(20), out, min_titles=10)
    assert json.loads(out.read_text())["titles"][0]["ag_id"] == "a0"


def test_does_not_overwrite_existing_when_invalid(tmp_path):
    out = tmp_path / "catalog.json"
    out.write_text('{"version":1,"generated_at":"old","titles":[]}')
    with pytest.raises(ValidationError):
        write_if_valid(_catalog(1), out, min_titles=10)
    # previous good file must be untouched
    assert json.loads(out.read_text())["generated_at"] == "old"
```

- [ ] **Step 2: Run test to verify it fails**

Run: `pytest tests/test_cli_write.py -q`
Expected: FAIL with `ImportError: cannot import name 'write_if_valid'`

- [ ] **Step 3: Add `write_if_valid` and `main` to build_catalog.py**

Append to `tools/anime_generation_pipeline/agpipeline/build_catalog.py`:
```python
import json
import os
from pathlib import Path

from agpipeline.validate import validate_catalog


def write_if_valid(catalog: Catalog, out_path: Path, min_titles: int) -> None:
    """Validate first; only overwrite out_path when the catalog is valid."""
    validate_catalog(catalog, min_titles=min_titles)  # raises -> existing file untouched
    Path(out_path).write_text(
        json.dumps(catalog.to_dict(), ensure_ascii=False, indent=2), encoding="utf-8"
    )


def main() -> int:
    import datetime
    import requests
    from simplejustwatchapi.justwatch import search  # provided by the JustWatch lib

    tmdb_key = os.environ["TMDB_API_KEY"]
    out_path = Path(os.environ.get("OUTPUT_PATH", "catalog_anime_generation.json"))
    min_titles = int(os.environ.get("MIN_TITLES", "100"))

    def http_get(url: str, params: dict) -> dict:
        resp = requests.get(url, params=params, timeout=30,
                            headers={"User-Agent": "ag-pipeline/0.1"})
        resp.raise_for_status()
        return resp.json()

    # NOTE: the JustWatch enumeration and AniList/Jikan title->mal_id search are
    # wired here against the live libraries; see sources.fetch_* and the open
    # questions in the spec. Kept thin so logic stays in tested pure functions.
    from agpipeline.sources import fetch_anime_generation_titles, fetch_crunchyroll_catalog
    from agpipeline.matching import IdMapper
    from agpipeline.enrich import Enricher

    ag_titles = fetch_anime_generation_titles(search)
    cr_catalog = fetch_crunchyroll_catalog(http_get)
    id_mapper = IdMapper.from_file(Path(os.environ.get("ANIME_LISTS_PATH", "anime-list-full.json")))
    enricher = Enricher(http_get=http_get, tmdb_key=tmdb_key)

    def mal_lookup(raw):
        from agpipeline.sources import anilist_mal_id_for
        return anilist_mal_id_for(http_get, raw.title, raw.year)

    catalog = build_catalog(
        ag_titles=ag_titles, cr_catalog=cr_catalog, id_mapper=id_mapper,
        enricher=enricher, mal_lookup=mal_lookup, version=1,
        generated_at=datetime.datetime.now(datetime.timezone.utc)
            .replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    )
    write_if_valid(catalog, out_path, min_titles=min_titles)
    print(f"Wrote {len(catalog.titles)} titles to {out_path}")
    return 0
```

Add the remaining live wrappers to `sources.py` (`fetch_anime_generation_titles`, `anilist_mal_id_for`) as thin functions that call the libraries and delegate to the tested parsers; they run only in CI.

Add to `pyproject.toml` under `[project]`:
```toml
[project.scripts]
ag-build-catalog = "agpipeline.build_catalog:main"
```

- [ ] **Step 4: Run test to verify it passes**

Run: `pytest tests/test_cli_write.py -q`
Expected: PASS

- [ ] **Step 5: Run the full suite**

Run: `pytest -q`
Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add agpipeline/build_catalog.py agpipeline/sources.py pyproject.toml tests/test_cli_write.py
git commit -m "feat(pipeline): add CLI entrypoint with validate-before-write guard"
```

---

## Task 10: GitHub Actions scheduled workflow

**Files:**
- Create: `.github/workflows/anime-generation-catalog.yml`

- [ ] **Step 1: Create the workflow**

`.github/workflows/anime-generation-catalog.yml`:
```yaml
name: Anime Generation Catalog

on:
  schedule:
    - cron: "0 */6 * * *"   # every 6 hours (UTC); GHA cron is best-effort
  workflow_dispatch: {}      # manual trigger for testing

permissions:
  contents: write

jobs:
  build:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: tools/anime_generation_pipeline
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: "3.11"

      - name: Install package
        run: pip install -e ".[dev]"

      - name: Run unit tests
        run: pytest -q

      - name: Download anime-lists mapping
        run: curl -fsSL -o anime-list-full.json https://raw.githubusercontent.com/Fribb/anime-lists/master/anime-list-full.json

      - name: Build catalog
        env:
          TMDB_API_KEY: ${{ secrets.TMDB_API_KEY }}
          OUTPUT_PATH: ${{ github.workspace }}/docs/catalog/catalog_anime_generation.json
          ANIME_LISTS_PATH: anime-list-full.json
          MIN_TITLES: "100"
        run: ag-build-catalog

      - name: Publish JSON (commit to gh-pages catalog dir)
        run: |
          cd "${{ github.workspace }}"
          if git diff --quiet -- docs/catalog/catalog_anime_generation.json; then
            echo "No catalog change; skipping commit."
            exit 0
          fi
          git config user.name "ag-pipeline-bot"
          git config user.email "ag-pipeline-bot@users.noreply.github.com"
          git add docs/catalog/catalog_anime_generation.json
          git commit -m "chore(catalog): update Anime Generation catalog [skip ci]"
          git push
```

The published file at `docs/catalog/catalog_anime_generation.json` is served via GitHub Pages (or raw URL) and is the URL the app fetches. `TMDB_API_KEY` is set as a repository secret.

- [ ] **Step 2: Validate the workflow YAML locally**

Run: `python -c "import yaml,sys; yaml.safe_load(open('.github/workflows/anime-generation-catalog.yml'))" && echo OK`
Expected: `OK`

- [ ] **Step 3: Commit**

```bash
git add .github/workflows/anime-generation-catalog.yml
git commit -m "ci(pipeline): add scheduled Anime Generation catalog workflow"
```

---

## Notes for the implementer

- The live network wrappers (`fetch_anime_generation_titles`, `anilist_mal_id_for`, `fetch_crunchyroll_catalog`'s real paging) are intentionally thin and **not** unit-tested — they are exercised by the first CI `workflow_dispatch` run. Before relying on the output, do the empirical checks from spec §11: JustWatch language-field population for the AG provider, and TMDB match-rate on the classic back-catalog. Log counts (titles found, matched-to-CR, missing-TMDB) in `main` so the CI log quantifies coverage — **no silent truncation**.
- If JustWatch blocks the GitHub runner IPs, move the schedule to a self-hosted runner / PC cron (spec §4 "piano B"); the package and CLI are unchanged.
