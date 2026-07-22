# NCZ — Nurses Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `NCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Nurses (general/registered), midwives, mental health nurses, nurse specialists (theatre, ICU, anaesthetics …), primary care nurses, nurse aides (`TO_CONFIRM` placement) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — `TO_CONFIRM` exact statutory names)
- `NCZ_GENERAL` — Register of General/Registered Nurses
- `NCZ_MIDWIFE` — Register of Midwives
- `NCZ_MENTAL_HEALTH` — Register of Mental Health Nurses
- `NCZ_SPECIALIST` — Specialist nursing register (theatre/ICU/anaesthetic/…)
- `NCZ_PRIMARY_CARE` — Primary care nurses (`TO_CONFIRM`)
- `NCZ_PROVISIONAL` — Provisional/student-to-registration bridge (`TO_CONFIRM`)

## Appointment roles in use
REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
COUNCIL_CEO.

## Working contexts
Registrar's Office · Registration & Licensing · Inspections & Compliance · Complaints &
Investigations · Disciplinary Committee Secretariat · Finance & Revenue · Records &
Certification · Legal & Appeals · Council/Committee Member · Executive.

## Committees (`TO_CONFIRM`)
Registration Committee · Professional Conduct Committee · Education/CPD Committee · Appeals
Committee.

## Application types (varapi FSM A)
INITIAL_REGISTRATION · ANNUAL_RENEWAL · RESTORATION · CHANGE_OF_DETAILS ·
QUALIFICATION_ADDITION (e.g. midwifery onto general) · SCOPE_APPLICATION ·
CERTIFICATE_OF_GOOD_STANDING · VERIFICATION_REQUEST.

## Renewal cycle + CPD
ANNUAL practising-certificate renewal (`TO_CONFIRM`). CPD units per cycle `TO_CONFIRM`;
Fundo-evidenced, varapi-adjudicated, renewal-gating (ROM-W5).

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
  "fee_schedule_ref": "NCZ_FEES_PENDING",
  "document_requirements": ["qualification", "identity", "TO_CONFIRM"],
  "registers": ["NCZ_GENERAL", "NCZ_MIDWIFE", "NCZ_MENTAL_HEALTH", "NCZ_SPECIALIST"]
}
```

## Reports (W9)
Standard council set: OPERATIONAL queues · MANAGEMENT TAT · STATUTORY annual register return
(`TO_CONFIRM`) · PUBLIC_INTEREST register extract · OVERSIGHT HPA indicator feed.
