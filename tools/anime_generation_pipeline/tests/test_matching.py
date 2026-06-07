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
