-- ============================================================================
-- nhume-service V007 — OF-B18: proof-of-handover grade ladder (§12.4)
--
--  * nhume_delivery_requests gains the per-order-class handover contract:
--    required_pod_grade (explicit grade demand; NULL = legacy ungraded),
--    named_recipient_required (named-recipient enforcement, fail-closed
--    DELIVERY_ATTEMPTED on verification failure), handover_otp (the server-side
--    OTP truth an OTP_MATCH grade is verified against — an OTP grade without a
--    stored OTP can NEVER pass, fail-closed), and the bounded reattempt
--    counters (attempts exhausted => RETURNED per §12.7).
--
--  * nhume_delivery_proofs gains the achieved verification_grade
--    (OTP_PLUS_ID / OTP_MATCH / ID_CHECK / NAMED_RECIPIENT_MATCH /
--    SIGNATURE_ONLY / UNGRADED), the receiver-name attestation +
--    named-recipient match result, the courier ID-check attestation, and the
--    coded failure_reason for refused handovers (verified = FALSE rows are
--    the honest DELIVERY_ATTEMPTED evidence, never deleted).
--
--  GPS proximity is NEVER a grade (§12.4 binding; CC-8) — geofence_match
--  remains corroboration only.
-- ============================================================================

ALTER TABLE nhume_delivery_requests
    ADD COLUMN required_pod_grade        VARCHAR(32),
    ADD COLUMN named_recipient_required  BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN handover_otp              VARCHAR(12),
    ADD COLUMN handover_attempts         INT     NOT NULL DEFAULT 0,
    ADD COLUMN max_handover_attempts     INT     NOT NULL DEFAULT 3;

ALTER TABLE nhume_delivery_proofs
    ADD COLUMN verification_grade        VARCHAR(32),
    ADD COLUMN receiver_name             VARCHAR(255),
    ADD COLUMN named_recipient_match     BOOLEAN,
    ADD COLUMN id_document_type          VARCHAR(48),
    ADD COLUMN id_document_ref           VARCHAR(64),
    ADD COLUMN failure_reason            VARCHAR(128);

-- Failed-handover forensics: refused proofs per delivery.
CREATE INDEX idx_nhume_proof_unverified
    ON nhume_delivery_proofs (delivery_id)
    WHERE verified = FALSE;
