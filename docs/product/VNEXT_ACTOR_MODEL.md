# vNext Actor Model

> Generated: 2026-06-08T14:34:53.641Z
> 24 actor types (human and non-human)

| actor | identity | auth | context | transactions | ui |
| --- | --- | --- | --- | --- | --- |
| Client / Patient / Citizen | Health ID (VITO) | Keycloak session + TSHEPO | personal / life-hub | onboarding, wellness, marketplace, consent | /home, /wellness, citizen-app |
| Provider (generic) | Health ID + Provider ID (VARAPI) | provider-id login + MFA | facility + workspace + shift | encounter, orders, referral | /queue, /ehr, provider-app |
| Nurse | Provider ID + Staff role | same as provider | ward/department | triage, vitals, inpatient | /ehr/*/vitals |
| Doctor | Provider ID + licensure | provider session | consultation room | consultation, prescribe, refer | /ehr/*/encounter |
| Pharmacist | Provider ID | provider session | pharmacy workspace | prescription verify, dispense | /pharmacy |
| Laboratory user | Staff ID | facility session | lab unit | lab order, result | /lab |
| Radiology / imaging user | Staff ID | facility session | imaging unit | imaging order, report | /ehr/*/imaging |
| Facility administrator | Staff ID + admin role | role guard ADMIN | facility ops | staffing, assets, dispatch | /operations/* |
| Programme manager | Staff ID | programme scope | programme ID | campaigns, outreach | /public-health |
| Health information officer | Staff ID | HIO role | district/national | outbreak, reporting | /public-health |
| Finance / billing user | Staff ID | finance role | billing workspace | bill, claim, remittance | /finance/* |
| Registry administrator | Admin ID | registry admin RBAC | registry ops | identity issuance, reconciliation | /registry/* |
| System administrator | Platform admin | ADMIN role | platform | trust, users, audit | /admin/* |
| Implementer / support user | Support account | developer role | integration | adapter config, replay | /developer |
| Data analyst | Analyst role | reporting RBAC | analytics workspace | report, dashboard | /reports |
| Community health worker | Provider/CHW ID | outreach mode | field outreach | household visit, screening | provider-app/outreach |
| Device | Device ID | mTLS / device registration | facility pod | telemetry, print | none |
| Mobile app (as actor) | App client credentials | OAuth + trust headers | citizen or provider mode | all mobile journeys | apps/mobile/* |
| AI assistant (Nompilo) | Service account | BFF proxy | route + transaction context | guidance, handoff | /ask, Nompilo launcher |
| Scheduled job / worker | Job principal | service-to-service | batch | batch, outbox publish | none |
| Facility pod | Pod ID | pod trust | edge deployment | federated sync | none |
| External integration system | Integration credentials | FHIR/gateway auth | external | interop, replay | /developer |
| Logistics actor / courier | Courier ID | courier mode | delivery run | delivery, POD | /operations/dispatch |
| Offline sync actor | Edge node ID | offline trust envelope | offline queue | offline clinical queue | provider-app/offline |

## Actor detail

### Client / Patient / Citizen (`citizen`)

| Field | Value |
|-------|-------|
| Identity model | Health ID (VITO) |
| Authentication / trust | Keycloak session + TSHEPO |
| Context model | personal / life-hub |
| Permissions | self-scope; MINIMAL friction |
| Workspace | home, wellness, wallet |
| Typical transactions | onboarding, wellness, marketplace, consent |
| Related services | vito, experience-bff, wellness, mushe-wallet |
| UI / mobile / API surface | /home, /wellness, citizen-app |
| Audit requirements | access + consent |
| Safety constraints | no clinical write without consent |

### Provider (generic) (`provider`)

| Field | Value |
|-------|-------|
| Identity model | Health ID + Provider ID (VARAPI) |
| Authentication / trust | provider-id login + MFA |
| Context model | facility + workspace + shift |
| Permissions | RBAC/ABAC via TSHEPO |
| Workspace | queue, EHR, clinical |
| Typical transactions | encounter, orders, referral |
| Related services | pct, varapi, tuso, experience-bff |
| UI / mobile / API surface | /queue, /ehr, provider-app |
| Audit requirements | clinical audit chain |
| Safety constraints | licensure + facility context required |

### Nurse (`nurse`)

| Field | Value |
|-------|-------|
| Identity model | Provider ID + Staff role |
| Authentication / trust | same as provider |
| Context model | ward/department |
| Permissions | nursing order set |
| Workspace | queue, EHR vitals |
| Typical transactions | triage, vitals, inpatient |
| Related services | pct, inpatient |
| UI / mobile / API surface | /ehr/*/vitals |
| Audit requirements | clinical |
| Safety constraints | scope of practice |

