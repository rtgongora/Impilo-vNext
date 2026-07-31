# Emergency pack — standards traceability

## Baseline

Governed register: [`docs/clinical-governance/emergency/standards-baseline.json`](../../clinical-governance/emergency/standards-baseline.json)

| standardId | Family | Role |
|------------|--------|------|
| EMS.IITT.ADULT | IITT | Adult acuity of record |
| EMS.IITT.PAEDIATRIC | IITT | Under-12 acuity |
| EMS.IITT.HIGH_RISK | IITT | Starred high-risk definitions |
| EMS.BEC.ABCDE | BEC | Undifferentiated assessment / danger signs |
| EMS.DSEC.MINIMUM_DATASET | DSEC | 47 core + 31 extended elements + indicators |
| EMS.SSC26.SCREENING | SSC26 | Sepsis screening (EWS, not qSOFA) |
| EMS.EDLIZ.EMERGENCY_MEDICINES | EDLIZ | Emergency medicines |

## Exclusions / status

[`docs/clinical-governance/emergency/coverage-exclusions.json`](../../clinical-governance/emergency/coverage-exclusions.json)

| standardId | Decision | Wave |
|------------|----------|------|
| EMS.IITT.* / BEC / SSC26 | DEFERRED → content citation | W4 (engine/wiring progressed; CKP citation still tracked) |
| EMS.DSEC.MINIMUM_DATASET | **SHIPPED_PARTIAL** | W17 — projection + mapping landed; value lists still ENGINEERING_SEED |
| EMS.EDLIZ.EMERGENCY_MEDICINES | DEFERRED | W8 |

DSEC column mapping: [`dsec-element-mapping.json`](dsec-element-mapping.json).

## Guard

Clinical conclusions stay out of TypeScript in the emergency feature tree:
`scripts/guard/check-no-ts-clinical-logic.sh` (proven both ways).
