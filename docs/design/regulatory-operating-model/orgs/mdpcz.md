# MDPCZ — Medical & Dental Practitioners Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `MDPCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Medical practitioners, dental practitioners, specialists (medical + dental) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — `TO_CONFIRM` exact statutory register names)
- `MDPCZ_MEDICAL` — Register of Medical Practitioners
- `MDPCZ_DENTAL` — Register of Dental Practitioners
- `MDPCZ_SPECIALIST` — Specialist Register (medical + dental specialities)
- `MDPCZ_PROVISIONAL` — Provisional/intern register (supervised practice)
- `TO_CONFIRM`: foreign-trained temporary register; dental therapists/technicians placement
  (MDPCZ vs AHPCZ).

## Appointment roles in use
REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
COUNCIL_CEO.

## Working contexts
Registrar's Office · Registration & Licensing · Inspections & Compliance · Complaints &
Investigations · Disciplinary Committee Secretariat · Finance & Revenue · Records &
Certification · Legal & Appeals · Council/Committee Member · Executive.

## Committees (org-registry V008 seeds — `TO_CONFIRM`)
Registration Committee · Professional Conduct (Disciplinary) Committee · Education/CPD
Committee · Appeals Committee.

## Application types (varapi FSM A)
INITIAL_REGISTRATION · PROVISIONAL_REGISTRATION · SPECIALIST_REGISTRATION · ANNUAL_RENEWAL ·
RESTORATION · CHANGE_OF_DETAILS · QUALIFICATION_ADDITION · SCOPE_APPLICATION ·
CERTIFICATE_OF_GOOD_STANDING · VERIFICATION_REQUEST.

## Renewal cycle + CPD
ANNUAL practising-certificate renewal (`TO_CONFIRM`). CPD: required units per cycle
`TO_CONFIRM` — structure = `provider_council_cpd_profiles` (required vs earned units);
evidence via Fundo seam; renewal gated on varapi-adjudicated compliance (ROM-W5).

## Fees
Council fee schedule structure seeded; amounts NULL + PENDING_REGULATOR_APPROVAL.

## Registration-number pattern (varapi councils.registration_number_pattern)
`TO_CONFIRM` — placeholder `^[A-Z]{1,3}\\d{3,6}$` until the registrar confirms the format.

## `council_regulatory_configs` seed block (varapi V028)
```json
{
  "workflow_template_code": "COUNCIL_STANDARD_V1",
  "renewal_cycle": {"period": "ANNUAL", "TO_CONFIRM": true},
  "cpd_rules": {"required_units": null, "status": "TO_CONFIRM"},
  "fee_schedule_ref": "MDPCZ_FEES_PENDING",
  "document_requirements": ["qualification", "internship_evidence", "identity", "TO_CONFIRM"],
  "registers": ["MDPCZ_MEDICAL", "MDPCZ_DENTAL", "MDPCZ_SPECIALIST", "MDPCZ_PROVISIONAL"]
}
```

## Reports (W9)
OPERATIONAL: registration/renewal/RFI/complaint/disciplinary queues. MANAGEMENT: TAT by
officer/stage/profession. STATUTORY: annual register return (`TO_CONFIRM`). PUBLIC_INTEREST:
public register extract (disclosure-classed). OVERSIGHT: HPA indicator feed.
