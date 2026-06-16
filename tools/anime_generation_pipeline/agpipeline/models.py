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
    maturity_rating: str = ""
    episode_count: int = 0

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
            maturity_rating=d.get("maturity_rating", ""),
            episode_count=int(d.get("episode_count") or 0),
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
