# Registry Plane Dependency Map

Date: 2026-05-14

| Service | Primary DB | Kafka/Events | Key dependencies | Trust dependencies | BFF/UI dependency |
|---|---|---|---|---|---|
| `vito-service` | Postgres `vito` | outbox (`vito.event_outbox`) + Kafka producer | `varapi-service`, `pct-service`, optional OpenCR | TSHEPO authz policy endpoint (`TSHEPO_AUTHZ_BASE_URL`) + Trust headers | Experience + mobile profile/identity lookup flows |
| `varapi-service` | Postgres `varapi` | outbox (`varapi.event_outbox`) + Kafka producer/consumer | `zibo-service`, `tuso-service`, optional MusheX + learning sync | TSHEPO authz policy endpoint (`TSHEPO_AUTHZ_BASE_URL`) + Trust headers | Experience/provider workflows and admin surfaces |
| `tuso-service` | Postgres `tuso` | outbox (`tuso.event_outbox`) + Kafka producer | optional GOFR, `zibo-service`, `varapi-service` | JWT/Trust context on internal facility/workspace operations | Experience facility lookup and workspace routing |
| `zibo-service` | Postgres `zibo` | Kafka producer/consumer + terminology jobs | Terminology validation/assignment clients | JWT/Trust context for mutation and governance routes | Used by registry and clinical consumers (indirect UI) |
| `ubomi-service` | Postgres `ubomi` | Kafka producer | external CRVS boundary adapters | JWT/Trust context; no anonymous production path | Primarily backend integration, limited direct UI |
| `indawo-service` | Postgres `impilo_indawo` | outbox + Kafka producer | site registry + address snapshot dependencies | JWT + role enforcement; optional anonymous only via explicit flag | Experience location/site reference surfaces |
| `msika-service` | Postgres `msika` | outbox polling + Kafka producer/consumer | catalog import/mapping validators | JWT + TrustContextFilter | Enterprise/marketplace lookup and pack APIs |
| `product-registry-service` | Postgres `product_registry` | Kafka producer/consumer | registry search/snapshot/export clients | JWT + TrustContextFilter (hardened in this pass) | BFF internal product registry routes |

## Operational Dependency Notes

- Core registry services are stateful and DB-backed; readiness requires DB migrations + health probes.
- Trust context propagation is mandatory for governed identity/provider/facility/catalog mutations.
- TSHEPO policy endpoints are consumed from `vito-service` and `varapi-service` (and registry-adjacent `msika-flow-service`).
- Registry runtime evidence remains partially blocked on integrated multi-service CI E2E (not yet a single registry-plane orchestrated runtime gate).
