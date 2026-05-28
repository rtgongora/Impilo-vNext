# Doctrine Alignment Checklist (Major Surfaces)

> Per `docs/templates/CORE_TRANSACTION_FEATURE_ALIGNMENT_CHECKLIST.md` and Health OS doctrine.

| Surface | Plane | Journey | Doctrine | Backend | Contract/API | Route | Web | Mobile | Trust | Offline | Maturity | Gap |
|---------|-------|---------|----------|---------|--------------|-------|-----|--------|-------|---------|----------|-----|
| Core transaction | Experience | Cross-cutting | CORE_TRANSACTION_DOCTRINE | experience-bff | core-transaction.ts | `/core-transaction` | Partial | Partial | Yes | Partial | Partial | Mobile depth |
| Provider workspace | Experience | Provider | PROVIDER_JOURNEY | experience-bff | experience-bff.openapi | `/provider-workspace` | Partial | Partial | Yes | Partial | Partial | — |
| Person home | Experience | Person | PERSON_JOURNEY | experience-bff | mobile-citizen | `/home` | Live | Live | Yes | Partial | Live | — |
| Registry hub | Registry | Platform | REGISTRY_SPINE | vito/varapi/tuso | vito/varapi/tuso openapi | `/registry` | Partial | Partial | Yes | No | Partial | TUSO admin depth |
| Public health | Data/Intel | Platform | — | surveillance/campaigns | surveillance.openapi | `/public-health` | Partial | Partial | Yes | Partial | Partial | Map layers |
| Trust admin | Trust | Platform | TRUST_LAYER | tshepo | tshepo-authz | `/admin/trust` | Partial | Partial | Yes | No | Partial | — |
| SHR / EHR | Clinical | Provider | DATA_OWNERSHIP | butano | butano.custom | `/ehr/*` | Live | Partial | Yes | Partial | Live/Partial | Mobile SHR |
| Nompilo Ask | Experience | Cross-cutting | NOMPILO_COMPANION | guidance/llm | experience-bff | `/ask` | Partial | Partial | Yes | No | Partial | Context grounding improved |
| Marketplace | Enterprise | Person/Provider | — | msika-apps | msika-flow | `/marketplace` | Partial | Partial | Yes | No | Partial | Order lists 501 |
| Workflow ops | Platform | Platform | EVENT_WORKFLOW | workflow-service | workflow.openapi | `/operations/workflows` | Partial | Partial | Yes | Partial | Partial | Detail pages |
| UBOMI | Registry | Platform | — | ubomi-service | ubomi.openapi | `/ubomi` | Not wired | Not wired | Yes | No | Not wired | BFF bridge |

**Legend:** Maturity uses canonical five labels from `MATURITY_TAXONOMY.md`.
