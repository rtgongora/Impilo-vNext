# PCZ — Pharmacists Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `PCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Pharmacists, pharmacy technicians, dispensing assistants (`TO_CONFIRM` placement) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — `TO_CONFIRM` exact statutory names)
- `PCZ_PHARMACIST` — Register of Pharmacists
- `PCZ_TECHNICIAN` — Register of Pharmacy Technicians
- `PCZ_DISPENSING_ASSISTANT` — Dispensing assistants (`TO_CONFIRM`)
- `PCZ_PROVISIONAL` — Provisional/intern register (`TO_CONFIRM`)

## Appointment roles in use
REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
COUNCIL_CEO.

## Working contexts
Standard council set (Registrar's Office · Registration & Licensing · Inspections & Compliance
· Complaints & Investigations · Disciplinary Secretariat · Finance · Records · Legal &
Appeals · Committee Member · Executive).

## Committees (`TO_CONFIRM`)
Registration Committee · Professional Conduct Committee · Education/CPD Committee · Appeals
Committee.

## Application types (varapi FSM A)
INITIAL_REGISTRATION · ANNUAL_RENEWAL · RESTORATION · CHANGE_OF_DETAILS ·
QUALIFICATION_ADDITION · SCOPE_APPLICATION · CERTIFICATE_OF_GOOD_STANDING ·
VERIFICATION_REQUEST.

Note: pharmacy PREMISES licensing is the HPA/tuso lane (a pharmacy establishment runs ROM-W6's
practice establishment case with PCZ as external council reviewer — tuso
`external_council_review` already models this).

## Renewal cycle + CPD
ANNUAL (`TO_CONFIRM`); CPD units `TO_CONFIRM`; Fundo-evidenced, varapi-adjudicated.

## Fees
Structure seeded; amounts NULL + PENDING_REGULATOR_APPROVAL.

## Registration-number pattern
`TO_CONFIRM` — placeholder `^[A-Z]{1,3}\\d{3,6}$`.

## `council_regulatory_configs` seed block
```json
{
  "workflow_template_code": "COUNCIL_STANDARD_V1",
  "renewal_cycle": {"period": "ANNUAL", "TO_CONFIRM": true},
  "cpd_rules": {"required_units": null, "status": "TO_CONFIRM"},
  "fee_schedule_ref": "PCZ_FEES_PENDING",
  "document_requirements": ["qualification", "internship_evidence", "identity", "TO_CONFIRM"],
  "registers": ["PCZ_PHARMACIST", "PCZ_TECHNICIAN"]
}
```

## Reports (W9)
Standard council set: OPERATIONAL queues · MANAGEMENT TAT · STATUTORY annual register return
(`TO_CONFIRM`) · PUBLIC_INTEREST register extract · OVERSIGHT HPA indicator feed.
