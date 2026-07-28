# Adult Medicine — completion register against the brief

**Source: [`brief.md`](brief.md).** One row per section, with the status and — where a section is
not complete — exactly what is missing and who owns it.

This file exists because the pack has twice been reported as more complete than it was. A summary
that says "the clinical decision-support backbone is complete" is true and was read as "the pack is
complete"; the backbone is a minority of the brief. The register is per-section so that reading is
not available.

**Convention: PARTIAL is never counted as delivered.** A section is DONE only when nothing in it is
outstanding.

| § | Section | Status | What is outstanding |
|---|---|---|---|
| 1 | Standards baseline + 8-part DAK decomposition | **PARTIAL** | 3 of the 8 DAK parts exist (recommendations, data elements, decision support). Personas, user scenarios, business workflows, indicators and functional/non-functional requirements were never produced. Content missing for stroke, chronic respiratory and neuro. |
| 2 | Domain boundary | **DONE** | — |
| 3 | Principal care settings | **PARTIAL** | One record across settings holds. Day-treatment, infusion, dialysis and anticoagulation clinic are not modelled as settings. |
| 4 | Canonical episode and problem model | **DONE** | — |
| 5 | 17-step adult journey | **PARTIAL** | The steps exist as surfaces; the journey is not modelled as a journey, so nothing tracks a patient's position in it. |
| 6 | Full medical clerking | **PARTIAL** | V106 covers social, family, functional, procedures and advance directives. Roughly 25 of the ~35 items are not structured, and the conditional logic ("do not re-document what is known") is not built. |
| 7 | Examination framework | **PARTIAL** | 22 regions, 6 states and the coverage read are DONE (V113). **The eleven graphics are modelled but not drawn** — `graphic`/`site`/`laterality` are stored and validated, and no diagram renders anywhere. |
| 8 | Thirteen specialty workspaces | **PARTIAL** | All thirteen exist as governed configuration over the shared spine. Each states its own `notBuilt` list on screen; those lists are the outstanding work, and they are long. |
| 9 | Multimorbidity engine | **PARTIAL** | 11 panels and 7 detections are built and honest about their own inputs. Appointments, investigations, functional status, patient priorities and the care team are **not composed by the BFF**, so those detections report UNDETERMINED. |
| 10 | Medicines and clinical pharmacology | **PARTIAL** | Reconciliation (V107) and deprescribing content exist; medication facts are now derived rather than asserted. No interaction checking, no renal/hepatic dosing engine, no formulary/refill/possession. |
| 11 | Diagnostic orchestration | **PARTIAL** | **Result action is built** (V115) and acknowledgement already existed in OROS, so §25's "reviewed and actioned" is now shown end to end at the record layer. Outstanding: order sets, appropriateness at ordering, and duplicate detection *at order time* — the §9 engine detects repeated investigations after the fact; preventing the duplicate before the needle is OROS's seam. |
| 12 | Procedures | **PARTIAL** | Indication, appropriateness and interpretation exist for 3 of the 18 named procedures. Execution is procedures-service's and is out of this pack's scope by design. |
| 13 | Inpatient medicine | **PARTIAL** | The patient-centred ward round composes admission and problem list. The ~18-item ward workspace §13 describes is not built. |
| 14 | Consultation and MDT | **PARTIAL** | Record, service, API, BFF and UI are built; ownership never moves on an answer and the screen says so unconditionally. Outstanding: the transfer-of-care act a consultation may *recommend* has no record of its own, so a takeover still has to happen outside the system. |
| 15 | Longitudinal monitoring and home care | **NOT BUILT** in this pack | telemonitoring-service exists and is claimed to cover much of this; that claim is **unverified** and should be checked before anyone builds a rival. |
| 16 | Clinical decision support | **DONE** | 12 governed packs, 383 fixtures, full provenance and override metadata. The pack's strongest asset. |
| 17 | African and Zimbabwean context | **PARTIAL** | HIV, TB and EDLIZ are deep. Malaria, sickle-cell and rheumatic heart disease have no content; stockouts, distance and connectivity are not modelled. |
| 18 | Integration | **PARTIAL** | PCT, CKP, zibo, OROS, BUTANO (Condition), procedures. Dura, Madi, TUSO, VARAPI, Khuluma, Nompilo, Ndila, Rito, Ruvimbo/COSTA, Fundo and Simba are not wired from this pack. |
| 19 | Data model and interoperability | **PARTIAL** | Three producers now: `Condition`, `ClinicalImpression` (the examination) and `DetectedIssue` (the §9 findings). PII excluded, unknown values mapped to null rather than guessed, and the six examination states kept distinct at the FHIR boundary. **Two caveats:** the CKP Kafka relay defaults to OFF (`impilo.clinical.kafka.relay-enabled`), so DetectedIssue rows accumulate in the outbox until it is enabled per environment; and a DetectedIssue that stops being detected is never retired — the engine emits nothing on resolution, so an archived issue stays FINAL. ~23 resources still have no producer. |
| 20 | Offline operation | **NOT BUILT** | Two verified blockers, **both in services this pack does not own**: `OfflineRulesEngine.READ_ACTIONS` has no `READ_PROBLEM`/`READ_PROGRAMME` (trust plane), and `offline-edge-service` replays `/fhir/Observation` only. The mobile seam itself works — `useOfflineStore` is live in production screens. Medicine would be *consuming* an existing seam, not building one. Routed, not absorbed. |
| 21 | Analytics | **PARTIAL** | 6 implemented, 6 partial, 9 not computable — see [`analytics-coverage.md`](analytics-coverage.md). Equity is **structurally** impossible here: PCT holds CPID and no PII by design. |
| 22 | Testing | **PARTIAL** | Unit, content, migration and mutation proofs are strong. The 25 named clinical scenarios are not covered as scenarios. |
| 23 | Required demonstrations | **PARTIAL** | The **record layer** is proven for all ten (`scripts/runtime-proof/medicine-demonstrations.sh`, 35/35, 8 stated gaps). The service and clinician layers are not. See [`demonstrations.md`](demonstrations.md). |
| 24 | Expected outputs | **PARTIAL** | Delivered: repository audit, medicine architecture, specialty map, HIV/TB DAK traceability, canonical problem model, decision-support catalogue, procedures integration, analytics, tests, completion report. Missing: DAK-style traceability for other specialties, full medical clerking, demonstration data. |
| 25 | Definition of done | **NOT MET** | Of the ten conditions: one longitudinal record ✔, specialty views on shared truth ✔, HIV/TB DAK traceable ✔, procedures through the common pipeline ✔ (for the 3 built). **results reviewed and actioned** ✔ at the record layer (§11 — OROS acknowledges, V115 records what it caused). Outstanding: problems/medicines/tests/procedures fully reconciled, multimorbidity managed coherently end to end, Emergency and surgical handoffs proven, **offline operation** (§20), **end-to-end journeys pass** (§23 beyond the record layer). |

