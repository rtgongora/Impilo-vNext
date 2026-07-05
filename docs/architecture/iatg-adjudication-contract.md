# IATG Adjudication Contract — Workflow Definitions + Decision Records

**Status:** Wave-1 (IATG program, WS-G). Defines the adjudication workflow
definitions seeded into `workflow-service` and the append-only decision record
owned by `workforce-governance-service`, plus the Wave-2 caller wiring plan.

## Ownership

| Concern | Owner | Artifact |
|---|---|---|
| Adjudication workflow definitions + instance lifecycle | `workflow-service` (generic engine) | `wf_definitions` seed migration `V003__iatg_adjudication_definitions.sql` |
| Adjudication decision record (outcome truth) | `workforce-governance-service` | `wgv_adjudication_decision`, migration `V008__adjudication_decision_record.sql` |
| Starting instances / claim intake UX | Experience BFF claim flows (Wave-2) + org-registry Channel-C claims | reference `workflow_instance_id` |

The workflow engine is process orchestration only; it is **not** the source of
truth for adjudication outcomes. The decision record in workforce-governance
is the SoR for "what was decided, by whom, why, and with what effect".

## Workflow definitions (workflow-service V003 seed)

Three published definitions, seeded for the national tenant
(`00000000-0000-0000-0000-000000000001`), category `GOVERNANCE`, version 1,
status `PUBLISHED` (the engine only starts instances from PUBLISHED
definitions):

| Code | Fixed `definition_id` | Subject |
|---|---|---|
| `identity-claim-adjudication` | `ad1d0000-0000-4000-8000-000000000001` | IDENTITY (Health ID / identity attribute claims) |
| `provider-claim-adjudication` | `ad1d0000-0000-4000-8000-000000000002` | PROVIDER (regulated professional capacity claims) |
| `facility-claim-adjudication` | `ad1d0000-0000-4000-8000-000000000003` | FACILITY / ORGANIZATION (facility control & onboarding claims, incl. org-registry Channel-C) |

The `definition_id`s are fixed so callers can start instances without a
lookup; `code` remains the stable human-facing identifier and lookup key
(`GET /internal/v1/workflows/definitions?…`).

### State machine (doctrine)

```
DRAFT → SUBMITTED → TRIAGED → { EVIDENCE_REQUESTED ⇄ UNDER_ADJUDICATION }
    UNDER_ADJUDICATION → DECIDED_APPROVED | DECIDED_DENIED | DECIDED_CONDITIONAL
    DECIDED_*          → APPEALED → UNDER_ADJUDICATION   (appeal loop)
    DECIDED_*          → CLOSED                          (terminal)
```

### Definition JSON shape (verified against `WorkflowDefinitionEntity`)

`wf_definitions.steps_json` is a JSON **array of step objects**; the engine
(`WorkflowService.startInstance` / `advanceToNextStep`) requires only the
`"name"` key per step and advances linearly. The doctrine transition graph is
carried as data in each step's `"transitions"` array (and validated by
Wave-2 callers / policy, not by the generic engine):

```json
[
  {"name": "DRAFT",               "type": "STATE",    "transitions": ["SUBMITTED"]},
  {"name": "SUBMITTED",           "type": "STATE",    "transitions": ["TRIAGED"]},
  {"name": "TRIAGED",             "type": "STATE",    "transitions": ["EVIDENCE_REQUESTED", "UNDER_ADJUDICATION"]},
  {"name": "EVIDENCE_REQUESTED",  "type": "STATE",    "transitions": ["UNDER_ADJUDICATION"]},
  {"name": "UNDER_ADJUDICATION",  "type": "STATE",    "transitions": ["EVIDENCE_REQUESTED", "DECIDED_APPROVED", "DECIDED_DENIED", "DECIDED_CONDITIONAL"]},
  {"name": "DECIDED_APPROVED",    "type": "DECISION", "transitions": ["APPEALED", "CLOSED"]},
  {"name": "DECIDED_DENIED",      "type": "DECISION", "transitions": ["APPEALED", "CLOSED"]},
  {"name": "DECIDED_CONDITIONAL", "type": "DECISION", "transitions": ["APPEALED", "CLOSED"]},
  {"name": "APPEALED",            "type": "STATE",    "transitions": ["UNDER_ADJUDICATION"]},
  {"name": "CLOSED",              "type": "TERMINAL", "transitions": []}
]
```

`wf_definitions.schema_json` carries machine metadata:

