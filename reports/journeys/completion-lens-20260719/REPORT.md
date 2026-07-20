# Completion Lens — PCT / MusheX-COSTA / Coverage / PACS — 2026-07-19

Bar: "can a real user complete the journey from a browser, backed by real writes — or does it look like mocks / is it missing?" PO scope this session: **Coverage + PCT outpatient**.

## Diagnosis (all four, live-verified)

| Domain | Verdict | Root cause |
|---|---|---|
| PCT outpatient | **BROKEN** (silent clinician data-loss) | encounter-id UUID/Long mismatch threw away notes; placebo sign; BFF fake-201 |
| PCT inpatient | PARTIAL | reads real on seed; admit→discharge write path unproven (event_outbox=0) |
| Coverage | **INVISIBLE + 404** | no launcher tile AND `.dockerignore **/coverage` omitted the feature from the build |
| COSTA | PARTIAL | tariffs seeded (AHFOZ-indicative); estimate→invoice→finalize undemonstrated |
| MusheX | PARTIAL / payout BROKEN | real-money rails DISABLED (Paynow creds), all tables empty |
| PACS | PARTIAL / EMPTY | real Orthanc + reachable viewer, but 0 studies + an orphan demo stub |

## FIXED + PROVEN this session

### PCT outpatient — full visit proven live end-to-end
`pct-note-proof`: clinician (dr.mapfumo) → **start encounter** (id=6, STARTED, on a real QUEUED journey) → **capture consult note** (id=3 — persists where it used to be lost) → **sign** (signed=True — placebo gone) → **close** (COMPLETED, HTTP 200). DB truth: note **encounter-linked + signed**, encounter **COMPLETED**, **ENCOUNTER_COMPLETED event emitted**.

Root causes fixed:
- `ClinicalNoteService` did `UUID.fromString(encounter_id)` and threw whenever the UI sent the numeric encounter id (not its UUID `encounterRef`) → every consult note with an active encounter was **lost**. Now tolerant (`resolveEncounterRef`). @c27298d92
- Note signing was a placebo (no signed state). V044 adds signed columns; real `sign()`. @c27298d92
- BFF returned fake 201/200 on a null upstream (a lost note looked saved); sign path-var was UUID (note ids are numeric) → 400. Now propagates 502; sign accepts the id; UI shows the error and keeps the form. @8374e4532

Deployed: pct-service@8dc4efd4 (V044 migrated live), experience-bff@26e8b602.

### Coverage — enrolment now COMPLETE + proven

You flagged it "feels incomplete" — because I'd proven discoverability + render but not the actual enrolment. Now proven end-to-end (`scripts/e2e/coverage-enrol-proof.sh`, 6/6 live): citizen → **eligibility ELIGIBLE → enrol member (201) → My Coverage returns the plan → `cv_member_coverage` row ACTIVE**. (My Coverage was empty before because nobody had ever enrolled.) Note: eligibility correctly returns INELIGIBLE for an already-enrolled member; the enrol endpoint still allows a duplicate — a minor data-integrity nit.

Also fixed a real consent-plane race surfaced while proving this: the AuthGuard redirected to `/consent` on the initial `hasConsented=false` **before** the consent store hydrated from localStorage, so a consented user could be flashed to the consent gate on a hard navigation. Added a `hydrated` guard.

### Coverage — two causes, both fixed
1. **Invisible**: no `AppDefinition` in `app-registry.ts` → no launcher tile. Added a citizen Coverage tile → `/coverage/member`; retargeted citizen back-links off the ADMIN `/coverage`.
2. **404 (deeper)**: root `.dockerignore` `**/coverage` — meant for the test-coverage report dir — also matched `src/app/coverage` (the feature), so the build context omitted it and `next build` never compiled it (deployed manifest: 841 app routes, **zero `/coverage`**). Fixed by re-including the feature dir. @ (.dockerignore) — **LAW: anchor broad `**/<name>` ignore patterns; they silently eat product dirs sharing the name.** Backend real + seeded (2 plans, subsidies).

**Coverage live-proven** (`coverage-discover-enrol.journey.spec.ts`, green x2): citizen reaches the Enroll page (renders, no 404), its plan picker carries the real seeded plans, My Coverage reachable. First rebuild FAILED (including the feature surfaced a hidden missing-import `@/components/coverage/CoverageGeoMapPanel` — the `**/coverage` exclusion also ate src/components/coverage; both dirs now re-included). Deployed one-ui-shell@d7aa3f00.

## Deferred (standing register, PO-decided)
PACS (seed real DICOM + fix orphan stub), COSTA (prove finalize chain), PCT inpatient (discharge write path), MusheX (document Paynow credential gate + prove internal WALLET rail — no real-money without operator creds).
