# vNext Service Accountability Matrix

> **Generated:** 2026-06-13  
> **Machine-readable:** [`reports/product/vnext-service-accountability-matrix.csv`](../../reports/product/vnext-service-accountability-matrix.csv)  
> **Regenerate:** `python3 scripts/architecture/generate-service-accountability-matrix.py`  
> **Preview truth:** 98/98 deployments ready; 89/89 K8s microservices enabled

---

## Executive summary

| Metric | Count |
|--------|------:|
| **Total classified components** | 146 |
| **Runtime deployment required** | 99 |
| **K8s microservices (helm)** | 89 |
| **Deployed in preview** | 89/89 microservices + 9 infra/dedicated |
| **Running in preview** | 98/98 deployments |
| **BFF downstream gaps** | 15 services |
| **Frontend wiring partial/unknown** | 78 registry services |
| **UAT modules (workbook)** | 47 of ~91 services |
| **Frontend↔backend parity complete** | 17 / 37 capability rows |
| **Mobile parity complete** | 8 / 37 capability rows |

**Doctrine:** No service is “optional” for product accountability. Wave sequencing is operational only.

---

## Accountability tiers

### Tier 1 — Runtime sovereign services (89 microservices)
All **built, deployed, running** in `impilo-full-preview` as of 2026-06-13.

Accountability requirements:
- BFF downstream URL (or documented N/A reason)
- Shell or mobile surface path
- Keycloak/trust posture documented
- UAT workbook row with Madi-level navigation specificity
- Health endpoint reachable in cluster

### Tier 2 — Runtime infrastructure (9 deployments)
`postgres`, `redis`, `kafka`, `keycloak`, `envoy`, `minio`, `hapi-fhir`, `butano-fhir`, `fhir-gateway-service` (where counted as infra path).

Accountability: platform health, not end-user workflow tests.

### Tier 3 — Experience orchestration (2 dedicated)
`one-ui-shell`, `experience-bff` — own full workbook modules; golden thread tests.

### Tier 4 — Build/validate only (33)
UI workspaces absorbed into shell (`portal`, `pct-web`, `oros-web`, etc.), libraries, mobile app artifacts.

Accountability: **build passes**, **routes absorbed** into `one-ui-shell`, **not separate K8s deployments**.

### Tier 5 — Doctrine-only external contracts (14)
`dhis2`, `external-elmis`, `banking-rails`, etc.

Accountability: **internal adapter must run**; external availability documented separately.

---

## BFF downstream gaps (blockers for orchestration)

These microservices are **deployed and running** but **lack** generated BFF `*_BASE_URL` mapping:

| Service | Product impact | Required fix |
|---------|----------------|--------------|
| `audit-ledger-service` | Admin audit trail aggregation | Add `AUDIT_LEDGER_BASE_URL` to generator |
| `butano-fhir` | FHIR layer (may use `FHIR_BASE_URL` instead) | Document N/A or add explicit mapping |
| `card-print-agent` | Card printing workflows | Add BFF proxy or document agent-only path |
| `connector-fhir-adapter` | HIE connector | Add `CONNECTOR_FHIR_BASE_URL` |
| `developer-portal-service` | Developer surfaces | Add or route via `/developer` only |
| `identity-assurance-service` | Assurance level flows | Add `IDENTITY_ASSURANCE_BASE_URL` |
| `jobs-service` | Background jobs admin | Add or ops-only surface |
| `observability-service` | Ops dashboards | Ops path; document lower walkthrough priority |
| `offline-edge-service` | Edge sync | Mobile/offline test path |
| `offline-sync-service` | Offline sync | Mobile test execution required |
| `pharmacy-elmis-adapter` | NatPharm adapter | Add `PHARMACY_ELMIS_BASE_URL` |
| `product-registry-service` | Product catalog | Add `PRODUCT_REGISTRY_BASE_URL` |
| `referral-service` | Referral workflows | Add `REFERRAL_BASE_URL` |
| `schema-registry-service` | Schema governance | Platform ops path |
| `security-hardening-service` | Security posture | Ops path |
| `share-slip-service` | Share slip flows | Add `SHARE_SLIP_BASE_URL` |
| `wellness-service` | **Deprecated** — Simba SoR | BFF uses `SIMBA_BASE_URL`; retire helm entry |

---

## Shell surfacing model

`one-ui-shell` uses **31 curated launcher apps** — not 1:1 service tiles. Services surface through **domain route families**:

