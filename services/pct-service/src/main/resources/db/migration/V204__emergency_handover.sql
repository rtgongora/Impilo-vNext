-- The acceptance handshake (W9). The single most important new table in this pack.
--
-- V200 already declares the invariant in its CHECK: state <> 'CLOSED_HANDED_OVER' OR
-- handover_id IS NOT NULL. But nothing in this codebase has ever set handover_id — the FSM's most
-- consequential transition (OPEN_AWAITING_ACCEPTANCE -> CLOSED_HANDED_OVER) has been reachable in
-- the enum since W2 and unreachable in practice ever since, backstopped only by a constraint nobody
-- could satisfy. This migration is what makes it real.
--
-- THE ACCEPTANCE-HANDSHAKE INVARIANT: emergency responsibility transfers ONLY on a write by the
-- accepting party, carrying the accepting party's OWN record id (accepting_ref). Requesting, paging,
-- ordering, transporting and documenting are all still OPEN_AWAITING_ACCEPTANCE. A referral, an
-- admission request or a theatre request must never end emergency responsibility on its own — that
-- is precisely the defect this table exists to prevent, and it is enforced below as a CHECK, not a
-- convention: chk_eh_accepted_pair makes an ACCEPTED row without accepting_ref unrepresentable.
--
-- No timeout may ever discharge responsibility. DECLINED and EXPIRED both return the episode to
-- OPEN_IN_CARE (never to a closed state) — a patient nobody has accepted is still emergency's
-- patient. There is deliberately no automatic expiry sweep in this migration: expiry is an explicit
-- action (by a clinician or a later scheduled job), never a silent state change.

CREATE TABLE pct.emergency_handover (
    handover_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID        NOT NULL,

    -- No ON DELETE CASCADE — episodes are never deleted (only merged, per V200), so this is a
    -- consistency statement, not a live concern: a handover must never be tidied away with its episode.
    episode_id            UUID        NOT NULL REFERENCES pct.emergency_episode(episode_id),

    -- Who is being asked to accept. A closed vocabulary because a wrong-side-of-string comparison
    -- ("icu" vs "ICU" vs "Icu") is exactly the kind of silent miss this pack keeps finding elsewhere.
    target_type           VARCHAR(32) NOT NULL,
    target_service        VARCHAR(64),
    target_facility_id    UUID,

    requested_by          VARCHAR(128) NOT NULL,
    requested_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_reason        TEXT,

    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- THE INVARIANT. accepting_ref is the accepting party's OWN record id: an admission id for a
    -- ward/ICU/HDU acceptance, a procedure_episode id when theatre's creation of it IS the
    -- acceptance, an mh_referral id for a mental-health acceptance. A status of ACCEPTED without one
    -- is unrepresentable by chk_eh_accepted_pair below.
    accepted_by           VARCHAR(128),
    accepted_at           TIMESTAMPTZ,
    accepting_ref         VARCHAR(128),
    accepting_service     VARCHAR(64),

    declined_by           VARCHAR(128),
    declined_at           TIMESTAMPTZ,
    decline_reason        TEXT,

    expired_at            TIMESTAMPTZ,
    expired_by            VARCHAR(128),

    -- Link, not ownership: rito owns the safety case opened when a handover expires unaccepted.
    rito_case_ref         VARCHAR(128),

    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_eh_target_type CHECK (target_type IN (
        'ADMISSION', 'THEATRE', 'MENTAL_HEALTH', 'INTERFACILITY_TRANSFER', 'OTHER_SERVICE')),

    CONSTRAINT chk_eh_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'EXPIRED')),

    -- THE INVARIANT, as a schema property. Every accepting field is required together, and
    -- accepting_ref specifically cannot be satisfied by an empty string masquerading as a value.
    CONSTRAINT chk_eh_accepted_pair CHECK (
        status <> 'ACCEPTED' OR (
            accepted_by IS NOT NULL AND accepted_at IS NOT NULL
            AND accepting_ref IS NOT NULL AND btrim(accepting_ref) <> '')),

    CONSTRAINT chk_eh_declined_pair CHECK (
        status <> 'DECLINED' OR (
            declined_by IS NOT NULL AND declined_at IS NOT NULL
            AND decline_reason IS NOT NULL AND btrim(decline_reason) <> '')),

    CONSTRAINT chk_eh_expired_pair CHECK (
        status <> 'EXPIRED' OR expired_at IS NOT NULL)
);

-- Anti-noise, and more than that here: at most ONE live request per episode. Two simultaneous
-- pending handovers for the same patient (e.g. both ICU and theatre paged) is the double-request
-- hazard the acceptance handshake exists to prevent, not a feature to support.
CREATE UNIQUE INDEX uq_emergency_handover_pending
    ON pct.emergency_handover (tenant_id, episode_id)
    WHERE status = 'PENDING';

CREATE INDEX idx_emergency_handover_episode
    ON pct.emergency_handover (tenant_id, episode_id, requested_at DESC);

-- The responder board for the accepting side: open requests by target, oldest first.
CREATE INDEX idx_emergency_handover_pending_by_target
    ON pct.emergency_handover (tenant_id, target_type, requested_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE pct.emergency_handover IS
    'The acceptance handshake. Responsibility transfers only when the accepting party writes back '
    'with its own record id (accepting_ref) — never on a request, a page, or a timeout. Satisfies '
    'the emergency_episode.handover_id CHECK added in V200, which nothing could set before this table '
    'existed.';
COMMENT ON COLUMN pct.emergency_handover.accepting_ref IS
    'The accepting party''s OWN record id: an admission id, a procedure_episode id (theatre''s '
    'creation of it IS the acceptance), an mh_referral id. This is the handshake — a status of '
    'ACCEPTED without one is unrepresentable.';
