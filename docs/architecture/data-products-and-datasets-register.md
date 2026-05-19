# Data Products and Datasets Register

Rule: No dataset access is permitted without DAGS + TSHEPO governance.

| Dataset/Data Product | Owner service | Source services | PII/PHI class | De-id status | Access governance owner | Purpose-of-use | Refresh | Consumers | Retention | Audit required | Status | Blocker |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| Gold encounters | `data-warehouse-service` | PCT/BUTANO | PHI | pseudonymized refs partial | DAGS + TSHEPO | analytics/reporting | near-real-time batch | reporting, analytics | policy-driven | yes | implemented | none |
| Gold medications | `data-warehouse-service` | pharmacy/PCT | PHI | partial | DAGS + TSHEPO | prescribing analytics | batch | reporting | policy-driven | yes | implemented | de-id policy completeness |
| Gold labs | `data-warehouse-service` | OROS/BUTANO | PHI | partial | DAGS + TSHEPO | lab surveillance/reporting | batch | reporting, surveillance | policy-driven | yes | implemented | de-id policy completeness |
| Bronze NDR event store | `ndr-service` | clinical/registry/enterprise feeds | sensitive mixed | raw governed | DAGS + TSHEPO | lineage/replay | streaming | pipelines/governance | policy-driven | yes | implemented | NDR dual-service overlap |
| NDR gold encounter aggregate | `ndr-service` | bronze + governance rules | PHI (minimized) | partial | DAGS + TSHEPO | governed query/export | scheduled | authorized analytics | policy-driven | yes | implemented | NDR consolidation |
| National dataset catalog | `national-data-repository-service` | governance-defined | mixed | policy dependent | DAGS + TSHEPO | dataset lifecycle mgmt | on-change | reporting/data consumers | policy-driven | yes | partial | overlaps with `ndr-service` |
| Surveillance signal dataset | `surveillance-service` | surveillance ingest + rules | mostly aggregate | n/a | DAGS + TSHEPO | outbreak detection | near-real-time | public-health ops | policy-driven | yes | implemented | weekly aggregate gap |
| Surveillance case dataset | `surveillance-service` | case reporting flows | PHI-sensitive | partial | DAGS + TSHEPO | case management | near-real-time | public-health ops | policy-driven | yes | implemented | downstream workflow depth |
| Campaign execution dataset | `campaigns-service` | campaigns + enrollment feeds | mixed | partial | DAGS + TSHEPO | campaign monitoring | near-real-time | public-health dashboards | policy-driven | yes | implemented | outcome harmonization |
| Report run and export metadata | `reporting-service` | report definitions + data queries | low/metadata | n/a | DAGS + TSHEPO | operations/audit | near-real-time | experience/admin | policy-driven | yes | implemented | export policy parity |
| AI model registry metadata | `ai-model-registry-service` | AI lifecycle ops | non-PII metadata + links | n/a | DAGS + TSHEPO | governance/compliance | on-change | AI governance UI | policy-driven | yes | partial | service security hardening |
| Registry reference extracts | data plane pipelines | VITO/VARAPI/TUSO/ZIBO | low-sensitive | n/a | DAGS + TSHEPO | join/reference integrity | scheduled | all analytics products | policy-driven | yes | partial | extract standardization |
| Enterprise aggregate facts | data plane pipelines | COSTA/GL/MUSheX/etc | sensitive financial | aggregate | DAGS + TSHEPO | cost/resource analytics | scheduled | data/reporting | policy-driven | yes | partial | cross-plane contract completeness |
