-- ============================================================
-- FHIR Gateway
-- V004: record what actually happened downstream
--
-- fhir_audit_log records `outcome`, which collapses to SUCCESS | FORWARD_FAILED | NO_ROUTE |
-- CONSENT_DENIED. FhirForwarder already KNOWS the downstream HTTP status — ForwardAttempt carries
-- it — and GatewayForwardService throws it away, reading only attempt.delivered().
--
-- So a 403 (missing trust headers), a 409 (duplicate), a 422 (PII violation) and a 500 are all
-- recorded identically, with nothing to tell them apart. Three consequences, all live:
--
--   1. offline-edge's entire conflict-review path depends on distinguishing HTTP 409 from a
--      transport failure: a 409 becomes a ConflictReviewEntity and a reconciliation event, anything
--      else marks the action FAILED. Routed through this gateway, every 409 silently degrades to
--      FAILED and the conflict is lost.
--   2. "The SHR rejected this resource as malformed" and "the SHR was unreachable" are opposite
--      operational problems and are currently the same audit row.
--   3. Nobody can tell from the audit table whether the repointed target is even accepting writes.
--
-- target_endpoint matters for the same reason: the audit row records that a forward failed but not
-- WHERE it was sent, and this gateway's delivery target is a single environment variable that has
-- already been wrong once (it pointed at the ungoverned stock HAPI server).
--
-- subject_cpid is added because a consent denial is unanswerable without it — "whose write was
-- refused" is the first question anyone asks, and the row could not answer it. It is a pseudonymous
-- clinical identifier, which is what this plane is built to carry; no PII belongs here.
-- ============================================================

ALTER TABLE fhir_gateway.fhir_audit_log
    ADD COLUMN IF NOT EXISTS downstream_status INT,
    ADD COLUMN IF NOT EXISTS target_endpoint    VARCHAR(500),
    ADD COLUMN IF NOT EXISTS subject_cpid       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS idempotency_key    VARCHAR(255);

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.downstream_status IS
    'The HTTP status the destination actually returned. NULL when no call was made (CONSENT_DENIED, '
    'NO_ROUTE) or when the failure was transport-level, which is itself the distinction: a NULL '
    'here with outcome=FORWARD_FAILED means "never reached", a 422 means "reached and refused".';

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.target_endpoint IS
    'Where the resource was sent. The delivery target is one environment variable and has been '
    'wrong before; an audit row that cannot say where it delivered cannot prove where it did not.';

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.subject_cpid IS
    'The pseudonymous subject. A consent denial is unanswerable without it. CPID only — never PII.';

COMMENT ON COLUMN fhir_gateway.fhir_audit_log.idempotency_key IS
    'The caller''s Idempotency-Key, so a replayed forward is traceable to the original.';

-- "Show me everything the SHR refused today, and why" — the query the audit table could not answer.
CREATE INDEX IF NOT EXISTS idx_fhir_audit_downstream
    ON fhir_gateway.fhir_audit_log (tenant_id, created_at DESC, downstream_status)
    WHERE downstream_status IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_fhir_audit_subject
    ON fhir_gateway.fhir_audit_log (tenant_id, subject_cpid, created_at DESC);
