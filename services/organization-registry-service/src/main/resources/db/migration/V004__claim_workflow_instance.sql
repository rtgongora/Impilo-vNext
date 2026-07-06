-- ============================================================================
-- organization-registry-service V004 — Channel-C claim ↔ IATG adjudication link
--
-- Adds workflow_instance_id to the Channel-C claim so a claim escalated to
-- adjudication can be linked to the workflow-service instance that adjudicates
-- it (workflow-service definitions identity/provider/facility-claim-adjudication,
-- V003 seed). The instance id is the authoritative join key used to resolve the
-- claim when workforce-governance emits
-- `impilo.governance.adjudication.decision.recorded`.
--
-- Honest-state semantics:
--   status=UNDER_REVIEW, workflow_instance_id NOT NULL -> adjudication in progress
--   status=UNDER_REVIEW, workflow_instance_id NULL     -> escalated but adjudication
--                                                         pending/unavailable (workflow
--                                                         start deferred or failed)
--
-- adjudication_ref continues to carry the authoritative decision id once the
-- decision is recorded and resolved.
-- ============================================================================

ALTER TABLE org_registry.org_registry_claim_submission
    ADD COLUMN workflow_instance_id UUID;

-- Resolution path: decision event carries workflow_instance_id -> find the claim.
CREATE INDEX idx_org_registry_claim_workflow_instance
    ON org_registry.org_registry_claim_submission (workflow_instance_id)
    WHERE workflow_instance_id IS NOT NULL;
