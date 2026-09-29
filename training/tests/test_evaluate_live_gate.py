"""Checks the saved-vector evaluator against the classifier and feedback rules."""

import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
from evaluate_live_gate import Mlp, Sample, Score, evaluate, gate, held_out, knn, read_csv


def vector(x=0.0, orientation=0.0):
    out = np.zeros(66, dtype=np.float32)
    out[0] = x
    out[60] = orientation
    return out


def test_knn_weighted_distance_votes_and_stable_ties():
    refs = [Sample("B", vector(0)), Sample("A", vector(1)), Sample("A", vector(2))]
    result = knn(refs, vector(0), k=3)
    assert result.top1 == "B"  # 1/(0+.01) outweighs both A votes.
    assert result.nearest_distance == 0
    assert np.isclose(result.probability("B"), 100 / (100 + 1 / 1.01 + 1 / 2.01))
    assert knn([Sample("B", vector()), Sample("A", vector())], vector(), k=1).top1 == "B"
    assert np.isclose(knn([Sample("A", vector(0, 1))], vector(), k=1).nearest_distance, np.sqrt(0.5))


def test_gate_uses_target_probability_margin_and_shape_distance_for_abcd():
    score = Score(("A", "E"), np.asarray([0.90, 0.10], np.float32), nearest_distance=0.60, nearest_shape_distance=0.20)
    assert gate(score, "A")  # A ignores orientation in the distance gate.
    assert not gate(Score(("A", "E"), score.probabilities, 0.60, 0.20), "E")
    assert not gate(Score(("A", "E"), np.asarray([0.84, 0.16], np.float32), 0.1, 0.1), "A")
    assert not gate(Score(("A", "E"), np.asarray([0.85, 0.15], np.float32), 0.1, 0.1), "E")
    assert not gate(Score(("A", "E"), score.probabilities, 0.6, 0.4), "A")
    assert gate(Score(("A", "E"), score.probabilities), "A")  # MLP has NaN nearest distance.


def test_holdout_uses_session_then_signer_then_exact_pose():
    q = Sample("A", vector(), "signer1", "session1")
    assert held_out(Sample("B", vector(1), "signer1", "session1"), q)
    assert not held_out(Sample("A", vector(1), "signer1", "session2"), q)
    assert held_out(Sample("A", vector()), Sample("A", vector()))
    assert held_out(Sample("A", vector(1), "signer1"), Sample("A", vector(2), "signer1"))


def test_mlp_npz_and_target_false_accept(tmp_path):
    weights = np.zeros((2, 66), np.float32)
    archive = tmp_path / "weights.npz"
    np.savez(archive, W0=weights, b0=np.asarray([2.0, 0.0], np.float32), labels=np.asarray(["A", "B"]))
    model = Mlp(archive)
    score = model.classify(vector())
    assert score.top1 == "A" and gate(score, "A") and not gate(score, "B")
    result = evaluate(None, [Sample("A", vector()), Sample("B", vector())], model="mlp", mlp=model)
    assert result["true_accept"] == 0.5
    assert result["false_accept"] == 0.5
    assert result["worst_target"] == "A"


def test_current_team_loo_has_no_self_match():
    root = Path(__file__).resolve().parents[2]
    team = read_csv(root / "training/data/team-references-v1.csv")
    result = evaluate(team, team)
    assert result["n"] == 98
    assert 0.99 <= result["top1"] <= 1.0
    assert 0.85 <= result["true_accept"] <= 0.95
