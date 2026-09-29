# Handspell legal documents: strict rubric and language audit

Date: 2026-09-28. Rubric: `/mnt/drive/Claude/ai-slop-rubric.md`, version 2026-09-09. Audited source: `/home/earthstone/projects/shipaton-static-alphabet`, copied from `/mnt/drive/Work/Project/Shipaton`.

## Verdict

**The legal surface needs substantive corrections before public distribution. Changing the tone alone will not resolve its failures.** The privacy notice is specific to Handspell and largely understandable. It also contains conversational asides, development details, promises about future features, and claims broader than the available evidence. Its biggest gaps are an accountable operator and contact, Terms, and the lifecycle of RevenueCat-held information.

Strict score for the three assessed domains: **9/28 = 32.1%**. This falls below Appendix B's 40% boundary **for this legal/trust/copy subset**; it is not a whole-app score. The engineering and operational domains were not audited comprehensively.

The proposed public-dataset integration creates a separate attribution/provenance obligation. This report records the pre-integration state; it does not assume that ASLYset assets or final attribution have already been added. The integration must replace the existing self-record-only statements and preserve the dataset's actual license and attribution.

## Scope and evidence limits

Read: `docs/PRIVACY.md`, `docs/LEGAL_OPEN_ITEMS.md`, `NOTICE`, `LICENSE`, every bundled `assets/legal/*` file, `LegalScreen.kt`, `PaperScreen.kt`, Settings and paywall UI/string resources. Cross-checked camera handling, debug capture/export, local progress deletion, WebView storage/network handling, preferences, billing initialization, and release configuration.

Applied rubric §§1.1, 1.2, 1.6, 1.9, 1.11–1.12, 2.1–2.2, 3.7, 4.1, 4.3 and Appendix B. The rubric's statutory claims were treated as audit prompts, not proof that every jurisdiction applies. The operator, release audience, countries, ages, and RevenueCat account agreement remain unknown. No statutory violation, corporate status, or invented lawful basis is asserted.

This was a document and source-code audit. No device session, traffic capture, RevenueCat dashboard, consent records, storefront, final APK, legal review, or monitored-contact test was available. No app files were changed. The writable copy had no Git metadata, so a commit identifier was unavailable.

At inspection, the source/bundled copies matched byte-for-byte:

| Source | Bundled copy | Result |
|---|---|---|
| `docs/PRIVACY.md` | `android/app/src/main/assets/legal/PRIVACY.md` | Identical; SHA-256 `15c6e89c7c3fe699c2c40ce82d0f53483e8e3c879f0cece65723b9afca89a2f0` |
| `NOTICE` | `android/app/src/main/assets/legal/NOTICE.txt` | Identical |
| `LICENSE` | `android/app/src/main/assets/legal/LICENSE.txt` | Identical |
| `android/app/src/main/assets/models/LICENSE` | `android/app/src/main/assets/legal/MODEL_LICENSE.txt` | Identical |

Line references below refer to this inspected snapshot. Source privacy/notice/license findings also apply to their identical bundled copies.

## Strict scoring

Appendix B defines only the endpoints, 0 = absent and 4 = solid. For reproducibility, this audit uses 1 = substantial gaps, 2 = partial and inconsistent, 3 = mostly evidenced with limited gaps, 4 = solid within the assessed scope. Missing evidence earns no positive credit. These intermediate anchors are this auditor's explicit interpretation, not additional rubric text.

