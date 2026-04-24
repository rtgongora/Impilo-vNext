# Intake doctrine — Impilo vNext (Experience BFF wave)

This document captures the **intake**, **Experience Doctrine identity**, **day-zero bootstrap**, **self-help**, and **governed bulk import** model implemented on the Experience BFF in coordination with sovereign registries (Vito, Varapi, Tuso, Indawo) and trust-layer expectations (Tshepo at the edge).

## Experience Doctrine (human identity)

- **Impilo Health ID** (`healthId`) is the **primary longitudinal human anchor** in the Experience layer.
- **Legacy `cpid` fields in UI/API** where still present are treated as **display or compatibility slots**; new flows should prefer **`impiloHealthId`** / **`impiloId`** from Vito client registry payloads.
- Provider, council, national ID, employee, Fundo, MusheX, and similar identifiers are **linked identifiers** on profiles, not competing primary person keys.

## Intake is not full lifecycle

- Intake creates **shell / provisional** state and queues deeper verification, licensing, and merge review in downstream services.
- **Search-before-create** is mandatory where the BFF exposes search proxies (`POST /internal/v1/patients/search`, Varapi/Tuso search via registry controllers).

## BFF responsibilities (this wave)

| Area | Implementation |
|------|----------------|
| Client directory + walk-in create | `PatientController` → Vito `GET /v1/client-registry/clients`, `GET .../clients/{healthId}`, `POST /v1/identity/register`, `POST /v1/internal/clients/search` |
| Facility directory (home / workspace) | `FacilityController` → Tuso search + get with seed fallback |
| Registry zone lists | `RegistryController` → Varapi provider search, Tuso facility search |
| Intake orchestration (session / import / recovery / bootstrap probe) | `RegistryIntakeController` + `RegistryIntakeService` (Redis JSON, TTL configurable) |

## Redis-backed artefacts

Keys (prefix `impilo:registry-intake:`):

- `session:{uuid}` — progressive intake session (workflow/completion state, optional linked health id).
- `import:{uuid}` — import job metadata + preview + row results.
- `import-payload:{uuid}` — raw CSV text (governed max 400KB in service validation).
- `recovery:{uuid}` — self-help / operator recovery ticket shell.

## Governed bulk import (facilities)

1. `POST /internal/v1/registry-intake/import-jobs` with `targetRegistry: "FACILITY"` and `csv` body (header required).
2. Preview is embedded in the job document (`previewRows`, `previewSummary`).
3. `POST .../import-jobs/{id}/execute` with `{ "dryRun": true \| false }`.
   - Duplicate guard: **search-before-create** on `facilityCode` via Tuso internal search.
   - Rows that match existing codes surface as `DUPLICATE_CANDIDATE` without create.
4. Live create issues `POST /v1/internal/facilities` with minimal fields; failures return row-level `FAILED` with downstream message.

**CSV limitation:** comma-separated fields without embedded commas (MVP parser). Use RFC4180-aware tooling in a follow-up if needed.

## Day-zero bootstrap

`GET /internal/v1/registry-intake/bootstrap/snapshot` performs **read-only reachability and sample counts** against Vito dashboard, Varapi provider search (size 1), and Tuso facility search (size 1). It does **not** create organisations or roles; those remain **Keycloak / Tshepo policy** concerns with runbooks outside this BFF.

## Tshepo

- Request path continues to rely on **gateway trust headers** and Spring Security **realm roles** for coarse RBAC.
- Fine-grained PDP hooks per intake action are a **recommended next wave** (evaluate action/resource against Tshepo Authz before mutating registries).

## Manual testing (dev)

1. Start Redis + downstream stubs or real Vito/Varapi/Tuso with trust headers as in local `application.yml`.
2. `GET /internal/v1/registry-intake/bootstrap/snapshot` with mandatory v1.2 headers (`X-Tenant-ID`, `X-Pod-ID`, `X-Request-ID`, `X-Correlation-ID`) and bearer token if JWT enabled.
3. `POST /internal/v1/registry-intake/sessions` with JSON `{ "intakeType": "CLIENT_PROGRESSIVE", "targetRegistry": "VITO" }`.
4. `POST /internal/v1/patients/search` with Vito’s masked search body (see Vito `InternalClientController` contract).
5. Create import job with two-line CSV (header + one facility), then `execute` with `dryRun: true`, then `false` if Tuso accepts writes.

## Known gaps / next wave

- Indawo site intake UI + BFF orchestration (list is cursor-based today; add name filter server-side or client-side merge).
- Varapi provider **create** orchestration from intake session (Impilo ID anchor enforcement in one POST).
- Kafka / Tshepo Audit emit for `intake.*` and `import.*` events (structured envelope).
- Full Tshepo PDP matrix for IMPORT_ADMIN, IDENTITY_STEWARD, registry clerks.
- XLSX mapping UI and chunked import for very large files (S3/Landela file ref pattern).
