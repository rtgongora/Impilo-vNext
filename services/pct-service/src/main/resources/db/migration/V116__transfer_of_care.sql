-- =============================================================================================
-- V116 — transfer of care (brief.md §14, §23.10)
--
-- V114 made ownership an explicit, NOT NULL column on every consultation, and made answering
-- incapable of changing it. A consultation may RECOMMEND a takeover (takeover_recommended); it
-- cannot perform one. That left a hole: nothing else performed one either. The act of taking over
-- a patient still happened outside the system, so the ownership column could only ever say what it
-- said at request time.
--
-- This table is that act. It mirrors the emergency acceptance handshake (V204): responsibility
-- moves ONLY on a write by the receiving service, carrying that service's OWN record id
-- (accepting_ref). Requesting a transfer, recommending one on a consultation answer, and declining
-- one never move owning_service. Silence and timeout never discharge either — there is no EXPIRED
-- status here; an unresolved request stays PENDING until someone accepts, declines, or withdraws it.
--
-- Do not confuse this with:
--   * consultation ACCEPT (REQUESTED → ACCEPTED) — that accepts the *question*, not the patient
--   * emergency_handover (V204) — emergency episode SoR; different object
--   * inpatient shift handover / ward transfer — staff SBAR and bed logistics, not service ownership
-- =============================================================================================

CREATE TABLE pct_care_transfers (
    transfer_id          UUID            PRIMARY KEY,
    tenant_id            UUID            NOT NULL,
    subject_cpid         VARCHAR(64)     NOT NULL,

    -- care-continuum-doctrine CC-5.
    journey_id           UUID,
    encounter_id         UUID,

    -- Optional link to the consultation that recommended (or prompted) this transfer. Nullable:
    -- a transfer can stand alone; most will follow a takeover_recommended answer.
    consultation_id      UUID            REFERENCES pct_consultations(consultation_id),

    from_service         VARCHAR(64)     NOT NULL,
    to_service           VARCHAR(64)     NOT NULL,

    requested_by         VARCHAR(255)    NOT NULL,
    requested_at         TIMESTAMPTZ     NOT NULL DEFAULT now(),
    reason               TEXT            NOT NULL,

    status               VARCHAR(24)     NOT NULL DEFAULT 'PENDING',

    -- THE INVARIANT. accepting_ref is the receiving service's OWN record id (a surgical episode,
    -- an admission, a specialty enrolment). ACCEPTED without one is unrepresentable.
    accepted_by          VARCHAR(255),
    accepted_at          TIMESTAMPTZ,
    accepting_ref        VARCHAR(128),

    declined_by          VARCHAR(255),
    declined_at          TIMESTAMPTZ,
    decline_reason       TEXT,

    withdrawn_by         VARCHAR(255),
    withdrawn_at         TIMESTAMPTZ,

    created_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT pct_care_transfers_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL
    ),
    CONSTRAINT pct_care_transfers_reason_check CHECK (length(btrim(reason)) > 0),
    CONSTRAINT pct_care_transfers_parties_check CHECK (
        from_service <> to_service AND length(btrim(from_service)) > 0 AND length(btrim(to_service)) > 0
    ),
    CONSTRAINT pct_care_transfers_status_check CHECK (status IN (
        'PENDING',     -- asked; nobody has accepted; from_service still holds the patient
        'ACCEPTED',    -- receiving service wrote back with its own record id — ownership moves
        'DECLINED',    -- receiving service refused; from_service keeps the patient
        'WITHDRAWN'    -- requester cancelled before acceptance
    )),
    CONSTRAINT pct_care_transfers_accepted_pair CHECK (
        status <> 'ACCEPTED'
        OR (accepted_by IS NOT NULL AND accepted_at IS NOT NULL
            AND accepting_ref IS NOT NULL AND length(btrim(accepting_ref)) > 0)
    ),
    CONSTRAINT pct_care_transfers_declined_pair CHECK (
        status <> 'DECLINED'
        OR (declined_by IS NOT NULL AND declined_at IS NOT NULL
            AND decline_reason IS NOT NULL AND length(btrim(decline_reason)) > 0)
    ),
    CONSTRAINT pct_care_transfers_withdrawn_pair CHECK (
        status <> 'WITHDRAWN'
        OR (withdrawn_by IS NOT NULL AND withdrawn_at IS NOT NULL)
    )
);

-- At most one live request to a given receiving service for a given patient. Two simultaneous
-- pending transfers to SURGERY for the same person is the double-request hazard, not a feature.
CREATE UNIQUE INDEX uq_pct_care_transfers_pending_to
    ON pct_care_transfers (tenant_id, subject_cpid, to_service)
    WHERE status = 'PENDING';

-- At most one live request per consultation that prompted it.
CREATE UNIQUE INDEX uq_pct_care_transfers_pending_consultation
    ON pct_care_transfers (tenant_id, consultation_id)
    WHERE status = 'PENDING' AND consultation_id IS NOT NULL;

CREATE INDEX idx_pct_care_transfers_subject
    ON pct_care_transfers (tenant_id, subject_cpid, requested_at DESC);

CREATE INDEX idx_pct_care_transfers_pending_inbox
    ON pct_care_transfers (tenant_id, to_service, requested_at)
    WHERE status = 'PENDING';

COMMENT ON TABLE pct_care_transfers IS
    'Transfer of care (brief.md §14 / §23.10). Ownership of the patient moves ONLY when the '
    'receiving service accepts with its own record id (accepting_ref). A consultation may recommend '
    'a takeover; this table is the act that performs one. Requesting, declining and withdrawing never '
    'move owning_service on pct_consultations.';

COMMENT ON COLUMN pct_care_transfers.accepting_ref IS
    'The receiving service''s OWN record id — a surgical episode, an admission, a specialty '
    'enrolment. This is the handshake. ACCEPTED without one is unrepresentable '
    '(pct_care_transfers_accepted_pair).';
