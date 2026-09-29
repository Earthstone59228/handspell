# Training data — provenance and consent

The static-letter reference set combines the team's original A–D recordings with derivatives
of **ASLYset**, an original public image dataset released by Miguel Rivera under CC BY 4.0.
The ASLYset input is licensed from its original release, not scraped from an unverified mirror.
Its derived vectors and reference illustrations retain attribution and CC BY 4.0 terms; application
code remains MIT-licensed. See `NOTICE` and `training/data/aslyset/LICENSE.txt`.

The debug-only capture screen (`docs/CLASSIFIER.md` §7) is available and writes the full
141-column capture CSVs described below. The original 98 A–D examples were recorded earlier with
the desktop tool in [`recorder/`](../recorder/README.md). That tool normalizes through the same
`handspell/normalize.py` and writes reference vectors directly. The public expansion instead uses
`training/scripts/import_aslyset.py`, documented at the end of this file.

The original desktop-recorded A–D input has two historical limitations:

- **No signer IDs in its reference rows.** The strict 67-column `references-v1.csv` format contains
  only letter and vector fields. Those original team rows have no accompanying signer attribution,
  so they cannot support leave-one-signer-out evaluation on their own. ASLYset's derived NPZ retains
  original User1–User4 IDs separately and supports the dated held-out-signer evaluation.
- **No raw capture rows from that desktop session.** Its image landmarks, timestamps and device
  metadata were not retained. New debug CSVs can retain these fields. The ASLYset source archive
  contains original images outside the repository, while the committed NPZ retains normalized
  vectors and source-file references rather than raw world/image landmark arrays.

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

The following alternatives were rejected in the earlier review in `docs/research/data-and-asl-reference.md` §1:

- **Kaggle ASL Alphabet (grassknoted)** — rejected. Effectively one signer under one lighting setup,
  and the dataset page does not display a clear, machine-verifiable license.
- **Kaggle Sign Language MNIST (datamunge)** — rejected. Confirmed CC0, but images are 28×28
  grayscale pixel crops with no hand/finger geometry preserved at that resolution — unusable for
  MediaPipe landmark extraction regardless of licensing.
- Other candidates surveyed in the same review (danrasband test set, lexset synthetic set) were
  rejected for the same reason: no clearly stated permissive license found on the dataset page.

### Re-checked 2026-09-14

The review above concluded that no permissively licensed, multi-signer, landmark-format ASL dataset
existed. That was wrong, and the correction is recorded here rather than edited out of the notes:
**FSBoard** ([Kaggle](https://www.kaggle.com/datasets/googleai/fsboard),
[arXiv:2407.15806](https://arxiv.org/abs/2407.15806)) is CC BY 4.0, was recorded by 147 paid and
consenting Deaf signers, and ships 30 Hz MediaPipe Holistic landmarks as one Parquet row per frame.

It is still not used for training, and the reason is about the problem rather than the licence:

- **Its labels are whole phrases, not per-frame letters.** The paper's baseline is a sequence model
  (ByT5-Small over landmarks, 11.1% character error rate on a test set of unseen phrases *and*
  signers). There is no per-frame letter ground truth in it, so using it would mean replacing the
  stage-1 classifier's shape rather than adding data to it — out of scope for this build.
- **Self-recorded data matches the domain that gets demoed**: one webcam, one mount, one distance,
  a handful of teammates. It is also the set we can extend the same afternoon a confusable pair
  turns out to be hard, which a 1.38 TB download is not.

FSBoard is a candidate **continuous-fingerspelling evaluation** set, and a starting point if a
later version takes on continuous fingerspelling recognition. If it is used, CC BY 4.0 requires
attribution, and the release additionally asks that signers' faces be blurred when publicising
examples, that signers not be re-identified, and that the Deaf community be involved in applications
built for them.

One rule for anything else that turns up: **a licence tag on a re-upload is not a licence.**
`granthgaurav/asl-mediapipe-converted-dataset` (MIT), `psewmuthu/how2sign-holistic` (MIT) and
`nguyenchitinh/asl-citizen` (MIT) all claim permissive licences over features derived from datasets
their uploaders do not own. Derived dataset assets can retain source-license obligations. CC BY-SA and CC BY-NC inputs
require an asset-specific analysis of adaptation, redistribution and intended commercial use;
this review does not establish that they would relicense all independent application code or
prohibit every unrelated paid feature. They were not selected for this integration. Full detail in
`docs/research/data-and-asl-reference.md` §1.1 and §1.2.

## Lifeprint rule

Lifeprint / ASL University's own permission page forbids using its material to build apps. No
handshape description, image, or closely-paraphrased text from Lifeprint appears anywhere in this
repo, the training data, or the app. Where the app references authoritative handshape descriptions,
it uses original wording per `docs/research/data-and-asl-reference.md` §2, and Lifeprint is linked
only as an outbound "learn more" reference in documentation, never embedded.

## ASLYset integration — 2026-09-28

Original release: Miguel Rivera (2019), [ASLYset, V1](https://data.mendeley.com/datasets/xs6mvhx6rh/1),
DOI 10.17632/xs6mvhx6rh.1, [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
It describes 5,200 416×416 images from four volunteers: 24 static letters plus SP/FN.
The importer excludes SP/FN and does not create J/Z examples. It verifies the original archive
SHA256, mirrors images to the upright selfie frame, runs the bundled Hand Landmarker model, and
normalizes using MediaPipe's reported handedness exactly as Android does. It rejects missing or
multiple hands and degenerate geometry. No source photo is shipped or committed.

`training/data/aslyset/landmarks-v1.npz` retains normalized vectors, source filenames,
reported handedness and original opaque User1–User4 IDs for reproducible signer-held-out
evaluation. `provenance.json` records source/license/model/archive hashes and extraction counts.
`references-v1.csv` preserves the team's original A–D data and supplements all 24 static letters
with up to 64 total exemplars per letter. The selection interleaves source users; its first public
reference is a real medoid for the illustration, never an averaged or invented handshape.

The license release is evidence of permission to use the data. It is not evidence that these
four volunteers represent all ASL signers or that the app passes its live-device accuracy criteria.
Evaluation results and remaining limitations are recorded in `docs/research/aslyset-evaluation-2026-09-28.json`.
