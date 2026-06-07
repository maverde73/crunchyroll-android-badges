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
