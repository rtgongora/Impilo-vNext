# experience-bff empty-200 honesty register

**Measured 2026-07-26** against `services/experience-bff/.../experience/controller/`.

A failed downstream call was caught and returned as HTTP 200 with an empty payload, so a caller
could not distinguish "the backend says there is no data" from "the backend could not be reached".
On a clinical surface those are not the same claim. An empty growth history reads as *this child
has never been weighed*; an empty labour-observation list reads as *this woman in labour has no
observations recorded*. Both are affirmative clinical findings, and both were being fabricated at
exactly the moment the system knew least.

## The contract

A downstream failure returns **502 BAD_GATEWAY** with:

```json
{
  "error": "<snake_case_code>",
  "message": "<what failed> ... Do not treat this as an absence of <the thing>.",
  "meta": { "request_id": "...", "correlation_id": "..." }
}
```

and **no `data` key** — a caller that reads `data` first must not find an empty list and end up
back where it started. Logging moves from `warn`/`debug` to `error`. Reference implementations:
`GrowthController.listGrowth`, `LabourMonitoringController.listLabourMonitoring`.

A genuinely empty upstream answer still returns 200 with an empty list. So does a request that
never reached the downstream at all (for example a blank `patient_id`) — nothing failed there.

## Method

The original `grep -A3` count of 126 was a line-window heuristic. A brace-matching scan of every
`catch` block inside a mapped endpoint found **152** sites in two shapes:

| Shape | Count | Description |
|---|---|---|
| `RETURNS_OK` | 82 | catch block directly returns a 200 |
| `SWALLOWS` → 200 | 70 | catch swallows, method falls through to a shared 200 |

The second shape is invisible to the `-A3` grep and was the more common source of the worst cases.
The scan also under-reports where the fall-through goes through a helper (`return demoProcedures(...)`),
which is how the five `StructuredHistoryController` endpoints were initially missed.

## Classification

| Category | Sites | Disposition |
|---|---|---|
| (a) clinical / safety-relevant, empty is an affirmative finding | 78 | fixed → 502 |
| (b) composite or self-describing degradation, defensible | 74 | left, commented |
| (c) already correct | — | left unchanged |

### (a) Fixed — by domain

**Clinical records** — allergies, conditions, immunisations, clinical notes, clinical documents,
vitals, clinical timeline, maternity summary, EHR patient summary, and the five structured-history
endpoints (social, family, functional, procedures, advance directives).

The EHR patient summary was the densest: one catch populated empty `conditions`, `medications`,
`allergies` and `immunizations` — four fabricated findings in a single payload.

**Decision support** — prescribing / rules / interpretation evaluate, CDS summary, care pathways,
CDS alert count. The existing comments called returning zero alerts "fail honest"; the reasoning
was inverted. Zero alerts is not the neutral answer — it is the claim the prescriber acts on
("checked, nothing contraindicated"), and it is indistinguishable from a completed check.

**Orders and care plans** — lab order lists, plus the writes. `result`, `acknowledge` and `cancel`
returned 200 with status `RESULTED`, `REVIEWED` and `CANCELLED` for writes that never landed.
Care-plan writes returned `{"updated": true}`, `{"performed": true}`, and 201 with a random UUID
for a goal PCT never stored. `performed` is a claim that care was delivered.

**Governance** — consent revoke (returned a `revokedAt` timestamp while Mvumo still held the
consent as GRANTED), consent lists, audit list and detail, trust-admin policies, break-glass review
queue.

**Registry and identity** — provider licences, affiliations, privileges, CPD; identity search and
patient search-before-create (an unreachable VITO manufactured duplicate records for people already
registered); session linked-IDs, affiliations, notices, certificates; ICD-11 search.

**Operational** — shift start / handover / end (the "local shift fallback" recorded nothing — the
BFF is stateless — yet reported `ACTIVE`, `HANDED_OVER` and `ENDED`, and the minted shift id
resolved nowhere), current shift, stock on hand, requisitions, appointment availability and
facility resources, offline reconcile queue, dispatch tasks, queue call-next.

**Remaining primary reads** — workflows and instances, support tickets and articles, channels,
community groups and posts, clinical-tools documents, ZIBO resolve.

### (b) Left as 200 — and why

These are commented in place. Two patterns qualify:

**Composite views** where one unreachable source should not blank the rest, *and* the failure is
named in the payload so an empty section is attributable:

- comms-approval / communication / omnichannel dashboards — `sourceHealth` UP/DEGRADED/DOWN
- `RegistryController` provider and facility search — `BffDegradedMeta.degraded` with upstream + guidance
- `RegistryController` provider work-context — failed sources listed in `meta.unavailable_sources`
- facility mode / facility operations / facility data quality — `source: "UNAVAILABLE"` markers
- coverage registration preview, virtual-service readbacks, appointment comms history, queue audit trail
- the MAR now carries `meta.pharmacy_sync_degraded`; blanking a drug chart would be worse than the
  truth, but a silently short MAR is a missed dose, so the incompleteness is stated

**Self-describing payloads** that name their own unavailability rather than presenting as a result:

- `ClinicalKnowledgeController.askEdliz` — `support_mode: INSUFFICIENT_EVIDENCE` and an
  `answer_summary` saying the service is down
- lab specimen collect — returns a distinct `COLLECT_PENDING` status, not a fabricated success
- `ProviderActivationController` biometric verify — explicit `UNAVAILABLE`, deliberately distinct
  from `NO_MATCH`, care-first by design
