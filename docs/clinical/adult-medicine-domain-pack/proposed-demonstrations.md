# Adult Medicine — PROPOSED demonstrations (awaiting product-owner confirmation)

> ⚠️ **These are proposals, not the recovered requirement.**
>
> The pack's implementation report refers to "the ten §23 demonstrations". **The brief containing
> §23 is not in this repository** — searched for `§23`, "demonstrations", "25 FHIR resources" and
> every `*brief*` filename; no version was ever committed, and the git history of the pack's docs
> shows none was committed and later deleted. The only trace of §23 anywhere is the phrase in the
> report itself.
>
> The surgical pack *did* commit a section matrix (`docs/clinical/surgical-domain-pack/audit.md`
> §1–25), but **its section numbers differ** — there §23 is analytics indicators and §24 is
> demonstrations — so it cannot be used to infer what adult medicine's §23 asked for.
>
> Rather than invent ten items and present them as the requirement, this file proposes ten grounded
> in what the pack actually built. **If the real §23 list exists outside the repo, this file should
> be replaced by it, not reconciled with it.**

## Why propose at all, rather than wait

Every demonstration below is a journey a clinician can actually walk today against shipped code. Even
if the real §23 differs, proving these is not wasted: each one is a claim the pack currently makes
that nobody has walked end to end. The value is in the walking, not in the numbering.

## Proposed set

Each names the surface, the assertion, and — most importantly — **the failure it would expose**,
because a demonstration that can only pass is not evidence.

| # | Demonstration | Walks | Would expose |
|---|---|---|---|
| D1 | **Diagnose → SHR** — record a problem in PCT, see it arrive in BUTANO as a FHIR `Condition` and appear in the IPS bundle | `/ehr/[id]/conditions` → pct outbox → `pct.problem.recorded` → BUTANO | The producer gap fixed this session; a silent regression returns the problem list to invisible |
| D2 | **HIV enrolment, one disease one entry** — enrol an HIV patient, attempt a second active enrolment, get 409 | `/ehr/[id]/programmes` | The partial-unique guard; a duplicate cohort entry |
| D3 | **Regimen change keeps history** — switch ART line, confirm the prior regimen is retained and exactly one is current | programmes → regimens | Overwriting treatment history |
| D4 | **Exit requires a reason** — attempt to exit an enrolment without one, be refused | programmes | A lost-to-follow-up patient recorded as a treatment success |
| D5 | **Failed read ≠ no disease** — force the programme read to fail, confirm the workspace says so and never renders "not in any programme" | `/ehr/[id]/medicine` | The single most dangerous UI failure in this pack |
| D6 | **CDS: three meanings of empty** — evaluate a topic with complete facts, with missing facts, and with the service down; confirm three distinct renderings | `/ehr/[id]/medicine/cds` | An unrun evaluation reading as an all-clear |
| D7 | **Unstated certainty stays unstated** — record a problem with no diagnostic certainty, confirm the SHR `Condition` has no verification status | PCT → BUTANO | A suspicion promoted to a confirmed diagnosis |
| D8 | **Confidential lane** — confirm an HIV enrolment carries the confidential badge and a TB one does not, and that the ENFORCE gap is stated rather than implied | programmes / workspace | A protection label that does not protect |
| D9 | **Ward round sees the problems** — round on an admitted patient; with the admission read failing, confirm UNKNOWN rather than "not admitted" | `/ehr/[id]/ward-round` | Sending a doctor away from a patient in a bed |
| D10 | **Cohort counts are not headcounts** — read cohort counts for a patient enrolled in two programmes, confirm they appear in both and the response says not to total them | `/v1/programme-enrolments/cohort-counts` | A cohort table summed into a "patients reached" figure |

## What proving these requires

D1, D7 and D10 need a live estate (Kafka + BUTANO + a deployed pct). D2–D6, D8, D9 are provable in
the shell against the BFF. The existing `scripts/runtime-proof/medicine-programmes-journeys.sh` is
the precedent for the artefact shape: positive **and** negative controls, and a failure that names
what it means clinically rather than just asserting a status code.

**Not yet run.** Listing them is not proving them, and this file should not be read as evidence that
any of them passes.
