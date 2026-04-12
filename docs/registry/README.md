# Service registry (Phase A1)

| File | Role |
|------|------|
| [`services-registry.yaml`](./services-registry.yaml) | **Source of truth** — Maven modules, planes, sovereign membership, default HTTP ports, product names. |
| [`services-index.md`](./services-index.md) | **Generated** table for quick reading (do not edit by hand). |

## Regenerate the index

```bash
cd scripts/registry
npm install
npm run generate
```

## Refresh YAML from seed data (optional)

`scripts/registry/seed-registry.mjs` rebuilds `services-registry.yaml` from embedded structured data (useful after bulk renames). **It overwrites the YAML file** — commit or diff first.

```bash
cd scripts/registry
node seed-registry.mjs
npm run generate
```

## Related

- Ports: [`docs/runbooks/port-allocation.md`](../runbooks/port-allocation.md)
- Roadmap: [`docs/roadmaps/agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md)
- Completeness (Phase A2): `cd scripts/completeness && npm install && npm run report` → [`docs/reports/`](../reports/) (`completeness-report.json` / `.md`)
- Experience BFF (**Phase C complete**): downstream [`experience-bff-downstream-route-map.md`](../architecture/experience-bff-downstream-route-map.md); domain mapping [`experience-bff-phase-c-domain-mapping.md`](../architecture/experience-bff-phase-c-domain-mapping.md); `/internal/v1` index [`experience-bff-internal-routes.md`](../architecture/experience-bff-internal-routes.md) (`cd scripts/bff-routes && node list-bff-internal-routes.mjs`). **Phase D** (Experience UI) baseline for the agent-led roadmap is **complete** — see [`agent-led-fullstack-completeness-roadmap.md`](../roadmaps/agent-led-fullstack-completeness-roadmap.md); **Phase E** (events) is next.
- Maven modules: `services/pom.xml` `<modules>` — registry should stay aligned (Phase A2 may add drift checks).
