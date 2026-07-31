-- W14-E MPDSR review lifecycle — maternal/perinatal death surveillance & response (Rito SoR).
-- Confidential review instrument; facility-readiness prompts are firewalled (see mpdsr-review-contract.md).

CREATE TABLE rito.rit_mpdsr_review (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    case_id             UUID NOT NULL REFERENCES rito.rit_case(id),
    death_case_id       UUID NOT NULL,
    near_miss_ref       VARCHAR(128),
    who_classification  VARCHAR(64),
    review_level        VARCHAR(32) NOT NULL DEFAULT 'FACILITY',
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    facility_id         UUID,
    district_id         UUID,
    trigger_flags       VARCHAR(256),
    opened_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_mpdsr_review_death_case UNIQUE (tenant_id, death_case_id)
);
CREATE INDEX idx_rit_mpdsr_review_case ON rito.rit_mpdsr_review (case_id);
CREATE INDEX idx_rit_mpdsr_review_status ON rito.rit_mpdsr_review (tenant_id, status);

CREATE TABLE rito.rit_mpdsr_committee_meeting (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    review_id           UUID NOT NULL REFERENCES rito.rit_mpdsr_review(id) ON DELETE CASCADE,
    meeting_at          TIMESTAMPTZ NOT NULL,
    review_level        VARCHAR(32) NOT NULL,
    chair_actor_id      VARCHAR(128),
    members_json        JSONB NOT NULL DEFAULT '[]'::jsonb,
    minutes_summary     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_rit_mpdsr_meeting_review ON rito.rit_mpdsr_committee_meeting (review_id);

CREATE TABLE rito.rit_mpdsr_finding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    review_id           UUID NOT NULL REFERENCES rito.rit_mpdsr_review(id) ON DELETE CASCADE,
    delay_type          VARCHAR(48) NOT NULL,
    modifiable_factor   VARCHAR(512) NOT NULL,
    narrative           TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_rit_mpdsr_finding_delay CHECK (
        delay_type IN ('TYPE1_DECIDING_TO_SEEK', 'TYPE2_REACHING', 'TYPE3_RECEIVING')
    )
);
CREATE INDEX idx_rit_mpdsr_finding_review ON rito.rit_mpdsr_finding (review_id);

-- Facility-readiness prompt raised from a review. Internal review_id is stored for audit only;
-- outbound/task payloads MUST NOT carry review attribution (enforced in MpdsrReadinessFirewall).
CREATE TABLE rito.rit_mpdsr_readiness_task (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    review_id           UUID NOT NULL REFERENCES rito.rit_mpdsr_review(id) ON DELETE CASCADE,
    facility_id         UUID NOT NULL,
    task_reference      VARCHAR(48) NOT NULL,
    payload_json        JSONB NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    due_at              TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rit_mpdsr_readiness_task_ref UNIQUE (tenant_id, task_reference)
);
CREATE INDEX idx_rit_mpdsr_readiness_task_review ON rito.rit_mpdsr_readiness_task (review_id);
