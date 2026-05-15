# Clinical Care Protocols and Pathways Map

## Purpose

Define how encounter workflow links to care pathways, protocols, forms, and decision support without duplicating specialist service ownership.

## Protocol/Pathway Linkage Model

| Capability | Owner service | PCT linkage model | Invocation surface | Encounter contexts | Lovable reference coverage | Status | Blocker |
|---|---|---|---|---|---|---|---|
| Encounter-level pathway reference | `pct-service` | `pathwayRef` on encounter start metadata | `POST /v1/journeys/{id}/encounter/start` | outpatient, emergency, inpatient, community, virtual | pathway-aware encounter UX intent | implemented (this pass) | none |
| Encounter-level protocol reference | `pct-service` | `protocolRef` on encounter start metadata | same as above | all | protocol/checklist intent | implemented (this pass) | none |
| Structured clinical forms | `forms-service` | PCT stores references; form payload lifecycle remains forms-owned | `/internal/v1/forms*` | all | structured assessment content | implemented | none |
| Guidance Q&A and reminders | `guidance-service` | linked by patient/encounter context through BFF | `/internal/v1/guidance/*` | all | decision support assist | implemented | none |
| Rule evaluation | `rules-service` | invoked with encounter and patient context | `/internal/v1/rules/*` | all, especially triage/discharge | protocolized rule checks | implemented | none |
| Clinical pathways sessions | `clinical-knowledge-platform-service` | pathway session IDs and pathway refs | `/internal/v1/clinical/pathways*` | all | pathway progression intent | represented but incomplete | no canonical BFF orchestration from encounter start to pathway-session start |
| Triage protocol execution | `pct-service` + `rules-service` | triage category + rule lookup by BFF/UI | triage + rules endpoints | emergency/outpatient/community | triage sorting coverage | represented but incomplete | tighter protocol catalogs and coded triage bundles needed |
| Discharge protocol checklist | `pct-service` + `inpatient-service` + BFF | discharge workflow state + blocker clearance APIs | discharge + admission APIs | outpatient/inpatient/emergency | disposition checklist intent | represented but incomplete | follow-up pathway handoff not first-class payload yet |
| Follow-up pathway | scheduling + PCT + CKP | references only; no unified orchestration contract | appointments + encounter/discharge | all | review continuity flow | missing and should be implemented | requires bounded BFF orchestration endpoint |

## What Was Implemented in This Pass

- Added encounter-level `pathwayRef` and `protocolRef` fields to PCT encounter model/API.
- Propagated pathway/protocol metadata through Experience BFF encounter-create route.
- Added Experience encounter-start form fields for pathway/protocol references to make pathway selection explicit.

## Explicit Non-Faked Boundaries

- PCT does not execute clinical knowledge logic directly; it stores encounter linkage metadata.
- Rules/guidance/knowledge execution remains in `rules-service`, `guidance-service`, and `clinical-knowledge-platform-service`.
- Where pathway execution is not fully wired from encounter create, UI must show explicit partial/available-by-context behavior.
