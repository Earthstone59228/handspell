# Classifier

One path, two stages. No finger-angle rule thresholds, no MediaPipe Model Maker. The normalisation
spec below is implemented twice — `vision/normalize/DefaultHandNormalizer.kt` and
`training/handspell/normalize.py` — and held identical by golden vectors.

## 1. Frame convention (get this wrong and everything downstream is mirrored)

The pipeline feeds MediaPipe a **selfie-mirrored, display-upright** image, matching the official
Android sample: copy `ImageProxy` plane 0 into a Bitmap, apply `Matrix().apply { postScale(-1f, 1f,
w/2f, h/2f); postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }`, then `BitmapImageBuilder`.
MediaPipe's handedness head assumes a mirrored selfie image, so with this convention the reported
`Handedness` is the user's **actual physical hand** and needs no correction.

Consequences, all of which are load-bearing:

- Every landmark array — `landmarks()` and `worldLandmarks()` — is in this mirrored, upright frame.
  Axes: **+x toward the right of the screen, +y down the screen, +z toward the camera.**
- The overlay draws `landmarks()` directly onto the mirrored `PreviewView` with no coordinate flip.
- Python consumes landmark arrays, never images, so this convention only has to be honoured once —
  at capture time. The capture CSV records `frame_convention=selfie-upright-v1` in every row so a
  future change is detectable rather than silent.

## 2. Normalisation spec, version 1

Input: the 21 `worldLandmarks` (metres, origin at hand centre, estimated 3D) plus the handedness
label. **World landmarks, not image landmarks** — they are metric and free of perspective distortion,
so the same handshape at 30 cm and 60 cm from the lens produces the same vector. Their `z` is a model
estimate and is the noisiest channel; §8 requires an xy-only ablation so we know what it buys us.

All arithmetic in 64-bit; narrow to `Float` only when writing the output array.

1. **Mirror.** If `handedness == LEFT`, negate `x` for all 21 points. Canonical space is therefore
   "a right hand as seen in a mirror" — literally what a right-handed signer sees on the preview.
   One classifier now serves both hands, and left-handed signing is supported rather than corrected.
2. **Translate.** `p_i := p_i - p_0` (landmark 0, the wrist). The wrist is the only landmark that is
   not moved by finger articulation, so it is a stable origin.
3. **Rotation-align, then keep orientation separately.** Build an orthonormal frame from two palm
   vectors:
   - `u = p_9` (wrist → middle-finger MCP), `v = p_5 - p_17` (pinky MCP → index MCP)
   - `e1 = u / |u|`
   - `w = v - (v·e1) e1`; `e2 = w / |w|`
   - `e3 = e1 × e2`  (palm normal)
   - `R = [e1; e2; e3]` as rows; `q_i = R · p_i`
   If `|u| < 1e-6` or `|w| < 1e-6`, return null (degenerate; the caller treats it as no hand).
4. **Scale.** `s = |u|` — the wrist-to-middle-MCP span, a near-rigid palm segment that finger pose
   barely changes (unlike bounding-box or max-pairwise-distance references). `q_i := q_i / s`.
5. **Shape block.** Landmarks 1..20, xyz, in index order → 60 floats. Landmark 0 is dropped (always
   zero). Landmark 9 is kept even though it is exactly `(1,0,0)` by construction, so that vector
   index `(i-1)*3` maps to landmark `i` with no lookup table — debuggability beats saving 3 floats.
6. **Orientation block.** `e1` then `e3`, as computed in step 3, in the mirrored frame → 6 floats.
   `e2 = e3 × e1` is recoverable, so 6 is sufficient.

**Output: 66 floats.** `NormalizedHand.VECTOR_DIM = 66`, `SPEC_VERSION = 1`.

### Implementation notes — normative, settled by the Python reference (2026-09-13)

The Python implementation in `training/handspell/normalize.py` is the reference; these resolve every
point where §2 could be read two ways. The Kotlin implementation must make the identical choice.

1. `u = p_9` and `v = p_5 - p_17` are taken **after** step 2 (translation). Do not use raw landmarks.
2. `R` has rows `[e1; e2; e3]` and `q_i = R · p_i` (column-vector form). Python does `q = p @ R.T`
   row-wise, which is the same thing — get the transpose right.
