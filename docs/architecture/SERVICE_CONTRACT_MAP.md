# Service Contract Map

Contract alignment view for backend and integration runtimes.

## Contract Alignment Summary

| Contract Alignment Status | Count |
| --- | ---: |
| Aligned | 80 |
| Not Applicable | 75 |
| Partial | 3 |

## Backend Contract Matrix

| Service | Contract Alignment Status | API Surface | Contract Evidence | Remediation Status |
| --- | --- | --- | --- | --- |
| Ai Model Registry | Aligned | contracts/openapi/ai-model-registry.openapi.yaml | contracts/openapi/ai-model-registry.openapi.yaml | Fixed |
| Analytics Pipeline Service | Partial | contracts/openapi/analytics-pipeline.openapi.yaml | contracts/openapi/analytics-pipeline.openapi.yaml | Partially Fixed |
| Asset Registry | Aligned | contracts/openapi/asset-registry.openapi.yaml | contracts/openapi/asset-registry.openapi.yaml | Fixed |
| Audit Ledger | Aligned | contracts/openapi/audit-ledger.openapi.yaml | contracts/openapi/audit-ledger.openapi.yaml | Fixed |
| Butano Fhir | Aligned | contracts/openapi/butano-fhir.openapi.yaml | contracts/openapi/butano-fhir.openapi.yaml | Fixed |
| Butano | Aligned | contracts/openapi/butano.custom.openapi.yaml | contracts/openapi/butano.custom.openapi.yaml | Fixed |
| Campaigns | Aligned | contracts/openapi/campaigns.openapi.yaml | contracts/openapi/campaigns.openapi.yaml | Fixed |
| Card Print Agent | Aligned | contracts/openapi/card-print.openapi.yaml | contracts/openapi/card-print.openapi.yaml | Fixed |
| Channels | Aligned | contracts/openapi/channels.openapi.yaml | contracts/openapi/channels.openapi.yaml | Fixed |
| Clinical Knowledge Platform | Aligned | contracts/openapi/clinical-knowledge-platform.openapi.yaml | contracts/openapi/clinical-knowledge-platform.openapi.yaml | Fixed |
| Community | Aligned | contracts/openapi/community.openapi.yaml | contracts/openapi/community.openapi.yaml | Fixed |
| Connector Fhir Adapter | Aligned | contracts/openapi/connector-fhir.openapi.yaml | contracts/openapi/connector-fhir.openapi.yaml | Fixed |
| Costing Engine | Aligned | contracts/openapi/costa.openapi.yaml | contracts/openapi/costa.openapi.yaml | Fixed |
| Coverage | Aligned | contracts/openapi/coverage.openapi.yaml | contracts/openapi/coverage.openapi.yaml | Fixed |
| Credential Verification | Aligned | contracts/openapi/credential-verification.openapi.yaml | contracts/openapi/credential-verification.openapi.yaml | Fixed |
| Data Access Governance | Aligned | contracts/openapi/data-access-governance.openapi.yaml | contracts/openapi/data-access-governance.openapi.yaml | Fixed |
| Data Governance | Aligned | contracts/openapi/data-governance.openapi.yaml | contracts/openapi/data-governance.openapi.yaml | Fixed |
| Data Ingestion | Aligned | contracts/openapi/data-ingestion.openapi.yaml | contracts/openapi/data-ingestion.openapi.yaml | Fixed |
| Data Pipeline | Aligned | contracts/openapi/data-pipeline.openapi.yaml | contracts/openapi/data-pipeline.openapi.yaml | Fixed |
| Data Warehouse | Aligned | contracts/openapi/data-warehouse.openapi.yaml | contracts/openapi/data-warehouse.openapi.yaml | Fixed |
| Developer Portal | Aligned | contracts/openapi/developer-portal.openapi.yaml | contracts/openapi/developer-portal.openapi.yaml | Fixed |
| Dispatch | Aligned | contracts/openapi/dispatch.openapi.yaml | contracts/openapi/dispatch.openapi.yaml | Fixed |
| Document | Aligned | contracts/openapi/document-store.openapi.yaml | contracts/openapi/document-store.openapi.yaml | Fixed |
| Experience Bff | Aligned | contracts/openapi/experience-bff.openapi.yaml | contracts/openapi/experience-bff.openapi.yaml | Fixed |
| Fhir Gateway | Aligned | contracts/openapi/fhir-gateway.openapi.yaml | contracts/openapi/fhir-gateway.openapi.yaml | Fixed |
| Forms | Aligned | contracts/openapi/forms.openapi.yaml | contracts/openapi/forms.openapi.yaml | Fixed |
| General Ledger | Aligned | contracts/openapi/general-ledger.openapi.yaml | contracts/openapi/general-ledger.openapi.yaml | Fixed |
| Guidance | Aligned | contracts/openapi/guidance.openapi.yaml | contracts/openapi/guidance.openapi.yaml | Fixed |
| Hr Payroll | Aligned | contracts/openapi/hr-payroll.openapi.yaml | contracts/openapi/hr-payroll.openapi.yaml | Fixed |
| Identity Assurance | Aligned | contracts/openapi/identity-assurance.openapi.yaml | contracts/openapi/identity-assurance.openapi.yaml | Fixed |
| Indawo | Aligned | contracts/openapi/indawo.openapi.yaml | contracts/openapi/indawo.openapi.yaml | Fixed |
| Inpatient | Aligned | contracts/openapi/inpatient.openapi.yaml | contracts/openapi/inpatient.openapi.yaml | Fixed |
| Integration Hub | Aligned | contracts/openapi/integration-hub.openapi.yaml | contracts/openapi/integration-hub.openapi.yaml | Fixed |
| Inventory Elmis Adapter | Aligned | contracts/openapi/inventory-elmis.openapi.yaml | contracts/openapi/inventory-elmis.openapi.yaml | Fixed |
| Inventory | Aligned | contracts/openapi/inventory.openapi.yaml | contracts/openapi/inventory.openapi.yaml | Fixed |
| Iot Ingestion | Aligned | contracts/openapi/iot-ingestion.openapi.yaml | contracts/openapi/iot-ingestion.openapi.yaml | Fixed |
| Jobs | Aligned | contracts/openapi/jobs.openapi.yaml | contracts/openapi/jobs.openapi.yaml | Fixed |
| Landela Adapter | Aligned | contracts/openapi/landela-adapter.openapi.yaml | contracts/openapi/landela-adapter.openapi.yaml | Fixed |
| Learning | Aligned | contracts/openapi/learning.openapi.yaml | contracts/openapi/learning.openapi.yaml | Fixed |
| Llm Orchestration | Not Applicable | Not Declared | services/llm-orchestration-service/pom.xml | Fixed |
| Msika Flow | Aligned | contracts/openapi/msika-flow.openapi.yaml | contracts/openapi/msika-flow.openapi.yaml | Fixed |
| Msika | Aligned | contracts/openapi/msika-core.openapi.yaml | contracts/openapi/msika-core.openapi.yaml | Fixed |
| Mushe Wallet | Aligned | contracts/openapi/mushe-wallet.openapi.yaml | contracts/openapi/mushe-wallet.openapi.yaml | Fixed |
| Mushex | Aligned | contracts/openapi/mushex.openapi.yaml | contracts/openapi/mushex.openapi.yaml | Fixed |
| Mvumo | Aligned | contracts/openapi/mvumo.openapi.yaml | contracts/openapi/mvumo.openapi.yaml | Fixed |
| National Data Repository | Aligned | contracts/openapi/national-data-repository.openapi.yaml | contracts/openapi/national-data-repository.openapi.yaml | Fixed |
| Ndr | Aligned | contracts/openapi/ndr.openapi.yaml | contracts/openapi/ndr.openapi.yaml | Fixed |
| Ndila | Aligned | contracts/openapi/ndila.openapi.yaml | contracts/openapi/ndila.openapi.yaml; contracts/asyncapi/ndila-events.asyncapi.yaml | Fixed |
| Nhume | Partial | services/nhume-service runtime APIs (OpenAPI pending) | docs/architecture/nhume-dispatch-and-delivery.md | Partially Fixed |
| Notification | Aligned | contracts/openapi/notification.openapi.yaml | contracts/openapi/notification.openapi.yaml | Fixed |
| Observability | Aligned | contracts/openapi/observability.openapi.yaml | contracts/openapi/observability.openapi.yaml | Fixed |
| Offline Edge | Aligned | contracts/openapi/offline-edge.openapi.yaml | contracts/openapi/offline-edge.openapi.yaml | Fixed |
| Offline Sync | Aligned | contracts/openapi/offline-sync.openapi.yaml | contracts/openapi/offline-sync.openapi.yaml | Fixed |
| Oros | Aligned | contracts/openapi/oros.openapi.yaml | contracts/openapi/oros.openapi.yaml | Fixed |
| Pacs Adapter | Aligned | contracts/openapi/pacs-adapter.openapi.yaml | contracts/openapi/pacs-adapter.openapi.yaml; contracts/openapi/imaging-viewer-launch.openapi.yaml; contracts/asyncapi/imaging-pipeline.asyncapi.yaml | Fixed |
| Pct | Aligned | contracts/openapi/pct.openapi.yaml | contracts/openapi/pct.openapi.yaml | Fixed |
| Pharmacy Elmis Adapter | Aligned | contracts/openapi/pharmacy-elmis.openapi.yaml | contracts/openapi/pharmacy-elmis.openapi.yaml | Fixed |
| Pharmacy | Aligned | contracts/openapi/pharmacy.openapi.yaml | contracts/openapi/pharmacy.openapi.yaml | Fixed |
| Procurement | Aligned | contracts/openapi/procurement.openapi.yaml | contracts/openapi/procurement.openapi.yaml | Fixed |
| Product Registry | Not Applicable | contracts/openapi/product-registry.openapi.yaml | contracts/openapi/product-registry.openapi.yaml | Fixed |
| Referral Service | Partial | contracts/openapi/referral.openapi.yaml | contracts/openapi/referral.openapi.yaml | Partially Fixed |
| Reporting | Aligned | contracts/openapi/reporting.openapi.yaml | contracts/openapi/reporting.openapi.yaml | Fixed |
| Rules | Aligned | contracts/openapi/rules.openapi.yaml | contracts/openapi/rules.openapi.yaml | Fixed |
| Scheduling | Aligned | contracts/openapi/scheduling.openapi.yaml | contracts/openapi/scheduling.openapi.yaml | Fixed |
| Schema Registry | Aligned | contracts/openapi/schema-registry.openapi.yaml | contracts/openapi/schema-registry.openapi.yaml | Fixed |
| Search | Aligned | contracts/openapi/search.openapi.yaml | contracts/openapi/search.openapi.yaml | Fixed |
| Security Hardening | Aligned | contracts/openapi/security-hardening.openapi.yaml | contracts/openapi/security-hardening.openapi.yaml | Fixed |
| Share Slip | Aligned | contracts/openapi/share-slip.openapi.yaml | contracts/openapi/share-slip.openapi.yaml | Fixed |
| Simba | Aligned | contracts/openapi/simba.openapi.yaml | contracts/openapi/simba.openapi.yaml | Fixed |
| Support | Aligned | contracts/openapi/support.openapi.yaml | contracts/openapi/support.openapi.yaml | Fixed |
| Surveillance | Aligned | contracts/openapi/surveillance.openapi.yaml | contracts/openapi/surveillance.openapi.yaml | Fixed |
| Tshepo Audit | Aligned | contracts/openapi/tshepo-audit.openapi.yaml | contracts/openapi/tshepo-audit.openapi.yaml | Fixed |
| Tshepo Authz | Aligned | contracts/openapi/tshepo-authz.openapi.yaml | contracts/openapi/tshepo-authz.openapi.yaml | Fixed |
| Tshepo Consent | Aligned | contracts/openapi/tshepo-consent.openapi.yaml | contracts/openapi/tshepo-consent.openapi.yaml | Fixed |
| Tshepo Identity | Aligned | contracts/openapi/tshepo-identity.openapi.yaml | contracts/openapi/tshepo-identity.openapi.yaml | Fixed |
| Tshepo Keys | Aligned | contracts/openapi/tshepo-keys.openapi.yaml | contracts/openapi/tshepo-keys.openapi.yaml | Fixed |
| Tshepo Offline | Aligned | contracts/openapi/tshepo-offline.openapi.yaml | contracts/openapi/tshepo-offline.openapi.yaml | Fixed |
| Tshepo | Aligned | contracts/openapi/tshepo.openapi.yaml | contracts/openapi/tshepo.openapi.yaml | Fixed |
| Tuso | Aligned | contracts/openapi/tuso.openapi.yaml | contracts/openapi/tuso.openapi.yaml | Fixed |
| Ubomi | Aligned | contracts/openapi/ubomi.openapi.yaml | contracts/openapi/ubomi.openapi.yaml | Fixed |
| Varapi | Aligned | contracts/openapi/varapi.openapi.yaml | contracts/openapi/varapi.openapi.yaml | Fixed |
| Vito | Aligned | contracts/openapi/vito.openapi.yaml | contracts/openapi/vito.openapi.yaml | Fixed |
| Wellness | Aligned | contracts/openapi/wellness.openapi.yaml | contracts/openapi/wellness.openapi.yaml | Fixed |
| Workflow | Aligned | contracts/openapi/workflow.openapi.yaml | contracts/openapi/workflow.openapi.yaml | Fixed |
| Workforce Governance | Aligned | contracts/openapi/workforce-governance.openapi.yaml | contracts/openapi/workforce-governance.openapi.yaml | Fixed |
| Zibo | Aligned | contracts/openapi/zibo.openapi.yaml | contracts/openapi/zibo.openapi.yaml | Fixed |

