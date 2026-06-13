# vNext BFF Downstream Gap Closure Plan

> **Wave:** Product Completion Wave 1  
> **Generated:** 2026-06-13  
> **Status:** Implemented (source); not redeployed

---

## Summary

| Metric | Before | After |
|--------|-------:|------:|
| Helm-enabled microservices | 89 | 89 |
| BFF `SERVICE_ENV` mappings | 72 | **88** |
| Documented exclusions | 0 | **1** (`wellness-service`) |
| **Unresolved gaps** | **17** | **0** |
| localhost in generated preview env | 0 | **0** |

---

## Root cause

`generate-full-preview-bff-downstream-env.mjs` maintained a hand-curated `SERVICE_ENV` table that lagged behind full-boot wave expansion. Preview BFF pods fell back to `application.yml` localhost defaults for unmapped services.

---

## Missing mappings (identified and closed)

| # | Service | Env var added | Port (preview) |
|---|---------|---------------|----------------|
| 1 | audit-ledger-service | `AUDIT_LEDGER_BASE_URL` | 8350 |
| 2 | butano-fhir | `BUTANO_FHIR_BASE_URL` | 8289 |
| 3 | card-print-agent | `CARD_PRINT_AGENT_BASE_URL` | 8291 |
| 4 | connector-fhir-adapter | `CONNECTOR_FHIR_BASE_URL` | 8151 |
| 5 | developer-portal-service | `DEVELOPER_PORTAL_BASE_URL` | 8370 |
| 6 | identity-assurance-service | `IDENTITY_ASSURANCE_BASE_URL` | 8201 |
| 7 | jobs-service | `JOBS_SERVICE_BASE_URL` | 8109 |
| 8 | observability-service | `OBSERVABILITY_BASE_URL` | 8211 |
| 9 | offline-edge-service | `OFFLINE_EDGE_BASE_URL` | 8360 |
| 10 | offline-sync-service | `OFFLINE_SYNC_BASE_URL` | 8095 |
| 11 | pharmacy-elmis-adapter | `PHARMACY_ELMIS_BASE_URL` | 8099 |
| 12 | product-registry-service | `PRODUCT_REGISTRY_BASE_URL` | 8097 |
| 13 | referral-service | `REFERRAL_SERVICE_BASE_URL` | 8399 |
| 14 | schema-registry-service | `SCHEMA_REGISTRY_BASE_URL` | 8371 |
| 15 | security-hardening-service | `SECURITY_HARDENING_BASE_URL` | 8221 |
| 16 | share-slip-service | `SHARE_SLIP_BASE_URL` | 8104 |

---

## Documented exclusion (not a gap)

| Service | Reason |
|---------|--------|
| `wellness-service` | Deprecated SoR; wellness absorbed by `simba-service`. BFF `wellness-base-url` aliases `SIMBA_BASE_URL` in `application.yml`. |

---

## Implementation changes

| File | Change |
|------|--------|
| `scripts/full-boot/generate-full-preview-bff-downstream-env.mjs` | +16 mappings, `BFF_DOWNSTREAM_EXCLUDED`, `validateCoverage()`, PCW-2 `IMPILO_BFF_FACILITIES_MODE: live` |
| `deploy/helm/impilo-vnext/values-full-preview-bff-env.generated.yaml` | Regenerated — **97 env vars**, no localhost, facilities **live** |
| `services/experience-bff/src/main/resources/application.yml` | `orchestration-backlog` bindings for 16 env vars |
| `services/experience-bff/.../OrchestrationBacklogEndpoints.java` | Spring binding for backlog endpoints |
| `scripts/guard/check-bff-downstream-mappings.sh` | **New** — fails CI if gaps reappear |

---

## Verification

```bash
node scripts/full-boot/generate-full-preview-bff-downstream-env.mjs
bash scripts/guard/check-bff-downstream-mappings.sh
```

**Result:** PASS (2026-06-13)

---

## Follow-up (PCW-2 complete; redeploy pending)

1. ~~Wire BFF `application.yml` properties for new env vars where Java clients are added~~ → **16/16 bound in `orchestration-backlog`; clients backlog**
2. Add integration tests per new downstream client (as clients are implemented)
3. **Preview redeploy required** to apply PCW-1/PCW-2 generated env + BFF image (user authorization)
4. ~~Switch `IMPILO_BFF_FACILITIES_MODE` from `stub` → `live` when TUSO seed confirmed~~ → **done in PCW-2**

See `reports/product/pcw-2-tier1-parity-closure-report.md`.

---

## Acceptance checklist

- [x] 15 missing mappings added (+ 1 documented exclusion)
- [x] Generated env uses cluster DNS `http://{service}:{port}`
- [x] Deterministic validation in generator (exit 1 on gap)
- [x] Guard script added
- [x] No localhost in generated preview config
- [ ] Running BFF pod updated (requires redeploy)
