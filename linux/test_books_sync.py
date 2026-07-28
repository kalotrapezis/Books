#!/usr/bin/env python3
"""Checks for books-sync: the merge rule, the names, and a round trip on disk.

Run it with the helper beside it:  python3 linux/test_books_sync.py
"""

import importlib.machinery
import importlib.util
import json
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_loader(
    "books_sync",
    importlib.machinery.SourceFileLoader("books_sync", str(HERE / "books-sync")),
)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)


def annotation(value, modified, note=""):
    return {"value": value, "color": "yellow", "text": "t", "note": note,
            "created": "2026-01-01T00:00:00Z", "modified": modified}


def test_folder_names_match_the_app():
    assert sync.folder_name("The Brothers Karamazov") == "The Brothers Karamazov"
    assert sync.folder_name("Notes on C/C++") == "Notes on C C++"
    assert sync.folder_name("  Whose?   Book  ") == "Whose Book"
    assert sync.folder_name(".hidden.") == "hidden"
    assert sync.folder_name("   ") == "Untitled book"
    assert sync.folder_name("///") == "Untitled book"
    assert sync.folder_name("π" * 200) == "π" * 80
    assert sync.folder_name("Καινή Διαθήκη") == "Καινή Διαθήκη"
    # A cut that lands on a space must not leave the folder ending in one.
    assert not sync.folder_name("word " * 40).endswith(" ")


def test_the_newer_edit_wins_either_way():
    older = {"annotations": [annotation("cfi1", "2026-01-01T00:00:00Z", "old")]}
    newer = {"annotations": [annotation("cfi1", "2026-02-01T00:00:00Z", "new")]}
    assert sync.merge(older, newer)["annotations"][0]["note"] == "new"
    assert sync.merge(newer, older)["annotations"][0]["note"] == "new"


def test_both_sides_keep_what_only_they_have():
    left = {"annotations": [annotation("a", "2026-01-01T00:00:00Z")]}
    right = {"annotations": [annotation("b", "2026-01-01T00:00:00Z")]}
    values = {a["value"] for a in sync.merge(left, right)["annotations"]}
    assert values == {"a", "b"}


def test_a_deletion_is_not_undone_by_the_other_side():
    deleted_here = {"annotations": [], sync.TOMBSTONES: {"a": "2026-02-01T00:00:00Z"}}
    still_there = {"annotations": [annotation("a", "2026-01-01T00:00:00Z")]}
    merged = sync.merge(deleted_here, still_there)
    assert merged["annotations"] == []
    # The tombstone outlives the merge, or the next sync brings it back again.
    assert merged[sync.TOMBSTONES] == {"a": "2026-02-01T00:00:00Z"}


def test_an_edit_after_the_deletion_wins():
    deleted_here = {"annotations": [], sync.TOMBSTONES: {"a": "2026-02-01T00:00:00Z"}}
    edited_later = {"annotations": [annotation("a", "2026-03-01T00:00:00Z", "back")]}
    merged = sync.merge(deleted_here, edited_later)
    assert [a["value"] for a in merged["annotations"]] == ["a"]


def test_bookmarks_are_a_union_without_repeats():
    left = {"bookmarks": ["epubcfi(/6/4)", "epubcfi(/6/8)"]}
    right = {"bookmarks": ["epubcfi(/6/8)", "epubcfi(/6/12)"]}
    assert sync.merge(left, right)["bookmarks"] == [
        "epubcfi(/6/8)", "epubcfi(/6/12)", "epubcfi(/6/4)",
    ]


def test_unknown_fields_survive():
    left = {"annotations": [], "progress": [12, 900], "lastLocation": "epubcfi(/6/4)"}
    right = {"annotations": [], "somethingFoliateAdds": {"x": 1}}
    merged = sync.merge(left, right)
    assert merged["progress"] == [12, 900]
    assert merged["lastLocation"] == "epubcfi(/6/4)"
    assert merged["somethingFoliateAdds"] == {"x": 1}


def test_a_round_trip_over_real_files():
    with tempfile.TemporaryDirectory() as workspace:
        root = Path(workspace) / "synced"
        data = Path(workspace) / "foliate"
        root.mkdir()
        data.mkdir()

        # Foliate's side: a book with one highlight.
        book = {
            "metadata": {"identifier": "urn:uuid:1", "title": "The Brothers Karamazov"},
            "annotations": [annotation("cfi1", "2026-01-01T00:00:00Z", "from the desktop")],
            "lastLocation": "epubcfi(/6/4)",
        }
        (data / "urn%3Auuid%3A1.json").write_text(json.dumps(book), encoding="utf-8")

        assert sync.sync_once(root, data) == 1
        landed = root / "The Brothers Karamazov" / sync.SYNC_FILE
        assert landed.exists(), "the book's folder is named after the book"
        assert json.loads(landed.read_text())["annotations"][0]["note"] == "from the desktop"

        # Nothing changed since: a second run writes nothing.
        assert sync.sync_once(root, data) == 0

        # The phone's side edits the note; it reaches Foliate.
        phone = json.loads(landed.read_text())
        phone["annotations"][0]["note"] = "from the phone"
        phone["annotations"][0]["modified"] = "2026-03-01T00:00:00Z"
        landed.write_text(json.dumps(phone), encoding="utf-8")

        assert sync.sync_once(root, data) == 1
        back = json.loads((data / "urn%3Auuid%3A1.json").read_text())
        assert back["annotations"][0]["note"] == "from the phone"
        assert back["lastLocation"] == "epubcfi(/6/4)", "Foliate's own fields survive"
        # And a copy was kept before the overwrite.
        assert list((data / ".books-backups").glob("*.json")), "backup before writing"


def main():
    tests = [v for k, v in sorted(globals().items()) if k.startswith("test_")]
    for test in tests:
        test()
        print(f"ok  {test.__name__}")
    print(f"\n{len(tests)} passed")


if __name__ == "__main__":
    sys.exit(main())
