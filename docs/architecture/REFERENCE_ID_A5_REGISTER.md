# A5 — programme_id and department_id reference register

Guard: `scripts/guard/check-reference-id-classification.sh`
Canonical registries: `wgv_programme` (workforce-governance-service V014) and
`tuso.facility_department` (tuso-service V051).

## Why this is a register and not a migration

The A5 item was described as "inventory and migrate `programme_id` across 9 services and
`department_id` across 10". Reading the columns rather than the count shows they are not
one kind of thing, and treating them as one would do damage:

**Already canonical.** `workforce-governance-service` and `tuso-service` hold
`UUID ... REFERENCES wgv_programme(id)` / `REFERENCES tuso.facility_department(id)`. These
are the registries themselves. The database already enforces resolvability; there is nothing
to sweep.

**Audit, and must not be touched.** `tshepo_authz.policy_decision_log.programme_id` and
`.department_id` record what a request actually presented. A decision-log row naming a
programme that does not exist is not a data-quality defect — it is the evidence that
something sent an unresolvable value, which is exactly what an audit log is for. Validating
or rewriting these would destroy the record of the problem A5 exists to find. They are
permanently out of scope.

**Genuine free-text references.** The remaining columns name something in another service's
registry with nothing checking that it resolves. This is the actual A5 surface, and it is
ten columns across eight services.

**A separate question that looks like this one.** `simba.screening_programmes.programme_id`
is `UUID NOT NULL DEFAULT gen_random_uuid()` — Simba mints it. A wellness screening programme
is plausibly a different kind of thing from a workforce-governance programme, but Simba's
other `programme_id UUID` columns (social groups, challenges, reels) could reference either,
and nothing in the schema says which. That is a duplicate-truth question for the Simba and
WGV owners, not a mapping exercise, and it is recorded here rather than resolved unilaterally.
Same for `rito-quality-safety-service.programme_id UUID`, which is unconstrained.

## The register

| Service | Column | Table | Disposition |
|---|---|---|---|
| vashandi-workforce-service | `department_id` | `vsh_workforce_assignment` | **Validated.** V012 soft-validates at precheck, records `department_ref_status`, quarantines non-resolving values in `vsh_reference_quarantine`. |
| vashandi-workforce-service | `programme_id` | `vsh_workforce_assignment` | **Validated.** As above, via `programme_ref_status`. |
| tshepo-authz-service | `department_id` | `policy_decision_log` | **Audit — do not validate.** Records what was presented. |
| tshepo-authz-service | `programme_id` | `policy_decision_log` | **Audit — do not validate.** Records what was presented. |
| hr-payroll-service | `department_id` | `hr.employees` | **Remediation.** Employee's organisational department. Should resolve against `tuso.facility_department`; owner is the HR/payroll lane. |
| general-ledger-service | `department_id` | `gl.journal_entries`, `gl.journal_lines` | **Remediation.** Cost-centre dimension on postings. Historical entries are immutable once posted, so this needs a validate-forward approach, not a backfill. |
| costing-engine-service | `department_id` | `costa_budget_version`, `costa_budget_line` | **Remediation.** Budget dimension; same validate-forward constraint as the ledger. |
| procurement-service | `department_id` | `proc.requisitions` | **Remediation.** Requesting department on a requisition. |
| asset-registry-service | `department_id` | `asr_equipment` | **Remediation.** Department an equipment item is assigned to. |
| oros-service | `destination_department_id` | `oros_routing` | **Remediation.** Routing destination for an order. Highest operational risk of the enterprise set: an unresolvable destination is a delivery that goes nowhere. |
| live-service | `programme_id` | `live.live_events` | **Remediation.** Programme a live event belongs to. |
| msika-apps-service | `requester_programme_id` | `ma_activation_requests` | **Remediation.** Programme requesting an app activation. |

## What "remediation" means here, and what it does not

None of the remediation columns are authority-bearing. Work-context authority comes from
Vashandi assignments and WGV appointments, and both of those are validated already — Vashandi
by V012, WGV by its foreign keys. So an unresolvable `department_id` in the general ledger is
a reporting and finance-integrity problem, not a route by which someone gains access they
should not have.

That is why this ships as a register rather than ten quarantine tables. Building the Vashandi
machinery eight more times, speculatively, in lanes that have not asked for it and against
tables whose owners have not agreed a resolution policy, produces a lot of infrastructure and
no resolved references. Two of these (`gl.journal_entries`, `costa_budget_line`) are immutable
once posted and cannot be backfilled at all, so a sweep would have to be validate-forward
regardless — which is a decision for the finance lane, not for this programme.

The guard's job is narrower and achievable: keep the surface from growing. A new free-text
`programme_id` or `department_id` column now fails the build unless someone adds a row here
and says which of the four kinds it is.

## Extending this

`ReferenceValidationService` in vashandi-workforce-service is the working pattern: soft
validation at write time, a `*_ref_status` column recording the outcome, and a quarantine
table that is a remediation surface rather than a discard bin. It distinguishes UNVALIDATED
(checked, not found) from UNVERIFIABLE (registry unreachable), which matters — an outage must
not look like a bad reference.

Any lane adopting this should reuse that shape rather than inventing another, and should not
block writes on validation failure. An assignment with an unvalidated department still works;
it simply does not unlock department-scoped authority.
