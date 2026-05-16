# Service Registry (Production Baseline)

## Canonical Service Architecture Register

The canonical architecture source of truth now lives under `docs/architecture/`:

- `docs/architecture/SERVICE_ARCHITECTURE_REGISTER.md`
- `docs/architecture/services-registry.yaml`
- `docs/architecture/service-update-policy.md`
- `docs/architecture/ring-plane-taxonomy.md`
- `docs/architecture/service-boundary-violations.md`

This `docs/registry/` area remains useful for generated support artifacts and historical baselines.

| File | Role |
|------|------|
| [`services-registry.yaml`](./services-registry.yaml) | **Generated baseline artifact** — produced from registry scripts; canonical governance gate and architecture source of truth are enforced from `docs/architecture/services-registry.yaml` and validator scripts in `scripts/architecture/`. |
| [`services-index.md`](./services-index.md) | **Generated** full service index (do not edit by hand). |
| [`service-plane-map.md`](./service-plane-map.md) | Generated one-primary-plane map. |
| [`service-ownership-matrix.md`](./service-ownership-matrix.md) | Generated ownership and SoR matrix. |
| [`service-readiness-register.md`](./service-readiness-register.md) | Generated production readiness register. |
| [`system-of-record-map.md`](./system-of-record-map.md) | Generated SoR responsibilities map. |
| [`forbidden-responsibilities-map.md`](./forbidden-responsibilities-map.md) | Generated anti-responsibility map. |
| [`cross-plane-contract-map.md`](./cross-plane-contract-map.md) | Generated upstream/downstream contract map. |

## Regenerate registry artifacts

```bash
cd scripts/registry
npm install
node seed-registry.mjs
node generate-service-index.mjs
node generate-architecture-registers.mjs
```

`scripts/registry/seed-registry.mjs` builds `services-registry.yaml` from Maven reactor + curated legacy metadata and applies canonical field mapping.

## Related

- Ports: [`docs/runbooks/port-allocation.md`](../runbooks/port-allocation.md)
- Roadmap: [`docs/roadmaps/agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md)
- Completeness (Phase A2): `cd scripts/completeness && npm install && npm run report` → [`docs/reports/`](../reports/) (`completeness-report.json` / `.md`)
- Experience BFF (**Phase C complete**): downstream [`experience-bff-downstream-route-map.md`](../architecture/experience-bff-downstream-route-map.md); domain mapping [`experience-bff-phase-c-domain-mapping.md`](../architecture/experience-bff-phase-c-domain-mapping.md); `/internal/v1` index [`experience-bff-internal-routes.md`](../architecture/experience-bff-internal-routes.md) (`cd scripts/bff-routes && node list-bff-internal-routes.mjs`). PCT + Mvumo chart aggregation: [`patient-care-consent-surface.md`](../architecture/patient-care-consent-surface.md). **Phase D** (Experience UI) and **Phase E** (Kafka catalog + AsyncAPI anchors) baselines are **complete** — see [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md); events: [`kafka-event-catalog.md`](../architecture/kafka-event-catalog.md), [`contracts/asyncapi/README.md`](../../contracts/asyncapi/README.md).
- Maven modules: `services/pom.xml` `<modules>` — registry should stay aligned (Phase A2 may add drift checks).
