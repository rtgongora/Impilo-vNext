# Cross-Plane Data Flow Map

| Flow | Source | Target | Event/API/Dataset | Governance requirement | PII/PHI handling | Status | Blocker |
|---|---|---|---|---|---|---|---|
| Clinical -> Data (encounters) | PCT/BUTANO/OROS/pharmacy/inpatient | ingestion/pipeline/NDR/warehouse | encounter/lab/medication events | TSHEPO + DAGS + purpose-of-use | PHI minimized for analytics | partial | end-to-end contract depth |
| Registry -> Data | VITO/VARAPI/TUSO/ZIBO | pipelines/datasets | reference extracts and joins | TSHEPO + DAGS | low-to-moderate sensitive refs | partial | extract standardization |
| Enterprise -> Data | COSTA/GL/MUSheX/procurement/HR | ingestion/pipeline/warehouse/reporting | billing/payment/claims/procurement/payroll aggregates | TSHEPO + DAGS | financial sensitivity controls | partial | producer contract parity |
| Integration -> Data | integration-hub/connectors/FHIR/IoT | ingestion | feed adapters/stream payloads | TSHEPO + DAGS | feed-specific policy | partial | adapter coverage |
| Data -> Experience | reporting/surveillance/campaigns/AI registry | `experience-bff` and UIs | dashboards/reports/worklists/model views | TSHEPO headers + BFF fail-close | only governed payloads | partial | bounded routes still mapped to surveillance primitives |
| Data -> Public Health Ops | surveillance/campaigns/governance outputs | public-health operations screens | alerts/worklists/program indicators | TSHEPO + DAGS + audit | aggregate-first, no unauthorized identifiers | partial | dedicated weekly/outbreak/field domain APIs still pending |

## Flow Controls

- Data Plane consumes but does not re-own source-of-truth data from Clinical, Registry, Enterprise.
- Access to sensitive datasets requires purpose-of-use and auditable decisioning.
- Experience surfaces must show explicit unavailable states when backend capability is not wired.
