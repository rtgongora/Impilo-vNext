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
- Experience BFF downstream map (Phase C seed): [`docs/architecture/experience-bff-downstream-route-map.md`](../architecture/experience-bff-downstream-route-map.md)
- Maven modules: `services/pom.xml` `<modules>` — registry should stay aligned (Phase A2 may add drift checks).