| Domain | Score | Weight | Earned / maximum | Reason |
|---|---:|---:|---:|---|
| Legal/compliance | 1/4 | ×3 | 3/12 | App-specific privacy and several license texts exist; operator/contact, Terms and vendor lifecycle are unresolved; material claims need correction. |
| Trust & safety | 1/4 | ×2 | 2/8 | Simulated-purchase disclosure and a neutral dismissal exist. About contains only version information; no accountable person/team or monitored contact. |
| AI-specific cues, limited to legal copy and claims | 2/4 | ×2 | 4/8 | Little stock LLM marketing prose and no dummy identity in bundled documents. Development narrative, hypothetical features, unsupported absolutes and stale provenance reduce confidence. Dependency/security/LLM implementation checks were not scored. |
| Resilience/engineering | Not scored | ×2 | Excluded | Some implementation was read to verify statements; this is not a resilience/security/accessibility test. |
| Operational | Not scored | ×1 | Excluded | No operational evidence review. |
| **Assessed subtotal** | | | **9/28 = 32.1%** | Denominator = 4 × (3 + 2 + 2), not the whole-app maximum of 40. |

Because the AI domain is restricted to the requested document scope, the subtotal is a scoped editorial/legal diagnostic, not a certification using all five complete rubric domains.

Quick triage: **5 failures out of 8 assessed applicable items**. The rubric's 4–9-failure band is “rough MVP, fixable”; the full 25-item checklist has not been completed.

| Triage item | Result | Evidence / qualification |
|---|---|---|
| 1: Specific and complete privacy policy | Fail | Specific data/purposes exist, but responsible contact, legal-basis decision where applicable and vendor retention are missing (`docs/PRIVACY.md:3,61–66`; `docs/LEGAL_OPEN_ITEMS.md:7–10`). |
| 2: Terms with real operator, jurisdiction and contact | Fail | Explicitly not drafted/bundled (`docs/LEGAL_OPEN_ITEMS.md:3–4`; `PaperScreen.kt:45–55`). |
| 4: Monitored contact | Fail | Explicitly unresolved (`docs/LEGAL_OPEN_ITEMS.md:8`); none in inspected user-facing surfaces. |
| 5: About / accountable identity | Fail | Student-project label only; About renders a version row (`SettingsScreen.kt:215–225`). No person/team responsible is identified. This rubric failure does not establish that incorporation is legally necessary. |
| 6: Substantiated social proof | Pass in this surface | No testimonials, usage totals, certification badges or customer logos were found in the reviewed legal/settings/paywall content. |
| 7: Clear subscription/cancellation flow | Pass for simulated demo only | Price/period, simulation disclosure, restore and neutral “Not now” exist (`PaywallScreen.kt:123–180`; `strings.xml:67–94`). Real subscription cancellation was not exercised. |
| 18: Data export/deletion lifecycle | Fail as an aggregate | Local progress deletion is implemented; no user progress export or operator-directed RevenueCat deletion/request process was evidenced. No server-backup obligation is inferred for local-only progress. |
| 21: No production placeholders | Pass in bundled legal copy | Owner placeholders are confined to explicit internal open-items documentation. They are not a dummy identity presented to users. Future-feature text is separately penalized for precision. |

Denominator: 25 − 2 conditional N/A items − 15 outside-scope/unverified items = 8. Item 3 is provisionally N/A to this build's lack of analytics/marketing tags, not a legal opinion about all WebView/device storage; item 24 is N/A to a bot/chat disclosure check because there is no conversational agent here. Items 8–17, 19–20, 22–23 and 25 are outside this document audit or require other evidence; none receives a pass. Accessibility item 25 remains unverified pending device/TalkBack/text-scaling and contrast checks.

### Language grades, separate from the rubric

The supplied rubric has no “formal sounding” scale. The following supplemental grades measure professional precision, using the same 0–4 anchors; they are **not added** to the weighted rubric score. Plain language, contractions, “you,” and descriptive headings are acceptable. Pretentious legalese would not earn extra credit.

| Surface | Grade | Assessment |
|---|---:|---|
| Privacy notice | 2/4 | Useful concrete data description, weakened by conversational advocacy, future promises, jargon and overbroad claims. |
| Legal open items | 4/4 as an internal document | Direct, explicit and appropriately refuses invented facts. It is a work tracker, not a final policy. |
| MIT project license | 4/4 for text/tone | Standard text; retain it verbatim. This grade does not establish ownership of every contribution or turn it into Terms. |
| Third-party notice | 3/4 | Mostly factual; includes internal process references and needs a more complete attribution inventory. |
| Legal rendering | 2/4 | Readable headings/paragraphs, but links are rendered as inert text and Markdown URLs can lose their destination. |
| Settings legal/privacy copy | 3/4 | Mostly plain and specific; camera/model assurances need scope and provenance corrections. |
| Paywall copy | 3/4 for simulated demo | Clear simulation/dismissal, but real-store instructions and vague future-pack value do not fit a final subscription agreement. |

