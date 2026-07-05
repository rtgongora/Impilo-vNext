-- ============================================================================
-- workforce-governance-service V008 — IATG adjudication decision record
--
-- Append-only record of adjudication decisions produced by the IATG
-- claim-adjudication workflows (workflow-service definitions
-- identity-claim-adjudication / provider-claim-adjudication /
-- facility-claim-adjudication, seeded in workflow-service V003).
--
-- APPEND-ONLY DOCTRINE: rows are never updated or deleted. A decision is
-- superseded by inserting a NEW row for the same (subject_type, subject_ref);
-- the effective decision is the newest row with effective_at <= now() and
-- (expires_at IS NULL OR expires_at > now()). History is preserved forever
-- for audit. A trigger enforces immutability at the database level.
--
-- Version note: V005-V007 are allocated to sibling Wave-1 branches; V008 is
-- pre-assigned to this change and must not be renumbered.
-- ============================================================================

CREATE TABLE wgv_adjudication_decision (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    workflow_instance_id UUID,                    -- wf_instances.instance_id (workflow-service), nullable for manual/bootstrap decisions
    subject_type         VARCHAR(32) NOT NULL,    -- IDENTITY|PROVIDER|FACILITY|ORGANIZATION
    subject_ref          VARCHAR(255) NOT NULL,   -- opaque ref (Health ID, Provider ID, TUSO facility id, org id)
    decision             VARCHAR(32) NOT NULL,    -- APPROVED|DENIED|CONDITIONAL
    reason               TEXT NOT NULL,           -- mandatory human-readable adjudication rationale
    authority_user_id    VARCHAR(255),            -- adjudicating authority (actor id)
    authority_role       VARCHAR(128),            -- role under which authority acted
    effective_at         TIMESTAMPTZ NOT NULL,
    expires_at           TIMESTAMPTZ,             -- NULL = open-ended
    permissions          JSONB,                   -- granted permissions (esp. CONDITIONAL)
    restrictions         JSONB,                   -- imposed restrictions/conditions
    evidence_refs        JSONB,                   -- array of evidence document/record refs
    access_request_id    UUID,                    -- optional link to wgv access request
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by           VARCHAR(255),
    CONSTRAINT ck_wgv_adj_decision_subject_type
        CHECK (subject_type IN ('IDENTITY', 'PROVIDER', 'FACILITY', 'ORGANIZATION')),
    CONSTRAINT ck_wgv_adj_decision_outcome
        CHECK (decision IN ('APPROVED', 'DENIED', 'CONDITIONAL')),
    CONSTRAINT ck_wgv_adj_decision_reason_not_blank
        CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_wgv_adj_decision_expiry_after_effective
        CHECK (expires_at IS NULL OR expires_at > effective_at)
);

-- Latest-effective resolution path: (tenant, subject) ordered by effective_at.
CREATE INDEX idx_wgv_adj_decision_subject
    ON wgv_adjudication_decision (tenant_id, subject_type, subject_ref, effective_at DESC);
CREATE INDEX idx_wgv_adj_decision_instance
    ON wgv_adjudication_decision (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;
CREATE INDEX idx_wgv_adj_decision_access_request
    ON wgv_adjudication_decision (access_request_id)
    WHERE access_request_id IS NOT NULL;

-- Append-only enforcement: no UPDATE, no DELETE. Supersede by INSERT.
CREATE OR REPLACE FUNCTION wgv_adjudication_decision_block_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'wgv_adjudication_decision is append-only: % is forbidden; supersede by inserting a new decision row', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_wgv_adjudication_decision_append_only
    BEFORE UPDATE OR DELETE ON wgv_adjudication_decision
    FOR EACH ROW
    EXECUTE FUNCTION wgv_adjudication_decision_block_mutation();
