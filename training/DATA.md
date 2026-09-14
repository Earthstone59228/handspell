# Training data — provenance and consent

All training data for the letter classifier is self-recorded by members of the Handspell team.
Nothing is scraped, licensed, or sourced from a third party.

The intended capture path is the app's debug-only capture screen (`docs/CLASSIFIER.md` §7), which
writes the full 141-column capture CSVs described below. **That screen is not written yet.** The
reference set currently in the repository was recorded with the desktop tool in
[`recorder/`](../recorder/README.md) (`docs/RECORDER.md`), which normalises through the same
`handspell/normalize.py` and writes `assets/classifier/references-v1.csv` directly.

Two consequences of that detour, stated because they are easy to forget:

- **No signer attribution.** `references-v1.csv` is a fixed 67-column format that
  `KnnLetterClassifier` parses strictly, so a `signer_id` cannot ride along inside it. Until the
  capture screen lands, signer attribution for that file has to live outside it, and the
  leave-one-signer-out protocol in `docs/CLASSIFIER.md` §8 cannot be run on it — which makes any
  accuracy number measured from it a same-signer number, and therefore an optimistic one.
- **No raw rows.** Image landmarks, timestamps and device metadata are never written, so the
  xy-image-space ablation in `docs/CLASSIFIER.md` §8 cannot be evaluated from what the recorder
  leaves behind; it needs the capture CSVs.

## What a capture row contains

One row per accepted frame: 21 world-space and 21 image-space hand landmark coordinates (x, y, z
each — 63 + 63 = 126 numeric columns), handedness and its confidence score, a capture timestamp, the
device model and app version, the landmarker model version, and an opaque signer id such as `s1`.
There is no name, no image, no video, and no biometric identifier beyond the numeric hand geometry
itself. Full column layout is in `docs/CLASSIFIER.md` §7.

## What is public, and what stays derived

Raw capture CSVs are committed to this public repository under `training/data/<signer_id>/`, so the
classifier's training run is reproducible by anyone who clones the repo. The derived artifacts —
reference exemplars used by the stage-1 k-NN and the trained MLP weights (`assets/classifier/`) — ship
inside the app itself.

## Consent

Each signer whose CSVs appear under `training/data/` has consented in writing to that data, tagged
with their signer id, being committed to this public repository. Any signer can withdraw consent at
any time by asking the team to delete their files from `training/data/` and history, and to retrain
and re-export the classifier without them.

Consent record (rows to be filled by the team before submission):

| Signer id | Date consented | Consented to public release |
|---|---|---|
| | | |
| | | |
| | | |

## Rejected third-party datasets

No third-party dataset was used, per `docs/research/data-and-asl-reference.md` §1:

- **Kaggle ASL Alphabet (grassknoted)** — rejected. Effectively one signer under one lighting setup,
  and the dataset page does not display a clear, machine-verifiable license.
- **Kaggle Sign Language MNIST (datamunge)** — rejected. Confirmed CC0, but images are 28×28
  grayscale pixel crops with no hand/finger geometry preserved at that resolution — unusable for
  MediaPipe landmark extraction regardless of licensing.
- Other candidates surveyed in the same review (danrasband test set, lexset synthetic set) were
  rejected for the same reason: no clearly stated permissive license found on the dataset page.

## Lifeprint rule

Lifeprint / ASL University's own permission page forbids using its material to build apps. No
handshape description, image, or closely-paraphrased text from Lifeprint appears anywhere in this
repo, the training data, or the app. Where the app references authoritative handshape descriptions,
it uses original wording per `docs/research/data-and-asl-reference.md` §2, and Lifeprint is linked
only as an outbound "learn more" reference in documentation, never embedded.
