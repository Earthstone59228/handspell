"""Tests for handspell.hsml, the weight container in docs/CLASSIFIER.md
section 4.

The format's whole job is to fail loudly, so most of these tests corrupt a
valid file one field at a time and check that it is rejected. MlpWeightsTest.kt
rejects the same corruptions from the other side.
"""

from __future__ import annotations

import struct
import zlib
from pathlib import Path

import numpy as np
import pytest
from handspell import hsml
from handspell.normalize import SPEC_VERSION, VECTOR_DIM
from scripts.export_weights import main as export_main


def _model(out_dims: tuple[int, ...] = (5, 3), seed: int = 7) -> tuple[list[hsml.Layer], list[str]]:
    rng = np.random.default_rng(seed)
    layers = []
    in_dim = VECTOR_DIM
    for index, out_dim in enumerate(out_dims):
        layers.append(
            hsml.Layer(
                weights=rng.normal(size=(out_dim, in_dim)).astype(np.float32),
                bias=rng.normal(size=out_dim).astype(np.float32),
                activation=hsml.Activation.IDENTITY if index == len(out_dims) - 1 else hsml.Activation.RELU,
            )
        )
        in_dim = out_dim
    labels = ["A", "B", "C"][:out_dims[-1]]
    return layers, labels


def _corrupt(data: bytes, offset: int, value: bytes) -> bytes:
    """Replaces bytes at `offset` and repairs the CRC, so the test exercises
    the field check rather than the checksum."""
    body = bytearray(data[:-4])
    body[offset : offset + len(value)] = value
    return bytes(body) + struct.pack("<I", zlib.crc32(bytes(body)) & 0xFFFFFFFF)


def test_round_trip_preserves_every_field() -> None:
    layers, labels = _model()
    model = hsml.unpack(hsml.pack(layers, labels))

    assert model.format_version == hsml.FORMAT_VERSION
    assert model.spec_version == SPEC_VERSION
    assert model.input_dim == VECTOR_DIM
    assert model.labels == labels
    assert [layer.out_dim for layer in model.layers] == [layer.out_dim for layer in layers]
    assert [layer.activation for layer in model.layers] == [layer.activation for layer in layers]
    for parsed, original in zip(model.layers, layers):
        np.testing.assert_array_equal(parsed.weights, original.weights)
        np.testing.assert_array_equal(parsed.bias, original.bias)


def test_forward_matches_an_explicit_matrix_walk() -> None:
    layers, labels = _model()
    model = hsml.unpack(hsml.pack(layers, labels))
    x = np.random.default_rng(3).normal(size=VECTOR_DIM)

    values = x.astype(np.float64)
    for layer in layers:
        values = layer.weights.astype(np.float64) @ values + layer.bias.astype(np.float64)
        if layer.activation is hsml.Activation.RELU:
            values = np.maximum(values, 0.0)
    expected = np.exp(values - values.max())
    expected = expected / expected.sum()

    np.testing.assert_allclose(hsml.forward(model, x), expected, atol=1e-12)


def test_forward_accepts_a_batch() -> None:
    layers, labels = _model()
    model = hsml.unpack(hsml.pack(layers, labels))
    batch = np.random.default_rng(4).normal(size=(5, VECTOR_DIM))

    probabilities = hsml.forward(model, batch)

    assert probabilities.shape == (5, len(labels))
    np.testing.assert_allclose(probabilities.sum(axis=1), np.ones(5), atol=1e-12)
    for index in range(5):
        np.testing.assert_allclose(probabilities[index], hsml.forward(model, batch[index]), atol=1e-12)


def test_forward_rejects_the_wrong_input_width() -> None:
    layers, labels = _model()
    model = hsml.unpack(hsml.pack(layers, labels))
    with pytest.raises(ValueError, match="expected"):
        hsml.forward(model, np.zeros(VECTOR_DIM - 1))


def test_bad_magic_is_rejected() -> None:
    data = hsml.pack(*_model())
    with pytest.raises(ValueError, match="magic"):
        hsml.unpack(_corrupt(data, 0, b"HSNL"))


def test_wrong_format_version_is_rejected() -> None:
    data = hsml.pack(*_model())
    with pytest.raises(ValueError, match="format version"):
        hsml.unpack(_corrupt(data, 4, struct.pack("<i", 2)))


def test_wrong_spec_version_is_rejected() -> None:
    data = hsml.pack(*_model())
    with pytest.raises(ValueError, match="spec version"):
        hsml.unpack(_corrupt(data, 8, struct.pack("<i", SPEC_VERSION + 1)))


def test_wrong_input_dim_is_rejected() -> None:
    data = hsml.pack(*_model())
    with pytest.raises(ValueError, match="input dim"):
        hsml.unpack(_corrupt(data, 12, struct.pack("<i", VECTOR_DIM - 1)))


def test_flipped_weight_byte_fails_the_crc() -> None:
    data = bytearray(hsml.pack(*_model()))
    data[40] ^= 0x01  # a single bit inside the first weight matrix
    with pytest.raises(ValueError, match="CRC32"):
        hsml.unpack(bytes(data))


def test_truncated_file_is_rejected() -> None:
    data = hsml.pack(*_model())
    with pytest.raises(ValueError):
        hsml.unpack(data[: len(data) // 2])


def test_relu_final_layer_is_refused_at_pack_time() -> None:
    layers, labels = _model()
    layers[-1] = hsml.Layer(layers[-1].weights, layers[-1].bias, hsml.Activation.RELU)
    with pytest.raises(ValueError, match="final layer must be identity"):
        hsml.pack(layers, labels)


def test_label_count_must_match_the_final_layer() -> None:
    layers, labels = _model()
    with pytest.raises(ValueError, match="labels"):
        hsml.pack(layers, [*labels, "D"])


def test_mismatched_layer_shapes_are_refused_at_pack_time() -> None:
    layers, labels = _model()
    layers[1] = hsml.Layer(
        weights=np.zeros((3, 4), dtype=np.float32),
        bias=np.zeros(3, dtype=np.float32),
        activation=hsml.Activation.IDENTITY,
    )
    with pytest.raises(ValueError, match="previous layer emits"):
        hsml.pack(layers, labels)


def test_write_and_read_a_file(tmp_path: Path) -> None:
    layers, labels = _model()
    path = tmp_path / "mlp-v1.bin"
    hsml.write(path, layers, labels)

    model = hsml.read(path)

    assert model.labels == labels
    assert path.read_bytes() == hsml.pack(layers, labels)


def test_export_weights_script_round_trips_an_npz(tmp_path: Path) -> None:
    layers, labels = _model(out_dims=(6, 3), seed=11)
    npz = tmp_path / "run.npz"
    arrays = {"labels": np.array(labels)}
    for index, layer in enumerate(layers):
        arrays[f"W{index}"] = layer.weights
        arrays[f"b{index}"] = layer.bias
    np.savez(npz, **arrays)
    metadata = tmp_path / "meta.json"
    metadata.write_text('{"signerCount": 3}', encoding="utf-8")
    out = tmp_path / "mlp-v1.bin"

    assert export_main(["--npz", str(npz), "--out", str(out), "--metadata", str(metadata)]) == 0

    model = hsml.read(out)
    assert model.labels == labels
    np.testing.assert_array_equal(model.layers[0].weights, layers[0].weights)
    sidecar = out.with_suffix(".json")
    assert '"signerCount": 3' in sidecar.read_text(encoding="utf-8")
    assert '"sha256"' in sidecar.read_text(encoding="utf-8")
