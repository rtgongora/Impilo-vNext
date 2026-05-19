# Clinical Encounter Service Boundary Map

| Domain slice | System of record |
|---|---|
| encounter workflow state/context/modality/disposition | `pct-service` |
| pathway/protocol/content semantics | `guidance-service`, `rules-service`, `clinical-knowledge-platform-service`, `forms-service` |
| orders/results/critical result states | `oros-service` |
| prescriptions/dispense/medication fulfillment | `pharmacy-service` |
| inpatient admission/bed/ward/transfer/discharge | `inpatient-service` |
| documents/attachments/operative notes | `document-service` |
| procedure consent workflow/evidence | `mvumo-service` + `tshepo-consent-service` |
| imaging metadata/DICOM/PACS/viewer sessions | `pacs-adapter-service` |
| trust policy decisioning + audit authority | `tshepo-authz-service`, `tshepo-audit-service` |
| patient/provider/facility/terminology authorities | `vito-service`, `varapi-service`, `tuso-service`, `zibo-service` |
| shared health record/FHIR persistence | `butano-service` / `butano-fhir` |
| experience orchestration/fail-close UX adapter | `experience-bff` |

## This Pass Notes

- PCT remains coordinator and now explicitly supports procedure/OR encounter contexts.
- No duplication of inpatient, OROS, pharmacy, document, or PACS ownership inside PCT.
- Encounter pathway/protocol update support is metadata linkage only, not pathway execution logic.
# Clinical Encounter Service Boundary Map

## Boundary Contract

PCT remains the encounter conductor, not a replacement for specialist domains.

## Ownership Matrix

| Domain | Owner | In-scope responsibility | Explicit non-ownership |
|---|---|---|---|
| Encounter context/journey/modality/disposition/referral linkage | `pct-service` | encounter start/complete metadata, journey transitions, referral package state, virtual encounter modality linkage | does not own orders, dispensing, inpatient bed inventory, or clinical knowledge execution |
| Orders/results | `oros-service` | order placement/routing/worksteps/results/critical flags | does not own encounter workflow state machine |
| Prescriptions/dispense | `pharmacy-service` | prescriptions, refill/cancel/dispense, medicine fulfilment | does not own encounter state |
| Admission/bed/transfers/inpatient discharge | `inpatient-service` | admission lifecycle, transfer operations, inpatient discharge operations | does not own outpatient queue or referral package lifecycle |
| Appointment/check-in/queue routing | queue + booking capabilities (`pct-service`, `tuso-service`, BFF queue/scheduling orchestration) | queue ticket state, appointment lifecycle and operational routing | no duplication of encounter conductor logic in scheduling service |
| Clinical notes/attachments/object binaries | `document-service` | object storage, metadata, attachment lifecycle | does not own referral workflow state |
| Forms schemas/validation | `forms-service` | structured form schema versioning and validation | does not own encounter transitions |
| Guidance | `guidance-service` | reminders, ask/search guidance responses | does not own encounter authoritative state |
| Rules | `rules-service` | rules registry/versioning/evaluation | does not own journey state |
| Clinical knowledge/pathways | `clinical-knowledge-platform-service` | pathways sessions, assistant traces, prescribing/rules support | does not own queue/encounter persistence |
| Consent capture/evidence | `mvumo-service` | remote consent workflows and evidence references | does not own policy decision authority |
| Trust policy decisioning/authz/audit authority | `tshepo-*` services | policy decisions, authz enforcement, governance/audit | no encounter business workflow ownership |
| Patient identity | `vito-service` | person/patient identity authority | does not own encounter logic |
| Provider identity/routing | `varapi-service` | provider registry and provider routing references | no encounter lifecycle ownership |
| Facility/workspace context/routing | `tuso-service` | facility/workspace/service references and booking context | no encounter lifecycle ownership |
| Terminology/coding authority | `zibo-service` | coding and terminology validation | no encounter lifecycle ownership |
| SHR/FHIR boundary | `butano-service`, `butano-fhir`, `fhir-gateway-service` | longitudinal record and FHIR persistence/exchange | no queue or encounter orchestration ownership |
| Experience orchestration | `experience-bff` | UI-facing aggregation, typed upstream errors, trust header propagation | no synthetic clinical success generation |

## Encounter Mastery Boundary Notes

- Encounter metadata added in this pass (`encounterContext`, `entryPoint`, `careSetting`, `priority`, `triageCategory`, `pathwayRef`, `protocolRef`) is stored by PCT and orchestrated by BFF/UI.
- Specialist services remain source-of-truth for their domains; PCT references and coordinates.
- Unavailable capabilities (realtime virtual media transport; on-call/team/pool routing depth) stay explicitly blocked instead of faked.
