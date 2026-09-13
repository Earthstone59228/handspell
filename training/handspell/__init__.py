"""Hand-landmark normalizer for the Shipaton ASL classifier.

See docs/CLASSIFIER.md sections 1-2 for the normative spec. This package is
one of two implementations of that spec (the other is
vision/normalize/DefaultHandNormalizer.kt in the Android app); both are held
identical by the golden vectors in training/testdata/normalizer_golden.json.
"""

from handspell.normalize import (
    ORIENTATION_DIM,
    SHAPE_DIM,
    SPEC_VERSION,
    VECTOR_DIM,
    normalize,
    normalize_capture_row,
)

__all__ = [
    "ORIENTATION_DIM",
    "SHAPE_DIM",
    "SPEC_VERSION",
    "VECTOR_DIM",
    "normalize",
    "normalize_capture_row",
]
