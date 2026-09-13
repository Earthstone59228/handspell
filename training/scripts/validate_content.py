#!/usr/bin/env python3
"""Validate the shipped content packs against the schema and project rules.

Run with:
    cd training && uv run --with jsonschema python scripts/validate_content.py

Checks:
  - index.json and every referenced pack parse as JSON and validate against
    docs/schemas/content-pack.schema.json.
  - Every drill/speed "letter" is one of the 24 static ASL letters (no J, no Z).
  - Every drill "hintId" (when not null) has a matching entry in
    android/app/src/main/res/values/strings_content.xml.
  - Every storyStep spell step's "letters" array spells its "spellWord" exactly.
  - Every drill "description" is at most 160 characters.
  - No string value anywhere in the index, the packs, or strings_content.xml
    contains a banned placeholder/AI-slop word or phrase (case-insensitive).

Exits non-zero if any check fails, so this can gate CI.
"""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from xml.etree import ElementTree

from jsonschema import Draft202012Validator

REPO_ROOT = Path(__file__).resolve().parents[2]
ASSETS_DIR = REPO_ROOT / "android/app/src/main/assets/content"
INDEX_PATH = ASSETS_DIR / "index.json"
SCHEMA_PATH = REPO_ROOT / "docs/schemas/content-pack.schema.json"
STRINGS_PATH = REPO_ROOT / "android/app/src/main/res/values/strings_content.xml"

STATIC_LETTERS = {chr(c) for c in range(ord("A"), ord("Z") + 1)} - {"J", "Z"}
assert len(STATIC_LETTERS) == 24

MAX_DESCRIPTION_LEN = 160

BANNED_WORDS = [
    "todo",
    "fixme",
    "placeholder",
    "coming soon",
    "lorem",
    "seamless",
    "unlock the power",
    "elevate",
    "game-chang",
    "at your fingertips",
    "revolution",
    "changeme",
    "your company",
    "example.com",
    "in today's",
]
BANNED_RE = re.compile("|".join(re.escape(w) for w in BANNED_WORDS), re.IGNORECASE)


class ValidationErrors(list):
    def add(self, message: str) -> None:
        self.append(message)


def load_json(path: Path, errors: ValidationErrors) -> dict | None:
    try:
        return json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as exc:
        errors.add(f"{path}: could not parse JSON ({exc})")
        return None


def collect_strings(value, out: list[str]) -> None:
    """Recursively collect every string value in a JSON-like structure."""
    if isinstance(value, str):
        out.append(value)
    elif isinstance(value, dict):
        for v in value.values():
            collect_strings(v, out)
    elif isinstance(value, list):
        for v in value:
            collect_strings(v, out)


def load_hint_ids(errors: ValidationErrors) -> set[str]:
    if not STRINGS_PATH.exists():
        errors.add(f"{STRINGS_PATH}: file does not exist")
        return set()
    try:
        tree = ElementTree.parse(STRINGS_PATH)
    except ElementTree.ParseError as exc:
        errors.add(f"{STRINGS_PATH}: could not parse XML ({exc})")
        return set()

    hint_ids: set[str] = set()
    for elem in tree.getroot().findall("string"):
        name = elem.get("name")
        if name:
            hint_ids.add(name)
        text = "".join(elem.itertext())
        if len(text) > 70:
            errors.add(f"{STRINGS_PATH}: string '{name}' is {len(text)} chars, over the 70 limit")
        if text.strip().endswith("!"):
            errors.add(f"{STRINGS_PATH}: string '{name}' contains an exclamation mark")
    return hint_ids


def check_banned_words(label: str, strings: list[str], errors: ValidationErrors) -> None:
    for s in strings:
        m = BANNED_RE.search(s)
        if m:
            errors.add(f"{label}: banned word/phrase '{m.group(0)}' found in string: {s!r}")


def validate_pack_schema(pack_path: Path, pack: dict, schema: dict, errors: ValidationErrors) -> None:
    validator = Draft202012Validator(schema)
    for err in sorted(validator.iter_errors(pack), key=str):
        errors.add(f"{pack_path}: schema violation: {err.message} (path: {list(err.path)})")


