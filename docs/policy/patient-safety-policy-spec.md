# Patient Safety — Tshepo Policy SPEC (queued)

> **Lock notice:** `PolicyEngine.java` / OPA is single-writer-locked to the consent/trust (CZO)
> cluster. This document is the **authoritative policy specification** for patient-safety
> authorization, queued for that cluster to author as `impilo.authz` rego. **Do not edit
> PolicyEngine.java from this lane.** Until enforced, the service relies on the tech-companion
> trust-header contract and role context carried in `X-Purpose-Of-Use` / actor headers.

## Roles

| Role | Scope |
|---|---|
| `citizen` | report own ADR/AEFI; respond to follow-ups about own report |
| `caregiver` | report on behalf of a dependant; respond to follow-ups |
| `provider` | create/submit ADR/AEFI from clinical context; view own + facility reports |
| `pharmacist` | as provider, medicine-focused; view dispense-linked reports |
| `vaccinator` | as provider, AEFI-focused |
| `facility-safety-focal` | view + manage all reports/cases for their facility |
| `district-pho` | view district cases; assigned investigations |
| `mcaz-reviewer` | triage, review, causality, follow-up, manual VigiFlow entry, mark export-ready |
| `mcaz-supervisor` | reviewer rights + reassign, close, escalate, open/close investigations |
| `sysadmin` | configuration + adapter status; no clinical content authoring |
| `restricted-phi` | de-identified views only (no `subject_cpid`, contact, narrative PII) |

## Resource → action matrix (allow)

| Action | citizen/caregiver | provider/pharmacist/vaccinator | facility-safety-focal | mcaz-reviewer | mcaz-supervisor |
|---|---|---|---|---|---|
| create/submit report | own only | yes | facility | — | — |
| read report | own only | own + facility | facility | all | all |
| triage / review / causality | — | — | — | yes | yes |
| request follow-up | — | — | — | yes | yes |
| respond to follow-up | own report | own report | facility | — | — |
| manual VigiFlow entry | — | — | — | yes | yes |
| mark export-ready | — | — | — | yes | yes |
| open/update investigation | — | — | view | assigned | yes |
| close / escalate / reassign | — | — | — | — | yes |
| config / adapters (read) | — | — | facility | yes | yes |

## Conditions (ABAC)
- **Tenant + pod isolation** on every decision (`X-Tenant-ID`, `X-Pod-ID`).
- **Subject relationship:** citizen/caregiver actions require `subject_is_self` or a verified
  caregiver relationship to `subject_cpid`.
- **Facility scope:** provider/facility-focal reads restricted to `reporter_facility_id` in actor's
  facility set.
- **Purpose of use:** `PHARMACOVIGILANCE` required for write actions; PHI fields gated by assurance
  level for `restricted-phi`.
- **Workflow state:** edits only in `DRAFT`/`NEEDS_MORE_INFORMATION`; regulator actions blocked on
  `CLOSED`.
- **Audit:** every meaningful action emits a `ps_case_action` row + domain event (serialized chain).

## Rego sketch (for the CZO cluster to finalise)

```rego
package impilo.authz.patient_safety

default allow = false

# MCAZ reviewers/supervisors may perform case disposition actions
allow {
    input.action in {"triage","review","causality","follow_up_request","vigiflow_manual_entry","mark_export_ready"}
    input.actor.role in {"mcaz-reviewer","mcaz-supervisor"}
    input.resource.tenant_id == input.actor.tenant_id
    input.resource.status != "CLOSED"
}

# Reporters may create/submit and read their own reports
allow {
    input.action in {"report_create","report_submit","report_read","follow_up_response"}
    input.actor.role in {"citizen","caregiver","provider","pharmacist","vaccinator"}
    own_report
    input.actor.purpose_of_use == "PHARMACOVIGILANCE"
}

own_report {
    input.resource.reporter_actor_id == input.actor.id
}
own_report {
    input.actor.role == "caregiver"
    input.resource.subject_cpid == input.actor.caregiver_for
}
```

Queued via the consent/trust coordination channel; see the sprint coordination memo.