3. `s = |u|` is measured on the translated, **pre-rotation** `u` (rotation preserves length, so this is
   exact); the division by `s` happens after rotation.
4. The orientation block `e1`, `e3` is expressed in the **mirrored + translated but unrotated** frame,
   i.e. world axes. Never re-express them in the rotated `q` frame — there they are trivially
   `(1,0,0)` / `(0,0,1)` and K/P, G/Q collapse.
5. Degenerate checks in order: `|u| < 1e-6` → null before computing `v`; then `|w| < 1e-6` → null.
   Strict less-than.
6. Handedness: Python mirrors only on the exact string `LEFT` (case-insensitive) and treats anything
   else as RIGHT. Kotlin has the `Handedness` enum so there is no ambiguity there.
7. Golden JSON floats are written with 9 significant digits and `0.0` is written explicitly (no `-0`).
   Readers parse them as doubles; no special handling.
8. Golden poses are a synthetic parametric hand, plausible but not anatomically exact letters. They
   exist to pin numbers across languages, not to teach anyone what a letter looks like.

### Why rotation-align *and* keep orientation

K and P are the same handshape, as are G and Q; they differ only in wrist rotation (K/G forward-up,
P/Q down). A fully rotation-invariant vector makes those two pairs mathematically indistinguishable.
A translation+scale-only vector keeps them apart but makes every other letter a function of how the
user happens to be holding the phone, which is worse for the other 22. Splitting the two — invariant
shape plus explicit absolute orientation — separates "what the fingers are doing" from "where the
hand is pointing", which is exactly how the confusable table describes the letters. P/Q fall out of
`e1.y > 0` (fingers pointing down the screen) versus `e1.y < 0`.

Caveat to state honestly: the orientation block is defined relative to the upright *display*, not to
gravity. Practising while lying down will degrade K/P and G/Q. Acceptable for a practice app.

### Golden vectors

`training/testdata/normalizer_golden.json`: an array of cases, each `{ handedness, world: [[x,y,z] ×
21], expected: [66 floats] }`, covering at least one hand per confusable group, one left hand, one
right hand, and two degenerate inputs whose `expected` is `null`. Floats are written with 9
significant digits. Both `NormalizerGoldenTest.kt` and `tests/test_normalize.py` read this same file
and assert **1e-6 absolute** agreement. The Gradle scaffold must add
`sourceSets.getByName("test").resources.srcDir("../../training/testdata")` so the JVM test can read it.

## 3. Stage 1 — k-nearest-neighbour over self-recorded exemplars

- **Reference set**: `assets/classifier/references-v1.csv`, one row per exemplar: `letter` followed
  by 66 floats. Built by `training/build_references.py` from capture CSVs.
- **Selection**: per letter, greedy dedupe — walk the captured frames in capture order, keep a frame
  only if its Euclidean distance to every already-kept exemplar of that letter is ≥ 0.05; stop at 64.
  This keeps pose variety and throws away the near-duplicate frames a 30 fps capture produces.
- **Inference**: Euclidean distance in the 66-d space with the orientation block weighted 0.5 (it is
  noisier and less discriminative than shape, but must not be ignored — see K/P). Take `k = 5`
  nearest exemplars, weight each by `1 / (d + 0.01)`, sum per letter, normalise to sum 1 → ranked
  probabilities. `Classification.nearestDistance` = distance to the single closest exemplar.
- **Why k-NN, not a per-letter centroid**: letters like G and O have genuinely multi-modal exemplar
  clouds across signers; a centroid averages two valid poses into an invalid one.

Stage 1 is what ships if stage 2 is not ready. It is the schedule floor, not a throwaway.

## 4. Stage 2 — small MLP, plain Kotlin inference

Architecture (fixed): `66 → Dense 128 + ReLU → Dropout 0.2 → Dense 64 + ReLU → Dense N → softmax`,
where `N` = number of shipped letters (24 unless §9 cuts some). ~18k parameters, ~70 KB as float32.