### Doctor (`doctor`)

| Field | Value |
|-------|-------|
| Identity model | Provider ID + licensure |
| Authentication / trust | provider session |
| Context model | consultation room |
| Permissions | prescribing, orders |
| Workspace | EHR encounter |
| Typical transactions | consultation, prescribe, refer |
| Related services | pct, pharmacy, oros |
| UI / mobile / API surface | /ehr/*/encounter |
| Audit requirements | prescribing audit |
| Safety constraints | break-glass for emergency |

### Pharmacist (`pharmacist`)

| Field | Value |
|-------|-------|
| Identity model | Provider ID |
| Authentication / trust | provider session |
| Context model | pharmacy workspace |
| Permissions | dispense authority |
| Workspace | /pharmacy |
| Typical transactions | prescription verify, dispense |
| Related services | pharmacy-service |
| UI / mobile / API surface | /pharmacy |
| Audit requirements | dispense audit |
| Safety constraints | formulary checks |

### Laboratory user (`laboratory-user`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID |
| Authentication / trust | facility session |
| Context model | lab unit |
| Permissions | lab result entry |
| Workspace | /lab |
| Typical transactions | lab order, result |
| Related services | oros-service |
| UI / mobile / API surface | /lab |
| Audit requirements | result audit |
| Safety constraints | result release rules |

### Radiology / imaging user (`radiology-user`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID |
| Authentication / trust | facility session |
| Context model | imaging unit |
| Permissions | PACS access |
| Workspace | /pacs, /imaging |
| Typical transactions | imaging order, report |
| Related services | pacs-adapter |
| UI / mobile / API surface | /ehr/*/imaging |
| Audit requirements | PACS access audit |
| Safety constraints | CPID-only SHR |

