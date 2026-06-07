# Local development port allocation (authoritative)

**Phase A0 — single source of truth** for host ports when running Spring Boot services on bare metal (`localhost`). Docker Compose maps container ports to these same defaults where a service is included in compose.

Override any port with **`SERVER_PORT`** (or the service-specific env documented in that service’s `application.yml`).

Canonical process: [`docs/roadmaps/agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md).

Service metadata (plane, sovereign, product names): [`docs/registry/services-registry.yaml`](../registry/services-registry.yaml) and generated [`services-index.md`](../registry/services-index.md).

---

## Reserved / infrastructure (do not bind Java services here)

| Port | Use |
|------|-----|
| 8080 | Keycloak |
| 9000 / 9001 | MinIO |
| 9092 | Kafka (client) |
| 9090 | Prometheus (host map in observability stack) — **not** TSHEPO gRPC |
| 10000 / 9901 | Envoy |
| 3000–3006 | UI apps (see `CLAUDE.md`; **3000** = One UI Shell / merged Experience) |

**TSHEPO Authz gRPC** uses **9090** *inside* `tshepo-authz-service` only (separate from host Prometheus when both run on one machine — use profiles or different hosts).

---

## Core platform & trust

| Port | Service / module | Notes |
|------|------------------|--------|
| **8079** | `tshepo-service` | Legacy monolith-style PDP; **do not** run alongside `tshepo-authz-service` on 8081 without stopping one |
| **8081** | `tshepo-authz-service` | HTTP; gRPC **9090** |
| **8181** | `tshepo-identity-service` | |
| **8182** | `tshepo-consent-service` | |
| **8183** | `tshepo-audit-service` | |
| **8184** | `tshepo-keys-service` | |
| **8185** | `tshepo-offline-service` | |

---

## Registry spine

| Port | Service / module |
|------|------------------|
| 8082 | `vito-service` |
| 8083 | `varapi-service` |
| 8084 | `tuso-service` |
| 8085 | `zibo-service` |
| 8086 | `msika-service` |
| **8087** | `ubomi-service` |
| 8092 | `landela-adapter-service` |
| **8150** | `indawo-service` |
| **8151** | `connector-fhir-adapter` |
| **8152** | `national-data-repository-service` |
| 8097 | `product-registry-service` |

---

## Clinical execution & SHR

| Port | Service / module | Notes |
|------|------------------|--------|
| 8088 | `pct-service` | |
| **8125** | `simba-service` | SIMBA — wellness & lifestyle orchestration |
| 8089 | `oros-service` | |
| 8090 | `butano-service` | HAPI host mapping may also use 8090 in compose — container network isolates |
| **8091** | `fhir-gateway-service` | BFF `fhir-gateway-base-url` |
| **8289** | `butano-fhir` | FHIR orchestration layer; not the same as gateway |
| **8121** | `inpatient-service` | |
| **8300** | `madi-service` | MADI — blood donation, blood bank, transfusion |
| **8122** | `community-service` | Community health / CHW / outreach (PCT care setting) |
| 8093 | `document-service` | Document Store |
| **8113** | `pacs-adapter-service` | |
| 8096 | `pharmacy-service` | |
| 8099 | `pharmacy-elmis-adapter` | |
| 8098 | `inventory-service` | |
| **8108** | `inventory-elmis-adapter` | |
| **8291** | `card-print-agent` | |

---

## Finance & marketplace

| Port | Service / module |
|------|------------------|
| 8100 | `msika-flow-service` |
| 8101 | `costing-engine-service` (COSTA) |
| **8102** | `mushex-service` (MUSheX) |
| **8140** | `coverage-service` |
| 8094 | `credential-verification-service` |
| **8104** | `share-slip-service` |
| 8095 | `offline-sync-service` |
| **8109** | `jobs-service` |

---

## Knowledge, workflow, extensions (aligned with Experience BFF defaults)

| Port | Service / module |
|------|------------------|
| **8230** | `search-service` |
| **8240** | `forms-service` |
| **8241** | `rules-service` |
| **8250** | `workflow-service` |
| 8260 | `guidance-service` |
| **8265** | `booking-service` |
| **8280** | `ai-model-registry-service` | AI governance & model registry (Law 11 / v1.3 Ring-0) |
| 8270 | `clinical-knowledge-platform-service` |

---

## Data & governance

| Port | Service / module |
|------|------------------|
| **8215** | `data-pipeline-service` |
| **8210** | `data-ingestion-service` |
| **8211** | `observability-service` |
| 8220 | `data-governance-service` |
| **8221** | `security-hardening-service` |
| **8232** | `ndr-service` |
| **8233** | `data-warehouse-service` |
| 8170 | `data-access-governance-service` |

---

## Operations & channels

| Port | Service / module |
|------|------------------|
| 8110 | `integration-hub` |
| **8200** | `notification-service` |
| **8201** | `identity-assurance-service` |
| 8130 | `channels-service` |
| 8180 | `surveillance-service` |
| 8190 | `campaigns-service` |
| **8176** | `reporting-service` |
| 8310 | `asset-registry-service` |
| 8320 | `dispatch-service` |
| 8330 | `iot-ingestion-service` |
| 8340 | `support-service` |
| 8350 | `audit-ledger-service` |
| 8360 | `offline-edge-service` |
| 8370 | `developer-portal-service` |
| 8371 | `schema-registry-service` |

---

## Experience layer

| Port | Service / module |
|------|------------------|
| **8160** | `experience-bff` |
| **8161** | `wellness-service` (citizen wellness, health wallet, Health Connect ingest — BFF proxies same paths) |

**Optional DB split:** run `wellness-service` with Spring profile `wellness-own-db` and database `impilo_wellness` (see `services/wellness-service/src/main/resources/application-wellness-own-db.yml`) after provisioning an empty Postgres DB and running Flyway once.

---

## Experience BFF downstream defaults

These match `ServiceClientConfig` / `impilo.services` in `experience-bff` (local URLs without path suffix unless noted).

| Env-style key | Default base URL |
|---------------|------------------|
| PCT | `http://localhost:8088` |
| OROS | `http://localhost:8089` |
| Pharmacy | `http://localhost:8096` |
| BUTANO | `http://localhost:8090` |
| MSIKA | `http://localhost:8086` |
| Msika Flow | `http://localhost:8100` |
| MUSheX | `http://localhost:8102` |
| VITO | `http://localhost:8082` |
| TUSO | `http://localhost:8084` |
| VARAPI | `http://localhost:8083` |
| Document Store | `http://localhost:8093` |
| COSTA | `http://localhost:8101` |
| Coverage | `http://localhost:8140` |
| Surveillance | `http://localhost:8180` |
| Campaigns | `http://localhost:8190` |
| Indawo | `http://localhost:8150` |
| Data governance | `http://localhost:8220` |
| Landela | `http://localhost:8092` |
| Notification | `http://localhost:8200` |
| FHIR (HAPI) | `http://localhost:8090/fhir` |
| FHIR Gateway | `http://localhost:8091` |
| Search | `http://localhost:8230` |
| Forms | `http://localhost:8240` |
| Rules | `http://localhost:8241` |
| Workflow | `http://localhost:8250` |
| Guidance | `http://localhost:8260` |
| Clinical Knowledge Platform | `http://localhost:8270` (`impilo.clinical-platform.base-url`) |
| Wellness | `http://localhost:8161` (`impilo.services.wellness-base-url` / `WELLNESS_SERVICE_BASE_URL`) |

---

## Change log

| Date | Change |
|------|--------|
| 2026-04-12 | Added **8161** `wellness-service` (citizen wellness + Health Connect); BFF `wellness-base-url` default. |
| 2026-04-11 | Phase A0: unique defaults, BFF alignment, compose + manifest sync. |