Training recipe (`training/train_mlp.py`, PyTorch CPU, minutes not hours):
- Input: all capture CSVs, normalised with the *same* `normalize.py` the golden test covers.
- Augmentation, applied to the raw landmarks before normalisation so it exercises the real pipeline:
  isotropic jitter `N(0, 0.004 m)` per landmark; ±12° rotation about each axis; ±8% scale. No
  horizontal mirroring as augmentation — mirroring is part of the spec, not noise.
- Split: **leave-one-signer-out**. Train on all but one signer, validate on that signer, repeat.
  Final shipped weights are trained on all signers with hyperparameters chosen by the LOSO mean.
- Optimiser AdamW, lr 1e-3, cosine decay, batch 128, 200 epochs, early stop on validation macro-F1
  with patience 20. Class-balanced sampling so a letter that was easier to capture does not dominate.
- Label smoothing 0.05, so the network does not learn to emit 0.999 on a bad frame — overconfidence
  is the specific failure that would teach someone a wrong sign.

### Weight export format — `assets/classifier/mlp-v1.bin`

Little-endian throughout. Written by `training/export_weights.py`, parsed by `MlpWeights.kt` with
`ByteBuffer.order(ByteOrder.LITTLE_ENDIAN)`.

| Offset | Type | Meaning |
|---|---|---|
| 0 | 4 bytes | ASCII magic `HSML` |
| 4 | int32 | format version = 1 |
| 8 | int32 | normalisation spec version (must equal 1) |
| 12 | int32 | input dim (must equal 66) |
| 16 | int32 | layer count = 3 |
| 20 | int32[] | per layer: `outDim`, then `activation` (0 = ReLU, 1 = identity) — 2 int32 per layer |
| … | float32[] | per layer in order: `W` row-major `[outDim][inDim]`, then `b[outDim]` |
| … | int32 | label count `N` |
| … | — | `N` × (int32 byte length + UTF-8 bytes), label strings in class-index order |
| … | uint32 | CRC32 of every byte before this field |

Forward pass is `y = W · x + b` per layer, ReLU between, softmax at the end. A sidecar
`assets/classifier/mlp-v1.json` carries human-readable metadata (dims, labels, training date, git
sha, signer count, LOSO scores, sha256 of the `.bin`) and is shown on the settings screen. The app
refuses to load a file whose magic, format version, spec version, input dim or CRC is wrong, and
falls back to stage 1 with a visible notice rather than crashing or silently mis-scoring.

## 5. Confusable groups

Cues below are from `docs/research/data-and-asl-reference.md` §3; the geometry column is how the
66-d vector expresses them.

| Group | Cue | Where it lives in the vector |
|---|---|---|
| A / S / T | thumb on the side vs. across the front vs. poking between index and middle | thumb tip (4) `z` and `y` relative to the palm plane |
| M / N / T | thumb tucks under 3 / 2 / 0 fingers | thumb tip `x` against index MCP (5) and middle MCP (9) |
| K / P, G / Q | identical shape, wrist points forward-up vs. down | orientation block: sign of `e1.y` |
| H / U / V | spread and rotation: together+sideways / together+up / spread+up | index tip (8) to middle tip (12) distance, plus `e1` |
| R / U | index crossed over middle vs. parallel | sign of `(q_8 - q_12)·e2` |
| D / F | index straight, others curled vs. index pinched, three straight | index PIP/tip (6, 8) extension |

Handling, in order of preference: (1) capture 1.5× the usual number of exemplars for every letter in
a confusable group, deliberately varying thumb depth and wrist angle; (2) report the confusion matrix
per group in every evaluation run, not just the macro number; (3) the feedback engine's margin
requirement (§6) means a frame that is ambiguous *within* a group shows "keep adjusting", never a
match; (4) the Adjust hint for a letter names its group-mate's distinguishing cue in authored copy
("Tuck your thumb between your index and middle finger"), never generated text.

## 6. Thresholds and smoothing

Defaults live in `FeedbackThresholds` and nowhere else.

