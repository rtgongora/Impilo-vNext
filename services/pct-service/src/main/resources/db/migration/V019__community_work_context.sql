-- Community work-context (PCT) — the sixth work context (journey §5.5), previously Missing.
--
-- The provider-app already has outreach screens (household list, screening, follow-up, dashboard) that were
-- NotWired to any backend context. PCT is the SoR for the clinical encounter, so the community visit /
-- household registry live here. Records support GPS capture and OFFLINE reconciliation: each record carries a
-- client-generated offline id so a batch captured offline can be replayed idempotently on reconnect.

-- Household registry ----------------------------------------------------------
CREATE TABLE pct_households (
    household_id      UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    facility_id       UUID            NOT NULL,
    head_of_household VARCHAR(255)    NOT NULL,
    address           TEXT,
    gps_latitude      DOUBLE PRECISION,
    gps_longitude     DOUBLE PRECISION,
    risk_category     VARCHAR(32)     NOT NULL DEFAULT 'STANDARD',
    members           JSONB           NOT NULL DEFAULT '[]'::jsonb,  -- denormalised member roster (patient refs)
    last_visit_date   DATE,
    next_visit_date   DATE,
    offline_id        VARCHAR(128),   -- client-generated id for offline idempotency
    registered_by     VARCHAR(255)    NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_households_facility ON pct_households (tenant_id, facility_id);
CREATE UNIQUE INDEX uq_pct_households_offline ON pct_households (tenant_id, offline_id) WHERE offline_id IS NOT NULL;

-- Community visits ------------------------------------------------------------
CREATE TABLE pct_community_visits (
    visit_id          UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    household_id      UUID            NOT NULL,
    facility_id       UUID,
    visit_type        VARCHAR(64)     NOT NULL DEFAULT 'HOUSEHOLD_VISIT',
    status            VARCHAR(32)     NOT NULL DEFAULT 'COMPLETED',  -- PLANNED | COMPLETED | MISSED
    findings          TEXT,
    referrals         JSONB           NOT NULL DEFAULT '[]'::jsonb,
    gps_latitude      DOUBLE PRECISION,
    gps_longitude     DOUBLE PRECISION,
    visit_date        DATE            NOT NULL DEFAULT CURRENT_DATE,
    captured_offline  BOOLEAN         NOT NULL DEFAULT FALSE,
    offline_id        VARCHAR(128),
    visited_by        VARCHAR(255)    NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_community_visits_household ON pct_community_visits (tenant_id, household_id);
CREATE UNIQUE INDEX uq_pct_community_visits_offline ON pct_community_visits (tenant_id, offline_id) WHERE offline_id IS NOT NULL;

-- Community screenings --------------------------------------------------------
CREATE TABLE pct_community_screenings (
    screening_id      UUID            PRIMARY KEY,
    tenant_id         UUID            NOT NULL,
    household_id      UUID,
    visit_id          UUID,
    patient_id        VARCHAR(128)    NOT NULL,
    screening_type    VARCHAR(64)     NOT NULL,
    results           JSONB           NOT NULL DEFAULT '{}'::jsonb,
    referral_made     BOOLEAN         NOT NULL DEFAULT FALSE,
    captured_offline  BOOLEAN         NOT NULL DEFAULT FALSE,
    offline_id        VARCHAR(128),
    screened_by       VARCHAR(255)    NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);
CREATE INDEX idx_pct_community_screenings_patient ON pct_community_screenings (tenant_id, patient_id);
CREATE UNIQUE INDEX uq_pct_community_screenings_offline ON pct_community_screenings (tenant_id, offline_id) WHERE offline_id IS NOT NULL;

COMMENT ON TABLE pct_households IS 'Community work-context household registry (PCT). Offline-id supports idempotent reconciliation.';
COMMENT ON TABLE pct_community_visits IS 'Community/outreach visits (PCT). captured_offline + offline_id support sync-on-reconnect.';
COMMENT ON TABLE pct_community_screenings IS 'Community screening records (PCT). Offline-id supports idempotent reconciliation.';
