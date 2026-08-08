-- ============================================================
-- FHIR Gateway
-- V005: record WHAT was created, not only that something was
--
-- V004 made the audit row able to say where a forward went and what status came back. It still
-- could not say what the destination created. FhirForwarder discarded both the Location header and
-- the returned resource body, so the logical id the SHR assigned was never seen by anyone.
--
-- That is a governance gap and a migration blocker at the same time:
--
--   1. A service writing through the governed seam gets back an audit-log id and nothing else, so
--      it cannot store a reference to what it wrote. oros persists a butanoRef against the order;
--      inpatient links a Procedure; offline-edge reconciles against the created resource. Each of
--      them would have had to either record no reference or invent one. "The write succeeded but I
--      cannot name the result" is not a state a clinical record should have, and it is exactly the
--      state that made every direct-HTTP writer keep its own ungoverned path.
--   2. A disputed or duplicated resource cannot be traced back to the forward that produced it.
--      The audit table can prove a write happened and never prove which record it became.
--
-- NULL is meaningful and expected: no call was made (CONSENT_DENIED, NO_ROUTE), the call failed, or
-- the destination reported neither a Location header nor a body carrying an id. It is never
-- back-filled by guessing.
-- ============================================================

ALTER TABLE fhir_gateway.fhir_audit_log
    ADD COLUMN IF NOT EXISTS downstream_resource_id VARCHAR(128);

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.downstream_resource_id IS
    'The logical id the destination assigned, read from the Location header or the returned '
    'resource body. Non-NULL only on outcome=SUCCESS. NULL means the destination did not say — '
    'never a guess, and never back-filled.';

-- "Which forward created this resource?" — traceability from the record back to the decision.
CREATE INDEX IF NOT EXISTS idx_fhir_audit_downstream_resource
    ON fhir_gateway.fhir_audit_log (tenant_id, resource_type, downstream_resource_id)
    WHERE downstream_resource_id IS NOT NULL;