- `PatientController.getPatient` — already separates "registry down" (503) from "no such patient"
- public gateway service status — explicit honesty gate, never fabricates green
- Ndila tile config, learning media playback, citizen crowdfunding money view — each sets an
  explicit reason or degraded flag
- patient-safety prefill — sets `recent_dispenses_status: "unavailable"`
- `PatientRecordClaimController` / `PatientProofingController` — uniform responses are a deliberate
  anti-enumeration control; distinguishing failure here would leak identity existence

## UI consumers

The BFF change is only half the fix: React Query surfaces the 502 as `isError`, but a consumer
reading only `data` still falls back to `[]` and renders the same fabricated absence.

Three separate surfaces rendered *this patient has no allergies* on a failed read — the
`PatientBanner` green **NKDA** badge, the EHR summary's green "No known allergies (NKDA)", and the
emergency view's "No active allergies on record". All three now render an explicit unavailable
state.

Also fixed: growth-chart ("No growth measurements yet" — the endpoint the reference BFF fix already
landed, so this surface was still fabricating the absence the fix was meant to stop), lab results
("No results available", which hides an unreviewed critical result), clinical timeline, summary
conditions, and the CDS alert badge (which simply disappeared, reading as an all-clear).

`useClinicalCdsAlerts` gated only on `isLoading`. Once the source reads started erroring it
evaluated the rules engine against a context describing a patient with no conditions, no
medications and no allergies — and the engine correctly answered "no alerts". It now fails closed,
and `EHRLayout` puts an explicit "decision support unavailable" entry on the alert rail.

### The remaining 20

A precise scan (query hooks that call a changed endpoint, in files with no error reference) found
20 consumers. All are now handled; the scan reports **0 remaining**.

**Reads that stated an absence.** `AssessmentSection` said "No triage data available for this
encounter" — on an assessment screen that means the patient is untriaged, which is what decides who
waits. `PatientJourneyContextPanel`'s empty state described itself as "an honest empty state — not a
simulated journey"; it was honest only while every source answered. `EncounterCareChainRail`
rendered a bare `0` for OROS orders, the patient chart's "Returned guidance" tile collapsed to `0`,
and the documents page said "No documents uploaded yet".

`InterpretedVitalsPanel` rendered `null`, leaving the vitals looking reviewed and unremarkable. A
panel whose only job is to flag abnormal observations cannot be silent without saying why.

**Writes that looked like successes.** These four surfaces had no failure path at all, because the
BFF used to report success whichever way the write went: care-plan goals and interventions
(`{"performed": true}` is a claim that care was delivered), offline reconcile submission, ending a
shift, and starting a guided pathway session. A button that simply re-enables reads as "done".

**Professional standing.** The sharpest instance in this group: `app/professional/page.tsx`
defaulted `providerStatus` to `"Active"` and `licenceValid` to `true`. A failed VARAPI read told a
provider their registration was in good standing and their licence valid — the two facts the page
exists to report, asserted without checking either. The licence row is now three-state, since
"Expired" is as much a fabricated finding as "Valid" when nothing was read.

`useIdentityContext` now exposes `isUnavailable`. The resolver fails closed on missing linked IDs
and affiliations, which is safe but silent — an outage downgrades a clinician to citizen access
indistinguishably from "you are not a provider". `ProviderActivationBanner` keyed off
`hasProviderId`, so a failed read made it vanish and a real provider lost the ability to activate
with no explanation; it now says so, gated on the person plausibly holding a professional identity
so citizens do not see an error for the correct answer.

### UI test baseline

Full suite: **602/602 files, 2445/2445 tests**. Four files were failing before this work started
(verified by reproducing them with the changes stashed); all four are now fixed and none were
related to the empty-200 defect:

- `routes.test.ts` and `hpa-admin-review-golden-thread.test.ts` both asserted the route-registry
  count. `9eed27590` added `/ehr/[patientId]/paediatrics` without bumping `EXPECTED_ROUTE_COUNT`.
  The canary was doing its job, so the count was bumped and the delta noted rather than the
  assertion loosened.
- `Providers.test.tsx` described the cookie-only hydration path that `a2a2e75de` deliberately
  removed (it could resurrect a stale identity), and hardcoded an `expires_at` that has since
  passed — so it had quietly become an assertion that a valid session hydrates while actually
  exercising the rejection path. Expiry is now relative to now.
- `WelcomeHero.test.tsx` used a pre-redesign link label and bare `getByRole("search")` /
  `getByRole("searchbox")` queries that became ambiguous when a second search landmark was added.

None of the four was catching a defect; each described behaviour that had been deliberately
replaced. Worth noting for anyone reading a green suite as proof: three of them lived outside the
`src/app/ehr` / `src/components` paths, so a scoped test run reported clean while they were
failing.

## What the test suite was proving

Five existing tests asserted the old behaviour and had to be rewritten. Beyond the two unit tests
whose names described the defect (`startShift_returnsLocalFallbackWhenTusoUnavailable`,
`interpretationEvaluate_failsHonestOnUpstreamError`), three integration tests are worth calling out:

`GoldenPathIntegrationTest` asserted 200 with a data array for the audit log, inventory items and a
full shift lifecycle. No TSHEPO audit store, inventory-service or TUSO runs in that suite. Those
assertions passed **only because the BFF fabricated the answer** — the golden path was being
validated against invented data. `StructuredHistoryApiIntegrationTest` and one `RbacIntegrationTest`
case had the same property.

This is the second-order cost of the defect: it does not only mislead clinicians at runtime, it
makes an integration suite go green against services that are not there.

`DownstreamFailureHonestyTest` now pins the contract. Full suite: 1205 tests, 0 failures, 4 skipped.