def validate_pack_rules(pack_path: Path, pack: dict, hint_ids: set[str], errors: ValidationErrors) -> None:
    kind = pack.get("kind")
    items = pack.get("items", [])

    for item in items:
        item_type = item.get("type")

        if item_type == "drill":
            letter = item.get("letter")
            if letter not in STATIC_LETTERS:
                errors.add(f"{pack_path}: drill '{item.get('id')}' has non-static letter {letter!r}")

            description = item.get("description", "")
            if len(description) > MAX_DESCRIPTION_LEN:
                errors.add(
                    f"{pack_path}: drill '{item.get('id')}' description is "
                    f"{len(description)} chars, over the {MAX_DESCRIPTION_LEN} limit"
                )

            hint_id = item.get("hintId")
            if hint_id is not None and hint_id not in hint_ids:
                errors.add(
                    f"{pack_path}: drill '{item.get('id')}' references unknown hintId {hint_id!r}"
                )

        elif item_type == "storyStep":
            spell_word = item.get("spellWord")
            letters = item.get("letters") or []
            if spell_word is not None:
                expected = list(spell_word.upper())
                if letters != expected:
                    errors.add(
                        f"{pack_path}: storyStep '{item.get('id')}' letters {letters} "
                        f"do not spell spellWord {spell_word!r} (expected {expected})"
                    )
                for letter in letters:
                    if letter not in STATIC_LETTERS:
                        errors.add(
                            f"{pack_path}: storyStep '{item.get('id')}' has non-static letter {letter!r}"
                        )

        elif item_type == "speedRound":
            for letter in item.get("letters", []):
                if letter not in STATIC_LETTERS:
                    errors.add(
                        f"{pack_path}: speedRound '{item.get('id')}' has non-static letter {letter!r}"
                    )

        elif kind is not None:
            errors.add(f"{pack_path}: item '{item.get('id')}' has unexpected type {item_type!r}")


def main() -> int:
    errors = ValidationErrors()

    schema = load_json(SCHEMA_PATH, errors)
    index = load_json(INDEX_PATH, errors)
    hint_ids = load_hint_ids(errors)

    if schema is None or index is None:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        return 1

    # Banned words in index.json and strings_content.xml.
    index_strings: list[str] = []
    collect_strings(index, index_strings)
    check_banned_words(str(INDEX_PATH), index_strings, errors)

    if STRINGS_PATH.exists():
        try:
            tree = ElementTree.parse(STRINGS_PATH)
            xml_strings = ["".join(elem.itertext()) for elem in tree.getroot().findall("string")]
            check_banned_words(str(STRINGS_PATH), xml_strings, errors)
        except ElementTree.ParseError:
            pass  # already reported above

    packs = index.get("packs", [])
    if not packs:
        errors.add(f"{INDEX_PATH}: no packs listed")

    seen_pack_ids = set()
    for ref in packs:
        pack_id = ref.get("packId")
        rel_path = ref.get("path")
        if not rel_path:
            errors.add(f"{INDEX_PATH}: pack ref {ref} missing 'path'")
            continue

        pack_path = REPO_ROOT / "android/app/src/main/assets" / rel_path
        if not pack_path.exists():
            errors.add(f"{INDEX_PATH}: referenced pack does not exist: {pack_path}")
            continue

        pack = load_json(pack_path, errors)
        if pack is None:
            continue

        if pack.get("packId") != pack_id:
            errors.add(
                f"{pack_path}: packId {pack.get('packId')!r} does not match index entry {pack_id!r}"
            )
        if pack.get("tier") != ref.get("tier"):
            errors.add(f"{pack_path}: tier does not match index entry")
        if pack.get("kind") != ref.get("kind"):
            errors.add(f"{pack_path}: kind does not match index entry")

        seen_pack_ids.add(pack_id)

        validate_pack_schema(pack_path, pack, schema, errors)
        validate_pack_rules(pack_path, pack, hint_ids, errors)

        pack_strings: list[str] = []
        collect_strings(pack, pack_strings)
        check_banned_words(str(pack_path), pack_strings, errors)

    # Also validate every pack file on disk is actually listed in the index,
    # so a forgotten or orphaned file is caught.
    packs_dir = ASSETS_DIR / "packs"
    if packs_dir.exists():
        for path in sorted(packs_dir.glob("*.json")):
            pack = load_json(path, errors)
            if pack is not None and pack.get("packId") not in seen_pack_ids:
                errors.add(f"{path}: pack exists on disk but is not listed in {INDEX_PATH}")

    if errors:
        for e in errors:
            print(f"ERROR: {e}", file=sys.stderr)
        print(f"\n{len(errors)} error(s) found.", file=sys.stderr)
        return 1

    print(f"OK: index + {len(seen_pack_ids)} pack(s) validated, {len(hint_ids)} hint string(s) checked.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
