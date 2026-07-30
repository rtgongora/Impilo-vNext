# `path_contains` is segment-bounded — a trailing slash makes a pin inert

**Status:** finding, with this programme's own rules corrected (V059) and the rest reported.
**Found:** 2026-07-30, Work Context Resolution completion wave (B4/B5).
**Guard:** `scripts/guard/check-policy-path-pins.sh`

## The semantics

`PolicyEngine.pathContainsSegment` requires the pin to be followed by `/` or end-of-path:

```java
while ((idx = path.indexOf(pin, from)) >= 0) {
    int end = idx + pin.length();
    if (end == path.length() || path.charAt(end) == '/') {
        return true;
    }
    from = idx + 1;
}
return false;
```

That bounding is deliberate and worth keeping: it is what stops a pin of `/patients` granting
access to `/patientsummary`, closing the cross-service over-grant that bare substring matching
would allow.

It also means **a pin written with a trailing slash matches almost nothing**:

| pin | `/v1/patients` | `/v1/patients/` | `/v1/patients/p-123` |
|---|---|---|---|
| `"/patients"` | matches | matches | **matches** |
| `"/patients/"` | no | matches | **no** |

A pin ending in `/` is satisfied only by the bare collection root, or by the pathological
`/v1/patients//x`. It never matches a record. For a rule whose whole purpose is to govern
record access, the condition is decorative.

## How it was found

Not by reading. `ClinicalAccessBoundaryMatrixTest` runs V058's own rule text through the real
`PolicyEngine`, and its first assertion failed: a `FACILITY_MANAGEMENT` session read
`/v1/patients/p-123` under a DENY rule pinned `"/patients/"`. The pin was the reason.

`WorkModeBoundaryPinTest` now keeps both halves of that proof standing: it asserts the seeded
form does **not** deny a record, applies V059's own `replace()` pairs to V055's own rule text,
and asserts the corrected form does — so the test fails if either the defect or the fix is
ever misremembered.

## Corrected here

V055's nine rules, via **V059**. All nine were written with a trailing slash, so the WorkMode
boundary as seeded would have reported enforcement at the D7 cutover while permitting every
record access it names. The rows are seeded `active=false`, so the `UPDATE` is inert and
nothing was live to break. V055 itself is untouched — it has been applied, and editing an
applied migration breaks Flyway's checksum.

## Not corrected here — reported

The same shape appears in **~41 further rules** across five other lanes. They are **not**
corrected in this wave, for three reasons:

1. **The blast radius runs the other way.** These are `ALLOW` rules. An `ALLOW` whose
   condition never matches fails *closed*, so correcting one *widens* access. That is a
   change each lane must make deliberately, against its own route shapes — not a bulk
   find-and-replace by a passing programme.
2. **Intent is not always inferable.** `"/theatre/cases/"` may have been meant as the
   collection. The owning lane knows; this one is guessing.
3. **Nothing is live.** `ext_authz` is gated off, so the PDP is not on the preview request
   path today. No outage is implied by this report — only that the rules would not do what
   they say when it is.

| migration | pin | effect | rules |
|---|---|---|---|
| `V019__inpatient_clinical_write_policy_rules.sql` | `/ward-charts/` | ALLOW | 2 |
| `V020__patient_safety_policy_rules.sql` | `/patient-safety/` | ALLOW | 4 |
| `V021__rito_quality_safety_policy_rules.sql` | `/rito/` | ALLOW | 7 |
| `V027__inpatient_suite_policy_rules.sql` | `/escalations/`, `/discharge-summary/`, `/beds/` | ALLOW | 9 |
| `V028__daidzai_emergency_policy_rules.sql` | `/daidzai/incidents/`, `/daidzai/disasters/` | ALLOW | 6 |
| `V029__theatre_perioperative_policy_rules.sql` | `/theatre/cases/`, `/theatre/` | ALLOW | 22 |
| `V035__theatre_clinical_safety_policy_rules.sql` | `/theatre/cases/` | ALLOW | 20 |
| `V045__regulatory_org_isolation_shadow.sql` | `/regulatory/` | ALLOW, DENY | 3 |
| `V047__hpa_oversight_policies_shadow.sql` | `/regulatory/` | DENY | 1 |
| `V048__confidential_clinical_lane_policy_rules.sql` | `/confidential/`, `/safeguarding/` | ALLOW | 8 |
| `V050__khuluma_reachable_policy_rules.sql` | `/khuluma/` | ALLOW | 2 |
| `V055__work_mode_boundary_policy_rules.sql` | nine clinical/operational | DENY | 9 |
| `V056__break_glass_governance_policy_rules.sql` | `/v1/break-glass/review/` | ALLOW | 3 |

Counts are of pin occurrences in the source migration, not of distinct endpoints.

`V055` stays in the table at 9 although it is the one entry already fixed: the correction is an
`UPDATE` in V059, and V055's own text cannot be edited without breaking Flyway's checksum. The
table counts what is written in the migration, which is the only thing a text guard can see.

## The guard

`scripts/guard/check-policy-path-pins.sh` recomputes these counts and fails when a migration
gains a trailing-slash pin, or when a migration not in the table above introduces one. It does
not fail on the existing ones — a guard that fails on day one is a guard someone switches off.
Correcting a lane's rules means lowering its number in the table, so the registry shrinks as
the backlog is worked and cannot silently grow.