Supplemental total: **21/28 = 75%** for language/usability only. This illustrates why readable prose does not establish a legally complete product.

## Findings and concrete revisions

Severity: High = material misleading assurance or missing accountability/contract/lifecycle surface; Medium = meaningful precision, accessibility or provenance gap; Low = tone/editing issue. Pending integration findings are marked separately.

### H1 — No responsible operator, privacy/support contact, or Terms

Evidence: `docs/PRIVACY.md:3`; `docs/LEGAL_OPEN_ITEMS.md:3–10`; `SettingsScreen.kt:215–225`; `PaperScreen.kt:45–55`. The label “student hackathon project ... not a company product” describes context but identifies no accountable operator. Neither the MIT license nor a privacy notice supplies app-specific subscription/service Terms.

Rubric: §§1.1, 1.11, 2.1; triage 1, 2, 4, 5. For an eventual Google Play listing, Google's policy explicitly calls for developer information, a privacy inquiry route and retention/deletion disclosure, as well as an accessible public policy URL. These are release requirements to resolve, not proof that this prototype has been submitted to Play. [Google Play User Data policy](https://support.google.com/googleplay/android-developer/answer/10144311)

Revision approach: identify the actual responsible person/team/entity and genuine contact in the opening section; draft app-specific Terms only after the owner supplies jurisdiction and commercial scope. Retain unresolved values in the internal tracker. Do not ship guessed names, addresses, inboxes, or governing law. A natural person may be the actual operator; the rubric's preference for an entity does not justify inventing a company.

### H2 — RevenueCat retention and rights lack an executable owner-side process

Evidence: `docs/PRIVACY.md:61–66` lists an app identifier, device/OS, purchase data and last-seen information; `docs/LEGAL_OPEN_ITEMS.md:10` leaves vendor lifecycle open. `SettingsViewModel.kt:107–113` calls only `AlphabetStorage.clear()` and `progressStore.clearAll()`; it does not delete RevenueCat data or subscription records. There is no reviewed user export or operator request route.

Rubric: §§1.1–1.2, 3.7, 5. RevenueCat says end-user processing is governed by the customer's agreement/DPA and policy; end users should contact that customer for changes or deletion. A link to RevenueCat's own policy therefore does not replace Handspell's process. [RevenueCat privacy policy](https://www.revenuecat.com/privacy)

Concrete local-deletion sentence: **“Delete practice data removes your Alphabet checkmarks and camera/lesson progress from this phone. It does not cancel a subscription or delete information held by RevenueCat.”**

Owner-dependent additions: a real contact and identification method for billing-data requests, chosen retention periods or criteria, relevant exceptions, and the actual process to instruct RevenueCat. Confirm which DPA/agreement governs this account before claiming it has been accepted or that particular transfer safeguards apply.

### H3 — “Not shared with anyone else” is an unjustified downstream guarantee

