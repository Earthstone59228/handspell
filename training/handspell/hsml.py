"""HSML: the flat little-endian weight container defined in
docs/CLASSIFIER.md section 4, written here and parsed by
`android/app/src/main/java/dev/handspell/app/vision/classify/MlpWeights.kt`.

Layout, little-endian throughout:

    magic       4 bytes  b"HSML"
    int32       format version (1)
    int32       normalisation spec version (must match normalize.SPEC_VERSION)
    int32       input dim (must match normalize.VECTOR_DIM)
    int32       layer count
    int32[2]    per layer: out dim, activation (0 = ReLU, 1 = identity)
    float32[]   per layer: W row-major [out][in], then b[out]
    int32       label count
    ...         per label: int32 byte length, then UTF-8 bytes
    uint32      CRC32 of every byte before this field

Two implementations of one format is a cost, paid for by what it buys: no
serialisation runtime on the phone, a file whose every field can be read in a
hex dump, and a CRC that turns a half-written or truncated export into a
typed load error instead of a model that scores plausibly and wrongly.

`forward()` here is pure numpy and exists so the export can be checked
end to end in Python; the Kotlin forward pass is held to it by
training/testdata/mlp_golden.{bin,json}.
"""

from __future__ import annotations

import struct
import zlib
from dataclasses import dataclass, field
from enum import IntEnum
from pathlib import Path

import numpy as np

from handspell.normalize import SPEC_VERSION, VECTOR_DIM

MAGIC = b"HSML"
FORMAT_VERSION = 1

_INT = struct.Struct("<i")
_UINT = struct.Struct("<I")


class Activation(IntEnum):
    """Per-layer non-linearity, encoded as the int32 in the layer header."""

    RELU = 0
    IDENTITY = 1


@dataclass(frozen=True)
class Layer:
    """One dense layer: `y = W @ x + b`, then `activation`."""

    weights: np.ndarray  # (out_dim, in_dim)
    bias: np.ndarray  # (out_dim,)
    activation: Activation = Activation.RELU

    @property
    def in_dim(self) -> int:
        return int(self.weights.shape[1])

    @property
    def out_dim(self) -> int:
        return int(self.weights.shape[0])


@dataclass(frozen=True)
class Model:
    """A parsed (or about-to-be-written) HSML file."""

    layers: list[Layer]
    labels: list[str]
    input_dim: int = VECTOR_DIM
    spec_version: int = SPEC_VERSION
    format_version: int = FORMAT_VERSION
    crc32: int = field(default=0, compare=False)


def _validate(layers: list[Layer], labels: list[str], input_dim: int) -> None:
    if not layers:
        raise ValueError("a model needs at least one layer")
    if not labels:
        raise ValueError("a model needs at least one label")
    expected_in = input_dim
    for index, layer in enumerate(layers):
        if layer.weights.ndim != 2:
            raise ValueError(f"layer {index}: weights must be 2-d, got shape {layer.weights.shape}")
        if layer.bias.shape != (layer.out_dim,):
            raise ValueError(
                f"layer {index}: bias has shape {layer.bias.shape}, expected ({layer.out_dim},)"
            )
        if layer.in_dim != expected_in:
            raise ValueError(
                f"layer {index}: expects {layer.in_dim} inputs, previous layer emits {expected_in}"
            )
        expected_in = layer.out_dim
    if layers[-1].activation is not Activation.IDENTITY:
        raise ValueError(
            "the final layer must be identity: softmax is applied by the reader, so a ReLU here "
            "would clamp every negative logit to the same value"
        )
    if expected_in != len(labels):
        raise ValueError(f"final layer emits {expected_in} classes but {len(labels)} labels were given")
    for label in labels:
        if not label or not label.strip():
            raise ValueError("labels must be non-empty")


def pack(
    layers: list[Layer],
    labels: list[str],
    input_dim: int = VECTOR_DIM,
    spec_version: int = SPEC_VERSION,
    format_version: int = FORMAT_VERSION,
) -> bytes:
    """Serialises a model to HSML bytes, CRC included."""
    _validate(layers, labels, input_dim)

    body = bytearray()
    body += MAGIC
    body += _INT.pack(format_version)
    body += _INT.pack(spec_version)
    body += _INT.pack(input_dim)
    body += _INT.pack(len(layers))
    for layer in layers:
        body += _INT.pack(layer.out_dim)
        body += _INT.pack(int(layer.activation))
    for layer in layers:
        body += np.ascontiguousarray(layer.weights, dtype="<f4").tobytes()
        body += np.ascontiguousarray(layer.bias, dtype="<f4").tobytes()
    body += _INT.pack(len(labels))
    for label in labels:
        encoded = label.encode("utf-8")
        body += _INT.pack(len(encoded))
        body += encoded
    return bytes(body) + _UINT.pack(zlib.crc32(bytes(body)) & 0xFFFFFFFF)


