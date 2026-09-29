"""Guard the leave-one-letter-out split and all-target false-accept denominator."""

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from evaluate_live_gate import STATIC, Sample, Score
from sweep_knn_adaptation import assess, mean_shift


def sample(letter, value):
    vector = np.zeros(66, np.float32)
    vector[0] = value
    return Sample(letter, vector)


def test_mean_shift_never_uses_held_letter_live_samples():
    photo = [sample(c, 0) for c in "ABCD"]
    live = [sample("A", 100), sample("B", 1), sample("C", 2), sample("D", 3)]
    shift = mean_shift(photo, live, "A")
    assert shift[0] == 2
    live[0] = sample("A", -100)
    assert np.array_equal(shift, mean_shift(photo, live, "A"))


def test_false_accept_checks_all_static_targets():
    probabilities = np.zeros(len(STATIC), np.float32)
    probabilities[STATIC.index("L")] = 1
    score = Score(STATIC, probabilities)
    result = assess([{"actual": c, "score": score} for c in "ABCD"], "p/m only", None, 0.85, 0.20)
    assert result["true"] == 0
    assert result["worst_letter"] == "L"
    assert result["worst"] == 1
    assert np.isclose(result["false"], 1 / 23)
