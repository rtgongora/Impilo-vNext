# Surgery and Surgical Specialties — demonstrations traceability (§3/§24)

**Wave SB-6.** `audit.md` finding 24 rates the ten demonstrations `dak-baseline.md` §3 requires as
PARTIAL: "theatre rigs prove elective, emergency, obstetric, day-case, cancellation, complication
journeys at case level; none of the ten as written (they span the whole course)." This is that
traceability — for each demonstration, what makes it real today across S0-SB-4/SB-6, and which
runtime-proof rig or test class actually exercises it, citing the proof rather than re-running it.
Mirrors `docs/clinical/procedures-pipeline/demonstrations-traceability.md` (Wave P15) exactly, for
the sibling spec this program also builds.

## Method

Each row cites the concrete migration/class/method that makes the demonstration executable and
the runtime-proof rig or test class that proves it. "Closed" means the wave named made it real;
"not closed" names the still-missing capability and its owner, not a euphemism for "someday."

| # | Scenario | Stages | Status | What makes it real | Proof |
|---|---|---|---|---|---|
| 1 | Elective hernia, clinic to operation to follow-up | 1-20 | **Closeable today** | One `surgical_episode` (S1, V002) spans the whole course: `pct_problem_ref` anchors the diagnosis, `surgical_assessment` (S2, V003) the workup, `surgical_decision` (S3, V004) the decision-to-operate, `surgical_prehab_item` (SB-1, V005) optimisation, `mvumo.consent_request` (P5) consent, `inpatient.procedure_episode` (P4) the operation itself, `surgical_followup` (SB-2, V006) the discharge plan — one row per episode in each table, joined by `surgical_episode_id`/`pct_problem_ref`, never four unrelated records | `surgery-episode-journeys.sh`, `surgery-assessment-journeys.sh`, `surgery-decision-journeys.sh`, `surgery-complication-prehab-journeys.sh`, `surgery-longitudinal-followup-specialty-journeys.sh` — each proves one leg; no single rig walks all six in one patient's session, named as a gap below (same class of gap as P15's own demonstration 5) |
| 2 | Emergency laparotomy from Emergency through theatre and complication monitoring | 1, 3-4, 9-14 | **Closeable today** | Nothing in `surgical_prehab_item` or `mvumo` financial/optimisation content is a mandatory gate before `inpatient.procedure_episode.startProcedure` — an emergency case simply has no rows there, which is itself the bypass; `requireFinancialClearance` (P12, V302) explicitly short-circuits for `triage_priority IN ('EMERGENCY','IMMEDIATE')` before COSTA is even asked; `requireSiteAndSideConfirmed` (P4) is called BEFORE the financial gate and is **never** waivable by emergency override — the one gate this demonstration says must NOT be bypassed, isn't | `procedures-financial-clearance-journeys.sh` J-P12-9/10/12 (SB-6 extension — an EMERGENCY-triage episode reaching `EMERGENCY_OVERRIDE` with no BLOCKED_* value, proven against real Postgres, not just the mocked `ProcedureFinancialClearanceTest`), `procedures-site-side-journeys.sh` (site/side never waivable) |
| 3 | Breast cancer: assessment, MDT, surgery, histology, oncology handoff | 1-6, 11-13, 17-20 | **Closeable today, MDT representation unconfirmed** | The episode cannot close on unreviewed histology: `SurgicalEpisodeService.requireHistologyReviewed` (SB-1) refuses the CLOSED transition on `SPECIMEN_STATE_UNKNOWN`/`HISTOLOGY_UNREVIEWED`; `surgical_decision.final_decision`/`decided_by`/`decided_at` (S3) is a real, enforced 3-way pair that CAN carry an MDT's decision as free text | `surgery-complication-prehab-journeys.sh` (histology gate), `surgery-decision-journeys.sh` (decision pairing). **Not built**: no structured MDT field (participants, MDT outcome, tumour-board date) distinct from the ordinary decided-by/at pair — an MDT decision today is indistinguishable in the schema from a single surgeon's decision, only distinguishable by what a free-text value happens to say |
| 4 | Diabetic foot from Medicine and Vascular Surgery to intervention and rehabilitation | 1-5, 11-16 | **Not closed** | `surgical_episode.specialty` (SB-2, V006) is a single `VARCHAR` column, CHECK-enforced to one of fifteen values — there is no structural way for one episode to carry two specialties (Medicine + Vascular Surgery) sharing care, confirmed by reading V002/V006 directly, not assumed. The demonstration's actual point — shared care across two specialties on one episode — is unbuilt, not merely thin | — |
| 5 | Paediatric surgical case invoking the Paediatric Pack | all | **Closeable today, cross-pack leg unconfirmed** | `mvumo.consent_request`'s assent/guardian columns (P5, V300) and `MvumoService.recordAssent` (P15) are real and already used by the procedures pipeline's own demonstration 3 for exactly this purpose — surgery-service's episodes reuse the same `mvumo_consent_request_id` linkage, no surgical-specific copy | `procedures-consent-depth-journeys.sh` J-P5-9, `MvumoServiceAssentTest`. NOT independently confirmed this wave: whether age-specific dosing and any paediatric-pack-specific override actually resolve from `paediatric-domain-pack` state when invoked from a surgical episode — that pack's own remit, not re-verified here (same unconfirmed-cross-pack-leg shape as P15's own demonstration 4) |
| 6 | Obstetric operation invoking the Reproductive Pack | all | **Closeable today** | Identical mechanism to the procedures pipeline's own demonstration 4: `TheatreService.recordObstetricContext`/`recordDelivery`/`recordNeonatalHandover` emit `theatre.obstetric.*` events; pct-service's `TheatreObstetricConsumer` opens the baby's own episode on CPID. Surgery-service adds nothing parallel — the demonstration's own point ("no parallel obstetric model") is satisfied by absence, the same way P15 found for the pipeline's demonstration 4 | `theatre-emergency-journeys.sh` (obstetric/neonatal legs) — same rig, same finding, cited rather than re-proven |
| 7 | Orthopaedic implant with complete traceability | 6-13, 17-20 | **Closeable today, patient-facing leg unconfirmed** | `ImplantTraceabilityService.traceByRecall(udi, lot)` (P8) is real and queries every affected patient; `surgical_longitudinal_object` (SB-2, V006) references implants by `implant_registry_ref` rather than copying them, so a recall trace and a surgical episode's own longitudinal-object list both resolve to the SAME inventory record | `procedures-p8-specimen-device-journeys.sh`, `surgery-longitudinal-followup-specialty-journeys.sh` (federation proof, J-SB2-17). NOT built: any patient-facing implant card or automatic notification — identical named gap to P15's own demonstration 8, not re-derived independently, same owners (inventory-service + notification-service) |
| 8 | Cancelled operation with safe rescheduling | 7, 9 | **Closeable today, rebooking-link unconfirmed** | Identical mechanism to the procedures pipeline's own demonstration 9: OROS `ProcedureWorkflowState.CANCELLED` is terminal with a mandatory `workflow_state_reason`+`workflow_next_action` pair (P2, V300) — the request survives cancellation with a reason and a next action, on the record. `surgical_waitlist_revalidation` (SB-2) is a DIFFERENT fact (clinical still-appropriate revalidation, append-only) and does not itself represent a cancellation-to-rebooking link | `procedures-request-lifecycle-journeys.sh` (mandatory-reason invariant). NOT built: a structural rebooking link (no `supersedes`/`original_order_id` column) — identical named gap to P15's own demonstration 9, same owner (oros-service), not re-derived independently |
| 9 | Postoperative sepsis and unplanned return to theatre | 13-15, 19 | **Not closed — same class of gap as P15's demonstration 10** | `surgical_complication_pathway` (SB-1, V005) genuinely drives escalation: `recognise → grade → own → investigate → treat → disclose → close`, CHECK-enforced (`chk_complication_pathway_closure`), contributes to `pct_problems` on close. **But a second operation cannot join the SAME episode**: `surgical_episode.status` (S1) has no REOPENED state (`ASSESSMENT/LISTED_FOR_SURGERY/OPERATED/CLOSED/ABANDONED` only, CHECK-enforced in `V002__surgical_episode.sql`), and no `parent_episode_id`/`reoperation_of` column exists in the surgery schema. **Correction (2026-07-30): `inpatient.procedure_episode` does NOT have the identical gap**, contrary to what P10 and P15 recorded — `ProcedureEpisodeService.returnToTheatre()` has kept a returning case on the same episode since Wave 4. The inpatient side is missing a complication trigger, predecessor linkage and a distinct state; the surgery side is missing the whole capability. Two related jobs, not one shared one | `surgery-complication-prehab-journeys.sh` (pathway workflow, real). Reoperation-joins-same-episode on the surgery schema: no proof exists because no code exists. The precondition — running the ten theatre rigs — was discharged on 2026-07-30, all ten at baseline (`reports/journeys/theatre-gate-20260730/SUMMARY.md`) |
| 10 | Histology result that changes the care plan | 17-20 | **Partial** | The passive half is real: `requireHistologyReviewed` (SB-1) refuses episode CLOSED on an unacknowledged specimen — a critical result genuinely blocks the course from being filed and forgotten. The ACTIVE half the demonstration names — "reopens planning" — is not built: acknowledging a critical result does not itself trigger any new `surgical_decision` row, notification, or care-plan update; it only prevents forward progress until a human separately chooses to act | `surgery-complication-prehab-journeys.sh` (the blocking half only) |

## What this wave closed

Nothing new — this is a traceability pass over Waves S0-SB-4/SB-6, the same methodological choice
P15 made for the procedures pipeline: naming what is real and what is not, rather than building
against an unvendored eleventh-hour spec reading.

## What remains not closed, named rather than silently dropped

1. **Demonstration 4 (diabetic foot, two-specialty shared care)** — `surgical_episode.specialty`
   is a single column; representing shared care across two specialties on one episode needs
   either a join table (`surgical_episode_specialty_involvement`) or a different modelling
   choice entirely. Not attempted this wave — a real schema decision, not a wire-up.
2. **Demonstration 9 (reoperation joins the same episode) and demonstration 3's MDT gap** — the
   two schemas are **not** in the same state, and four waves of this document treated them as if
   they were.

   `surgery.surgical_episode` genuinely has nothing: its `status` CHECK is a hard five-value
   constraint (`ASSESSMENT/LISTED_FOR_SURGERY/OPERATED/CLOSED/ABANDONED`) with no `REOPENED` and
   no `reoperation_of_episode_id`.

   `inpatient.procedure_episode` is further along than recorded: `ProcedureEpisodeService.returnToTheatre()`
   has existed since Wave 4 and keeps the same episode, returning it to `READY_FOR_THEATRE` and
   flagging `procedure_postop_record.return_to_theatre`. Its gaps are a complication-originated
   trigger, predecessor linkage, and a distinct `REOPENED` state. Note the asymmetry that makes
   these different jobs: `procedure_episode.status` has **no DB CHECK at all** — its state machine
   is Java-only — whereas `surgical_episode.status` is CHECK-enforced and needs a migration to
   widen.

   **The standing "run the rigs first" recommendation has been discharged.** All ten theatre rigs
   ran on 2026-07-30 and every one matched its recorded baseline
   (`reports/journeys/theatre-gate-20260730/SUMMARY.md`). They need Docker and `mvn package`, not
   the packaged estate five consecutive waves believed was required. The pass immediately found a
   total outage of theatre intake introduced by this programme's own V300. Nothing now blocks
   touching either schema's lifecycle.
3. **Demonstrations 5/7/8's cross-pack or cross-service legs** (paediatric-pack dosing/override,
   patient-facing implant notification, structural rebooking link) belong to peer services or
   packs and are named with their owners above, not attempted here — identical shape and, for 7
   and 8, the identical underlying gap to P15's own findings for the sibling pipeline.
4. **Demonstration 1's single combined walkthrough** — each leg (episode, assessment, decision,
   prehab, consent, operation, follow-up) has its own real proof; no one rig chains all of them
   for one patient in one session. Judged the same way P15 judged its own demonstration 5: real
   confirmation value, not new capability, lower priority than naming demonstrations 4 and 9's
   genuine structural gaps.
