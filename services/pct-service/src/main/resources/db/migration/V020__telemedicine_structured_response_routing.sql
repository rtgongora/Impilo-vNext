-- Telemedicine completeness (journey 7): structured response + routing pools.
--
-- Stage 6 (Response Package) needs a STRUCTURED clinical response (diagnosis / action plan / red flags /
-- follow-up) per shared read-model C7, not just free-text. Stage 3 (Routing & Worklists) needs explicit
-- team/pool/on-call routing, not only a single named provider. These columns add those, keeping the existing
-- freeform routing_target / responses for backward compatibility.
--
-- Real-time media (WebRTC/WS) remains intentionally absent and fails closed (501) — not added here.

ALTER TABLE pct_referrals
    ADD COLUMN IF NOT EXISTS structured_response JSONB,        -- C7 response: {diagnosis, actionPlan, redFlags, followUp}
    ADD COLUMN IF NOT EXISTS routing_kind        VARCHAR(32),  -- PROVIDER | SERVICE | POOL | ON_CALL | FACILITY
    ADD COLUMN IF NOT EXISTS routing_pool_id     VARCHAR(128), -- target pool / on-call roster id
    ADD COLUMN IF NOT EXISTS routed_at           TIMESTAMPTZ;

COMMENT ON COLUMN pct_referrals.structured_response IS
    'Stage-6 structured clinical response (C7): diagnosis, action plan, red flags, follow-up.';
COMMENT ON COLUMN pct_referrals.routing_kind IS
    'Stage-3 routing target kind: PROVIDER | SERVICE | POOL | ON_CALL | FACILITY.';
