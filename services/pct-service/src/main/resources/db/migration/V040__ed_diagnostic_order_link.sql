-- =====================================================================================
-- PCT V040 — ED diagnostic order → trauma-episode LINK (W5, G1.9).
--
-- PCT places trauma ED diagnostics on OROS with encounter_ref = trauma_episode_id, and keeps this
-- LINK row (oros_order_id → trauma_episode_id + visit). This is the AUTHORITATIVE correlation used to
-- reconcile an OROS CRITICAL result back onto the episode — necessary because the OROS critical event
-- payload OMITS encounter_ref (an honest Gate-1 limitation; enriching the OROS payload is a post-gate
-- follow-up requiring an oros change we deliberately avoid). PCT reads OROS via its API only.
-- =====================================================================================

CREATE TABLE ed_diagnostic_order (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID NOT NULL,
    visit_id           UUID,
    trauma_episode_id  UUID,
    oros_order_id      VARCHAR(64) NOT NULL,
    order_type         VARCHAR(24),                 -- LAB / IMAGING / ...
    test_code          VARCHAR(64),
    status             VARCHAR(24) NOT NULL DEFAULT 'PLACED',  -- PLACED / CRITICAL / ACKED
    oros_result_id     VARCHAR(64),
    critical_reason    TEXT,
    acked_at           TIMESTAMPTZ,
    safety_emitted_at  TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ed_diagnostic_order_oros UNIQUE (tenant_id, oros_order_id)
);

CREATE INDEX idx_ed_diagnostic_order_episode ON ed_diagnostic_order (trauma_episode_id) WHERE trauma_episode_id IS NOT NULL;
CREATE INDEX idx_ed_diagnostic_order_oros ON ed_diagnostic_order (oros_order_id);
