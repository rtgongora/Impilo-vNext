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
| Pre-procedure assessment | partial | forms/guidance references available; no unified procedure aggregate |
| Consent/checklist | partial | MVUMO-backed consent available; procedure-specific checklist wiring partial |
| Intra-procedure documentation | partial | document/forms support exists; dedicated intra-op timeline partial |
| Complication/critical event handling | partial | emergency activation pathways available; explicit procedure protocol catalog partial |
| Post-procedure recovery | partial | encounter/disposition + notes available; dedicated recovery workflow partial |
| Procedure outcome/disposition | implemented (bounded) | through PCT encounter lifecycle/discharge/admit/transfer linkage |

## This Pass (bounded implementation)

- Added explicit PCT encounter context support for procedure/OR/procedure-room contexts.
- Kept specialist ownership boundaries intact (no new procedure microservice created).

## Architecture Decision Boundary

No dedicated `procedure-service` or `theatre-service` is introduced in this pass.

Remaining decision:
- whether to keep procedure episode workflow state in PCT (coordinator-only) with references, or
- introduce a future sovereign procedure/theatre service via ADR.
