# Clinical Procedure / OR Context Map

## Doctrine

Procedure care is treated as a distinct encounter context, not a note subtype.

This pass adds explicit context support in PCT:
- `procedure`
- `procedure_room`
- `operating_room`

## Procedure Context Model

| Dimension | Owner | Current representation |
|---|---|---|
| Procedure encounter context | `pct-service` | encounter context enum + metadata |
| Procedure orders / specimens | `oros-service` | order/result domain |
| Procedure medications/consumables | `pharmacy-service` (+ inventory integration) | prescription/dispense domain |
| Procedure documentation | `document-service` (+ forms) | operative notes/attachments/forms |
| Procedure consent | `mvumo-service` + `tshepo-consent-service` | consent request/evidence workflow |
| Theatre/workspace allocation | `tuso-service` (+ inpatient where relevant) | facility/workspace/ward/bed authority |
| Inpatient linkage | `inpatient-service` + PCT | admission/transfer/discharge linkage |

## Workflow Coverage

| Phase | Status | Notes |
|---|---|---|
| Procedure booking / episode creation | implemented (bounded) | `inpatient-service` `procedure_episode`; BFF `/internal/v1/procedures/**`; web EHR + provider mobile theatre tab |
| Pre-procedure assessment | implemented (bounded) | nursing + anaesthesia preop assessments on episode aggregate |
| Consent / WHO checklist | implemented (bounded) | SIGN_IN / TIME_OUT / SIGN_OUT checklist seeded per episode; consent flag on theatre start |
| Intra-procedure documentation | implemented (bounded) | `procedure_intraop_event` timeline on episode |
| Complication / critical event handling | partial | emergency activation pathways available; explicit procedure protocol catalog partial |
| Post-procedure recovery (PACU) | implemented (bounded) | PACU arrival + Aldrete/pain/disposition on `procedure_postop_record` |
| Procedure outcome / disposition | implemented (bounded) | episode `COMPLETED` + structured history via inpatient SoR |
| Theatre slot / booking integration | implemented (bounded) | `booking-service` confirm/approve/convert → `InpatientClient` auto-creates episode + `PROCEDURE_EPISODE` link |
| Operative notes / documents | implemented (bounded) | BFF multipart upload → `document-service` with `episode_id` metadata → `procedure_episode_document` |
| MVUMO consent evidence binding | implemented (bounded) | Full lifecycle: BFF `/consent` → `/consent/explanation` → `/consent/verify-identity` → `/consent/grant` (or remote-session path); sync binds Tshepo `proofRef`; theatre start gated on `GRANTED` |
| Theatre consumables | implemented (bounded) | BFF → `inventory-service` `POST /v1/internal/consumption/clinical` (`refType=PROCEDURE`) + `procedure_consumable` audit |

## This Pass (bounded implementation)

- **Procedure episode SoR** in `inpatient-service` (`V010__procedure_episode_pipeline.sql`): `procedure_episode`, preop assessments, WHO checklist items, intra-op events, post-op/PACU record.
- **REST** at `/internal/v1/procedures/episodes/**` with lifecycle `BOOKED → PREOP → READY_FOR_THEATRE → IN_PROGRESS → PACU → RECOVERED → COMPLETED`.
- **BFF** `ProcedureWorkflowController` proxies inpatient; optional OROS preop orders on create; `StructuredHistoryController` prefers inpatient procedure history.
- **Experience**: EHR six-step perioperative wizard (`/ehr/[patientId]/procedures/[episodeId]`); provider mobile `TheatreProcedureScreen`.
- PCT encounter context support for procedure/OR/procedure-room contexts retained for encounter linkage.
- No dedicated `procedure-service` or `theatre-service` introduced.

## Architecture Decision Boundary

No dedicated `procedure-service` or `theatre-service` is introduced in this pass.

Remaining decision:
- whether to keep procedure episode workflow state in PCT (coordinator-only) with references, or
- introduce a future sovereign procedure/theatre service via ADR.
