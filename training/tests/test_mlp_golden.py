"""Tests for the MLP golden fixture that pins the Kotlin forward pass to the
numpy one.

MlpGoldenTest.kt reads the same two files out of test resources and asserts
1e-5 agreement; these tests assert the fixture is internally consistent and,
crucially, still fresh -- a change to hsml.py or mlp_golden.py that nobody
regenerated would otherwise leave the Kotlin side chasing stale numbers.
"""

from __future__ import annotations

import json
from pathlib import Path

import numpy as np
from handspell import hsml
from handspell.mlp_golden import build_model, golden_bytes, golden_json_text
from handspell.normalize import SPEC_VERSION, VECTOR_DIM
from handspell.references import STATIC_LETTERS

_TESTDATA = Path(__file__).resolve().parent.parent / "testdata"
_BIN_PATH = _TESTDATA / "mlp_golden.bin"
_JSON_PATH = _TESTDATA / "mlp_golden.json"


def _document() -> dict:
    return json.loads(_JSON_PATH.read_text(encoding="utf-8"))


def test_golden_is_fresh() -> None:
    """Regenerating both fixtures in memory must equal the committed files. If
    this fails, the exporter or the forward pass changed behaviour and nobody
    ran `python -m handspell.mlp_golden`."""
    assert golden_bytes() == _BIN_PATH.read_bytes()
    assert golden_json_text() == _JSON_PATH.read_text(encoding="utf-8")


def test_golden_bin_parses_and_matches_the_json_header() -> None:
    model = hsml.read(_BIN_PATH)
    document = _document()

    assert model.spec_version == SPEC_VERSION == document["specVersion"]
    assert model.input_dim == VECTOR_DIM == document["inputDim"]
    assert model.labels == document["labels"]
    assert [layer.out_dim for layer in model.layers] == [layer["outDim"] for layer in document["layers"]]
    assert [layer.activation.name.lower() for layer in model.layers] == [
        layer["activation"] for layer in document["layers"]
    ]


def test_labels_are_static_letters() -> None:
    for label in _document()["labels"]:
        assert label in STATIC_LETTERS


def test_expected_outputs_reproduce() -> None:
    model = hsml.read(_BIN_PATH)
    for case in _document()["cases"]:
        vector = np.array(case["input"], dtype=np.float64)
        assert vector.shape == (VECTOR_DIM,)
        probabilities = hsml.forward(model, vector)
        np.testing.assert_allclose(probabilities, np.array(case["expected"]), atol=1e-8)


def test_cases_are_distinct_and_discriminating() -> None:
    """A fixture where every class sat near 0.25 would pass even if the reader
    dropped a layer, so the generator is held to producing peaked outputs with
    more than one winner across the cases."""
    document = _document()
    assert len(document["cases"]) == 3
    winners = set()
    for case in document["cases"]:
        expected = np.array(case["expected"])
        np.testing.assert_allclose(expected.sum(), 1.0, atol=1e-6)
        assert expected.max() > 0.4
        winners.add(int(expected.argmax()))
    assert len(winners) > 1


def test_model_shape_is_the_documented_toy_net() -> None:
    model = build_model()
    assert [layer.in_dim for layer in model.layers] == [66, 8, 4]
    assert [layer.out_dim for layer in model.layers] == [8, 4, 4]
