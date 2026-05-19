# Known Limitations

## Build and Tooling

- Web builds emit Tailwind content-pattern warnings in multiple apps due broad `shared-ui` globs.
- Some workspaces report `@next/swc` version mismatch warnings while still building successfully.
- Mobile Vitest outputs deprecation notices for CJS Vite API usage.

## Runtime and Integration

- Full multi-service runtime validation still depends on external infra (databases, Kafka, Redis, Keycloak, etc).
- Not all domain workflows were executed end-to-end in this sweep; focus was build/runtime blocker removal.
- Trust header extension constants were normalized in BFF using literal header names where upstream constant library lagged.

## Doctrine and UX

- Platform doctrine coverage is broadly present but remains partial in consistency and cross-module UX coherence.
- Several modules are technically wired but need deeper operator-friendly observability surfaces.
- Mobile parity improved, but some provider/citizen flows remain less deep than web equivalents.

## Testing

- Backend pass here uses `-DskipTests` package builds; full integration/e2e test matrices remain to be run in a provisioned environment.
- Frontend/mobile test suites pass for executed workspaces, but not all cross-product journeys are covered by end-to-end tests.
