-- =============================================================================
-- Varapi V025 — FACILITY_ACCESS self-service requests (PJ4/PJ16, D-P7; W5)
--
-- "Being a valid professional does not authorise access at every institution."
-- A linked provider self-requests access to WORK at a facility (permanent
-- posting, rotation, locum, outreach, telemed, specialist pool, supervisory,
-- training) on the existing durable access-request rail. The facility side
-- approves (PENDING_FACILITY_REVIEW); an APPROVED request is materialised as a
-- Vashandi assignment by the workforce plane (event-driven), which is what the
-- work-session lane actually proves against. Temporary engagements MUST carry
-- an expiry — enforced again at the Vashandi assignment (V008).
-- =============================================================================

ALTER TABLE varapi.provider_access_request
    ADD COLUMN IF NOT EXISTS facility_id        UUID,
    ADD COLUMN IF NOT EXISTS engagement_type    VARCHAR(32),
    ADD COLUMN IF NOT EXISTS access_valid_from  DATE,
    ADD COLUMN IF NOT EXISTS access_valid_to    DATE;

COMMENT ON COLUMN varapi.provider_access_request.facility_id IS
    'FACILITY_ACCESS requests: the canonical tuso facility_uuid the provider requests to work at.';
COMMENT ON COLUMN varapi.provider_access_request.engagement_type IS
    'FACILITY_ACCESS requests: PERMANENT | ROTATION | LOCUM | OUTREACH | TELEMED | SPECIALIST_POOL | SUPERVISORY | TRAINING.';

CREATE INDEX IF NOT EXISTS idx_provider_access_request_facility
    ON varapi.provider_access_request (tenant_id, facility_id)
    WHERE facility_id IS NOT NULL;
