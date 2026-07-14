-- scheduling-service V002 — surgical waitlist (Wave 2 elective theatre)
-- The waitlist is the single owner of "who is waiting for surgery". It is fed
-- ONLY by referral.surgical.accepted (idempotent by referral_id) and carries a
-- reference back to the referral — never a copy of the clinical detail.
-- wait_days and escalation_state are ALWAYS computed at read (never stored),
-- so the escalation view can never go stale.

CREATE TABLE scheduling.surgical_waitlist_entry (
    id                          UUID            PRIMARY KEY,
    tenant_id                   UUID            NOT NULL,
    referral_id                 UUID            NOT NULL,   -- reference to referral.surgical_referral
    patient_id                  VARCHAR(255)    NOT NULL,
    zibo                        VARCHAR(64),                -- intended procedure code
    clinical_priority           VARCHAR(8)      NOT NULL DEFAULT 'P4',  -- P1 | P2 | P3 | P4
    target_timeframe_days       INTEGER         NOT NULL DEFAULT 90,
    listed_at                   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    target_date                 DATE,
    special_requirements        JSONB           NOT NULL DEFAULT '{}'::jsonb,
    status                      VARCHAR(16)     NOT NULL DEFAULT 'WAITING',
        -- WAITING | SCHEDULED | REMOVED | DEFERRED | CANCELLED
    scheduled_or_list_item_id   UUID,           -- reference to scheduling.or_list_item
    history                     JSONB           NOT NULL DEFAULT '[]'::jsonb,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_waitlist_status
        CHECK (status IN ('WAITING','SCHEDULED','REMOVED','DEFERRED','CANCELLED')),
    CONSTRAINT chk_waitlist_priority
        CHECK (clinical_priority IN ('P1','P2','P3','P4'))
);

CREATE INDEX idx_waitlist_tenant_status
    ON scheduling.surgical_waitlist_entry (tenant_id, status, clinical_priority, target_date);

-- Idempotent placement: one active waitlist entry per referral. A redelivered
-- referral.surgical.accepted event (at-least-once Kafka) cannot create a duplicate.
CREATE UNIQUE INDEX uq_waitlist_active_referral
    ON scheduling.surgical_waitlist_entry (tenant_id, referral_id)
    WHERE status IN ('WAITING','DEFERRED','SCHEDULED');
