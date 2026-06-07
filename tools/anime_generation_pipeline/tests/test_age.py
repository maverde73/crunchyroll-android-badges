from agpipeline.age import normalize_age


def test_justwatch_italian_labels():
    assert normalize_age("VM14") == "14+"
    assert normalize_age("VM18") == "18+"
    assert normalize_age("VM12") == "12+"
    assert normalize_age("T") == "T"


def test_mal_jikan_labels():
    assert normalize_age("PG-13 - Teens 13 or older") == "12+"
    assert normalize_age("R - 17+ (violence & profanity)") == "16+"
    assert normalize_age("R+ - Mild Nudity") == "16+"
    assert normalize_age("Rx - Hentai") == "18+"
    assert normalize_age("G - All Ages") == "T"


def test_tmdb_us_labels():
    assert normalize_age("TV-14") == "14+"
    assert normalize_age("TV-MA") == "18+"
    assert normalize_age("TV-G") == "T"
    assert normalize_age("PG-13") == "12+"
    assert normalize_age("NC-17") == "18+"


def test_kitsu_labels():
    assert normalize_age("R") == "16+"
    assert normalize_age("R18") == "18+"
    assert normalize_age("PG") == "T"


def test_bare_numbers_and_unknown():
    assert normalize_age("18") == "18+"
    assert normalize_age("0") == "T"
    assert normalize_age("") == ""
    assert normalize_age("NR") == ""
    assert normalize_age(None) == ""
