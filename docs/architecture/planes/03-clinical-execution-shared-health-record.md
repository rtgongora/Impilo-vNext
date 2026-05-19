# Clinical Execution & Shared Health Record Plane

BUTANO/FHIR, patient care workflows, encounters, orders, results, pharmacy and inpatient capabilities.

## Service Ownership

| Service ID | Maven module | Domain | System of record | Forbidden responsibilities |
|---|---|---|---|---|
| `butano-fhir` | `butano-fhir` | care-delivery | Butano Fhir canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `butano-service` | `butano-service` | care-delivery | Butano canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `clinical-knowledge-platform-service` | `clinical-knowledge-platform-service` | clinical-knowledge | Clinical Knowledge Platform canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `document-service` | `document-service` | care-delivery | Document canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `fhir-gateway-service` | `fhir-gateway-service` | care-delivery | Fhir Gateway canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `forms-service` | `forms-service` | clinical-knowledge | Forms canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `guidance-service` | `guidance-service` | clinical-knowledge | Guidance canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `inpatient-service` | `inpatient-service` | care-delivery | Inpatient canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `inventory-elmis-adapter` | `inventory-elmis-adapter` | care-delivery | Inventory Elmis Adapter canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `inventory-service` | `inventory-service` | care-delivery | Inventory canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `oros-service` | `oros-service` | care-delivery | Oros canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pacs-adapter-service` | `pacs-adapter-service` | care-delivery | Pacs Adapter canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pct-service` | `pct-service` | care-delivery | Pct canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pharmacy-elmis-adapter` | `pharmacy-elmis-adapter` | care-delivery | Pharmacy Elmis Adapter canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `pharmacy-service` | `pharmacy-service` | care-delivery | Pharmacy canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `rules-service` | `rules-service` | clinical-knowledge | Rules canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |
| `scheduling-service` | `scheduling-service` | care-delivery | Scheduling canonical records | must-not-act-as-identity-source-of-record, must-not-own-enterprise-ledgering |

## Operating Guardrails

- Authz, audit and observability controls are mandatory on production routes.
- No new mock or stub path is allowed in production execution routes.
- Contract and integration changes must be reflected in registry and cross-plane maps.