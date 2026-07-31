-- Wave 3 Phase D — NEW_PCT clerking facts where no SoR existed.
-- Append-only. CC-5 anchors (journey_id / encounter_id) where visit-scoped.
-- Safeguarding is confidential and must never be folded into social-history.

-- 1. Presenting concern + symptom timeline (ambulatory; ED chief complaint INTEGRATE via source)
CREATE TABLE pct_presenting_concerns (
    concern_id     UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    journey_id     VARCHAR(128),
    encounter_id   VARCHAR(128),
    concern_text   TEXT          NOT NULL,
    onset_at       TIMESTAMPTZ,
    timeline_json  TEXT,
    source         VARCHAR(32)   NOT NULL DEFAULT 'AMBULATORY',
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pct_presenting_concern_source_check CHECK (source IN ('AMBULATORY', 'ED_INTEGRATE')),
    CONSTRAINT pct_presenting_concern_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL)
);
CREATE INDEX idx_pct_presenting_concerns_subject
    ON pct_presenting_concerns (tenant_id, subject_cpid, created_at DESC);

-- 2. Systems review (ROS) keyed by visit
CREATE TABLE pct_systems_reviews (
    review_id      UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    journey_id     VARCHAR(128),
    encounter_id   VARCHAR(128),
    systems_json   TEXT          NOT NULL,
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pct_systems_review_anchor_check CHECK (
        journey_id IS NOT NULL OR encounter_id IS NOT NULL)
);
CREATE INDEX idx_pct_systems_reviews_subject
    ON pct_systems_reviews (tenant_id, subject_cpid, created_at DESC);

-- 3. Prior ICU history (longitudinal fact, not bed capability)
CREATE TABLE pct_prior_icu_history (
    entry_id       UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    approx_year    INTEGER,
    facility_note  VARCHAR(512),
    reason         TEXT,
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_prior_icu_subject
    ON pct_prior_icu_history (tenant_id, subject_cpid, created_at DESC);

-- 4. Infection exposure (clinical) — distinct from surveillance contact-tracing
CREATE TABLE pct_infection_exposures (
    exposure_id    UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    journey_id     VARCHAR(128),
    encounter_id   VARCHAR(128),
    exposure_type  VARCHAR(128)  NOT NULL,
    detail         TEXT,
    exposed_on     DATE,
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_infection_exposures_subject
    ON pct_infection_exposures (tenant_id, subject_cpid, created_at DESC);

-- 5. Cognition + mood ambulatory screens — score XOR absent reason; null score = unassessed
CREATE TABLE pct_cognition_mood_screens (
    screen_id            UUID          PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    subject_cpid         VARCHAR(128)  NOT NULL,
    journey_id           VARCHAR(128),
    encounter_id         VARCHAR(128),
    instrument           VARCHAR(64)   NOT NULL,
    score                NUMERIC(10,2),
    score_absent_reason  VARCHAR(512),
    mood_note            TEXT,
    recorded_by          VARCHAR(255)  NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pct_cognition_mood_score_xor CHECK (
        (score IS NOT NULL AND score_absent_reason IS NULL)
        OR (score IS NULL AND score_absent_reason IS NOT NULL)
        OR (score IS NULL AND score_absent_reason IS NULL)
    )
);
CREATE INDEX idx_pct_cognition_mood_subject
    ON pct_cognition_mood_screens (tenant_id, subject_cpid, created_at DESC);

-- 6. Genetic risk (general adult) — separate from preconception-only fields
CREATE TABLE pct_genetic_risks (
    risk_id        UUID          PRIMARY KEY,
    tenant_id      UUID          NOT NULL,
    subject_cpid   VARCHAR(128)  NOT NULL,
    risk_factor    VARCHAR(255)  NOT NULL,
    detail         TEXT,
    recorded_by    VARCHAR(255)  NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_genetic_risks_subject
    ON pct_genetic_risks (tenant_id, subject_cpid, created_at DESC);

-- 7. Safeguarding disclosure — confidential lane; NEVER open social-history
CREATE TABLE pct_safeguarding_disclosures (
    disclosure_id         UUID          PRIMARY KEY,
    tenant_id             UUID          NOT NULL,
    subject_cpid          VARCHAR(128)  NOT NULL,
    journey_id            VARCHAR(128),
    encounter_id          VARCHAR(128),
    disclosure_summary    TEXT          NOT NULL,
    confidentiality_lane  VARCHAR(64)   NOT NULL DEFAULT 'SAFEGUARDING',
    recorded_by           VARCHAR(255)  NOT NULL,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT pct_safeguarding_lane_check CHECK (confidentiality_lane = 'SAFEGUARDING')
);
CREATE INDEX idx_pct_safeguarding_subject
    ON pct_safeguarding_disclosures (tenant_id, subject_cpid, created_at DESC);

COMMENT ON TABLE pct_safeguarding_disclosures IS
    'Confidential safeguarding disclosures. Must not be exposed via social-history routes.';
COMMENT ON TABLE pct_infection_exposures IS
    'Clinical infection exposure for clerking — not surveillance contact-tracing.';
