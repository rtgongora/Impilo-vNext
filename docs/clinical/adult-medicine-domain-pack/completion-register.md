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
| 6 | Full medical clerking | **PARTIAL** | V106 covers social, family, functional, procedures and advance directives. **The Save/Add buttons on social-history, family-history and advance-directives were previously dead** — the BFF had no write route for any of the five V106 tables, so nothing a clinician entered on those screens could ever reach `pct-service`. Now wired: `StructuredHistoryController` exposes five POST routes, `PctServiceClient` forwards to pct-service's append-only writes, and the three UI pages call the new `useMutation` hooks (`StructuredHistoryWritesTest`, plus UI wiring tests). Functional-assessment and procedure writes are exposed on the BFF but have no UI entry point yet. Roughly 25 of the ~35 items are not structured, and the conditional logic ("do not re-document what is known") is not built. |
| 7 | Examination framework | **PARTIAL** | 22 regions, 6 states and the coverage read are DONE (V113). **The eleven graphics are modelled but not drawn** — `graphic`/`site`/`laterality` are stored and validated, and no diagram renders anywhere. |
| 8 | Thirteen specialty workspaces | **PARTIAL** | All thirteen exist as governed configuration over the shared spine, each stating its own `notBuilt` list on screen (a test fails the build if any list is empty). **An estate sweep corrected those lists in ~14 places** — they were authored from the brief, not from a search. Already owned elsewhere, so integrate rather than build: transfusion planning (**madi-service**, a full blood service), glucose/device data (**telemonitoring**), tobacco cessation (**simba**), and **advance-care planning, which THIS PACK already built** (`pct_advance_directives` V106 + UI) and which was wrongly listed as missing on two lists. Five more have a real spine and need a specialty view rather than a system of record: endoscopy, biopsy, dialysis prep + vascular access, ECG/echo/spirometry, volume status. ~30 are correctly absent. |
| 9 | Multimorbidity engine | **PARTIAL** | 11 panels and 7 detections are built and honest about their own inputs. Appointments (booking-service), investigations (OROS, LAB/IMAGING only) and functional status (pct functional assessments) are **now composed by the BFF** — `excessive_visit_schedule`, `repeated_investigations` and `high_treatment_burden` move off UNDETERMINED, and `appointment_burden`, `functional_impact` and `monitoring_plan` move off unknown, all proven in `MultimorbidityControllerTest`. Patient priorities and the care team remain uncomposed **because no service in the estate owns either concept yet** (an advance directive is not "what matters to the patient day to day"; an MDT roster is not a standing care team) — those two panels correctly stay unknown until a first home for the data is built. |
| 10 | Medicines and clinical pharmacology | **PARTIAL** | Reconciliation (V107) and deprescribing content exist; medication facts are now derived rather than asserted. No interaction checking, no renal/hepatic dosing engine, no formulary/refill/possession. |
| 11 | Diagnostic orchestration | **PARTIAL** | **Result action is built** (V115) and acknowledgement already existed in OROS, so §25's "reviewed and actioned" is now shown end to end at the record layer. Outstanding, **restated after re-verification**: a duplicate-order guard DOES exist at placement time and is live — `OrderStateMachine.guardNoDuplicateTeleconsultOrder:218`, called from `placeOrder`/`createDraft`, 409, indexed by V015 — but it is **teleconsult-scoped and keyed on `sourceRef`**, so an ordinary in-clinic repeat is never checked. Broadening it to `patientCpid + ziboOrderCode` against the §9 engine's `repeatIntervals` is the change, and the hook point already exists. Order sets have **no backend concept at all** (`ui/…/data/orderSets.ts` is a hardcoded constant; `EncounterCart`/`OrderSetPicker` are dead — nothing renders them). Both OROS's. |
| 12 | Procedures | **PARTIAL** | Indication, appropriateness and interpretation exist for 3 of the 18 named procedures. Execution is procedures-service's and is out of this pack's scope by design. |
| 13 | Inpatient medicine | **PARTIAL** | The patient-centred ward round composes admission and problem list. The ~18-item ward workspace §13 describes is not built. |
| 14 | Consultation and MDT | **PARTIAL** | Record, service, API, BFF and UI are built; ownership never moves on an answer and the screen says so unconditionally. Outstanding: the transfer-of-care act a consultation may *recommend* has no record of its own, so a takeover still has to happen outside the system. |
| 15 | Longitudinal monitoring and home care | **ROUTED** to telemonitoring-service | **Claim now VERIFIED.** telemonitoring covers the machinery more deeply than the brief asks (per-reading device/validation/quality/timestamp, immutable approval-gated threshold chains, escalation ladder with accountable closure, review cadence, four mechanisms against false reassurance) and is fully reachable. **Do not build a rival:** its V004 made it the single designated SHR writer for monitoring-band Observations, closing a three-writer sprawl — an adult-medicine home-vitals table would be writer four. Outstanding, as extensions inside telemonitoring: symptoms, PROs, functional status, medication adherence, fluid status, dialysis data, peak flow; per-reading `performer` and clinical `context`; and a BFF/UI enrolment surface (plan create/approve and device issue are backend-only). **This pack owns one item:** a problem-list anchor from `tm_monitoring_plans` to `pct_problems` — copy surgery-service's `PctProblemContributionClient`. |
| 16 | Clinical decision support | **DONE** | 12 governed packs, 383 fixtures, full provenance and override metadata. The pack's strongest asset. |
| 17 | African and Zimbabwean context | **PARTIAL** | HIV, TB and EDLIZ are deep. Malaria, sickle-cell and rheumatic heart disease have no content; stockouts, distance and connectivity are not modelled. |
| 18 | Integration | **PARTIAL** | PCT, CKP, zibo, OROS, BUTANO (Condition), procedures. Dura, Madi, TUSO, VARAPI, Khuluma, Nompilo, Ndila, Rito, Ruvimbo/COSTA, Fundo and Simba are not wired from this pack. |
| 19 | Data model and interoperability | **PARTIAL** | Three producers now: `Condition`, `ClinicalImpression` (the examination) and `DetectedIssue` (the §9 findings). PII excluded, unknown values mapped to null rather than guessed, and the six examination states kept distinct at the FHIR boundary. **Two caveats:** the CKP Kafka relay still defaults to OFF (`impilo.clinical.kafka.relay-enabled`) — configured `true` for `impilo-full-preview` in `values-full-preview.yaml` and proven against a real embedded broker (`ClinicalKafkaOutboxRelayIT`), but that configuration is not yet deployed, so rows still accumulate in the outbox in every environment actually running today; and a DetectedIssue that stops being detected is never retired — the engine emits nothing on resolution, so an archived issue stays FINAL. ~23 resources still have no producer. |
| 20 | Offline operation | **NOT BUILT** | Two verified blockers, **both in services this pack does not own**. (a) `OfflineRulesEngine.READ_ACTIONS:51` in **`tshepo-offline-service`** — *corrected: this register previously said offline-edge-service* — holds only READ_PATIENT/MEDICATION/ENCOUNTER/OBSERVATION, and the gate is two-layered (the set, and the capability token's `allowed-offline-actions` filter at mint time). Adding the three reads to the set **and** the READ_PATIENT-covers branch at `:124` needs no token change. (b) `offline-edge-service` — the client is not the constraint; `OfflineEdgeService.replayActions:143` only dispatches to FHIR for two vitals action types. **Corrected:** `useOfflineStore` is live in **one** screen (`HouseholdListScreen.tsx:27`), not several — `ScreeningScreen.tsx:21` imports it and never calls it. Medicine would be *consuming* an existing seam. Routed, not absorbed. |
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

1. ~~Enable the CKP Kafka relay~~ **Configured, proven, not yet deployed.**
   `impilo.clinical.kafka.relay-enabled` is now `true` for `impilo-full-preview` in
   `values-full-preview.yaml`, and `ClinicalKafkaOutboxRelayIT` proves — against a real embedded
   Kafka broker, not a mock — that a real HTTP call to `/assess` produces a real outbox row the real
   relay drains onto the exact topic `butano-service` listens on, with the field names its consumer
   reads. The preview deploy that makes this live in the running estate is held for explicit
   authorization (dev-preview-sandbox.mdc); until then every `DetectedIssue` the §9 engine produces
   still sits in the outbox in the environments actually running today.
2. **Compose the remaining §9 sources in the BFF** — appointments, investigations, functional status,
   patient priorities, care team. Four of the seven detections currently report UNDETERMINED because
   nobody sends them, not because the engine cannot answer. Each is one read.
3. **A transfer-of-care record.** A consultation can now *recommend* a takeover and deliberately
   cannot perform one, which is right — but nothing performs one either, so the act happens outside
   the system and the ownership column can only ever say what it said at request time.
