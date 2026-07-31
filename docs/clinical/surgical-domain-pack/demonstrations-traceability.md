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
| 3 | Breast cancer: assessment, MDT, surgery, histology, oncology handoff | 1-6, 11-13, 17-20 | **Closed (completion wave, 2026-07-30)** | The episode still cannot close on unreviewed histology (`requireHistologyReviewed`, SB-1). An MDT decision is now structurally distinguishable from a single surgeon's: `surgical_decision.decision_forum` (V012) is typed `INDIVIDUAL`/`MDT`, and an MDT decision must cite the PCT board record behind it via a `mdt_decision_ref` + `mdt_decision_source` pair. Surgery **references** PCT and keeps no board record of its own — the source column exists because PCT holds two MDT systems of record (`pct_mdt_decisions` V114 and `pct_mdt_sessions` case items V051), so a bare id would be ambiguous. That duplication is named as an inherited defect in the lease §7, not resolved here | `SurgicalDecisionForumTest`, `surgery-decision-forum-journeys.sh` (real Postgres CHECKs: MDT-without-board and individual-with-board both refused, both sources accepted, invented values refused). Reachable via the existing V302-gated decision route; the UI distinguishes a confirmed reference from one recorded while PCT was unreachable. **Participants and tumour-board date are deliberately still absent from surgery** — they are the board's record, and PCT owns it |
| 4 | Diabetic foot from Medicine and Vascular Surgery to intervention and rehabilitation | 1-5, 11-16 | **Closed (completion wave, 2026-07-30)** | `surgery.surgical_episode_specialty` (V011) carries every specialty on the case with a `LEAD`/`SHARED` role and each team's own `contribution`, so one episode genuinely represents shared care. `surgical_episode.specialty` is kept as the LEAD specialty and synchronised by `EpisodeSpecialtyService`, so no existing reader of that column breaks — additive, not a replacement. Exactly one lead is enforced by a partial unique index rather than by service code, handover demotes the incumbent before promoting the successor in one transaction, and the lead cannot be removed, only handed over | `EpisodeSpecialtyServiceTest`, `surgery-shared-specialty-journeys.sh` (real Postgres: backfill, second-lead refusal, duplicate refusal, invented specialty/role refusal, lead-column sync after handover). Reachable: tshepo-authz V303, BFF proxy, `/work/clinical/surgery` specialties panel |
| 5 | Paediatric surgical case invoking the Paediatric Pack | all | **Closeable today, cross-pack leg unconfirmed** | `mvumo.consent_request`'s assent/guardian columns (P5, V300) and `MvumoService.recordAssent` (P15) are real and already used by the procedures pipeline's own demonstration 3 for exactly this purpose — surgery-service's episodes reuse the same `mvumo_consent_request_id` linkage, no surgical-specific copy | `procedures-consent-depth-journeys.sh` J-P5-9, `MvumoServiceAssentTest`. NOT independently confirmed this wave: whether age-specific dosing and any paediatric-pack-specific override actually resolve from `paediatric-domain-pack` state when invoked from a surgical episode — that pack's own remit, not re-verified here (same unconfirmed-cross-pack-leg shape as P15's own demonstration 4) |
| 6 | Obstetric operation invoking the Reproductive Pack | all | **Closeable today** | Identical mechanism to the procedures pipeline's own demonstration 4: `TheatreService.recordObstetricContext`/`recordDelivery`/`recordNeonatalHandover` emit `theatre.obstetric.*` events; pct-service's `TheatreObstetricConsumer` opens the baby's own episode on CPID. Surgery-service adds nothing parallel — the demonstration's own point ("no parallel obstetric model") is satisfied by absence, the same way P15 found for the pipeline's demonstration 4 | `theatre-emergency-journeys.sh` (obstetric/neonatal legs) — same rig, same finding, cited rather than re-proven |
| 7 | Orthopaedic implant with complete traceability | 6-13, 17-20 | **Closeable today, patient-facing leg unconfirmed** | `ImplantTraceabilityService.traceByRecall(udi, lot)` (P8) is real and queries every affected patient; `surgical_longitudinal_object` (SB-2, V006) references implants by `implant_registry_ref` rather than copying them, so a recall trace and a surgical episode's own longitudinal-object list both resolve to the SAME inventory record | `procedures-p8-specimen-device-journeys.sh`, `surgery-longitudinal-followup-specialty-journeys.sh` (federation proof, J-SB2-17). NOT built: any patient-facing implant card or automatic notification — identical named gap to P15's own demonstration 8, not re-derived independently, same owners (inventory-service + notification-service) |
| 8 | Cancelled operation with safe rescheduling | 7, 9 | **Closeable today, rebooking-link unconfirmed** | Identical mechanism to the procedures pipeline's own demonstration 9: OROS `ProcedureWorkflowState.CANCELLED` is terminal with a mandatory `workflow_state_reason`+`workflow_next_action` pair (P2, V300) — the request survives cancellation with a reason and a next action, on the record. `surgical_waitlist_revalidation` (SB-2) is a DIFFERENT fact (clinical still-appropriate revalidation, append-only) and does not itself represent a cancellation-to-rebooking link | `procedures-request-lifecycle-journeys.sh` (mandatory-reason invariant). NOT built: a structural rebooking link (no `supersedes`/`original_order_id` column) — identical named gap to P15's own demonstration 9, same owner (oros-service), not re-derived independently |
| 9 | Postoperative sepsis and unplanned return to theatre | 13-15, 19 | **Closed (completion wave, 2026-07-30)** | `surgical_complication_pathway` (SB-1, V005) still drives escalation. A second operation now joins the SAME episode: `surgery-service V010` admits `REOPENED` to the status CHECK and adds a `reoperation_of_episode_id` self-FK. The reopen is **audited and unbypassable** — reason, who and when are an all-or-nothing triple enforced by four CHECK constraints, so even a direct SQL write cannot produce an unexplained REOPENED row, and `transition()` explicitly refuses a plain move to REOPENED so the audit cannot be sidestepped through the ordinary route. On the inpatient side, `V305`'s `procedure_return_to_theatre` replaces the single boolean with a real event per return: sequence, reason, a closed-vocabulary complication category, planned-versus-unplanned, and a link to the operative note it originated from — the complication-originated trigger this row previously named as missing | `SurgicalEpisodeReopenTest`, `ProcedureReturnToTheatreTest`, `surgery-reoperation-journeys.sh` and the extended `theatre-alt-journeys.sh` (real Postgres: audit enforcement, self-reference refusal, predecessor linkage, category vocabulary, planned/unplanned mismatch refusal). Reachable: tshepo-authz V303 (surgery) and V034 (inpatient), BFF proxy, reopen panel on `/work/clinical/surgery` |
| 10 | Histology result that changes the care plan | 17-20 | **Partial** | The passive half is real: `requireHistologyReviewed` (SB-1) refuses episode CLOSED on an unacknowledged specimen — a critical result genuinely blocks the course from being filed and forgotten. The ACTIVE half the demonstration names — "reopens planning" — is not built: acknowledging a critical result does not itself trigger any new `surgical_decision` row, notification, or care-plan update; it only prevents forward progress until a human separately chooses to act | `surgery-complication-prehab-journeys.sh` (the blocking half only) |

## What the completion wave closed (2026-07-30)

The three demonstrations this document had carried as unclosed — **3** (MDT representation),
**4** (two-specialty shared care) and **9** (reoperation joins the same episode) — are closed, in
each case by a schema change rather than a relabelling. Their rows above carry the mechanism and
the proof.

Two things are worth keeping from how they closed. Demonstration 3 was closed by **referencing**
PCT's board record rather than building a third MDT table, and the reference had to become a pair
(id plus source) purely because PCT holds two MDT systems of record; that duplication is recorded
as an inherited defect, not silently absorbed. Demonstration 9's reopen is enforced at the
database level, not by service code alone, specifically so that the audit trail cannot be produced
without a reason attached to a named clinician.

The standing "run the rigs first" precondition was discharged the same day: all ten theatre rigs
ran and every one matched its recorded baseline
(`reports/journeys/theatre-gate-20260730/SUMMARY.md`). They need Docker and `mvn package`, not the
packaged estate five consecutive waves believed was required. The pass immediately found a total
outage of theatre intake introduced by this programme's own V300.

## What remains not closed, named rather than silently dropped

1. **Demonstration 10's active half** — acknowledging a critical histology result still does not
   itself open a new decision, notification or care-plan update. The blocking half is real; the
   "reopens planning" half is a workflow trigger nobody has built. Note that this is now the only
   sense in which "reopen" is unbuilt: the episode-level reoperation it used to be conflated with
   closed with demonstration 9.
2. **Demonstrations 5/7/8's cross-pack or cross-service legs** (paediatric-pack dosing/override,
   patient-facing implant notification, structural rebooking link) belong to peer services or
   packs and are named with their owners above, not attempted here — identical shape and, for 7
   and 8, the identical underlying gap to P15's own findings for the sibling pipeline.
3. **None of this has been exercised over real HTTP.** Every "closed" above means: schema, service
   logic, policy rows, BFF proxy and UI surface exist, and are proven by unit tests, route-shape
   tests and real-Postgres rigs. No browser has loaded `/work/clinical/surgery` and no request has
   crossed Envoy into surgery-service. Read "closed" as built-and-verified-in-CI, not as running.
4. **Demonstration 1's single combined walkthrough** — each leg (episode, assessment, decision,
   prehab, consent, operation, follow-up) has its own real proof; no one rig chains all of them
   for one patient in one session. Judged the same way P15 judged its own demonstration 5: real
   confirmation value, not new capability, lower priority than naming demonstrations 4 and 9's
   genuine structural gaps.