### Facility administrator (`facility-administrator`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID + admin role |
| Authentication / trust | role guard ADMIN |
| Context model | facility ops |
| Permissions | facility config |
| Workspace | /operations, /facility |
| Typical transactions | staffing, assets, dispatch |
| Related services | tuso, dispatch |
| UI / mobile / API surface | /operations/* |
| Audit requirements | ops audit |
| Safety constraints | separation of duties |

### Programme manager (`programme-manager`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID |
| Authentication / trust | programme scope |
| Context model | programme ID |
| Permissions | programme reports |
| Workspace | /public-health |
| Typical transactions | campaigns, outreach |
| Related services | campaigns, surveillance |
| UI / mobile / API surface | /public-health |
| Audit requirements | programme audit |
| Safety constraints | aggregate data only where required |

### Health information officer (`health-information-officer`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID |
| Authentication / trust | HIO role |
| Context model | district/national |
| Permissions | surveillance read |
| Workspace | /public-health, /ndila |
| Typical transactions | outbreak, reporting |
| Related services | surveillance, ndila |
| UI / mobile / API surface | /public-health |
| Audit requirements | PH audit |
| Safety constraints | de-identification |

### Finance / billing user (`finance-billing-user`)

| Field | Value |
|-------|-------|
| Identity model | Staff ID |
| Authentication / trust | finance role |
| Context model | billing workspace |
| Permissions | financial ops |
| Workspace | /finance |
| Typical transactions | bill, claim, remittance |
| Related services | costing-engine, mushex, coverage |
| UI / mobile / API surface | /finance/* |
| Audit requirements | financial audit |
| Safety constraints | segregation of duties |

### Registry administrator (`registry-administrator`)

| Field | Value |
|-------|-------|
| Identity model | Admin ID |
| Authentication / trust | registry admin RBAC |
| Context model | registry ops |
| Permissions | registry write |
| Workspace | /registry, /operations/vito |
| Typical transactions | identity issuance, reconciliation |
| Related services | vito, varapi |
| UI / mobile / API surface | /registry/* |
| Audit requirements | registry audit mandatory |
| Safety constraints | PII handling |

### System administrator (`system-administrator`)

| Field | Value |
|-------|-------|
| Identity model | Platform admin |
| Authentication / trust | ADMIN role |
| Context model | platform |
| Permissions | governance |
| Workspace | /admin |
| Typical transactions | trust, users, audit |
| Related services | tshepo-authz, tshepo-audit |
| UI / mobile / API surface | /admin/* |
| Audit requirements | critical |
| Safety constraints | break-glass oversight |

### Implementer / support user (`implementer`)

| Field | Value |
|-------|-------|
| Identity model | Support account |
| Authentication / trust | developer role |
| Context model | integration |
| Permissions | integration hub read |
| Workspace | /developer |
| Typical transactions | adapter config, replay |
| Related services | integration-hub |
| UI / mobile / API surface | /developer |
| Audit requirements | integration audit |
| Safety constraints | no production patient data |

### Data analyst (`data-analyst`)

| Field | Value |
|-------|-------|
| Identity model | Analyst role |
| Authentication / trust | reporting RBAC |
| Context model | analytics workspace |
| Permissions | report run |
| Workspace | /reports, /data-intelligence |
| Typical transactions | report, dashboard |
| Related services | reporting, data-warehouse |
| UI / mobile / API surface | /reports |
| Audit requirements | report access audit |
| Safety constraints | de-identification |

### Community health worker (`community-health-worker`)

| Field | Value |
|-------|-------|
| Identity model | Provider/CHW ID |
| Authentication / trust | outreach mode |
| Context model | field outreach |
| Permissions | outreach write |
| Workspace | provider-app outreach mode |
| Typical transactions | household visit, screening |
| Related services | community-service |
| UI / mobile / API surface | provider-app/outreach |
| Audit requirements | field visit audit |
| Safety constraints | offline sync rules |

### Device (`device`)

| Field | Value |
|-------|-------|
| Identity model | Device ID |
| Authentication / trust | mTLS / device registration |
| Context model | facility pod |
| Permissions | device-scoped |
| Workspace | n/a |
| Typical transactions | telemetry, print |
| Related services | iot-ingestion, card-print-agent |
| UI / mobile / API surface | none |
| Audit requirements | device event audit |
| Safety constraints | device block list |

### Mobile app (as actor) (`mobile-app`)

| Field | Value |
|-------|-------|
| Identity model | App client credentials |
| Authentication / trust | OAuth + trust headers |
| Context model | citizen or provider mode |
| Permissions | app-scoped |
| Workspace | mobile tabs |
| Typical transactions | all mobile journeys |
| Related services | experience-bff mobile/* |
| UI / mobile / API surface | apps/mobile/* |
| Audit requirements | mobile session audit |
| Safety constraints | app attestation |

### AI assistant (Nompilo) (`ai-assistant`)

| Field | Value |
|-------|-------|
| Identity model | Service account |
| Authentication / trust | BFF proxy |
| Context model | route + transaction context |
| Permissions | read-mostly; no sovereign write |
| Workspace | /ask |
| Typical transactions | guidance, handoff |
| Related services | guidance, llm-orchestration |
| UI / mobile / API surface | /ask, Nompilo launcher |
| Audit requirements | assist audit |
| Safety constraints | no clinical override |

### Scheduled job / worker (`scheduled-job`)

| Field | Value |
|-------|-------|
| Identity model | Job principal |
| Authentication / trust | service-to-service |
| Context model | batch |
| Permissions | job-scoped |
| Workspace | n/a |
| Typical transactions | batch, outbox publish |
| Related services | jobs-service |
| UI / mobile / API surface | none |
| Audit requirements | job audit |
| Safety constraints | idempotent |

### Facility pod (`facility-pod`)

| Field | Value |
|-------|-------|
| Identity model | Pod ID |
| Authentication / trust | pod trust |
| Context model | edge deployment |
| Permissions | pod-scoped |
| Workspace | offline-edge |
| Typical transactions | federated sync |
| Related services | offline-edge-service |
| UI / mobile / API surface | none |
| Audit requirements | federation audit |
| Safety constraints | reconciliation |

### External integration system (`external-integration`)

| Field | Value |
|-------|-------|
| Identity model | Integration credentials |
| Authentication / trust | FHIR/gateway auth |
| Context model | external |
| Permissions | adapter-scoped |
| Workspace | n/a |
| Typical transactions | interop, replay |
| Related services | fhir-gateway, integration-hub |
| UI / mobile / API surface | /developer |
| Audit requirements | integration audit |
| Safety constraints | rate limits |

### Logistics actor / courier (`logistics-actor`)

| Field | Value |
|-------|-------|
| Identity model | Courier ID |
| Authentication / trust | courier mode |
| Context model | delivery run |
| Permissions | dispatch scope |
| Workspace | provider-app courier mode |
| Typical transactions | delivery, POD |
| Related services | dispatch, nhume |
| UI / mobile / API surface | /operations/dispatch |
| Audit requirements | delivery audit |
| Safety constraints | chain of custody |

### Offline sync actor (`offline-sync-actor`)

| Field | Value |
|-------|-------|
| Identity model | Edge node ID |
| Authentication / trust | offline trust envelope |
| Context model | offline queue |
| Permissions | queued writes |
| Workspace | offline mode |
| Typical transactions | offline clinical queue |
| Related services | offline-sync-service |
| UI / mobile / API surface | provider-app/offline |
| Audit requirements | sync audit |
| Safety constraints | conflict resolution |

