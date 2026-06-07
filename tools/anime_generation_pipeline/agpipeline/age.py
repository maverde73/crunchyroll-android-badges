from __future__ import annotations

import re

# Map an age label from any source (JustWatch IT, TMDB IT/US, MAL/Jikan, Kitsu)
# to a single minimum-age band: "T" (all ages) or "<n>+".
# Italian system bands: T, 6+, 12+, 14+, 16+, 18+.
_EXACT = {
    # all ages
    "T": "T", "TUTTI": "T", "U": "T", "ALL": "T",
    "G": "T", "TV-Y": "T", "TV-G": "T", "TV-PG": "T", "PG": "T",
    # 7
    "TV-Y7": "7+",
    # 12 (teen)
    "PG-13": "12+", "VM12": "12+",
    # 14
    "TV-14": "14+", "VM14": "14+",
    # 16 (older teen / R-17+)
    "VM16": "16+", "R": "16+",
    # 18 (adult)
    "TV-MA": "18+", "NC-17": "18+", "R18": "18+", "R18+": "18+", "VM18": "18+",
    # no rating -> empty
    "NR": "", "UNRATED": "", "": "",
}


def normalize_age(label: str | None) -> str:
    """Return a minimum-age band ('T' or '<n>+') for any source label, or '' if unknown."""
    if not label:
        return ""
    s = label.strip().upper()
    if s in _EXACT:
        return _EXACT[s]
    # MAL/Jikan long labels, e.g. "PG-13 - Teens 13 or older", "R - 17+ (violence...)"
    if s.startswith("PG-13"):
        return "12+"
    if s.startswith("R+") or s.startswith("R - 17") or s.startswith("R-17"):
        return "16+"
    if s.startswith("RX"):
        return "18+"
    if s.startswith("PG"):
        return "T"
    if s.startswith("G "):
        return "T"
    # Italian "VM<n>" / "VM-<n>"
    if s.startswith("VM"):
        m = re.search(r"(\d+)", s)
        return f"{m.group(1)}+" if m else ""
    # Bare numbers like "14", "18", "0"
    m = re.match(r"^(\d{1,2})\+?$", s)
    if m:
        n = int(m.group(1))
        return "T" if n == 0 else f"{n}+"
    return ""