| Shell domain | Backend services composed |
|--------------|---------------------------|
| `/clinical`, `/ehr` | PCT, BUTANO, inpatient, forms, rules |
| `/queue` | PCT, scheduling |
| `/pharmacy` | pharmacy-service, pharmacy-elmis-adapter |
| `/lab` | oros-service |
| `/madi/*` | madi-service (11 parity rows — **benchmark**) |
| `/finance/*` | mushex, costing-engine, msika, general-ledger |
| `/registry/*` | vito, varapi, tuso, ubomi, zibo |
| `/public-health/*` | indawo, surveillance, campaigns |
| `/marketplace/*` | msika-flow, msika-apps |
| `/learning/*` | learning-service (Fundo) |
| `/live/*` | live-service, rtc-gateway |
| `/nhume/*` | dispatch-service |
| `/operations/*` | workflow, integration-hub, observability |
| `/admin/*` | tshepo-*, workforce-governance |

**Explicit `serviceSlug` bindings (8):** fundo, vito, msika, nompilo, nhume, madi (×2), ndila.

**Gap:** ~79 microservices lack dedicated launcher tiles — acceptable if domain routes + BFF composition exist; **not acceptable** if no route and no API path.

---

## Services with no dedicated UAT workbook module (~44)

Platform/adapter tail lacking dedicated UAT modules in `uat-full-preview-test-summary-4917def8.json`:

`ai-model-registry-service`, `analytics-pipeline-service`, `asset-registry-service`, `audit-ledger-service`, `card-print-agent`, `channels-service`, `connector-fhir-adapter`, `credential-verification-service`, `data-access-governance-service`, `developer-portal-service`, `general-ledger-service`, `hr-payroll-service`, `identity-assurance-service`, `integration-hub`, `iot-ingestion-service`, `jobs-service`, `landela-adapter-service`, `llm-orchestration-service` (partial via Nompilo), `msika-apps-service`, `mushe-wallet-service`, `mvumo-service`, `national-data-repository-service`, `ndr-service`, `observability-service`, `offline-edge-service`, `offline-sync-service`, `procurement-service`, `product-registry-service`, `referral-service`, `schema-registry-service`, `security-hardening-service`, `share-slip-service`, `support-service`, `tshepo-offline-service`, `workforce-governance-service`, and others.

**Blocker classification:** `gap: not in testing workbook` — not `optional`.

---

## Maturity and blocker taxonomy

| Label | Meaning |
|-------|---------|
| **running** | Deployed, pod ready, health OK |
| **running but incomplete** | Deployed; partial parity |
| **running but lower test priority** | Platform/ops; workbook row still required |
| **running with external dependency unavailable** | Adapter up; honest blocked UI |
| **blocked: BFF gap** | No downstream URL |
| **blocked: not surfaced** | No shell/mobile route |
| **blocked: not testable** | Missing seed data/credentials |
| **defect: not deployed** | Runtime required but helm disabled |
| **supporting component** | Library or absorbed UI workspace |
| **external contract reference** | Doctrine-only; adapter accountability applies |

---

## Definition of Done — preview testability (per service)

1. Service pod `Running` and `/actuator/health` returns UP in cluster
2. BFF can reach service via cluster DNS (or documented direct path)
3. Shell or mobile route exists with honest state labels
4. Workbook row: user story, acceptance criteria, DoD, Given/When/Then
5. Exact navigation paths listed (Madi benchmark)
6. Test credentials and seed data documented
7. Pass/fail rule and evidence capture defined
8. Regression risk noted
9. Blocker reason if any step fails

---

## CSV column reference

See [`reports/product/vnext-service-accountability-matrix.csv`](../../reports/product/vnext-service-accountability-matrix.csv) for all 146 rows with:

`service_name`, `service_slug`, `plane_domain`, `product_role`, `internal_or_external`, `runtime_deployment_required`, `deployed_preview`, `running_preview`, `surfaced_shell`, `surfaced_mobile`, `bff_downstream`, `keycloak_trust`, `required_roles`, `required_context`, `main_routes`, `main_apis`, `events_topics`, `dependencies`, `health_endpoint`, `test_scenarios_required`, `current_maturity`, `current_blockers`, `required_fixes`, `preview_testability_dod`

---

## Immediate fixes recommended

1. **Rename classification** `optional_full_boot` → `wave_sequenced_full_boot` (language correction)
2. **Close 15 BFF downstream gaps** in `generate-full-preview-bff-downstream-env.mjs`
3. **Expand UAT workbook** from 47 → 91 service modules
4. **Reduce BFF stub modes** where sovereign seed exists (facilities → TUSO live)
5. **Per-service preview auth** instead of global OAuth disable only
6. **Parity pass** on 20 partial frontend↔backend rows (HIGH first)
