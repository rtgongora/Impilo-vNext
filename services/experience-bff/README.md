# Experience BFF

Backend-for-frontend for the **Impilo web experience** (`one-ui-shell`, `ui/experience`). Port **8160**. Proxies to sovereign services with v1.1/1.2 trust header forwarding.

## Key environment (production-shaped)

| Variable | Role |
|----------|------|
| `PCT_BASE_URL` | Longitudinal / patient health summary |
| `VITO_BASE_URL` | Client registry, CPID resolution |
| `MVUMO_BASE_URL` | **Mvumo** — consent orchestration, `consent-summary` for chart aggregation |
| `BUTANO_BASE_URL` | IPS / visit summary proxy |
| `KEYCLOAK_URL` / JWT | Resource server |

Full table: `src/main/resources/application.yml` and `docs/architecture/experience-bff-downstream-route-map.md`.

## Patient chart aggregate

- `GET /internal/v1/summary/patient/{patientId}` — PCT + Mvumo `consentSummary` (`EhrPatientSummaryController`).
- ` /internal/v1/mvumo/**` — transparent proxy to Mvumo (`MvumoServiceProxyController`).

## Build

```bash
cd services
mvn -pl experience-bff -am package -DskipTests
```

## UI dev

`NEXT_PUBLIC_BFF_URL=http://localhost:8160` with Next rewrites in `ui/experience/next.config.mjs`.

See [`docs/architecture/patient-care-consent-surface.md`](../../docs/architecture/patient-care-consent-surface.md).
