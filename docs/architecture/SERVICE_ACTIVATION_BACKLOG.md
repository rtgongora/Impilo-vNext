# Service Activation Backlog

Open safe-remediation backlog for unresolved activation and contract alignment gaps.

| Priority | Service | Primary Plane | Gap | Remediation Status |
| --- | --- | --- | --- | --- |
| P1 | Analytics Pipeline Service | Integration & Operations | Concrete runtime-backed contract verification required before promotion to Aligned | Fixed |
| P1 | Referral Service | Clinical Execution | Concrete runtime-backed contract verification required before promotion to Aligned | Fixed |
| P1 | Orthanc PACS (k8s posture) | Integration & Operations | Add in-repo Kubernetes workload/service or explicitly switch to managed external endpoint to remove config drift | Partially Fixed |
| P1 | BUTANO imaging report linkage | Clinical Execution | Wire safe DiagnosticReport linkage for imaging lifecycle (ServiceRequest-based references) | Partially Fixed |
| P1 | PACS backend neutral external connector | Integration & Operations | Implement concrete external PACS/VNA connector behind provider abstraction (currently contract + stub only) | Partially Fixed |
| P1 | Viewer engine production integration | Experience | Keep viewer shell neutral but complete OHIF/Cornerstone production embed behind viewer policy | Partially Fixed |
| P1 | TSHEPO imaging break-glass policy closure | Trust & Governance | Confirm operational policy for denied/override flows and enforce in runbooks and dashboards | Needs Owner Decision |
| P1 | Teleconsult writeback closure | Clinical Execution | Wire teleconsult summary/report artifacts to BUTANO FHIR resources with governed linkage | Fixed |
| P1 | Telemedicine notifications closure | Integration & Operations | Wire session/reminder/report lifecycle notifications through notification-service with failure visibility | Fixed |
| P1 | Telemedicine ops SLA dashboard | Experience | Surface overdue consults, failed sessions, failed notifications, and specialist backlog in ops/admin UI | Fixed |
| P1 | Specialty tele-workbench expansion | Clinical Execution | Extend specialist pathway worklists for telepathology/teleoncology/teleophthalmology/teledermatology/telecardiology | Fixed |
| P1 | TSHEPO telemedicine break-glass policy closure | Trust & Governance | Finalize override/denial operational semantics and enforce audit/runbook controls | Fixed |
| P1 | Telemedicine external session-provider adapters | Integration & Operations | Add concrete external media provider adapters behind PCT session-provider abstraction without breaking neutral core | Partially Fixed |
| P1 | Document-service external storage provider adapters | Integration & Operations | Add Landela/external DMS adapters behind document-service provider contract (currently MinIO adapter only) | Partially Fixed |
| P1 | Document OCR and e-signature provider abstractions | Clinical Execution | Add explicit OCR and e-signature provider-neutral contracts with governed workflows | Partially Fixed |
| P1 | Simba OpenAPI parity | Experience | Expand `simba.openapi.yaml` to cover full implemented runtime endpoints and boundary-safe assistant APIs | Fixed |
| P1 | Wellness frontend runtime parity | Experience | Replace remaining demo wellness pages (diet/goals/clubs/programmes/routes variants) with real wellness-service APIs | Fixed |
| P1 | Wellness consent-policy deep integration | Trust & Governance | Use explicit source permissions and share scopes as enforceable policy surface while TSHEPO policy adapters are integrated incrementally | Fixed |
| P1 | Wellness clinical writeback governance | Clinical Execution | Keep personal wellness data non-clinical by default and require explicit provider acceptance workflow before any BUTANO promotion | Fixed |

Event-contract parity backlog items were closed in the final convergence run.
Execution and rollback traceability remains in:
`docs/architecture/EVENT_CONTRACT_PARITY_CONVERGENCE_PLAN.md`.