def unpack(data: bytes) -> Model:
    """Parses HSML bytes, raising ValueError for exactly the conditions
    MlpWeights.kt rejects: bad magic, wrong format or spec version, wrong
    input dim, truncation, or a CRC that does not match.
    """
    header_size = len(MAGIC) + 4 * 4
    if len(data) < header_size + 4:
        raise ValueError(f"file is {len(data)} bytes, too short to hold a header and a checksum")
    if data[: len(MAGIC)] != MAGIC:
        raise ValueError(f"magic is {data[: len(MAGIC)]!r}, expected {MAGIC!r}")

    stored_crc = _UINT.unpack_from(data, len(data) - 4)[0]
    computed_crc = zlib.crc32(data[: len(data) - 4]) & 0xFFFFFFFF
    if stored_crc != computed_crc:
        raise ValueError(f"CRC32 mismatch: file says {stored_crc}, bytes give {computed_crc}")

    offset = len(MAGIC)

    def read_int() -> int:
        nonlocal offset
        if offset + 4 > len(data) - 4:
            raise ValueError("file ends mid-field")
        value = _INT.unpack_from(data, offset)[0]
        offset += 4
        return value

    format_version = read_int()
    if format_version != FORMAT_VERSION:
        raise ValueError(f"format version is {format_version}, this reader handles {FORMAT_VERSION}")
    spec_version = read_int()
    if spec_version != SPEC_VERSION:
        raise ValueError(f"spec version is {spec_version}, this build normalises to {SPEC_VERSION}")
    input_dim = read_int()
    if input_dim != VECTOR_DIM:
        raise ValueError(f"input dim is {input_dim}, this build normalises to {VECTOR_DIM}")
    layer_count = read_int()
    if layer_count < 1:
        raise ValueError(f"layer count is {layer_count}, expected at least 1")

    headers = []
    for index in range(layer_count):
        out_dim = read_int()
        if out_dim < 1:
            raise ValueError(f"layer {index}: out dim is {out_dim}, expected at least 1")
        activation_code = read_int()
        try:
            activation = Activation(activation_code)
        except ValueError as error:
            raise ValueError(f"layer {index}: unknown activation code {activation_code}") from error
        headers.append((out_dim, activation))

    layers: list[Layer] = []
    in_dim = input_dim
    for index, (out_dim, activation) in enumerate(headers):
        weight_count = in_dim * out_dim
        need = (weight_count + out_dim) * 4
        if offset + need > len(data) - 4:
            raise ValueError(f"layer {index}: file ends mid-field")
        weights = np.frombuffer(data, dtype="<f4", count=weight_count, offset=offset).reshape(out_dim, in_dim)
        offset += weight_count * 4
        bias = np.frombuffer(data, dtype="<f4", count=out_dim, offset=offset)
        offset += out_dim * 4
        layers.append(Layer(weights=np.array(weights), bias=np.array(bias), activation=activation))
        in_dim = out_dim

    label_count = read_int()
    if label_count != layers[-1].out_dim:
        raise ValueError(
            f"label count {label_count} does not match final layer out dim {layers[-1].out_dim}"
        )
    labels = []
    for index in range(label_count):
        length = read_int()
        if length < 1 or offset + length > len(data) - 4:
            raise ValueError(f"label {index}: declares {length} bytes, which does not fit the file")
        labels.append(data[offset : offset + length].decode("utf-8"))
        offset += length

    if offset != len(data) - 4:
        raise ValueError(f"{len(data) - 4 - offset} unexpected bytes between the labels and the CRC")

    return Model(
        layers=layers,
        labels=labels,
        input_dim=input_dim,
        spec_version=spec_version,
        format_version=format_version,
        crc32=stored_crc,
    )


def write(path: Path, layers: list[Layer], labels: list[str], **kwargs) -> bytes:
    """Packs and writes a model, returning the bytes written."""
    data = pack(layers, labels, **kwargs)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return data


def read(path: Path) -> Model:
    return unpack(path.read_bytes())


def forward(model: Model, x: np.ndarray) -> np.ndarray:
    """Runs the model over one vector `(input_dim,)` or a batch
    `(n, input_dim)` and returns softmax probabilities of the same rank.

    Arithmetic is float64 over float32 weights, matching what
    MlpLetterClassifier.kt does with Double accumulators, so the two agree far
    inside the 1e-5 the golden test asserts.
    """
    values = np.asarray(x, dtype=np.float64)
    single = values.ndim == 1
    if single:
        values = values[None, :]
    if values.ndim != 2 or values.shape[1] != model.input_dim:
        raise ValueError(f"expected (n, {model.input_dim}) input, got shape {np.shape(x)}")

    for layer in model.layers:
        values = values @ np.asarray(layer.weights, dtype=np.float64).T + np.asarray(
            layer.bias, dtype=np.float64
        )
        if layer.activation is Activation.RELU:
            values = np.maximum(values, 0.0)

    shifted = values - values.max(axis=1, keepdims=True)
    exponentiated = np.exp(shifted)
    probabilities = exponentiated / exponentiated.sum(axis=1, keepdims=True)
    return probabilities[0] if single else probabilities
