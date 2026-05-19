# Service Wiring Matrix

Legend: `Complete`, `Partial`, `Stub/mock`, `Broken`, `Missing`, `N/A`

| Service / Feature | Backend | Persistence | Migrations | API | Gateway/BFF | Trust headers/context | Web UI | Mobile UI | Tests/smoke | Observability | Gap |
|---|---|---|---|---|---|---|---|---|---|---|---|
| Tshepo trust cluster | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Cross-service enforcement consistency |
| Vito client registry | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | End-to-end UX parity |
| Varapi provider registry | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Deeper provider workflows |
| Tuso facility registry | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Facility context propagation audit |
| Butano SHR | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Additional clinical journey surfacing |
| Ubomi CRVS | Complete | Complete | Complete | Complete | Partial | Partial | Partial | N/A | Partial | Partial | UI discoverability |
| Zibo terminology | Complete | Complete | Complete | Complete | Partial | Partial | Complete | N/A | Partial | Partial | Workflow-level integration depth |
| Msika registry/core | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Partial | Partial | Partial | Mobile parity breadth |
| Msika Flow orchestration | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Partial | Partial | Partial | Deep operator telemetry |
| MusheX | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Reconciliation UX hardening |
| Costa | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Costing journey completeness |
| Fundo/Learning | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Mobile learner parity |
| Nompilo/LLM orchestration | Complete | Partial | Partial | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Provider abstraction hardening |
| Comms Hub/channels | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Delivery status UX surfaces |
| Telehealth (pct + bff flows) | Complete | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Session status and failover UX |
| Public health intelligence | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | End-user role scoping polish |
| Ndila geospatial | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Operational map dashboards |
| Nhume dispatch/delivery | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Full dispatch lifecycle UI |
| Integration Hub/service | Complete | Complete | Complete | Complete | Partial | Partial | Partial | Partial | Partial | Partial | Adapter registry UX and control plane |
| Community/social timeline | Complete | Complete | Complete | Complete | Complete | Partial | Complete | Complete | Partial | Partial | Moderation governance depth |
| Experience BFF | Complete | N/A | N/A | Complete | N/A | Partial | N/A | N/A | Partial | Partial | Header doctrine normalization |
| Provider app | N/A | N/A | N/A | Complete | Complete | Partial | N/A | Complete | Complete | Partial | Runtime telemetry |
| Citizen app | N/A | N/A | N/A | Complete | Complete | Partial | N/A | Complete | Complete | Partial | Marketplace/ops journey depth |
