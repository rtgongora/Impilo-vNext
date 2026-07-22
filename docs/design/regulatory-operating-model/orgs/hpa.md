# HPA — Health Professions Authority

| Field | Value |
|---|---|
| Org code | `HPA` |
| org_registry org_type | `PUBLIC_HEALTH_AUTHORITY` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Role | Apex authority: coordination + supervision of the eight councils; facility/premises regulation (with councils); standards + policy administration |
| Jurisdiction | NATIONAL (inspectorate MAY be region-scoped — jurisdiction_code per appointment) |

## Mandate on the platform (distinct from the councils)

- **Oversight environment** (ROM-W10): council supervision, consolidated regulatory
  intelligence (aggregate indicators across all nine orgs), council performance monitoring,
  cross-council complaints + escalations, statutory reporting to the Ministry, shared
  registers/verification services, risk/audit/compliance oversight. Aggregate reads + explicit
  per-case escalation grants ONLY — no standing council operational access (doctrine §11).
- **Facility/premises regulation** (already built in tuso V018–V021): application types
  catalogue, 39 facility classifications, hpa_route PUBLIC_MISSION_LA|PRIVATE, hpa_class
  A|B|C, inspections, fees (SI 78/2017, amounts PENDING_REGULATOR_APPROVAL), PIC nomination.
  ROM-W6 adds the pre-licensing establishment case.

## Appointment roles in use
REGISTRAR (Secretary-General equivalent — `TO_CONFIRM` title), DEPUTY_REGISTRAR,
HPA_OVERSIGHT_OFFICER, HPA_INSPECTORATE_OFFICER, INSPECTOR, FINANCE_OFFICER, RECORDS_OFFICER,
LEGAL_OFFICER, COMMITTEE_MEMBER, COUNCIL_CEO (Authority CEO).

## Working contexts (workspaces)
HPA Secretariat · HPA Inspectorate · Registration & Licensing (facility lane) · Finance &
Revenue · Records & Certification · Legal & Appeals · Executive/Oversight.

## Registers
HPA holds the PREMISES register (tuso `facility_regulatory_profile` + `facility_credential` —
already de-facto; read projection only, no new register entity). It holds no professional
register — those are the councils'.

## Committees (org-registry V008 seeds)
`TO_CONFIRM` with HPA: minimum seed = Executive Committee, Finance Committee, Facilities
Standards Committee, Appeals Committee.

## Application types (tuso — existing + W6)
Existing `FacilityApplicationType`: NEW_REGISTRATION, RENEWAL, MATERIAL_CHANGE,
CHANGE_OF_PREMISES, CHANGE_OF_PRACTITIONER_IN_CHARGE, ADD_UNIT, REOPENING. W6 adds the
pre-licensing PRACTICE_ESTABLISHMENT case (new rail, enum untouched).

## Fees
SI 78 of 2017 structure seeded (tuso V021 `regulatory_fee_schedule`); amounts NULL +
PENDING_REGULATOR_APPROVAL until governed configuration.

## `council_regulatory_configs` seed block (varapi V028)
HPA appears in varapi.councils as the facility-regulator profile (council_type `HPA`):

```json
{
  "workflow_template_code": "HPA_FACILITY_DEFAULT",
  "renewal_cycle": {"period": "ANNUAL", "TO_CONFIRM": true},
  "cpd_rules": null,
  "fee_schedule_ref": "SI_78_2017",
  "document_requirements": ["TO_CONFIRM"],
  "oversight": {"aggregate_indicators": true, "escalation_grants": true}
}
```

## Registration-number pattern
Not applicable (no professional register). Facility identifiers per tuso
(`HPA_INSTITUTION_ID`, `HPA_REGISTRATION_NUMBER` identifier systems — already live).

## Reports (W9 definitions)
OPERATIONAL: facility application/inspection/renewal queues. MANAGEMENT: TAT by officer/stage/
class/route. STATUTORY: annual facilities return (`TO_CONFIRM` format). PUBLIC_INTEREST:
facility register extract. OVERSIGHT: 9-org consolidated indicators (applications, TAT,
complaints, disciplinary, CPD compliance, revenue) for Ministry.
