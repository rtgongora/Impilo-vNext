# EHPCZ — Environmental Health Practitioners Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `EHPCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Environmental health officers/practitioners, environmental health technicians (`TO_CONFIRM` full schedule) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — `TO_CONFIRM` exact statutory names)
- `EHPCZ_OFFICER` — Register of Environmental Health Officers/Practitioners
- `EHPCZ_TECHNICIAN` — Register of Environmental Health Technicians
- `EHPCZ_PROVISIONAL` — Provisional register (`TO_CONFIRM`)

## Appointment roles in use
REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
COUNCIL_CEO.

## Working contexts
Standard council set.

## Committees (`TO_CONFIRM`)
Registration Committee · Professional Conduct Committee · Education/CPD Committee · Appeals
Committee.

## Application types (varapi FSM A)
INITIAL_REGISTRATION · ANNUAL_RENEWAL · RESTORATION · CHANGE_OF_DETAILS ·
QUALIFICATION_ADDITION · SCOPE_APPLICATION · CERTIFICATE_OF_GOOD_STANDING ·
VERIFICATION_REQUEST.

## Renewal cycle + CPD
ANNUAL (`TO_CONFIRM`); CPD units `TO_CONFIRM`; Fundo-evidenced, varapi-adjudicated.

## Fees
Structure seeded; amounts NULL + PENDING_REGULATOR_APPROVAL.

## Registration-number pattern
`TO_CONFIRM` — placeholder `^[A-Z]{1,4}\\d{3,6}$`.

## `council_regulatory_configs` seed block
```json
{
  "workflow_template_code": "COUNCIL_STANDARD_V1",
  "renewal_cycle": {"period": "ANNUAL", "TO_CONFIRM": true},
  "cpd_rules": {"required_units": null, "status": "TO_CONFIRM"},
  "fee_schedule_ref": "EHPCZ_FEES_PENDING",
  "document_requirements": ["qualification", "identity", "TO_CONFIRM"],
  "registers": ["EHPCZ_OFFICER", "EHPCZ_TECHNICIAN"]
}
```

## Reports (W9)
Standard council set: OPERATIONAL queues · MANAGEMENT TAT · STATUTORY annual register return
(`TO_CONFIRM`) · PUBLIC_INTEREST register extract · OVERSIGHT HPA indicator feed.
