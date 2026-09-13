"""Builds the stage-1 reference set from capture CSVs.

    uv run python scripts/build_references.py \
        --captures ~/captures \
        --out ../android/app/src/main/assets/classifier/references-v1.csv

`--captures` is a directory laid out as `<signer_id>/<letter>_<session>.csv`,
which is what the in-app capture screen writes to external files storage
(docs/CLASSIFIER.md section 7); pull it off the device with `adb pull` first.

The output is the only file the k-NN classifier reads, and it is generated,
never hand-edited -- see android/app/src/main/assets/classifier/README.md.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from handspell.references import (
    DEDUPE_DISTANCE,
    MAX_EXEMPLARS_PER_LETTER,
    STATIC_LETTERS,
    select_exemplars,
    write_reference_csv,
)

_DEFAULT_OUT = (
    Path(__file__).resolve().parent.parent.parent
    / "android"
    / "app"
    / "src"
    / "main"
    / "assets"
    / "classifier"
    / "references-v1.csv"
)


def _parse_args(argv: list[str] | None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--captures", type=Path, required=True, help="directory of capture CSVs")
    parser.add_argument("--out", type=Path, default=_DEFAULT_OUT, help=f"output CSV (default: {_DEFAULT_OUT})")
    parser.add_argument(
        "--dedupe-distance",
        type=float,
        default=DEDUPE_DISTANCE,
        help=f"minimum distance between kept exemplars (default: {DEDUPE_DISTANCE})",
    )
    parser.add_argument(
        "--max-per-letter",
        type=int,
        default=MAX_EXEMPLARS_PER_LETTER,
        help=f"cap per letter (default: {MAX_EXEMPLARS_PER_LETTER})",
    )
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = _parse_args(argv)
    if not args.captures.is_dir():
        print(f"error: {args.captures} is not a directory", file=sys.stderr)
        return 2

    exemplars, stats = select_exemplars(
        args.captures,
        dedupe_distance=args.dedupe_distance,
        max_per_letter=args.max_per_letter,
    )
    if not exemplars:
        print(f"error: no usable capture rows under {args.captures}", file=sys.stderr)
        return 1

    write_reference_csv(args.out, exemplars)

    print(f"read {stats.rows} rows from {stats.files} capture files")
    print(
        f"dropped {stats.degenerate} degenerate, {stats.near_duplicate} near-duplicate, "
        f"{stats.over_cap} over-cap"
    )
    print(f"wrote {stats.kept} exemplars to {args.out}")
    for letter in STATIC_LETTERS:
        count = stats.kept_per_letter.get(letter, 0)
        marker = "  <- no exemplars" if count == 0 else ""
        print(f"  {letter}: {count}{marker}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
