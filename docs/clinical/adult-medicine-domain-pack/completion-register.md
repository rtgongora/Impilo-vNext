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
| 11 | Diagnostic orchestration | **NOT BUILT** | Order sets, appropriateness at ordering, duplicate detection at ordering, and result acknowledgement/action tracking. Note the §9 engine detects *repeated* investigations after the fact; preventing the duplicate at order time is OROS's seam and is not built. **This is the gap behind §25's "results are reviewed and actioned", which cannot currently be shown.** |
| 12 | Procedures | **PARTIAL** | Indication, appropriateness and interpretation exist for 3 of the 18 named procedures. Execution is procedures-service's and is out of this pack's scope by design. |
| 13 | Inpatient medicine | **PARTIAL** | The patient-centred ward round composes admission and problem list. The ~18-item ward workspace §13 describes is not built. |
| 14 | Consultation and MDT | **PARTIAL** | The **record** exists (V114) with the ownership and attribution invariants enforced by CHECK. The **service, API and UI do not** — nothing writes or reads it yet. |
| 15 | Longitudinal monitoring and home care | **NOT BUILT** in this pack | telemonitoring-service exists and is claimed to cover much of this; that claim is **unverified** and should be checked before anyone builds a rival. |
| 16 | Clinical decision support | **DONE** | 12 governed packs, 383 fixtures, full provenance and override metadata. The pack's strongest asset. |
| 17 | African and Zimbabwean context | **PARTIAL** | HIV, TB and EDLIZ are deep. Malaria, sickle-cell and rheumatic heart disease have no content; stockouts, distance and connectivity are not modelled. |
| 18 | Integration | **PARTIAL** | PCT, CKP, zibo, OROS, BUTANO (Condition), procedures. Dura, Madi, TUSO, VARAPI, Khuluma, Nompilo, Ndila, Rito, Ruvimbo/COSTA, Fundo and Simba are not wired from this pack. |
| 19 | Data model and interoperability | **PARTIAL** | `Condition` flows PCT → outbox → Kafka → BUTANO, with PII excluded and unknown certainty mapped to null rather than guessed. **The other ~25 resources have no producer.** `EpisodeOfCare`, `ClinicalImpression` (the examination), `CarePlan`, `DetectedIssue` (the §9 findings) and `Flag` are the highest-value next producers, and each is a small extension of the Condition pattern. A gateway allowlist is not a producer. |
| 20 | Offline operation | **NOT BUILT** | Two verified blockers, **both in services this pack does not own**: `OfflineRulesEngine.READ_ACTIONS` has no `READ_PROBLEM`/`READ_PROGRAMME` (trust plane), and `offline-edge-service` replays `/fhir/Observation` only. The mobile seam itself works — `useOfflineStore` is live in production screens. Medicine would be *consuming* an existing seam, not building one. Routed, not absorbed. |
| 21 | Analytics | **PARTIAL** | 6 implemented, 6 partial, 9 not computable — see [`analytics-coverage.md`](analytics-coverage.md). Equity is **structurally** impossible here: PCT holds CPID and no PII by design. |
| 22 | Testing | **PARTIAL** | Unit, content, migration and mutation proofs are strong. The 25 named clinical scenarios are not covered as scenarios. |
| 23 | Required demonstrations | **PARTIAL** | The **record layer** is proven for all ten (`scripts/runtime-proof/medicine-demonstrations.sh`, 30/30, 8 stated gaps). The service and clinician layers are not. See [`demonstrations.md`](demonstrations.md). |
| 24 | Expected outputs | **PARTIAL** | Delivered: repository audit, medicine architecture, specialty map, HIV/TB DAK traceability, canonical problem model, decision-support catalogue, procedures integration, analytics, tests, completion report. Missing: DAK-style traceability for other specialties, full medical clerking, demonstration data. |
| 25 | Definition of done | **NOT MET** | Of the ten conditions: one longitudinal record ✔, specialty views on shared truth ✔, HIV/TB DAK traceable ✔, procedures through the common pipeline ✔ (for the 3 built). Outstanding: problems/medicines/tests/procedures fully reconciled, multimorbidity managed coherently end to end, Emergency and surgical handoffs proven, **results reviewed and actioned** (§11), **offline operation** (§20), **end-to-end journeys pass** (§23 beyond the record layer). |

## Totals

**3 DONE · 17 PARTIAL · 3 NOT BUILT** — against 4 DONE, 11 PARTIAL, 11 NOT BUILT at the start of this
programme.

The direction of travel matters more than the totals: the sections that moved are the ones other
sections depend on. §9, §7, §14 and the chronic registers were each blocking demonstrations, and the
demonstration rig's stated-gap count fell from 10 to 8 as a direct result.

## The three highest-value next pieces, in order

1. **§14's service and UI.** The record exists with its invariants enforced; nothing writes to it.
   This is the smallest remaining step from "modelled" to "usable" anywhere in the pack.
2. **§19 producers for `ClinicalImpression` and `DetectedIssue`.** Each is a small extension of the
   Condition producer already proven. Without them the examination and the multimorbidity findings
   are invisible outside the facility that recorded them.
3. **§11 result acknowledgement.** It is the one §25 condition with no partial credit at all, and
   the failure it prevents — a result nobody actioned — is the one that reaches patients fastest.
