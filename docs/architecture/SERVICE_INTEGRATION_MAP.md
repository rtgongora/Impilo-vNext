# Service Integration Map

Canonical dependency and integration posture for activation lanes.

## Event Contract Parity Findings

| Parity Area | Listener Topic(s) | Producer Topic(s) | Current Status | Remediation Status |
| --- | --- | --- | --- | --- |
| Surveillance clinical encounter rails | `clinical.pct.encounter.completed`, `clinical.pct.death.recorded` | `clinical.pct.encounter.completed`, `clinical.pct.death.recorded` | Canonical rails active | Fixed |
| Data Pipeline clinical rails | `clinical.pct.journey.completed`, `clinical.oros.result.available` | `clinical.pct.journey.completed`, `clinical.oros.result.available` | Canonical rails active | Fixed |
| Data Pipeline kernel client rail | `kernel.vito.client.registered` | `kernel.vito.client.registered` (dual-emit bridge from VITO identity rail) | Canonical rail bridged and active | Fixed |
| Reporting aggregate rail | `analytics.reporting.aggregate` | `analytics.reporting.aggregate` (data-pipeline outbox producer) | Explicit producer contract and emit path present | Fixed |

Targeted low-risk parity fixes in this hardening wave were documentation and contract-evidence alignment updates only; no risky runtime topic renames were applied.

Runtime follow-on plan for controlled convergence:
`docs/architecture/EVENT_CONTRACT_PARITY_CONVERGENCE_PLAN.md`.

| Service | Consumes | Consumed By | Integration Status |
| --- | --- | --- | --- |
| Ai Model Registry | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Analytics Pipeline Service | data-ingestion-service, ndr-service | None | Integrated |
| Asset Registry | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Audit Ledger | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Butano Fhir | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Butano | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Butano Web | experience-bff | None | Integrated |
| Campaigns | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Card Print Agent | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Channels | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Citizen App | experience-bff | None | Integrated |
| Clinical Knowledge Platform | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Community | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience | Integrated |
| Connector Fhir Adapter | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Costa Console | experience-bff | None | Integrated |
| Costing Engine | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Coverage | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Credential Verification | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Data Access Governance | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Data Governance | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Data Ingestion | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Data Pipeline | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Data Warehouse | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Developer Console | experience-bff | None | Integrated |
| Developer Portal | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Dispatch | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Document | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Ehr | experience-bff | None | Integrated |
| Experience | experience-bff | None | Integrated |
| Experience Bff | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience | Integrated |
| Fhir Gateway | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Forms | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| General Ledger | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Guidance | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Hr Payroll | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Identity Assurance | None | experience-bff, integration-hub | Integrated |
| Indawo | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Inpatient | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Integration Hub | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Inventory Elmis Adapter | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Inventory | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Inventory Web | experience-bff | None | Integrated |
| Iot Ingestion | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Jobs | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Knowledge Admin | experience-bff | None | Integrated |
| Landela Adapter | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Learning | tshepo-authz-service, multiple-domain-services-via-bff | web-mobile-experience | Integrated |
| Msika Flow Ops | experience-bff | None | Integrated |
| Msika Flow Portal | experience-bff | None | Integrated |
| Msika Flow | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Msika Flow Vendor | experience-bff | None | Integrated |
| Msika | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Msika Web | experience-bff | None | Integrated |
| Mushe Wallet | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Mushex Finance Console | experience-bff | None | Integrated |
| Mushex Ops Console | experience-bff | None | Integrated |
| Mushex Payer Portal | experience-bff | None | Integrated |
| Mushex | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Mvumo | None | experience-bff, integration-hub | Integrated |
| National Data Repository | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Ndr | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Notification | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Observability | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Offline Edge | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Offline Sync | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| One Ui Shell | experience-bff | None | Integrated |
| Ops Console | experience-bff | None | Integrated |
| Ops Docs | experience-bff | None | Integrated |
| Oros | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Oros Web | experience-bff | None | Integrated |
| Pacs Adapter | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Pct | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Pct Web | experience-bff | None | Integrated |
| Pharmacy Elmis Adapter | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Pharmacy | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Pharmacy Web | experience-bff | None | Integrated |
| Portal | experience-bff | None | Integrated |
| Procurement | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Product Registry | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Provider App | experience-bff | None | Integrated |
| Referral Service | tshepo-authz-service, pct-service | None | Integrated |
| Reporting | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Rules | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Scheduling | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Schema Registry | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Search | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Security Hardening | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Self | experience-bff | None | Integrated |
| Share Slip | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Shared Ui | experience-bff | None | Integrated |
| Simba | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Support Console | experience-bff | None | Integrated |
| Support | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Surveillance | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Tshepo Audit | None | experience-bff, integration-hub | Integrated |
| Tshepo Authz | None | experience-bff, integration-hub | Integrated |
| Tshepo Consent | None | experience-bff, integration-hub | Integrated |
| Tshepo Identity | None | experience-bff, integration-hub | Integrated |
| Tshepo Keys | None | experience-bff, integration-hub | Integrated |
| Tshepo Offline | None | experience-bff, integration-hub | Integrated |
| Tshepo | None | experience-bff, integration-hub | Integrated |
| Tuso | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Ubomi | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Varapi | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Vito | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Wellness | simba-service | experience-bff, integration-hub | Integrated |
| Workflow | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Workforce Governance | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Zibo | tshepo-authz-service | experience-bff, integration-hub | Integrated |
| Zibo Web | experience-bff | None | Integrated |
