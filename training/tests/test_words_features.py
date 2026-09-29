import numpy as np

from handspell import words_golden, words_net, words_net_golden
from handspell.words_features import FEATURE_DIM, MIN_FRAMES, mirror_features, sequence_features


def _sample(frames=20, seed=0):
    rng = np.random.default_rng(seed)
    hands = np.full((frames, 2, 21, 2), np.nan, np.float32)
    pose = np.tile(np.array([[0.5, 0.3], [0.6, 0.5], [0.4, 0.5]], np.float32), (frames, 1, 1))
    for f in range(frames):
        hands[f, 0] = rng.uniform(0.2, 0.8, (21, 2))
    return hands, pose


def test_dimension_and_finite():
    v = sequence_features(*_sample())
    assert v.shape == (FEATURE_DIM,) and np.isfinite(v).all()


def test_mirror_twice_is_identity_and_flips_x_only():
    v = sequence_features(*_sample())
    assert np.allclose(mirror_features(mirror_features(v)), v)
    assert not np.allclose(mirror_features(v), v)


def test_rest_frames_around_the_sign_change_nothing():
    hands, pose = _sample()
    padded_hands = np.concatenate([np.full((7, 2, 21, 2), np.nan, np.float32), hands, np.full((4, 2, 21, 2), np.nan, np.float32)])
    padded_pose = np.concatenate([pose[:7], pose, pose[:4]])
    assert np.allclose(sequence_features(hands, pose), sequence_features(padded_hands, padded_pose))


def test_too_short_or_no_hand_or_no_body_is_none():
    hands, pose = _sample(frames=MIN_FRAMES - 1)
    assert sequence_features(hands, pose) is None
    hands, pose = _sample()
    assert sequence_features(np.full_like(hands, np.nan), pose) is None
    assert sequence_features(hands, np.full_like(pose, np.nan)) is None


def test_slot_order_does_not_matter():
    hands, pose = _sample()
    hands[:, 1] = hands[:, 0][:, ::-1] * 0.5 + 0.1  # a second hand, present in every frame
    swapped = hands[:, ::-1]
    assert np.allclose(sequence_features(hands, pose), sequence_features(swapped, pose))


def test_golden_files_are_current():
    import json
    from handspell.words_features import FEATURE_DIM as dim
    assert json.loads(words_golden._PATH.read_text())["featureDim"] == dim
    assert words_golden.build()["featureDim"] == dim


def test_word_net_pack_roundtrip_and_forward_is_a_distribution():
    data, doc = words_net_golden.build()
    model = words_net.unpack(data)
    assert words_net.pack(model) == data
    for case in doc["cases"]:
        p = words_net.forward(model, np.array(case["features"], np.float32))
        assert abs(p.sum() - 1) < 1e-9 and np.allclose(p, case["expected"], atol=1e-6)


def test_corrupt_word_net_is_refused():
    data, _ = words_net_golden.build()
    bad = bytearray(data); bad[100] ^= 0xFF
    try:
        words_net.unpack(bytes(bad))
    except ValueError:
        return
    raise AssertionError("corrupt file accepted")
