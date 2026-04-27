# Mvumo Service

**Mvumo** — sovereign **Ring 0** national digital consent orchestration (same service class as Tshepo, VITO, etc.): templates, consent requests, remote sessions, adaptive assurance, proof artefacts, Kafka outbox.

## API

- Internal: `/internal/v1/mvumo/**` (see `MvumoInternalController`)
- **Consent summary** (EHR chart): `GET /internal/v1/mvumo/consent-summary?patientRef=Patient/{cpid}`

## Configuration

- `impilo.services` style: see `application.yml` — `mvumo.tshepo-consent.base-url`, Redis, Kafka, DB.
- Default port: **8195**

## Documentation

- Architecture: [`docs/architecture/mvumo-consent-architecture.md`](../../docs/architecture/mvumo-consent-architecture.md)
- Chart integration (BFF + UI): [`docs/architecture/patient-care-consent-surface.md`](../../docs/architecture/patient-care-consent-surface.md)

## Build

```bash
cd services
mvn -pl mvumo-service -am package -DskipTests
```
