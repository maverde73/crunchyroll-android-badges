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
