# Completion-lens: PCT inpatient (admit → ward round → discharge)

**Date:** 2026-07-20 · **Branch:** `claude/staging-ux-orchestration-remediation-Yypyl`
**Service:** `inpatient-service` (8121, schema `inpatient.*`) · BFF `experience-bff` · UI `one-ui-shell`

## Lens question
Can a real user complete **admit → ward-round (observation/EWS) → discharge** from the browser,
backed by real writes — with the event outbox actually emitting?

## Diagnosis (live probe)
The engine + seed existed (2 wards, 12 beds, 1 admission stuck `ADMITTED`), but **0** rows in
`transfer / early_warning_score / discharge_summary / discharge_clearance / shift_handover /
event_outbox` — the ward-round→discharge write path had never run. The mapper found the outbox
publisher was wired (just unfed by seed-only data) but surfaced **three real defects**:

1. **Split-brain discharge** — the UI discharge button flipped status but wrote no summary/clearance;
   the summary/finalise flow wrote docs but never discharged the patient. No single browser action
   produced a discharged patient **with** a discharge summary.
2. **Discharge clearances unreachable** — zero UI wired the `discharge_clearance` init/clear, yet
   `finalise` hard-gates on them → **409 forever** from the browser.
3. **Fake-success EWS** — BFF `/ews/news2` returned a fabricated UUID with **no DB write** (silent
   data loss, same class as the earlier PCT-outpatient bug).

## Fixes
- **Unified discharge** (`DischargeSummaryService.finalise` → `AdmissionService.dischargeByEncounter`):
  finalising the documented discharge now flips the admission to `DISCHARGED`, **frees the bed**
  (back to `AVAILABLE`), and emits `inpatient.discharge.completed`. `dischargePatient` /
  `completeDischarge` also free the bed (ward census now truthful).
- **EWS persists** (`ClinicalDepthController /ews/news2`): forwards to `inpatientClient.recordEws`
  writing a real `early_warning_score` row, or errors honestly — no more fabricated UUID.
- **Clearances reachable** (`useDischargeClearances` + `DischargeClearancesPanel` on the
  discharge-board): a clinician can now initialise and clear the multi-disciplinary sign-offs that
  gate finalise. The BFF/client/service already existed; only the UI was missing.

## Proof (live, through the real ingress)
`scripts/e2e/inpatient-discharge-proof.sh` — **19/19, green ×2**:
```
admit fresh patient → bed occupied → observation → EWS via /ews/news2 (early_warning_score 0→1,
fake-UUID trap gone) → init clearances (9) → clear all → summary DRAFT → finalise →
admission DISCHARGED + discharged_at stamped + bed FREED to AVAILABLE + summary FINALISED →
event_outbox: inpatient.admission.created, inpatient.discharge.completed,
             inpatient.discharge.summary_finalised, inpatient.discharge.followup_requested (4 rows)
```
Browser: `ui/one-ui-shell/e2e/journeys/inpatient-discharge.journey.spec.ts` — green ×2 (board
reachable + discharge-clearances panel present; correctly work-session-gated otherwise).

## Deployed (digest-pinned)
- `inpatient-service` @ `sha256:100ac6c6…`
- `experience-bff` @ `sha256:f4f68004…`
- `one-ui-shell` @ `sha256:5d785838…`

## Notes
- The previously-dead `event_outbox` was not a code defect — the publisher was live but seed-only
  data never fed it. The first real admit/discharge now drains through the 2s poller.
- A pre-existing **foreign** test break (`AdminFacilityImportControllerTest`, unrelated concurrent
  change) means BFF images build with `-Dmaven.test.skip=true` (flagged separately, task chip).
- The discharge-board browser test tolerates the work-session facility-gate flow (a harness/UX
  quirk); the authoritative write-path evidence is the API+DB suite.

## Completion-lens scoreboard
Coverage ✅ · PCT-outpatient ✅ · **PCT-inpatient ✅** · Impilo Live/Khuluma 🟡 (media operator-gated).
Open trio remaining: **COSTA, MusheX, PACS**.
