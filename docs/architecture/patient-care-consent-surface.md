# Patient care — consent surface (system + integration)

**Status:** Living — describes how **Mvumo** (sovereign Ring-0 service, same class as Tshepo / VITO) surfaces in the **Experience** chart without hiding consent only in history lists.

## System role

| Concern | Owner |
|--------|--------|
| Consent orchestration (templates, requests, sessions, proof) | **mvumo-service** (`:8195`) |
| FHIR Consent storage & evaluate for access | **tshepo-consent-service** |
| Longitudinal clinical summary (PCT view) | **pct-service** |
| CPID / patient resolution | **vito-service** |
| Chart UI (banner, summary, emergency strip) | **one-ui-shell** / **experience** → **experience-bff** only |

## Experience BFF

- **Aggregated chart payload:** `GET /internal/v1/summary/patient/{patientId}` — `EhrPatientSummaryController` merges PCT health summary with Mvumo `consentSummary` (via `GET {mvumo}/internal/v1/mvumo/consent-summary?patientRef=Patient/{cpid}`).
- **Direct Mvumo API (same trust headers):** ` /internal/v1/mvumo/**` proxied by `MvumoServiceProxyController`.
- **Configuration:** `MVUMO_BASE_URL` / `impilo.services.mvumo-base-url` — same pattern as `VITO_BASE_URL`, `PCT_BASE_URL`, etc. See [`experience-bff-downstream-route-map.md`](./experience-bff-downstream-route-map.md).

## Frontend

- Hooks: `usePatientSummary`, `usePatientConsentSurface` — [`ui/experience/src/hooks/queries/useSummary.ts`](../../ui/experience/src/hooks/queries/useSummary.ts) (mirrored in `ui/one-ui-shell`).
- Next.js rewrites `/internal/*` → BFF (`NEXT_PUBLIC_BFF_URL`, default `http://localhost:8160`).

## Build & run

- **Mvumo:** `services/mvumo-service` — `mvn -pl mvumo-service package` (from `services/` reactor).
- **BFF:** `services/experience-bff` — requires `MVUMO_BASE_URL` when running patient summary with non-empty consent data.
- **Docker:** `ops/runtime/docker-compose.operations.yml` sets `MVUMO_BASE_URL` for the BFF (e.g. host gateway when Mvumo runs on the host).
- **Helm:** `services/experience-bff/helm/experience-bff/values.yaml` includes `MVUMO_BASE_URL`.

## Deeper architecture

- [`mvumo-consent-architecture.md`](./mvumo-consent-architecture.md) — product vs Tshepo Consent, Kafka, Redis, Tshepo HTTP.
- [`../plan/SERVICE_CATALOG.md`](../plan/SERVICE_CATALOG.md) — Ring 0 catalog row for `mvumo-service`.
- [`../runtime/platform-startup-architecture.md`](../runtime/platform-startup-architecture.md) — layer model and BFF dependencies.
- OpenAPI stub: [`contracts/openapi/experience-bff.openapi.yaml`](../../contracts/openapi/experience-bff.openapi.yaml) — `/internal/v1/summary/patient/{patientId}`.
