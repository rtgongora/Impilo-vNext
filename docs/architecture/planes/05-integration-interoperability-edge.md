# Integration, Interoperability & Edge Plane

Integration hub, adapters, offline sync, jobs, notifications and channel/edge workflows.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `analytics-pipeline-service` | `analytics-pipeline-service` | platform-ops | Analytics Pipeline canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `asset-registry-service` | `asset-registry-service` | platform-ops | Asset Registry canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `audit-ledger-service` | `audit-ledger-service` | platform-ops | Audit Ledger canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `card-print-agent` | `card-print-agent` | interoperability | Card Print Agent canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `channels-service` | `channels-service` | interoperability | Channels canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `connector-fhir-adapter` | `connector-fhir-adapter` | interoperability | Connector Fhir Adapter canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `developer-portal-service` | `developer-portal-service` | platform-ops | Developer Portal canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `dispatch-service` | `dispatch-service` | platform-ops | Dispatch canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `integration-hub` | `integration-hub` | interoperability | Integration Hub canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `iot-ingestion-service` | `iot-ingestion-service` | platform-ops | Iot Ingestion canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `jobs-service` | `jobs-service` | interoperability | Jobs canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `landela-adapter-service` | `landela-adapter-service` | interoperability | Landela Adapter canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `llm-orchestration-service` | `llm-orchestration-service` | platform-ops | Llm Orchestration canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `ndila-service` | `ndila-service` | interoperability | canonical geospatial location registry, routing, ETA, and distance matrix orchestration, geofencing and catchment boundary operations, tracking asset telemetry normalization, spatial search and geospatial intelligence context | must-not-authorize-access-decisions, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-store-clinical-source-of-truth-outside-governed-clinical-shr-boundaries |
| `nhume-service` | `nhume-service` | interoperability | dispatch request and assignment lifecycle, courier and fleet operational registry, last-mile tracking and proof-of-delivery telemetry, delivery chain-of-custody and exception workflow | must-not-own-clinical-record-source-of-truth, must-not-own-patient-identity-source-of-truth, must-not-own-provider-identity-source-of-truth, must-not-own-consent-policy-authority, must-not-own-payment-ledgers |
| `notification-service` | `notification-service` | interoperability | Notification canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `observability-service` | `observability-service` | platform-ops | Observability canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `offline-edge-service` | `offline-edge-service` | platform-ops | Offline Edge canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `offline-sync-service` | `offline-sync-service` | interoperability | Offline Sync canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `referral-service` | `referral-service` | platform-ops | Referral canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `schema-registry-service` | `schema-registry-service` | platform-ops | Schema Registry canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `security-hardening-service` | `security-hardening-service` | platform-ops | Security Hardening canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `support-service` | `support-service` | platform-ops | Support canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |
| `workflow-service` | `workflow-service` | interoperability | Workflow canonical records | must-not-become-system-of-record-for-clinical-or-finance, must-not-embed-actor-facing-business-workflows |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.