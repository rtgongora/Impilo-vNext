# NTCZ — Natural Therapists Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `NTCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Natural therapy practitioners — e.g. homeopathy, naturopathy, osteopathy, chiropractic, acupuncture/TCM (`TO_CONFIRM` the scheduled natural therapy professions; distinct from the Traditional Medical Practitioners Council, which is OUT of ROM scope) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — per-modality, `TO_CONFIRM`)
- `NTCZ_<MODALITY>` — one register per scheduled natural-therapy modality
  (e.g. `NTCZ_HOMEOPATH`, `NTCZ_NATUROPATH`, `NTCZ_CHIROPRACTOR`, `NTCZ_ACUPUNCTURIST`)
- `NTCZ_PROVISIONAL` — Provisional register (`TO_CONFIRM`)

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
  "fee_schedule_ref": "NTCZ_FEES_PENDING",
  "document_requirements": ["qualification", "identity", "TO_CONFIRM"],
  "registers": ["TO_CONFIRM_MODALITY_SCHEDULE"]
}
```

## Reports (W9)
Standard council set: OPERATIONAL queues · MANAGEMENT TAT · STATUTORY annual register return
(`TO_CONFIRM`) · PUBLIC_INTEREST register extract · OVERSIGHT HPA indicator feed.
