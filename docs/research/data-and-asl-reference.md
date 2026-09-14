# Data & ASL Reference

verified 2026-09-13; section 1 re-checked 2026-09-14 (see 1.1 and 1.2 — the original verdict was
wrong about landmark datasets existing, and that is recorded rather than quietly edited away)

## 1. Dataset licenses

| Dataset | Signers / diversity | Images/letter | License | Ship in MIT repo? |
|---|---|---|---|---|
| [Kaggle ASL Alphabet (grassknoted)](https://www.kaggle.com/datasets/grassknoted/asl-alphabet) | Effectively **1 signer**, one background/lighting setup, ~87,000 images total, 29 classes (A-Z + SPACE/DELETE/NOTHING), 200x200px | ~3,000 | License field not machine-readable via API/fetch; widely mirrored as CC0-style "public" Kaggle dataset but the page itself does not display a clear named license — **treat as unverified/ambiguous**, do not assume permissive | No — provenance and license are unconfirmed; single-signer bias is also a quality risk independent of licensing |
| [Kaggle ASL Alphabet Test (danrasband)](https://www.kaggle.com/datasets/danrasband/asl-alphabet-test) | Small supplementary test set, multiple contributors' photos | small (test-only) | Not clearly stated on page (same caveat as above) | No — same ambiguity |
| [Kaggle Sign Language MNIST (datamunge)](https://www.kaggle.com/datasets/datamunge/sign-language-mnist) | Derived from the same style of single/few-signer image set | 34,627 images total across 24 classes (J, Z excluded) | **CC0** for the original content, MIT for packaging, confirmed via the [Hugging Face mirror README](https://huggingface.co/datasets/Voxel51/American-Sign-Language-MNIST) | **Not useful for this project regardless of license** — images are 28x28 grayscale pixel crops with no hand/finger geometry preserved at that resolution; MediaPipe landmark extraction requires a full-resolution hand image, which this dataset does not provide |
| [Kaggle Synthetic ASL Alphabet (lexset)](https://www.kaggle.com/datasets/lexset/synthetic-asl-alphabet) | Synthetic (Lexset "Seahaven" render pipeline), varied poses/lighting per letter, no real signer diversity | Multiple synthetic renders per letter | License not displayed in fetched content — **unverified**, must be checked manually on Kaggle before use | Unknown until license confirmed; even if permissive, synthetic-render domain gap to real webcam hands is a separate risk |
| [**Google FSBoard**](https://www.kaggle.com/datasets/googleai/fsboard) ([paper](https://arxiv.org/abs/2407.15806)) | **147 paid, consenting Deaf signers**, Pixel 4A selfie cameras, varied environments; the paper evaluates on **unseen signers** | >3M characters / >250 h; 30 Hz MediaPipe Holistic landmarks, **one parquet row per frame**, plus the RGB video | **CC BY 4.0** — permissive, commercial use allowed, attribution required | **Landmarks yes, per-frame letter labels no.** Labels are whole phrases and the baseline is seq2seq (ByT5-Small, 11.1% CER), so it feeds a sequence model or an eval set, not a per-frame letter k-NN. Full release is 1.38 TB with video |
| [ChicagoFSWild](https://www.kaggle.com/datasets/joebeachcapital/chicagofswild) | Multiple signers, in-the-wild footage | 13.6 GB, and it has the **frame-level letter labels** FSBoard lacks | "Other (specified in description)" — **not permissive** | No — nothing derived from it may ship in an MIT repo. Offline research/eval only |
| Re-uploaded landmark derivatives on Kaggle | Usually one source signer or one small original set | 3 MB – 220 MB | Tagged MIT / Apache / CC BY-SA by the uploader, but derived from grassknoted or How2Sign — see 1.2 | No — a re-uploader cannot license data they do not own |

**Verdict, revised 2026-09-14: still self-record for the shipped classifier — but no longer because nothing exists.** The 2026-09-13 review concluded that no permissively licensed, multi-signer, landmark-format ASL dataset existed. That conclusion was wrong: FSBoard is all three (1.1). Two other reasons still point at self-recorded data for *this* build, and they are the honest ones:

1. **Shape mismatch.** FSBoard labels whole phrases. The shipped stage-1 classifier is a per-frame 24-way k-NN over a single handshape. Turning FSBoard into per-frame letter labels means solving the alignment problem, i.e. building a sequence model — a different project with a different risk profile, not a data swap.
2. **Domain match.** The app sees one webcam, one mount position, one distance, a handful of teammates. A model fitted to 147 strangers' phone cameras is not obviously better at recognising the people who will actually demo it, and the self-recorded set is the one we can extend the same afternoon a confusable pair turns out to be hard.

FSBoard is still worth pulling as an **evaluation** set, and it is the only legitimate starting point if the project later wants continuous fingerspelling recognition. What must not happen is bundling any re-tagged derivative (1.2) into this repo or into a distributed model.

### 1.1 FSBoard — why this section exists

[FSboard](https://www.kaggle.com/datasets/googleai/fsboard), described in [arXiv:2407.15806](https://arxiv.org/abs/2407.15806), is the largest fingerspelling recognition dataset to date by more than 10x: 147 paid and consenting Deaf signers recorded with Pixel 4A selfie cameras in a variety of environments, >3 million characters over >250 hours, released under **CC BY 4.0**. It ships 30 Hz MediaPipe Holistic landmarks as Parquet — one row per landmark frame — and now also the underlying RGB video. The paper's baseline fine-tunes those landmarks into ByT5-Small for 11.1% character error rate, and it reports that number on a test set of unique phrases *and unique signers*, which is the cross-signer evaluation this project wants but cannot produce from three teammates.

The release asks three things of users, and they are not optional decoration: blur signers' faces when publicising examples, do not attempt to re-identify signers or use their likeness, and involve the Deaf community in the creation of applications targeted at them. Attribution is required by the licence; cite the dataset and the paper wherever it is used.

Its limitation is the reason it does not replace self-recorded data here: it labels phrases, not frames. There is no per-frame letter ground truth to train the k-NN on.

### 1.2 A licence tag on a re-upload is not a licence

Searching Kaggle for "ASL landmark" returns roughly a dozen small datasets that look like exactly what this project needs. Most are not, and the failure mode is uniform: someone derived landmarks from a dataset they do not own and attached their own permissive licence to the result.

| Dataset | Claimed licence | Why the claim does not hold |
|---|---|---|
| `granthgaurav/asl-mediapipe-converted-dataset` | MIT | Its own subtitle says "Preprocessed ASL Image Dataset of all 26 letters from A-Z using Mediapipe" — it is grassknoted-derived, and grassknoted's licence is the unverified one already rejected above |
| `psewmuthu/how2sign-holistic` | MIT | "Mediapipe Holistic Landmark Features Extracted from the How2Sign ASL Dataset". How2Sign is not MIT, so the uploader had no standing to relicense it |
| `nguyenchitinh/asl-citizen` | MIT | ASL-Citizen-Keypoints; ASL Citizen's own release terms are not MIT. Verify at the source before touching it |
| `srisahithis/american-sign-language-a-z-dataset-hand-landmarks` | Apache 2.0 | Ships JPEG images plus landmarks, i.e. the same grassknoted lineage, relicensed by a third party |
| `siruyyy/asl-hand-landmarks-24-letters-v1-a-y-no-jz` | CC BY-SA 4.0 | Same 24-letter scope as this project, which is tempting and irrelevant: a derivative of an unverified source, and share-alike would force the derived artefact under CC BY-SA |
| `iamavinashkr090502/asl-hand-landmark-and-gesture-dataset`, `googleai/fleurs-asl` | CC BY-SA 4.0 | Share-alike. Acceptable inputs, but they would pull the derived classifier and data under CC BY-SA, which conflicts with this repo's MIT licence |
| Several `ISL-Fingerspelling` sets | CC BY-NC 4.0 | Non-commercial. Incompatible with the paid tier this app ships |

Two rules follow, and they apply to anything found later as well. **Check the licence at the original source, not on a mirror.** And **a share-alike or non-commercial licence on a training input is a product decision, not a footnote** — it propagates to what the app may do with the result.

## 2. Authoritative handshape reference

[Lifeprint / ASL University (Dr. Bill Vicars)](https://www.lifeprint.com/asl101/fingerspelling/) is the most commonly cited plain-language ASL fingerspelling reference online, but its own [permission/terms page](https://www.lifeprint.com/asl101/pages-layout/permission.htm) explicitly states material may **not** be used "to make apps of any kind (web, phone, or any other format accessible to the public)" and may not be "published to the public via any online format" without a separate permission request. **Do not embed, paraphrase-closely, or adapt Lifeprint text/images into the app.** It may be linked to from documentation as a "learn more" reference only.

Wikipedia's [American manual alphabet](https://en.wikipedia.org/wiki/American_manual_alphabet) article and its accompanying [Wikimedia Commons category](https://commons.wikimedia.org/wiki/Category:American_manual_alphabet) are text/images under **CC-BY-SA 4.0** — usable with attribution and share-alike compliance, but per instructions we cite rather than copy its text verbatim into app copy (share-alike would otherwise require licensing our own hint copy under CC-BY-SA, which is undesirable for an MIT-licensed product).

Handshape facts themselves (which fingers are extended, thumb position) are not copyrightable — only a given source's specific wording/imagery is. The table below is original phrasing, cross-checked against Lifeprint's descriptions and the Helen Keller Services and DeafBlind.com plain-text alphabet references, cited per row.

| Letter | Original one-sentence handshape description | Variant note | Citation |
|---|---|---|---|
| A | Fist with the thumb resting flat against the side of the curled fingers, palm facing forward. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| B | Flat hand, four fingers together and upright, thumb folded across the palm. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| C | Hand curved into a "C" shape, thumb and fingers both bent to trace the letter's outline. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| D | Index finger points straight up, thumb and middle finger touch to form a circle, ring/pinky curl into the palm. | — | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| E | Fingertips curl down to touch the thumb, forming a claw-like closed hand. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| F | Thumb and index fingertip touch to form a circle; middle, ring, and pinky fingers stay straight and spread. | — | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| G | Index finger and thumb point straight out, parallel to each other, hand rotated to the side. | Often signed with hand oriented sideways rather than palm-forward. | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| H | Index and middle fingers extend straight out together, side by side, hand rotated to the side. | — | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| I | Pinky finger points straight up, remaining fingers and thumb curl into the palm. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| K | Index and middle fingers form a "V," with the thumb pressed between them at the base knuckle. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| L | Index finger points up and thumb points out to the side, forming a right angle. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| M | Thumb tucks under three fingers (index, middle, ring), pinky rests on top of the thumb. | Easily confused with N; see confusables section. | [Fiveable ASL handshapes](https://fiveable.me/lists/essential-asl-handshapes) |
| N | Thumb tucks under two fingers (index and middle), ring and pinky curl on top. | — | [Fiveable ASL handshapes](https://fiveable.me/lists/essential-asl-handshapes) |
| O | All fingers and thumb curve together to form a rounded "O" shape. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| P | Same as K, but the hand is rotated to point downward instead of forward. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| Q | Same as G, but the hand is rotated to point downward instead of forward. | — | [Lifeprint handshapes](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm) |
| R | Index and middle fingers cross, with the thumb resting against the ring/pinky knuckles. | — | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| S | Fist with the thumb crossed flat over the front of the curled fingers. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| T | Thumb tucks between the index and middle fingers, poking out slightly, rest of the hand in a fist. | — | [Fiveable ASL handshapes](https://fiveable.me/lists/essential-asl-handshapes) |
| U | Index and middle fingers extend straight up together, touching, palm facing out. | — | [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/) |
| V | Index and middle fingers extend up in a "V," spread apart, palm facing out. | — | [Wikipedia V sign](https://en.wikipedia.org/wiki/V_sign) |
| W | Index, middle, and ring fingers extend up and spread apart, thumb holds down the pinky. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| X | Index finger curls into a hook shape, thumb rests against the side of the curled middle finger. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |
| Y | Thumb and pinky extend out to the sides, middle three fingers curl into the palm. | — | [DeafBlind.com](https://www.deafblind.com/asl.html) |

J and Z are intentionally excluded from the static table (see section 4).

## 3. Confusable groups and the geometric cue that separates them

- **M vs. N vs. T** (all closed fists with thumb tucked): the cue is **how many fingers the thumb tucks under** — M = 3 fingers, N = 2 fingers, T = thumb pokes out between index/middle only. A landmark classifier should key on thumb-tip x/y position relative to the index and middle MCP (knuckle) joints. [Fiveable](https://fiveable.me/lists/essential-asl-handshapes)
- **A vs. S vs. T** (all closed fists): cue is **thumb position on the fist** — A = thumb flat against the side, S = thumb crossed over the front of the fingers, T = thumb poking out between index/middle. Distinguish via thumb-tip distance from the palm plane (side vs. front). [DeafBlind.com](https://www.deafblind.com/asl.html)
- **K vs. P**: identical handshape, differ only by **wrist/palm rotation** (K points forward/up, P points down) — this is the one confusable pair a static per-frame landmark model may need wrist-orientation features (not just finger geometry) to resolve. [Lifeprint](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm)
- **G vs. Q**: same relationship as K/P — identical index-finger-and-thumb-parallel shape, distinguished only by rotation (G forward, Q downward). [Lifeprint](https://www.lifeprint.com/asl101/pages-layout/handshapes.htm)
- **H vs. U vs. V**: **finger spread** is the cue — H = index+middle together, rotated sideways; U = index+middle together, pointing up; V = index+middle spread apart, pointing up. Track the distance between index and middle fingertips relative to hand size, plus wrist rotation for H specifically. [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/)
- **D vs. F**: cue is **which fingers stay straight** — D has only the index straight (middle/ring/pinky curl, thumb touches middle fingertip); F has three fingers straight (middle/ring/pinky) with thumb-to-index touching. Track index-finger extension state as the discriminator. [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/)
- **R vs. U**: cue is **crossed vs. parallel fingers** — R crosses index over middle, U holds them parallel and together. Track whether index/middle fingertip x-coordinates invert relative to each other. [Helen Keller Services](https://www.helenkeller.org/asl-handshapes-described/)

## 4. J/Z and left-handed signers

J and Z are the two letters excluded from a static-frame ASL alphabet classifier because they require **motion**, not a fixed handshape: J is signed by tracing a "J" path in the air from an "I" handshape, and Z by tracing a "Z" path from a "1" handshape. [Start ASL](https://www.startasl.com/understanding-the-letter-j-in-sign-language/) A landmark classifier scoped to single-frame poses correctly cannot represent these two letters without a temporal/trajectory model, confirming the 24-letter scope is a legitimate technical boundary, not a shortcut.

Left-handed signing is a fully accepted mirror image of right-handed signing in ASL — "neither is more correct than the other," per [Lifeprint's left-handed signers page](https://www.lifeprint.com/asl101/pages-layout/lefthandedsigners.htm) and [Handspeak](https://www.handspeak.com/study/52/). The only exception is directional signs (e.g., signing "LEFT"/"RIGHT" as concepts), which are irrelevant to fingerspelling letters. The capture/classifier pipeline should therefore support (or explicitly mirror-normalize) left-handed input rather than treating left-hand shapes as errors.

## 5. Cultural/accuracy framing

- Deaf-led AI researchers explicitly call out that sign language technology is dominated by hearing, non-signing researchers who build tools "convenient" to themselves rather than tools the Deaf community asked for, and who use datasets/annotations lacking real linguistic grounding — a direct warning against shipping this app as an authority on "correct" ASL. [Desai et al., "Systemic Biases in Sign Language AI Research," ACL Anthology 2024](https://aclanthology.org/2024.signlang-1.6/) / [arXiv:2403.02563](https://arxiv.org/abs/2403.02563)
- The 2016 **SignAloud** gesture-glove project is the canonical cautionary tale: Deaf Studies faculty said the inventors "obviously didn't check with the Deaf community," the device ignored facial expression and body-language grammar essential to ASL, and it was criticized as solving a problem the Deaf community wasn't asking to have solved. [NPR](https://www.npr.org/sections/alltechconsidered/2016/05/17/478244421/these-gloves-offer-a-modern-twist-on-sign-language), [ACM](https://cacm.acm.org/news/technology-for-the-deaf/)
- Follow-on commentary on "sign language gloves" broadly argues these projects put the burden of adaptation entirely on Deaf people (who must wear hardware) while offering limited vocabulary and one-directional (Deaf-to-hearing only) translation, rather than serving the Deaf community's own goals. [Liam O'Dell, "Lost in translation"](https://liamodell.com/2025/01/27/british-sign-language-bsl-artificial-intelligence-ai-chatgpt-signgpt-surrey-oxford-dcal-ucl-deaf-gloves/)
- General ASL-app research finds self-study apps are a reasonable starting point but real fluency and knowing whether a sign "looks natural" requires feedback from a live tutor or Deaf community members — apps should be framed as a **practice aid**, not a fluency authority or certification. [Preply ASL app roundup](https://preply.com/en/blog/apps-to-learn-sign-language/)
- Implication for this project: ship in-app copy that says "practice tool for fingerspelling drills," link out to Deaf-led instructional resources for real instruction, and avoid any claim of teaching "correct"/certified ASL — the confusable-pair analysis above exists specifically to reduce silent misteaching, not to claim authority.

## Recommendation

Self-record only for training data; no dataset found here is confirmed both permissively licensed and fit for MediaPipe landmark extraction at usable resolution/diversity. Do not reference or embed Lifeprint content in-app (explicitly prohibited); use Wikipedia/Commons and the plain-fact table above (original wording) instead, with citations. Build the classifier's hardest per-pair discrimination logic around thumb-tuck depth (M/N/T, A/S/T), finger spread (H/U/V), and wrist rotation (K/P, G/Q) as detailed above. Frame the app explicitly as a fingerspelling practice aid, not an ASL fluency or correctness authority, citing the Deaf-led AI-research critique and the SignAloud precedent in any public-facing "About" copy.
