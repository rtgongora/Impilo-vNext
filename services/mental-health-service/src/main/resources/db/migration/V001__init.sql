-- Mental Health service, W13 (PO ruling R8: build a real service, not a typed refusal).
--
-- Care Continuum COMPONENT (continuum_parent: pct-service). This service owns psychiatric
-- operational and phase truth and nothing else: it does NOT own the admission decision (that
-- stays PCT's, via mh_admission_request raising to the existing pct_admission_id handshake), does
-- NOT own person-level longitudinal registries (PCT's), and does NOT fork a second care continuum.
--
-- The spine: pct's emergency_handover(target_type='MENTAL_HEALTH') is the request; mh_referral is
-- this service's own record of that request and its own decision. Acceptance transfers
-- responsibility only when THIS service writes back to pct's handover with accepting_ref =
-- mh_referral.id — the same acceptance-handshake invariant every other counterparty in this pack
-- honours (see pct's emergency_handover doctrine). episode_id and handover_id below are plain
-- correlation columns, deliberately NOT foreign keys — pct's schema lives in a different database.

-- ── mh_referral — the accepting side of the handover ──────────────────────────────────────────
CREATE TABLE mh_referral (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    facility_id       UUID NOT NULL,
    episode_id        UUID NOT NULL,
    handover_id       UUID NOT NULL,
    subject_cpid      VARCHAR(64),
    status            VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    request_reason    TEXT,
    requested_by      VARCHAR(128) NOT NULL,
    requested_at      TIMESTAMPTZ NOT NULL,
    reviewed_by       VARCHAR(128),
    reviewed_at       TIMESTAMPTZ,
    decline_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhr_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED')),
    -- Value-or-reason: a decline with no reason is unauditable, and an accept with no reviewer is
    -- indistinguishable from a status flipped by nobody.
    CONSTRAINT chk_mhr_decline_reason CHECK (status <> 'DECLINED' OR decline_reason IS NOT NULL),
    CONSTRAINT chk_mhr_reviewed CHECK (
        status = 'PENDING' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    -- One referral per handover: a redelivered request must not mint a second competing decision.
    CONSTRAINT uq_mhr_handover UNIQUE (tenant_id, handover_id)
);

CREATE INDEX idx_mhr_tenant_status ON mh_referral (tenant_id, status, requested_at DESC);
CREATE INDEX idx_mhr_episode ON mh_referral (tenant_id, episode_id);

COMMENT ON TABLE mh_referral IS
    'The accepting side of a psychiatric emergency handover. One row per pct emergency_handover '
    '(target_type=MENTAL_HEALTH); episode_id/handover_id correlate to pct.emergency_episode/'
    'emergency_handover across the service boundary, deliberately not foreign keys.';

-- ── mh_assessment — risk, capacity, mental-state examination ──────────────────────────────────
CREATE TABLE mh_assessment (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    referral_id           UUID NOT NULL REFERENCES mh_referral(id),
    assessment_type       VARCHAR(24) NOT NULL DEFAULT 'INITIAL',
    mental_state_exam     TEXT,
    capacity_assessed     BOOLEAN NOT NULL DEFAULT FALSE,
    -- Nompilo doctrine: capacity is never assumed present or absent by default; an unassessed
    -- capacity is a distinct, expressible state from an assessed-and-present one.
    capacity_outcome      VARCHAR(24),
    assessed_by           VARCHAR(128) NOT NULL,
    assessed_at           TIMESTAMPTZ NOT NULL,
    notes                 TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mha_type CHECK (assessment_type IN ('INITIAL', 'REVIEW', 'DISCHARGE')),
    CONSTRAINT chk_mha_capacity_outcome CHECK (
        capacity_outcome IS NULL
        OR capacity_outcome IN ('HAS_CAPACITY', 'LACKS_CAPACITY', 'FLUCTUATING')),
    CONSTRAINT chk_mha_capacity_consistency CHECK (
        (capacity_assessed = TRUE AND capacity_outcome IS NOT NULL)
        OR (capacity_assessed = FALSE AND capacity_outcome IS NULL))
);

CREATE INDEX idx_mha_referral ON mh_assessment (tenant_id, referral_id, assessed_at DESC);

-- ── mh_risk_formulation — linked to an assessment ──────────────────────────────────────────────
CREATE TABLE mh_risk_formulation (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    assessment_id     UUID NOT NULL REFERENCES mh_assessment(id),
    risk_to_self      VARCHAR(16) NOT NULL,
    risk_to_others    VARCHAR(16) NOT NULL,
    risk_summary      TEXT NOT NULL,
    formulated_by     VARCHAR(128) NOT NULL,
    formulated_at     TIMESTAMPTZ NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhrf_risk_self CHECK (risk_to_self IN ('LOW', 'MODERATE', 'HIGH')),
    CONSTRAINT chk_mhrf_risk_others CHECK (risk_to_others IN ('LOW', 'MODERATE', 'HIGH'))
);

CREATE INDEX idx_mhrf_assessment ON mh_risk_formulation (tenant_id, assessment_id);

-- ── mh_safety_plan — linked to a referral ──────────────────────────────────────────────────────
CREATE TABLE mh_safety_plan (
    id                        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                 UUID NOT NULL,
    referral_id               UUID NOT NULL REFERENCES mh_referral(id),
    warning_signs             TEXT,
    coping_strategies         TEXT,
    support_contacts          TEXT,
    means_restriction_notes   TEXT,
    created_by                VARCHAR(128) NOT NULL,
    created_at                TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_at               TIMESTAMPTZ
);

CREATE INDEX idx_mhsp_referral ON mh_safety_plan (tenant_id, referral_id, created_at DESC);

-- ── mh_involuntary_episode — statutory detention/observation ──────────────────────────────────
--
-- legal_basis is deliberately FREE TEXT, not a closed vocabulary. This pack could not verify
-- Zimbabwe's Mental Health Act detention/observation category names against a citable source, and
-- this session's own standing rule is to never invent a classification that isn't verified to
-- exist nationally (see the trauma-centre-level and IITT-time-target precedents elsewhere in this
-- pack). Requiring the clinician to NAME the actual legal instrument/section in free text is the
-- honest alternative to a fabricated picklist.
CREATE TABLE mh_involuntary_episode (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    referral_id       UUID NOT NULL REFERENCES mh_referral(id),
    legal_basis       TEXT NOT NULL,
    authorised_by     VARCHAR(128) NOT NULL,
    authorised_at     TIMESTAMPTZ NOT NULL,
    review_due_at     TIMESTAMPTZ,
    status            VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    ended_at          TIMESTAMPTZ,
    ended_reason      TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhie_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_mhie_closure CHECK (
        (status = 'OPEN' AND ended_at IS NULL)
        OR (status = 'CLOSED' AND ended_at IS NOT NULL AND ended_reason IS NOT NULL))
);

-- At most one OPEN involuntary episode per referral — a second concurrent detention on the same
-- referral would be two conflicting legal statuses for one patient.
CREATE UNIQUE INDEX uq_mhie_open_per_referral
    ON mh_involuntary_episode (tenant_id, referral_id)
    WHERE status = 'OPEN';

CREATE INDEX idx_mhie_referral ON mh_involuntary_episode (tenant_id, referral_id);

COMMENT ON COLUMN mh_involuntary_episode.legal_basis IS
    'Free text, deliberately not a closed vocabulary — this pack could not verify a citable list '
    'of Zimbabwe Mental Health Act detention categories. The clinician names the actual legal '
    'instrument/section rather than picking from a fabricated list.';

-- ── mh_restraint_event — a restraint-REDUCTION register, not a restraint log ──────────────────
--
-- de_escalation_attempted is NOT NULL by design: value-or-reason, the same discipline this pack
-- uses everywhere a mandatory-content gate matters (R12). A restraint row with no record of what
-- was tried first is exactly what a reduction register exists to make impossible to write.
CREATE TABLE mh_restraint_event (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    referral_id                 UUID NOT NULL REFERENCES mh_referral(id),
    restraint_type              VARCHAR(32) NOT NULL,
    reason                      TEXT NOT NULL,
    de_escalation_attempted     TEXT NOT NULL,
    initiated_by                VARCHAR(128) NOT NULL,
    initiated_at                TIMESTAMPTZ NOT NULL,
    ended_at                    TIMESTAMPTZ,
    reviewed_by                 VARCHAR(128),
    reviewed_at                 TIMESTAMPTZ,
    review_outcome              TEXT,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhre_type CHECK (restraint_type IN (
        'PHYSICAL', 'MECHANICAL', 'CHEMICAL', 'SECLUSION')),
    CONSTRAINT chk_mhre_review CHECK (
        (reviewed_by IS NULL AND reviewed_at IS NULL AND review_outcome IS NULL)
        OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_outcome IS NOT NULL))
);

CREATE INDEX idx_mhre_referral ON mh_restraint_event (tenant_id, referral_id, initiated_at DESC);
-- Every restraint still open (not ended) and not yet reviewed — the reduction register's own
-- operational queue.
CREATE INDEX idx_mhre_unreviewed
    ON mh_restraint_event (tenant_id, initiated_at)
    WHERE reviewed_at IS NULL;

COMMENT ON TABLE mh_restraint_event IS
    'A restraint-REDUCTION register, not a restraint log: every event carries a mandatory '
    'de-escalation-attempted record (value-or-reason, R12 discipline) and every event is queued '
    'for review until reviewed_by/_at/review_outcome are all recorded together.';

-- ── mh_admission_request — raises to PCT, never decides ────────────────────────────────────────
--
-- This table records that mental-health ASKED for admission; it never records an admission
-- decision. pct_admission_id is set only after PCT's own admission-handshake records a decision —
-- correlation only, not a foreign key (pct's schema lives in a different database), and its
-- absence is the honest default, not an error state.
CREATE TABLE mh_admission_request (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    referral_id           UUID NOT NULL REFERENCES mh_referral(id),
    requested_by          VARCHAR(128) NOT NULL,
    requested_at          TIMESTAMPTZ NOT NULL,
    reason                TEXT NOT NULL,
    target_facility_id    UUID,
    status                VARCHAR(16) NOT NULL DEFAULT 'RAISED',
    pct_admission_id      VARCHAR(26),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhar_status CHECK (status IN ('RAISED', 'ACKNOWLEDGED'))
);

CREATE INDEX idx_mhar_referral ON mh_admission_request (tenant_id, referral_id);

COMMENT ON TABLE mh_admission_request IS
    'Raises admission to PCT; never decides it. pct_admission_id correlates to PCT''s own '
    'admission-handshake record once PCT has decided — absent by default, not an error.';

-- ── mh_followup — scheduled follow-up ──────────────────────────────────────────────────────────
CREATE TABLE mh_followup (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    referral_id       UUID NOT NULL REFERENCES mh_referral(id),
    followup_type     VARCHAR(24) NOT NULL DEFAULT 'CLINIC_REVIEW',
    scheduled_at      TIMESTAMPTZ NOT NULL,
    scheduled_by      VARCHAR(128) NOT NULL,
    completed_at      TIMESTAMPTZ,
    outcome_notes     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_mhf_type CHECK (followup_type IN (
        'CLINIC_REVIEW', 'HOME_VISIT', 'PHONE_CHECK', 'COMMUNITY_TEAM'))
);

CREATE INDEX idx_mhf_referral ON mh_followup (tenant_id, referral_id);
CREATE INDEX idx_mhf_due ON mh_followup (tenant_id, scheduled_at) WHERE completed_at IS NULL;

-- ── mh_event_outbox — the standard transactional outbox ───────────────────────────────────────
CREATE TABLE mh_event_outbox (
    id                  BIGSERIAL       PRIMARY KEY,
    event_id            UUID            NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type      VARCHAR(64)     NOT NULL,
    aggregate_id        VARCHAR(255)    NOT NULL,
    event_type          VARCHAR(128)    NOT NULL,
    schema_version      VARCHAR(16)     NOT NULL DEFAULT '1.0',
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255)    NOT NULL,
    producer            VARCHAR(64)     NOT NULL DEFAULT 'mental-health-service',
    tenant_id           UUID            NOT NULL,
    pod_id              VARCHAR(64)     NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255)    NOT NULL,
    subject_type        VARCHAR(64)     NOT NULL,
    occurred_at         TIMESTAMPTZ     NOT NULL,
    payload_json        JSONB           NOT NULL,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    publish_error       TEXT,
    retry_count         INT             NOT NULL DEFAULT 0,
    CONSTRAINT uq_mh_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_mh_outbox_unpublished ON mh_event_outbox (created_at) WHERE published_at IS NULL;
