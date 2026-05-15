# Registry API Contract Convergence Matrix

Date: 2026-05-14

| Service | OpenAPI present | Canonical trust headers aligned | Error envelope aligned | Route convention aligned | Legacy/compat routes present | Convergence gate | Remaining blocker |
|---|---|---|---|---|---|---|---|
| `vito-service` | yes | partial | partial | partial | yes (mixed `/v1` + `/internal/v1`) | keep compatibility while converging to canonical internal contract | harmonize error envelope and explicit trust-header requirements across identity + client-registry APIs |
| `varapi-service` | yes | partial | partial | partial | yes | same as above | align provider mutation error envelope + header declaration consistency |
| `tuso-service` | yes | substantial | partial | partial | yes | fail-close BFF integration now enforced; continue contract normalization | standardize locality/facility error envelopes and audit reference fields |
| `zibo-service` | yes | partial | partial | partial | no explicit legacy proxy | route and envelope harmonization gate | standardize terminology update/read response envelope semantics |
| `ubomi-service` | yes | partial | partial | partial | no explicit legacy proxy | CRVS dependency contract hardening gate | converge external-failure envelope semantics with registry canonical policy |
| `msika-service` | yes | partial | partial | partial | no explicit legacy proxy | catalog authority split gate with product-registry | align shared catalog error and metadata envelope conventions |
| `product-registry-service` | yes | substantial | partial | partial | no | ownership split + envelope convergence gate | complete envelope and route policy convergence with `msika-service` |
| `experience-bff` (registry routes) | n/a (BFF) | substantial on registry routes | substantial for hardened fail-close routes | substantial for `/internal/v1/registry*` + `/internal/v1/facilities` | no | route-level controller tests in place | extend route-level conformance tests for all registry admin mutation paths |

## Notes

- Contract convergence is now tracked as executable gates rather than vague partial status.
- New fail-close behavior for registry BFF routes removes synthetic-success dependency masking in live mode.
- Plane verdict remains dependent on first green runtime CI execution and deeper mutation-to-audit runtime evidence.
