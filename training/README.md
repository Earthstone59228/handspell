# training

Python side of the ASL hand-shape classifier: the normalizer, its golden
vectors, and (later) exemplar/reference building and MLP training. See
`docs/CLASSIFIER.md` for the full spec.

## Layout

- `handspell/normalize.py` — `normalize()` / `normalize_capture_row()`, spec v1.
- `handspell/golden.py` — builds the golden-vector fixture; `python -m handspell.golden` regenerates it.
- `testdata/normalizer_golden.json` — the cross-language contract (see below).
- `tests/test_normalize.py` — golden reproduction, analytic invariants, freshness check.
- `models/`, `scripts/`, `src/` — MediaPipe model asset and unrelated project scaffolding.

## Run the tests

```
cd training
uv run pytest
uvx ruff check handspell tests
```

## Regenerate the golden vectors

After any change to `handspell/normalize.py` or to the cases in
`handspell/golden.py`:

```
cd training
uv run python -m handspell.golden
uv run pytest
```

`tests/test_normalize.py::test_golden_is_fresh` regenerates the fixture in
memory and diffs it against the committed file byte-for-byte, so an
unregenerated fixture fails the test suite rather than silently drifting.

## The golden file is a cross-language contract

`testdata/normalizer_golden.json` is read by **both** this package's tests
and the Kotlin `NormalizerGoldenTest.kt` on the Android side
(`vision/normalize/DefaultHandNormalizer.kt`), which both assert 1e-6 absolute
agreement against the same cases. Do not edit the JSON by hand or change its
shape without updating both test suites — see `docs/CLASSIFIER.md` §2.
