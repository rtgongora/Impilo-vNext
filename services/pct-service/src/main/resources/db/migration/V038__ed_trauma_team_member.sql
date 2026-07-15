-- =====================================================================================
-- PCT V038 — ED trauma-team roster members (real notify → ack → escalate).
--
-- Trauma pipeline W4: replace the jsonb team blob + PENDING→SENT paging with a first-class
-- per-member roster. On trauma activation, PCT resolves the on-call trauma team from VASHANDI
-- (read-only, via the on-duty virtual-pool endpoint — a projection over the workforce SoR, NOT a
-- parallel roster) and materialises one row per rostered member. Each member gets a real
-- notification delivery-intent (notification-service), an ack, and an escalation on no-ack timeout.
-- =====================================================================================

CREATE TABLE ed_trauma_team_member (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    trauma_id              UUID NOT NULL REFERENCES ed_trauma_activation (trauma_id) ON DELETE CASCADE,
    visit_id               UUID NOT NULL,
    trauma_episode_id      UUID,
    -- Resolved from VASHANDI on-duty pool membership (the designated trauma panel).
    workforce_profile_id   VARCHAR(128) NOT NULL,
    role                   VARCHAR(64),               -- seeded shift_type = trauma role (EM_PHYSICIAN, SURGEON …)
    shift_id               VARCHAR(128),
    -- Notification delivery-intent (notification-service ns_notifications id).
    notification_ref       VARCHAR(128),
    notified_at            TIMESTAMPTZ,
    -- Acknowledgement + escalation lifecycle.
    ack_status             VARCHAR(24) NOT NULL DEFAULT 'PENDING',  -- PENDING / ACKED / ESCALATED
    ack_at                 TIMESTAMPTZ,
    ack_by                 VARCHAR(128),
    escalated_at           TIMESTAMPTZ,
    escalation_ref         VARCHAR(128),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_ed_trauma_team_member UNIQUE (trauma_id, workforce_profile_id)
);

CREATE INDEX idx_ed_trauma_team_member_trauma ON ed_trauma_team_member (trauma_id);
CREATE INDEX idx_ed_trauma_team_member_ack ON ed_trauma_team_member (trauma_id, ack_status);
