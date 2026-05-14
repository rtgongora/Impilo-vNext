# Trust API Contract Convergence Matrix

Date: 2026-05-14

| Service | OpenAPI present | Canonical headers aligned | Error envelope aligned | Decision envelope aligned | Legacy routes present | Compatibility removal gate | Remaining blocker |
|---|---|---|---|---|---|---|---|
| `mvumo-service` | yes (`mvumo.openapi.yaml`) | partial (supports canonical set; still accepts `X-Actor-Ref` alias) | partial (`ApiResponse` shape differs from TSHEPO authz envelope) | partial (decision metadata present but not fully uniform) | no | remove `X-Actor-Ref` alias after caller migration and telemetry shows zero legacy header usage | full envelope harmonisation with TSHEPO contract set |
| `tshepo-authz-service` | yes (`tshepo-authz.openapi.yaml`) | substantial | substantial | substantial | yes (`/v1/*-policy/evaluate` compatibility proxy routes) | remove compatibility routes after zero-usage window + retirement checklist completion | proxy still needed while legacy consumers finish migration |
| `tshepo-consent-service` | yes (`tshepo-consent.openapi.yaml`) | substantial | partial (response envelope differs from authz and mvumo) | substantial (`permitted`, reason, scope, consent evidence) | no | align error schema with canonical trust error envelope | envelope shape convergence not complete |
| `tshepo-identity-service` | yes (`tshepo-identity.openapi.yaml`) | partial | partial | not-applicable/partial | mixed route conventions | canonicalise route prefixes and error envelope | route/versioning convergence remains partial |
| `tshepo-audit-service` | yes (`tshepo-audit.openapi.yaml`) | partial | partial | not-applicable | mixed route conventions | canonicalise admin/internal and query route conventions | route prefix + envelope consistency incomplete |
| `tshepo-keys-service` | yes (`tshepo-keys.openapi.yaml`) | partial | partial | not-applicable/partial | mixed route conventions | canonicalise route prefixes and signing/verification error envelopes | contract parity work pending |
| `tshepo-offline-service` | yes (`tshepo-offline.openapi.yaml`) | partial | partial | partial | mixed route conventions | canonicalise offline trust API route prefixes and failure semantics | route and envelope convergence still partial |
| `identity-assurance-service` | yes (`identity-assurance.openapi.yaml`) | partial | partial | partial | no | align headers and error/decision schema to canonical trust contract | break-glass/attestation response parity incomplete |
| `tshepo-service` (legacy) | yes (`tshepo.openapi.yaml`) | compatibility-only | compatibility-only | compatibility-only | yes (legacy surface by definition) | zero-usage window + consumer retirement checklist completion | constrained legacy monolith not yet retired |

## Notes

- Compatibility routes are permitted only when explicitly inventoried and coupled to retirement gates.
- Canonical target remains: one trust header vocabulary, deterministic error envelope, and bounded compatibility windows.
