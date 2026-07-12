-- =====================================================================================
-- Daidzai — V002 :: anonymous-SOS callback verification (PD-3)
-- Health Services Gateway doctrine §7: a guest emergency request is captured immediately
-- and NEVER gated on sign-in, but before any resource moves a dispatcher/responder MUST
-- reach the caller on a callback number. These columns carry that callback + its
-- verification, and the request lifecycle gains an AWAITING_CALLBACK state (enforced in
-- EmergencyService.triageRequestToIncident — an unverified anonymous request cannot triage).
-- No PII beyond the callback number; identity remains secondary to response (§7.4).
-- =====================================================================================

ALTER TABLE daidzai.dai_emergency_request
    ADD COLUMN callback_number       VARCHAR(32),
    ADD COLUMN callback_verified      BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN callback_verified_at   TIMESTAMPTZ,
    ADD COLUMN callback_verified_by   VARCHAR(128);

COMMENT ON COLUMN daidzai.dai_emergency_request.callback_number IS
    'Callback number for a request; REQUIRED for public-anonymous intake (PD-3 dispatch gate).';
COMMENT ON COLUMN daidzai.dai_emergency_request.callback_verified IS
    'TRUE once a dispatcher/responder has reached the callback number; gates triage for AWAITING_CALLBACK requests.';

-- Partly-indexed lookup for the dispatcher callback worklist (public requests awaiting a call back).
CREATE INDEX idx_dai_request_awaiting_callback
    ON daidzai.dai_emergency_request (tenant_id, created_at)
    WHERE status = 'AWAITING_CALLBACK';
