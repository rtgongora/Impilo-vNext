# Clinical Procedures Pipeline — demonstrations traceability (§27/§28)

**Wave P15.** `audit.md` §29 ("Expected outputs") said "this audit is the first" — meaning no
traceability from the ten named demonstrations (`dak-baseline.md` §3) or the twenty-two named
tests (§27) to real, running proof existed anywhere. This is that traceability: for each
demonstration, what makes it real today, and which existing runtime-proof rig or unit test
actually exercises it — citing the proof rather than re-running it, so this document does not
duplicate engineering effort already spent proving the same fact.

The pipeline audit predates Waves P0–P14. Several of its own "absent" findings (site/side,
trainee/competence, sedation, specimen mismatch, recall, non-theatre settings) are now stale —
this document reflects the CURRENT state, re-verified against source during Wave P15, not the
audit's original text.

## Method

Each row cites the concrete migration/class/method that makes the demonstration executable and
the runtime-proof rig or test class that proves it. "Closed this wave" means Wave P15 added the
missing piece; "not closed" names the real, still-missing capability and its owner.

| # | Scenario | Status | What makes it real | Proof |
|---|---|---|---|---|
| 1 | Lumbar puncture, bedside | **Closeable today** | `procedure_episode.setting` (inpatient-service V300) carries BEDSIDE/WARD/CRITICAL_CARE/CLINIC; catalogue row `PROC-LUMBAR-PUNCTURE` (procedures-service V003, tagged "§28 demonstration 1") permits those settings; OROS result-review chain (`V013__result_observations.sql`, `V014__histopathology_report.sql`) | `procedures-site-side-journeys.sh` (setting/site/side schema), `procedures-catalogue-journeys.sh` (catalogue row itself) |
| 2 | Emergency chest drain | **Closeable today** | Catalogue row `PROC-CHEST-DRAIN` (V003, tagged "§28 demonstration 2") permits EMERGENCY setting; `AppropriatenessEngine.detectSiteAndSide` cannot be waived by emergency override (`AppropriatenessEngine.java:90-94`); `TheatreService.activateEmergencySurgery` (`TheatreService.java:229-260`) is the real rapid-intake path | `theatre-emergency-journeys.sh`, `AppropriatenessEngineTest` (site/side-under-emergency cases) |
| 3 | Paediatric guardian consent + child assent | **Closed this wave** | `mvumo.consent_request` has had real `assent_sought`/`assent_outcome`/`assent_notes`/`decision_maker_*` columns since P5 (V300), with a schema-level proof (`procedures-consent-depth-journeys.sh` J-P5-9) that a REFUSED child assent coexists with a GRANTED guardian consent — but **no Java code read or wrote any of those columns until this wave**. `ConsentRequestEntity` gained the mapped fields; `MvumoService.recordAssent` (deliberately NOT a state transition — assent and consent are separate acts) records them; `toRequestView` surfaces them distinctly from `state`. Catalogue requires `CONSENT-CHILD-ASSENT` for `PROC-PAED-LUMBAR-PUNCTURE`/`PROC-PAED-HERNIA-REPAIR` (V003) | `procedures-consent-depth-journeys.sh` J-P5-9 (schema) + `MvumoServiceAssentTest` (5 tests, Wave P15 — Java layer, including the demonstration's own point: a GRANTED consent and a REFUSED assent on the same request) |
| 4 | Obstetric procedure, Reproductive Pack governs | **Closeable today, one link unconfirmed** | `TheatreService.recordObstetricContext`/`recordDelivery`/`recordNeonatalHandover` (`TheatreService.java:397,491,545`) emit `theatre.obstetric.*` events; `pct-service`'s `TheatreObstetricConsumer` opens the baby's own episode idempotently on CPID; `PROC-CAESAREAN-SECTION` seeded (V003) | `theatre-emergency-journeys.sh` (obstetric/neonatal legs). NOT independently confirmed this wave: whether pct-service's `ConfidentialCarePolicyProvider`/`ReproductiveIntentionService` actually gate anything reachable from this specific path — that is pct-service's own remit, not re-verified here |
| 5 | Endoscopy sedation → biopsy → pathology → follow-up | **Closeable today** | `PROC-OGD` (V003, tagged "§28 demonstration 5") requires `SED-MODERATE`; `SafetyPauseAndSedationService` (P7); `SpecimenCustodyService` (P8); aftercare template `AFTERCARE-ENDOSCOPY` (P9) | `procedures-safety-pause-journeys.sh`, `procedures-p8-specimen-device-journeys.sh`, `procedures-recovery-aftercare-journeys.sh` — each proves one leg; no single rig walks all four in sequence, named as a gap below |
| 6 | Dialysis — recurring session | **Not closed** | `PROC-HAEMODIALYSIS` is seeded (V003, tagged "§28 demonstration 6") with `setting=DIALYSIS` and a complication profile (P10), but **no recurring/series model exists anywhere**: no `session_number`/`series_id`/`parent_episode_id` on `procedure_episode`, no vascular-access or dialysis-prescription entity, and `CONSENT-PROGRAMME` (the consent type a recurring programme of care would need) is never resolved by any Java code. Each session today would have to be an unlinked, one-shot `procedure_episode` — the demonstration's actual point (recurrence as a first-class fact) is unbuilt | — |
| 7 | Image-guided biopsy, specimen custody hand-off | **Closeable today** | `PROC-US-GUIDED-BIOPSY` (V003, tagged "§28 demonstration 7"); `SpecimenCustodyService.confirmLabel`/`recordReceipt` (P8) are real custody-transfer methods; P13's FHIR specimen ref | `procedures-p8-specimen-device-journeys.sh`, `procedures-fhir-specimen-journeys.sh` |
| 8 | Implant lot/recall traceability | **Closeable today, patient-notification leg unconfirmed** | `ImplantTraceabilityService.traceByRecall(udi, lot)` (`ImplantTraceabilityService.java:216-244`) is real and queries `inv_implant_registry`/`inv_patient_implant` for every affected patient | `procedures-p8-specimen-device-journeys.sh` proves the query returns the right patients. NOT built: any automatic notification (SMS/portal alert) from a recall match to the affected patient — recall reaches the record, not confirmed to reach the patient. Named debt, owner: inventory-service + notification-service |
| 9 | Cancelled procedure, rebooking, patient comms | **Closeable today, rebooking-link and comms unconfirmed** | OROS `ProcedureWorkflowState.CANCELLED` is terminal; `V300__procedure_request_lifecycle_depth.sql:26-49` makes `workflow_state_reason`+`workflow_next_action` mandatory on cancellation — the literal "request survives cancellation" mechanism (P2) | `procedures-request-lifecycle-journeys.sh` proves the mandatory-reason invariant. NOT built: a structural rebooking link (no `supersedes`/`original_order_id` column — a rebooking today is just a new, unlinked request) and confirmed wiring from cancellation to a patient-communication trigger (generic comms machinery exists in experience-bff's `AppointmentCommsWorkflowService`, tied to scheduling appointments, not confirmed reachable from procedure cancellation). Named debt, owner: oros-service (rebooking link) + experience-bff (comms wiring) |
| 10 | Complication escalating into Emergency/Surgery | **Closed (completion wave, 2026-07-30)** | The three gaps this row named are closed by `inpatient-service V305`. (a) **Complication-originated trigger**: `returnToTheatre` now requires a `complicationCategory` from a closed vocabulary (haemorrhage, sepsis, anastomotic leak, wound dehiscence, ischaemia, obstruction, retained item, device/implant failure, organ injury, planned second look, other) alongside a mandatory reason — the free-text `note.setComplications` is no longer the only record of why a patient went back. (b) **Predecessor linkage**: each return is its own row in `inpatient.procedure_return_to_theatre` with a sequence number and an optional link to the operative note it originated from, and `procedure_episode.reoperation_of_episode_id` covers the other shape, where the reoperation is given its own episode. (c) **A returned case is distinguishable**: the episode detail now carries its full list of returns rather than a single boolean. `PLANNED_SECOND_LOOK` must be flagged planned and nothing else may be — a planned relook counted as a complication corrupts the indicator this table feeds. The surgery-side counterpart is `surgery-service V010`'s audited `REOPENED` state (surgical demonstration 9) | `ProcedureReturnToTheatreTest`, extended `theatre-alt-journeys.sh` (real Postgres: cause and sequence stored, actor attributed, multiple returns counted, missing/invented category refused, planned-haemorrhage refused, self-referencing predecessor refused). Gated by tshepo-authz V034's existing `return-to-theatre` rows — no new policy row was needed, because the route is unchanged and only its body grew |

## What Wave P15 closed

- **Demonstration 3** (paediatric assent): `ConsentRequestEntity` field mapping, `MvumoService.recordAssent`, `MvumoInternalController` route, `MvumoServiceAssentTest` (5 tests).
- **§27 duplication detection**: `AppropriatenessEngine.detectDuplicateAndRecentEquivalent`'s `DUPLICATE_OPEN_REQUEST` branch has been real code since P2 with zero test coverage anywhere in the estate; `AppropriatenessEngineTest` gained 2 tests (asserted-true and asserted-false/null cases — null must not be read as "no open request exists" the way it must not be read as "open request exists" either).

## What Wave P15 did NOT close, named rather than silently dropped

1. **Demonstration 6 (dialysis recurrence)** — no session/series data model exists in `inpatient-service` for any recurring procedure. This is new schema plus new domain logic (session numbering, cross-session vascular-access continuity, a programme-level consent type actually being resolved), not a "wire the existing pieces together" task like demonstrations 1/2/5/7. Recommended as its own wave, sized independently — attempting it inside "P15: tests" would have meant building real capability under a wave labelled proof-only.
2. ~~**Demonstration 10 (complication reopens the episode)**~~ — **CLOSED by the completion wave
   on 2026-07-30.** Its row above carries the mechanism and the proof. Two things from how it
   closed are worth keeping.

   It was smaller than four waves of this document claimed. A reopen method had existed since
   Wave 4 (`ProcedureEpisodeService.returnToTheatre()`); what was missing was a
   complication-originated trigger, predecessor linkage and a distinguishable returned case — not a
   reopen path built from nothing. Documents that repeat a gap without re-reading the code grow
   the gap in the telling.

   Its stated blocker turned out not to exist. The recommendation to "run the ten theatre rigs
   first" was correct and was carried out: all ten ran, and every one matched its recorded baseline
   (`reports/journeys/theatre-gate-20260730/SUMMARY.md`). The rigs need Docker and `mvn package`,
   not the packaged estate five consecutive waves believed was required. That pass paid for itself
   immediately — it found that **every creation of a `procedure_episode` through JPA had been
   failing with an HTTP 500** since Wave P4, because V300's `setting NOT NULL DEFAULT 'THEATRE'`
   never reached a Hibernate insert. The whole theatre intake path was broken, elective and
   emergency alike, for the entire period the gate was deferred as too expensive to run.
3. **Demonstration 5's single combined walkthrough**: each of its four legs (sedation, specimen custody, pathology, aftercare) has its own real proof; no one rig chains all four for one patient in one session. Not attempted this wave — each leg's existing proof is not redundant with a combined walkthrough, but a combined rig adds confirmation value, not new capability, and was judged lower priority than closing demonstrations 3 and the §27 duplication gap, which were real, uncovered functionality/proof gaps rather than a presentation gap.
4. **Demonstrations 4/8/9's unconfirmed legs** (reproductive-pack gating, patient-notification-on-recall, rebooking-link + cancellation comms) belong to peer services (pct-service, notification-service, oros-service, experience-bff) and are named above with their owners, not attempted here.