## Imaging Contract Additions (May 2026)

- `contracts/openapi/pacs-adapter.openapi.yaml` expanded to cover implemented PACS adapter API surface including ops endpoints.
- `contracts/openapi/pacs-adapter.openapi.yaml` now includes `viewer-launch-context` and provider-neutral backend status metadata fields.
- `contracts/openapi/imaging-viewer-launch.openapi.yaml` added for governed viewer launch context.
- `contracts/openapi/experience-bff.openapi.yaml` now includes governed imaging ops and viewer-launch-context proxy endpoints.
- `contracts/asyncapi/imaging-pipeline.asyncapi.yaml` added for imaging event rail documentation.

## Telemedicine Contract Additions (May 2026)

- `contracts/openapi/experience-bff.openapi.yaml` now includes explicit teleconsult and telemedicine route coverage slices.
- `contracts/openapi/mobile-provider.openapi.yaml` now includes provider telemedicine session list/create/join/end routes.
- `contracts/openapi/mobile-citizen.openapi.yaml` now includes citizen telehealth session list/get/create/join/end routes.
- Canonical capability and residual contract gaps are tracked in `docs/architecture/TELEMEDICINE_PIPELINE.md`.

## Telemedicine + Document Neutrality Contract Refinement (May 2026)

- `contracts/openapi/pct.openapi.yaml` now includes typed telehealth session create/response schemas with provider-neutral `sessionProvider`.
- `contracts/openapi/mobile-provider.openapi.yaml` and `contracts/openapi/mobile-citizen.openapi.yaml` now include typed provider-neutral session-provider fields.
- `contracts/openapi/document-store.openapi.yaml` now includes `/v1/internal/objects/{objectId}/preview` and explicit provider-neutral storage posture.
- Document-management contract closure status and remaining gaps are tracked in `docs/architecture/DOCUMENT_MANAGEMENT_PIPELINE.md`.

## Simba + Wellness Contract Refinement (May 2026)

- `contracts/openapi/wellness.openapi.yaml` now includes personal-health-data runtime APIs for:
  - source registration and source permission governance,
  - manual wellness readings,
  - citizen/provider wellness summaries,
  - remote monitoring alerts and provider review.
- `contracts/openapi/simba.openapi.yaml` remains a partial contract relative to Simba runtime surface and needs a dedicated parity pass.
