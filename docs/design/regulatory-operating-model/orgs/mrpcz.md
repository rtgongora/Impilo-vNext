# MRPCZ — Medical Rehabilitation Practitioners Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `MRPCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative) | Physiotherapists, occupational therapists, rehabilitation technicians, prosthetists/orthotists, speech & language therapists/audiologists (`TO_CONFIRM` each placement) |
| Jurisdiction | NATIONAL |

## Registers (varapi V029/V030 seeds — `TO_CONFIRM` exact statutory names)
- `MRPCZ_PHYSIOTHERAPIST` — Register of Physiotherapists
- `MRPCZ_OCCUPATIONAL_THERAPIST` — Register of Occupational Therapists
- `MRPCZ_REHAB_TECHNICIAN` — Register of Rehabilitation Technicians
- `MRPCZ_PROSTHETIST_ORTHOTIST` — Prosthetists/Orthotists (`TO_CONFIRM`)
- `MRPCZ_SPEECH_AUDIOLOGY` — Speech therapists/Audiologists (`TO_CONFIRM`)
- `MRPCZ_PROVISIONAL` — Provisional register (`TO_CONFIRM`)

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
  "fee_schedule_ref": "MRPCZ_FEES_PENDING",
  "document_requirements": ["qualification", "identity", "TO_CONFIRM"],
  "registers": ["MRPCZ_PHYSIOTHERAPIST", "MRPCZ_OCCUPATIONAL_THERAPIST", "MRPCZ_REHAB_TECHNICIAN"]
}
```

## Reports (W9)
Standard council set: OPERATIONAL queues · MANAGEMENT TAT · STATUTORY annual register return
(`TO_CONFIRM`) · PUBLIC_INTEREST register extract · OVERSIGHT HPA indicator feed.