Evidence: `docs/PRIVACY.md:65–66` ends with “it is not shared with anyone else.” App code can show the app's integrations; it cannot establish every disclosure by a processor. RevenueCat's current DPA allows subprocessors and addresses transfers. The wording could reasonably imply that none exist. [RevenueCat DPA](https://www.revenuecat.com/dpa)

Rubric: §§1.1, 2.1. Replace with scoped wording: **“Handspell uses RevenueCat to manage Pro access and purchases. RevenueCat processes the information described here under the agreement for this integration.”** Add verified subprocessor/transfer information where applicable; do not replace one absolute with another unsupported promise. Describe the SDK-generated identifier as a generated identifier rather than implying that the data is irreversibly anonymized.

### H4 — Test Store assurances are unconditional while billing mode is not enforced by this source

Evidence: `docs/PRIVACY.md:14–15,31–34,70–72`; `strings.xml:67,80,92`. `android/app/build.gradle.kts:22,43` accepts an arbitrary property as the SDK key; `AppContainer.kt:40–44` treats any nonblank key as configured; `RevenueCatEntitlementGate.kt:27–30` initializes it without a Test Store mode assertion. `android/app/build.gradle.kts:47–50` makes release non-debuggable.

The same notice says judges receive an `assembleRelease` artifact with no capture screen and that every judge build uses Test Store. Current RevenueCat documentation says a Test Store key deliberately fails in a non-debuggable Android build unless an explicit internal-only override is supplied. That override is not present in this builder. This is a concrete inconsistency requiring a final-artifact check, not a witnessed crash or a claim that a real payment occurred. [RevenueCat Test Store documentation](https://www.revenuecat.com/docs/test-and-launch/sandbox/test-store)

Rubric: §§1.1, 2.1–2.2. Define and verify the exact judge/demo artifact and billing mode. Display “No money is charged” only when that artifact has a verified simulated configuration. Prefer **“This demonstration build uses simulated purchases through RevenueCat Test Store.”** For a real-store release, separately provide appropriate purchase/renewal/cancellation/refund Terms and links. Do not carry simulation copy into a live purchase flow.

### M5 — Exclusive-network-host claim has insufficient evidence and underspecifies the calls

Evidence: `docs/PRIVACY.md:13–15,55–59,78–79` says the only connection/host is RevenueCat and describes it chiefly as an entitlement check. Code also loads plans, purchases and restores (`RevenueCatEntitlementGate.kt:71–143`). `network_security_config.xml:10–13` bans cleartext but does not block HTTPS to all other hosts. The notice correctly acknowledges that limitation, but asserts traffic verification without the reviewed trace.

Rubric: §§1.1, 2.1. This audit did not disprove the host claim; it did not verify it. Replace the unsupported exact-host assurance with **“When Pro purchases are configured, the app uses RevenueCat to load plans, manage purchases, restore access and check Pro status.”** Preserve the better-supported on-device camera statement. Keep precise endpoint inventories and traffic evidence in technical release records, and promote an exclusive-host claim only after the full relevant flow is verified.

### M6 — Current behavior and prospective calibration are mixed together

Evidence: `docs/PRIVACY.md:11–13,43–51` describes a possible future “Improve recognition” UI, 24 measurements per letter, 66 numbers, and promised deletion. `PersonalCalibration.kt:29–47,79–86` provides a bounded backend foundation; `AppContainer.kt:36–66` does not wire that store/session into the current app.

Rubric: §§1.1, 4.1, 4.3. These are clearly qualified as future behavior, so they are not a false claim that the feature is live. They still clutter a current privacy notice and create commitments before the real UX is available.

Concrete current-version wording: **“Personal calibration is not available in this version.”** Put the 24/66-number implementation details and proposed future policy in internal documentation. Before the feature ships, replace that sentence with disclosures matching the actual collection, retention, choice and deletion behavior.

### M7 — Camera permission is overstated; progress retention and preferences are incomplete

Evidence: `docs/PRIVACY.md:76–77` says camera permission is required “for the app to work at all.” The start destination is the bundled Alphabet (`HandspellApp.kt:126–147`), and permission-free content/settings can open. Change to **“Camera: required for live handshape practice. You can browse the Alphabet and use other available screens without camera access.”**

`docs/PRIVACY.md:38–42` describes counts/results, but has no clear duration/criteria for the overall local store and does not describe the saved appearance preference. `DataStoreProgressStore.kt:33–47,59–80` stores practice timestamps and best-match timing; `AppPreferencesStore.kt:17–28` saves a separate theme choice. A 50-result cap is not a complete retention period. State how long the local stores persist, which Settings action removes which data, and that theme settings are separate, after validating uninstall/platform behavior for the supported build. Do not equate deletion of practice with removal of all SDK or preference state.

Rubric: §§1.1, 3.7. Backup exclusion (`AndroidManifest.xml:31–33`) supports the local-only intent; it is not a verified behavior test across Android/OEM variants.

### M8 — Legal links are inert, and Settings has no direct document entry

Evidence: `LegalScreen.kt:82–89,160–174` uses `Text(AnnotatedString)` with no link action. The inline parser matches Markdown links but appends only their label; it discards their URL and adds no link annotation. Bare URLs remain inert text. This hinders access to vendor policies/source licenses. `PaperScreen.kt:45–55` exposes actual documents through the Alphabet paper route (`HandspellApp.kt:140–141,254–263`), while `SettingsScreen.kt:78–82,196–225` includes privacy summaries without a document link.

Rubric: §§2.1, 4.1; accessibility relevance from triage 25, without a conformance verdict. Add accessible link behavior for appropriate HTTPS sources, retain visible destinations or meaningful labels, and offer a direct Privacy/Documents action in Settings. Confirm screen-reader link naming and link focus on device. Do not alter or truncate license text to make it look more polished.

### M9 — License inventory does not demonstrate complete native dependency notice coverage

Evidence: `NOTICE:6–30` names MediaPipe, AndroidX, Material Symbols, RevenueCat and Capacitor. The full Apache model and two Capacitor MIT texts are bundled. `NOTICE:26–27` gives only a RevenueCat MIT label and repository link; none of the inspected bundled files contains its upstream copyright notice and full license text. The Handspell MIT text identifies Handspell contributors and is not a replacement for third-party copyright notices.

Rubric: §1.9. Inventory direct and transitive redistributed components for the actual resolved build. Verify the final APK's license resources; add the relevant upstream notices/full text if absent. RevenueCat's upstream MIT license contains a copyright/permission notice retention condition. [RevenueCat Android SDK license](https://raw.githubusercontent.com/RevenueCat/purchases-android/main/LICENSE)

This is an attribution-completeness finding, not a final license-violation conclusion without artifact inspection. Avoid claiming “all notices preserved” solely because a summary file exists. Change `NOTICE:3–4` to a user-relevant introduction such as **“Handspell includes the third-party components listed below. Each component remains subject to its own license.”** Internal `docs/QUALITY.md` references belong in the maintenance record.

### L10 — Conversational commentary reduces precision without improving comprehension

These are editing issues unless tied to one of the substantive findings above:

| Existing passage | Evidence | Professional plain-language alternative |
|---|---|---|
| “treat it as a drill partner, not a teacher” | `docs/PRIVACY.md:19–21` | “Recognition feedback may be inaccurate. Handspell is a practice aid and does not provide a professional assessment or certification of ASL proficiency.” |
| “none of it touches the network in the first place” | `docs/PRIVACY.md:27–29` | “Camera frames and handshape classification are processed locally without a network connection.” |
| “a teammate moves the files off it by hand ... never automatically and never to us” | `docs/PRIVACY.md:31–34` | “Development builds include an optional tool that saves hand-landmark CSV files locally. A developer can export those files using USB or the system share sheet.” |
| “It never asks you anything and collects nothing” | `docs/PRIVACY.md:80–81` | “Vibration provides haptic feedback for Alphabet interactions.” |
| “there is no mailing list ... check back here” | `docs/PRIVACY.md:94–95` | “The effective date appears at the top of this notice. Updated notices are included with app updates.” |
| “as new packs arrive” | `strings.xml:82` | Describe the story lessons and speed challenges currently available; make future availability a separate, accurate statement if needed. |
| “recorded reference hands” | `strings.xml:71` | “Recognition uses reference handshape data bundled with the app. J and Z require motion and are excluded from camera drills.” |

The debug export rewrite describes the developer's action accurately; a share-sheet destination is chosen by that developer and can itself transmit files. The current “never to us” is especially confusing when “us” is the same team recording and receiving training data.

The “does not grade” wording also needs precision: the code produces matches, attempts, best times and speed results. Those are useful practice feedback, but a blanket denial of grading can appear inconsistent with the app's scoring vocabulary. Distinguish recognition estimates from professional certification.

Keep useful plain phrases such as “What is stored on your phone” and “Delete practice data.” There is no need to replace them with dense legal headings. Keep MIT/Apache license text unchanged.

## Incoming ASLYset integration: required provenance/attribution updates

Status: **High priority conditional release finding**, not an assertion that the planned integration is already unlicensed. The parent workstream is investigating/adding ASLYset under reported CC BY 4.0 terms. Its canonical source, authors and license evidence must be recorded by that workstream before relying on the claim.

Current statements requiring replacement or a dated historical qualifier:

| Location | Existing issue |
|---|---|
| `training/DATA.md:3–4` | All data is asserted to be self-recorded; nothing is asserted to be licensed or third-party. |
| `training/DATA.md:55` | “No third-party dataset was used.” |
| `training/DATA.md:7` | Capture screen is described as unwritten despite the existing `src/debug` implementation. |
| `docs/CLASSIFIER.md:100` | Stage-1 heading specifies self-recorded exemplars. |
| `docs/research/data-and-asl-reference.md:18–21` | Current verdict favors a self-record-only shipped classifier. Preserve the earlier rationale as history if useful, but identify the new source. |
| `android/app/src/main/assets/classifier/README.md:38–39` | Says no capture/session/reference asset exists; reconcile with actual bundled assets. |
| `NOTICE` / bundled `legal/NOTICE.txt` | No incoming dataset attribution or transformation record. |
| `strings.xml:71` | Model explanation assumes recorded reference hands; use a source-neutral accurate explanation or explicitly name the public dataset. |

The release record should retain the canonical dataset URL, actual creator/attribution names, source release/version, available copyright/license notices, license URL, relevant source files/checksums, transformation details and final generated artifacts. Document whether the downloaded data is source-authorized rather than a relabelled re-upload. Distinguish public source signers from teammate consent; do not populate the existing teammate consent table for people whose consent was obtained by a dataset publisher.

For redistributed material or applicable derived dataset assets, CC BY 4.0 calls for retained attribution/notice information, a license reference, source reference where practicable, and an indication of modifications. It also preserves other rights such as privacy/publicity and does not grant endorsement. Put practical attribution in root `NOTICE` and the in-app bundled notices, retain a durable license record, and describe conversion/filtering/normalization honestly. Handspell's MIT code license should not suggest the public dataset has been relicensed as MIT. [CC BY 4.0 legal code, sections 2–4](https://creativecommons.org/licenses/by/4.0/legalcode.en)

Whether a particular trained model is adapted copyright material is not settled by this audit; preserve attribution rather than inventing a universal conclusion about model licensing. Do not automatically claim that CC BY-SA would relicense every independent file in the repository, or that all CC BY-NC material legally bars any paid feature: `training/DATA.md:93–95` currently makes those broad conclusions without an asset-specific analysis. Those claims should be narrowed when refreshing provenance.

Do not claim cross-signer accuracy, language authority, benchmark results or production readiness merely because 24 letters now have references. Describe demonstrated support and its tested limitations. Classification remains on device; using public training data does not itself imply that a user's camera data is uploaded.

## Owner information still needed

1. Actual responsible operator/person/team/entity; location and intended countries of distribution. A corporate entity must not be invented.
2. Real monitored privacy/support contact and who handles requests. No outbound test message was sent in this audit.
3. The exact judge/demo/public artifact, selected billing mode and whether any real subscription is intended.
4. Terms choices: governing law subject to applicable consumer protections, eligibility, current paid benefits, renewal, cancellation and refund handling. No jurisdiction or refund promise was guessed.
5. RevenueCat account agreement/DPA status, retained categories, retention/deletion criteria, subprocessors/transfers as applicable, and an operational request workflow. Access to the account identifier must be handled without requiring names or emails the app does not collect.
6. Audience and age assessment. `docs/PRIVACY.md:88–90` says the app is not directed to under-13s; no supporting audience/marketing assessment was reviewed. No blanket COPPA/GDPR exemption follows from that sentence or from the absence of named accounts.
7. Evidence for teammate consent where teammate captures are retained, plus the incoming dataset's canonical license/provenance. The empty table in `training/DATA.md:45–51` is not proof of signed consent.

## Recommended order

First correct misleading assurances and finish the dataset's provenance/attribution in the actual release. Then supply operator/contact and the RevenueCat lifecycle, draft Terms using those facts, and replace current/future development narration with current-version disclosures. Finally make document/source links accessible and check the exact demo/release artifact, payment mode, deletion behavior, traffic and legal-file synchronization.

The audit does not authorize fabrication of owner details or sweeping “compliant” language. It provides concrete text and evidence so those missing facts can be supplied and the resulting documents reviewed.

## Post-integration addendum — 2026-09-28

This targeted follow-up reviewed the updated root/bundled `NOTICE`, `training/DATA.md`, `training/data/aslyset/{LICENSE.txt,provenance.json}` and `LegalScreen.kt`. **The 9/28 (32.1%) grade above remains the dated pre-integration grade.** This narrow review does not replace it with a whole-app score or re-audit changes in other workstreams.

The dataset attribution gap is addressed in the reviewed source: `NOTICE:34–47` now names Miguel Rivera, ASLYset V1, DOI/source and CC BY 4.0; explains mirroring, landmark extraction, normalization and exemplar selection; and separately identifies the derived illustrations, retained A–D references and absence of endorsement. Root and bundled notices match byte-for-byte (current SHA-256 `c1cad3c33a7f0aa8b15e9dd3984f00224ac5ae80056b3474710b9ef33049a680`). The canonical release independently confirms the contributor, 2019 publication, V1, DOI and CC BY 4.0. [ASLYset original release](https://data.mendeley.com/datasets/xs6mvhx6rh/1)

`training/data/aslyset/LICENSE.txt:1–8` supplies a license/source reference and modification notice; a license URI is permitted by CC BY 4.0, so the lack of verbatim full legal code here is not itself a deficiency. `provenance.json` records archive/model/reference hashes, extraction counts, opaque source signer IDs and transformation details. The bundled reference CSV's SHA-256 matches the recorded reference hash. The wording distinguishing MIT application code from CC BY 4.0 ASLYset-derived vectors and illustrations is clear; it does not silently relicense the source data as MIT. This confirms documentation and one local artifact hash, not the original volunteers' consent/privacy-rights clearance or a legal determination about every generated asset.

The inert-link part of finding M8 is corrected in source: `LegalScreen.kt:163–181` now creates `LinkAnnotation.Url` for Markdown HTTP(S) links and bare HTTP(S) URLs, retaining Markdown destinations and separating trailing punctuation. Device interaction, link appearance, focus and TalkBack semantics still require runtime verification; the Settings-entry portion was not re-reviewed in this follow-up.

Remaining provenance precision issues in the inspected `training/DATA.md` should be corrected: lines 11–13 describe the whole current set as desktop-recorded; lines 18–20 still await the capture screen that line 10 says is available; lines 22–24 broadly deny saved image-landmark/metadata rows despite current debug capture and public-source processing; lines 86–87 call FSBoard the only possible source of cross-signer numbers despite the new ASLYset source. Scope historical limitations to the original team-recorded A–D subset and distinguish its missing signer records from ASLYset's retained source IDs. The broad CC BY-SA/NC conclusions at lines 96–98 also remain unqualified.

Operator/contact/Terms remain unresolved by design. The earlier vendor lifecycle, billing-mode, privacy-claim and native-dependency-license findings are not closed by dataset attribution or link annotations; they need their own evidence or a subsequent review. No new owner details, consent records or legal conclusions were invented. Only this audit report was edited in the follow-up.
