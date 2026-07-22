# AHPCZ — Allied Health Practitioners Council of Zimbabwe

| Field | Value |
|---|---|
| Org code | `AHPCZ` |
| org_registry org_type | `PROFESSIONAL_COUNCIL` |
| Statutory basis | Health Professions Act [Chapter 27:19] |
| Professions (indicative — `TO_CONFIRM` full schedule) | The allied health professions not under a sister council: e.g. radiographers, dietitians/nutritionists, optometrists/opticians, psychologists, dental therapists/technicians, orthopaedic technologists, paramedics/EMTs (`TO_CONFIRM` each placement vs MRPCZ/MLCSCZ) |
| Jurisdiction | NATIONAL |

AHPCZ regulates the widest, most heterogeneous profession schedule — the register list below is
a seed SHAPE; the authoritative profession schedule MUST come from the council before V030 is
cut (`TO_CONFIRM`, ROM-W0 gate).

## Registers (varapi V029/V030 seeds — per-profession registers, `TO_CONFIRM`)
- `AHPCZ_<PROFESSION>` — one register per scheduled profession (e.g. `AHPCZ_RADIOGRAPHER`,
  `AHPCZ_DIETITIAN`, `AHPCZ_OPTOMETRIST`, `AHPCZ_PSYCHOLOGIST`, …)
- `AHPCZ_PROVISIONAL` — provisional/supervised (`TO_CONFIRM`)

## Appointment roles in use
REGISTRAR, DEPUTY_REGISTRAR, REGISTRATION_OFFICER, INSPECTOR, CPD_OFFICER,
INVESTIGATIONS_OFFICER, COMMITTEE_MEMBER, FINANCE_OFFICER, RECORDS_OFFICER, LEGAL_OFFICER,
COUNCIL_CEO.

## Working contexts
Standard council set.

## Committees (`TO_CONFIRM`)
Registration Committee · Professional Conduct Committee · Education/CPD Committee · Appeals
Committee (profession-stream sub-committees `TO_CONFIRM`).

## Application types (varapi FSM A)
INITIAL_REGISTRATION · ANNUAL_RENEWAL · RESTORATION · CHANGE_OF_DETAILS ·
QUALIFICATION_ADDITION · SCOPE_APPLICATION · CERTIFICATE_OF_GOOD_STANDING ·
VERIFICATION_REQUEST.

## Renewal cycle + CPD
ANNUAL (`TO_CONFIRM`); CPD units per profession stream `TO_CONFIRM`; Fundo-evidenced,
varapi-adjudicated.

## Fees
Structure seeded; amounts NULL + PENDING_REGULATOR_APPROVAL.

## Registration-number pattern
`TO_CONFIRM` — placeholder `^[A-Z]{1,4}\\d{3,6}$`.

## `council_regulatory_configs` seed block
```json
{
  "workflow_template_code": "COUNCIL_STANDARD_V1",
  "renewal_cycle": {"period": "ANNUAL", "TO_CONFIRM": true},
  "cpd_rules": {"required_units": null, "per_profession": true, "status": "TO_CONFIRM"},
  "fee_schedule_ref": "AHPCZ_FEES_PENDING",
  "document_requirements": ["qualification", "identity", "TO_CONFIRM"],
  "registers": ["TO_CONFIRM_PROFESSION_SCHEDULE"]
}
```

## Reports (W9)
Standard council set + per-profession breakdowns in MANAGEMENT/STATUTORY classes.