## Totals

**3 DONE · 18 PARTIAL · 2 NOT BUILT** — against 4 DONE, 11 PARTIAL, 11 NOT BUILT at the start of this
programme. Only §15 and §20 remain untouched, and §20 is blocked in services this pack does not own.

The direction of travel matters more than the totals: the sections that moved are the ones other
sections depend on. §9, §7, §14 and the chronic registers were each blocking demonstrations, and the
demonstration rig grew from 23 to 35 assertions while its stated-gap count fell from 10 to 8.

## The three highest-value next pieces, in order

All three of the previous three are done. The next three, on the same reasoning — smallest step from
modelled to usable, then the gaps that make correct work invisible:

1. **Enable the CKP Kafka relay** (`impilo.clinical.kafka.relay-enabled`, currently defaulting to
   false). Until it is on, every `DetectedIssue` the §9 engine produces sits in an outbox. The code
   is complete and the finding is invisible anyway — one configuration decision, per environment.
2. **Compose the remaining §9 sources in the BFF** — appointments, investigations, functional status,
   patient priorities, care team. Four of the seven detections currently report UNDETERMINED because
   nobody sends them, not because the engine cannot answer. Each is one read.
3. **A transfer-of-care record.** A consultation can now *recommend* a takeover and deliberately
   cannot perform one, which is right — but nothing performs one either, so the act happens outside
   the system and the ownership column can only ever say what it said at request time.
