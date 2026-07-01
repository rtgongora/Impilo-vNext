# Seed clinical form definitions

The canonical seed WHO-DAK exemplar form definitions live in the forms-service classpath:

```
services/forms-service/src/main/resources/seed-forms/*.json
```

They are loaded idempotently at startup by
`services/forms-service/.../config/ClinicalFormSeedLoader.java` (upsert by tenant+formKey, gated by
`forms.seed.clinical-forms.enabled`, default true) into the `fs_form_schemas` + `fs_form_schema_versions`
tables. The full WHO-DAK `ClinicalFormDefinition` JSON is stored immutably in the version snapshot; the
queryable applicability/governance metadata is lifted onto columns (V002) for the PCT resolver catalog.

## Seeded exemplars (10)

| # | formKey | Setting | Stage | Cadres | Wired e2e |
|---|---------|---------|-------|--------|-----------|
| 01 | `impilo.opd.triage.v1` | outpatient/emergency | triage | nurse+ | ✅ |
| 02 | `impilo.opd.consultation.v1` | outpatient/telemed | consultation | physician/CO | ✅ |
| 03 | `impilo.inpatient.admission.clerking.v1` | inpatient | admission | physician/CO | ✅ |
| 04 | `impilo.inpatient.discharge.summary.v1` | inpatient | discharge | physician/CO | ✅ |
| 05 | `impilo.inpatient.nursing.admission.v1` | inpatient | admission | nurse/midwife | seed |
| 06 | `impilo.inpatient.ward.round.v1` | inpatient | ward round | physician/CO/nurse | seed |
| 07 | `impilo.anc.contact1.v1` | outpatient/community | assessment | midwife/nurse | seed |
| 08 | `impilo.child.imci.v1` | outpatient/community | assessment | nurse/CO | seed |
| 09 | `impilo.ncd.chronic.review.v1` | outpatient/telemed | review | physician/CO/nurse | seed |
| 10 | `impilo.pharmacy.med.reconciliation.v1` | inpatient/outpatient | review | pharmacy | seed |

**All are DAK-aligned exemplars, not ratified national protocols** (see `../deferred-seams.md`). Each carries
role/cadre applicability, care setting/stage, coded fields, terminology bindings, and extraction
(`resourceMappings`) so the engine's pattern is fully exercised.