| Constant | Value | Meaning |
|---|---|---|
| `emaAlpha` | 0.35 | `p := 0.35·p_new + 0.65·p_prev` over the full probability vector |
| `matchProbability` | 0.85 | smoothed probability of the target required to start the hold |
| `matchMargin` | 0.20 | target must beat the runner-up letter by this much |
| `matchDistance` | 0.32 | stage 1 only: nearest-exemplar distance a match must be within |
| `adjustProbability` | 0.45 | below this, no letter is named |
| `rejectDistance` | 0.45 | stage 1 only: above this, report NotRecognized regardless of softmax |
| `holdToConfirmMs` | 400 | all match conditions must hold continuously for this long |
| `matchLatchMs` | 800 | once matched, stay matched this long even if the score dips (anti-flicker) |
| `noHandFrames` | 3 | consecutive hand-free frames before showing NoHand |

State machine, evaluated per frame: no hand for `noHandFrames` → `NoHand`. Hand present and all match
conditions met continuously for `holdToConfirmMs` → `Match` (latched for `matchLatchMs`). Hand
present and `p[target] ≥ adjustProbability`, or the target is in the top 3 → `Adjust` with
`holdProgress` = fraction of the hold window elapsed. Otherwise → `NotRecognized`.

`Match` is never emitted from a single frame. Changing the target resets every counter, so a pose
held across a prompt change cannot carry credit forward. After a `Match` the drill waits 600 ms
before advancing, so the learner actually sees the confirmation.

## 7. Capture screen CSV

Debug builds only. One row per accepted frame (the screen records at a 3-per-second sample rate while
held, so rows are not near-identical). Written to
`<app external files>/captures/<signer_id>/<letter>_<session_id>.csv`, one header row per file.

```
schema_version,frame_convention,session_id,signer_id,letter,captured_at_iso,timestamp_ms,
handedness,handedness_score,image_width,image_height,rotation_degrees,device_model,app_version,
landmarker_model,wx0,wy0,wz0,...,wx20,wy20,wz20,ix0,iy0,iz0,...,ix20,iy20,iz20
```

15 metadata columns + 63 world + 63 image = **141 columns**. Image landmarks are recorded even though
the normaliser ignores them, so an xy-image-space fallback can be evaluated offline without
re-capturing. `signer_id` is a short opaque label (`s1`…`s5`) chosen in the app, never a name. The
screen shows the target letter, a live landmark overlay, a frame counter per letter, and requires the
signer id to be set before it will write anything.

## 8. Evaluation protocol

Run by `training/evaluate.py`; output committed to `docs/research/eval-<date>.md`.

- **Split**: leave-one-signer-out across 3–5 teammates. A letter's number is the mean across folds;
  the per-fold spread is reported too, because one signer's thumb doing something odd is the failure
  mode we care about.
- **Metrics**: per-letter recall and precision, macro-F1, the full N×N confusion matrix, and the
  reject rate at the shipped thresholds (frames the engine declines to name). Plus the xy-only
  ablation, so we know whether world-landmark `z` is helping or adding noise.
- **Live check** before any letter is declared shippable: 10 on-device trials per letter, by a signer
  held out of training, in two lighting conditions. Record time-to-match and any false match (a
  `Match` for a letter the signer was not producing). A false match is a hard failure, not a metric.

## 9. Letter-cut criteria — Sep 18 checkpoint

A letter ships only if, on held-out-signer evaluation: **recall ≥ 0.80**, **false-match rate against
its confusable group ≤ 0.05**, and **≤ 1 false match in the 10 live trials**. Everything else is cut.

- Cut letters are removed from `LetterClassifier.supportedLetters`, so drills, story words and speed
  rounds are all built from the surviving set automatically — no screen has a dead entry.
- The About/Accuracy screen names every excluded letter and says why (motion for J and Z, accuracy
  for the rest). No "coming soon" tiles, no greyed-out mystery letters.
- If fewer than 18 letters pass, we ship the ones that pass and say the number out loud in the demo
  video. A 16-letter app that is honest beats a 24-letter app that teaches wrong signs.
- If stage 2 is not beating stage 1 on LOSO macro-F1 by Sep 24, stage 1 ships and stage 2 is dropped.
