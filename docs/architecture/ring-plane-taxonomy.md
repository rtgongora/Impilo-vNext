# Ring-Plane Taxonomy

## Ring Definitions
| Machine Value | Display Label | Definition |
|---|---|---|
| `ring_0_kernel` | Ring 0 Kernel | Sovereign foundational services with strictest operational governance. |
| `ring_1_execution` | Ring 1 Execution | Direct care and operational execution services. |
| `ring_2_scale` | Ring 2 Scale | Integration, analytics, background, and operational support services. |
| `infra` | Infrastructure | Platform infrastructure components. |
| `ui` | User Interface | Web and mobile applications. |
| `library` | Library | Shared reusable packages and SDKs. |
| `external` | External | External systems and dependencies. |
| `unclear` | Unclear | Evidence is currently insufficient. |

## Plane Definitions
| Machine Value | Display Label | Definition |
|---|---|---|
| `trust_governance` | Trust & Governance | Identity, authorization, consent, audit, policy, and trust decisions. |
| `registry_spine` | Registry Spine | Authoritative master/reference and registry ownership. |
| `clinical_execution` | Clinical Execution | Clinical delivery and shared health record execution. |
| `finance_resource` | Finance & Resource | Costing, billing, payment, claims, settlement, and finance truth. |
| `integration_ops` | Integration & Operations | Adapters, orchestration, routing, data movement, jobs, and observability. |
| `experience` | Experience | User-facing interaction and workflow orchestration. |
| `enterprise_resource` | Enterprise Resource | Workforce, learning, and enterprise support operations. |
| `unclear` | Unclear | Evidence is currently insufficient. |

## Category Definitions
| Category Display | Typical Intent |
|---|---|
| Kernel | Sovereign kernel authority |
| Clinical | Care workflow authority |
| Data | Intelligence/public health data capability |
| Integration | Interoperability and operations capability |
| Supply | Supply and logistics capability |
| Experience | Journey and UX capability |
| Assurance | Trust and governance assurance capability |
| Resilience | Reliability and observability capability |
| Finance | Financial lifecycle capability |
| Enterprise | Enterprise administration capability |
| Registry | Registry and master data capability |
| Infrastructure | Runtime platform capability |
| User Interface | Frontend application capability |
| External | Out-of-platform dependency |
| Unclear | Evidence-insufficient category |

## Classification Examples
- TSHEPO services: Ring 0 Kernel + Trust & Governance.
- VITO, VARAPI, TUSO, ZIBO, MSIKA, INDAWO, UBOMI: Ring 0 Kernel + Registry Spine.
- BUTANO, PCT, OROS, Pharmacy: Clinical Execution primary.
- MusheX and COSTA: Finance & Resource primary.
- Integration Hub, Offline Sync, Jobs, Notification, adapters: Integration & Operations primary.
- UI and mobile apps: User Interface ring + Experience primary.
- Shared packages: Library ring with plane by dominant responsibility.

## Anti-Patterns
- Do not classify a service as Experience just because it has a UI.
- Do not classify a service as Clinical just because clinicians use it.
- Do not classify a service as Registry Spine unless it owns authoritative master/reference data.
- Do not classify a service as Trust & Governance unless it owns policy, identity, consent, audit, risk, or trust decisions.
- Do not classify a service as Finance & Resource merely because it displays charges.
- Do not classify a service based on folder placement alone.
- Do not allow two services to own the same truth without explicit authority and reconciliation rules.

## Cross-Cutting Rules
- Classify cross-cutting services by system-of-record ownership, then add secondary Planes for dependencies.
- Classify UI apps as Experience primary even when domain-specific.
- Classify adapters as Integration & Operations unless explicitly authoritative.
- Classify shared libraries as Library ring and prohibit runtime truth ownership.
- Mark evidence-insufficient services as Unclear and add unresolved questions.