```json
{
  "stateMachine": "iatg-claim-adjudication",
  "stateMachineVersion": 1,
  "subjectType": "IDENTITY | PROVIDER | FACILITY",
  "initialState": "DRAFT",
  "terminalStates": ["CLOSED"],
  "decisionStates": ["DECIDED_APPROVED", "DECIDED_DENIED", "DECIDED_CONDITIONAL"],
  "decisionRecordService": "workforce-governance-service",
  "decisionRecordTable": "wgv_adjudication_decision"
}
```

**Engine note.** The generic engine's `ADVANCE` walks `steps_json` in array
order; the branch/loop edges (evidence loop, appeal loop, three decision
outcomes) are doctrine data that Wave-2 callers must honour when choosing the
next state. This keeps engine code untouched (Wave-1 constraint) while making
the doctrine machine-readable now.

## Decision record (workforce-governance V008)

Table `wgv_adjudication_decision` — **APPEND-ONLY** (no UPDATE/DELETE; a DB
trigger rejects both; supersede = insert a new row):

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `tenant_id` | UUID NOT NULL | governance convention |
| `workflow_instance_id` | UUID NULL | `wf_instances.instance_id` in workflow-service |
| `subject_type` | VARCHAR NOT NULL | `IDENTITY \| PROVIDER \| FACILITY \| ORGANIZATION` (CHECK) |
| `subject_ref` | VARCHAR NOT NULL | opaque subject reference |
| `decision` | VARCHAR NOT NULL | `APPROVED \| DENIED \| CONDITIONAL` (CHECK) |
| `reason` | TEXT NOT NULL | **mandatory**, non-blank (CHECK) |
| `authority_user_id`, `authority_role` | VARCHAR | adjudicating authority |
| `effective_at` | TIMESTAMPTZ NOT NULL | |
| `expires_at` | TIMESTAMPTZ NULL | must be `> effective_at` when set |
| `permissions`, `restrictions`, `evidence_refs` | JSONB | conditional-grant payloads + evidence links |
| `access_request_id` | UUID NULL | optional link to a WGV access request |
| `created_at`, `created_by` | | audit |

**Latest-effective resolution** for `(subject_type, subject_ref)`: the newest
row (by `effective_at`, `created_at` tiebreak) with `effective_at <= now` and
(`expires_at` null or `> now`). Full history is preserved forever.

### API (workforce-governance-service)

- `POST /internal/v1/workforce-governance/adjudications/decisions` — records a
  new decision row (201). Reason mandatory; superseding is just another POST.
- `GET /internal/v1/workforce-governance/adjudications/decisions?subjectType=&subjectRef=`
  — returns `{ latestEffective, history[] }`.

Outbox event on record: `impilo.governance.adjudication.decision.recorded`
(aggregate `ADJUDICATION_DECISION`), via the governance `event_outbox`.

Trust: served under `/internal/v1/*`; the controller reads
`TrustContextHolder` when the shared `TrustContextFilter` covers that prefix
(sibling Wave-1 branch) and falls back to raw trust headers otherwise. Authz
remains Envoy ext_authz → TSHEPO at the mesh edge.

## Who starts instances (Wave-2 caller wiring plan)

1. **Experience BFF claim flows** (identity / provider claim intake): call
   `POST /internal/v1/workflows/instances` on workflow-service with the fixed
   `definitionId`, `subjectRef` = claimed identifier, `context` = claim
   payload refs. The BFF stores the returned `instance_id` against the claim
   and drives doctrine transitions via
   `POST /internal/v1/workflows/instances/{id}/transition`.
2. **Org-registry Channel-C claims** (facility/organisation control claims):
   the claim record references `workflow_instance_id` of a
   `facility-claim-adjudication` instance; Channel-C adjudicators work the
   instance through TRIAGED/EVIDENCE_REQUESTED/UNDER_ADJUDICATION.
3. **On reaching a `DECIDED_*` state**, the adjudicating surface (BFF on
   behalf of the authority) calls the governance decision endpoint with
   `workflowInstanceId`, subject, decision, mandatory reason, and any
   permissions/restrictions/evidence — then transitions the instance to
   CLOSED (or the subject appeals, looping back to UNDER_ADJUDICATION; the
   appeal outcome is recorded as a **new** decision row superseding the
   first).
4. **Consumers** (TSHEPO policy inputs, identity-assurance, org-registry)
   resolve the latest-effective decision via the GET endpoint or the
   `impilo.governance.adjudication.decision.recorded` event; they never read
   workflow state as outcome truth.

Wave-2 will also wire Nompilo guidance and audit surfacing for adjudicator
workspaces; nothing in this contract requires engine changes.
