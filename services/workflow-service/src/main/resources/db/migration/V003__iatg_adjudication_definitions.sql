-- ============================================================================
-- workflow-service V003 — IATG claim-adjudication workflow definitions (seed)
--
-- Seeds THREE published workflow definitions for the Identity / Access / Trust
-- Governance (IATG) adjudication doctrine:
--   * identity-claim-adjudication  (subject: IDENTITY)
--   * provider-claim-adjudication  (subject: PROVIDER)
--   * facility-claim-adjudication  (subject: FACILITY)
--
-- Doctrine state machine (encoded per-step in steps_json "transitions"):
--   DRAFT -> SUBMITTED -> TRIAGED -> EVIDENCE_REQUESTED <-> UNDER_ADJUDICATION
--     -> DECIDED_APPROVED | DECIDED_DENIED | DECIDED_CONDITIONAL
--     -> (APPEALED -> UNDER_ADJUDICATION) -> CLOSED
--
-- The generic engine (WorkflowService) advances steps linearly by "name" and
-- only requires each step object to carry a "name"; the full transition graph
-- is carried as data in each step's "transitions" array and in schema_json so
-- Wave-2 callers (BFF claim flows, org-registry Channel-C) and TSHEPO policy
-- can validate doctrine transitions without engine changes.
--
-- Decision outcomes are recorded append-only in workforce-governance-service
-- (wgv_adjudication_decision, migration V008), referencing wf_instances via
-- workflow_instance_id. See docs/architecture/iatg-adjudication-contract.md.
--
-- Data-only migration: no schema changes. Idempotent via ON CONFLICT on
-- uq_wf_def_tenant_code_version. Tenant is the national seed tenant.
-- ============================================================================

INSERT INTO wf_definitions
    (definition_id, tenant_id, code, name, description, category, version, status, schema_json, steps_json, created_at, updated_at, published_at)
VALUES
(
    'ad1d0000-0000-4000-8000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'identity-claim-adjudication',
    'Identity Claim Adjudication',
    'IATG adjudication workflow for identity claims (a person asserting or disputing a Health ID / identity attribute). Decision outcomes are recorded append-only in workforce-governance wgv_adjudication_decision.',
    'GOVERNANCE',
    1,
    'PUBLISHED',
    '{"stateMachine":"iatg-claim-adjudication","stateMachineVersion":1,"subjectType":"IDENTITY","initialState":"DRAFT","terminalStates":["CLOSED"],"decisionStates":["DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"],"decisionRecordService":"workforce-governance-service","decisionRecordTable":"wgv_adjudication_decision"}'::jsonb,
    '[{"name":"DRAFT","type":"STATE","transitions":["SUBMITTED"]},{"name":"SUBMITTED","type":"STATE","transitions":["TRIAGED"]},{"name":"TRIAGED","type":"STATE","transitions":["EVIDENCE_REQUESTED","UNDER_ADJUDICATION"]},{"name":"EVIDENCE_REQUESTED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"UNDER_ADJUDICATION","type":"STATE","transitions":["EVIDENCE_REQUESTED","DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"]},{"name":"DECIDED_APPROVED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_DENIED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_CONDITIONAL","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"APPEALED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"CLOSED","type":"TERMINAL","transitions":[]}]'::jsonb,
    NOW(), NOW(), NOW()
),
(
    'ad1d0000-0000-4000-8000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'provider-claim-adjudication',
    'Provider Claim Adjudication',
    'IATG adjudication workflow for provider claims (a person asserting regulated professional capacity: Provider ID, licensure, scope of practice). Decision outcomes are recorded append-only in workforce-governance wgv_adjudication_decision.',
    'GOVERNANCE',
    1,
    'PUBLISHED',
    '{"stateMachine":"iatg-claim-adjudication","stateMachineVersion":1,"subjectType":"PROVIDER","initialState":"DRAFT","terminalStates":["CLOSED"],"decisionStates":["DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"],"decisionRecordService":"workforce-governance-service","decisionRecordTable":"wgv_adjudication_decision"}'::jsonb,
    '[{"name":"DRAFT","type":"STATE","transitions":["SUBMITTED"]},{"name":"SUBMITTED","type":"STATE","transitions":["TRIAGED"]},{"name":"TRIAGED","type":"STATE","transitions":["EVIDENCE_REQUESTED","UNDER_ADJUDICATION"]},{"name":"EVIDENCE_REQUESTED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"UNDER_ADJUDICATION","type":"STATE","transitions":["EVIDENCE_REQUESTED","DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"]},{"name":"DECIDED_APPROVED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_DENIED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_CONDITIONAL","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"APPEALED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"CLOSED","type":"TERMINAL","transitions":[]}]'::jsonb,
    NOW(), NOW(), NOW()
),
(
    'ad1d0000-0000-4000-8000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'facility-claim-adjudication',
    'Facility Claim Adjudication',
    'IATG adjudication workflow for facility/organisation claims (an organisation asserting control of a facility or requesting governed onboarding, incl. org-registry Channel-C claims). Decision outcomes are recorded append-only in workforce-governance wgv_adjudication_decision.',
    'GOVERNANCE',
    1,
    'PUBLISHED',
    '{"stateMachine":"iatg-claim-adjudication","stateMachineVersion":1,"subjectType":"FACILITY","initialState":"DRAFT","terminalStates":["CLOSED"],"decisionStates":["DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"],"decisionRecordService":"workforce-governance-service","decisionRecordTable":"wgv_adjudication_decision"}'::jsonb,
    '[{"name":"DRAFT","type":"STATE","transitions":["SUBMITTED"]},{"name":"SUBMITTED","type":"STATE","transitions":["TRIAGED"]},{"name":"TRIAGED","type":"STATE","transitions":["EVIDENCE_REQUESTED","UNDER_ADJUDICATION"]},{"name":"EVIDENCE_REQUESTED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"UNDER_ADJUDICATION","type":"STATE","transitions":["EVIDENCE_REQUESTED","DECIDED_APPROVED","DECIDED_DENIED","DECIDED_CONDITIONAL"]},{"name":"DECIDED_APPROVED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_DENIED","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"DECIDED_CONDITIONAL","type":"DECISION","transitions":["APPEALED","CLOSED"]},{"name":"APPEALED","type":"STATE","transitions":["UNDER_ADJUDICATION"]},{"name":"CLOSED","type":"TERMINAL","transitions":[]}]'::jsonb,
    NOW(), NOW(), NOW()
)
ON CONFLICT ON CONSTRAINT uq_wf_def_tenant_code_version DO NOTHING;
