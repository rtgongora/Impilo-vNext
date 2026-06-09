-- Citizen My Life + Health Connect (migrated from wellness-service into Simba).
-- Tables live in public schema for JDBC citizen/connect controllers.
SET search_path TO public, simba;

CREATE TABLE IF NOT EXISTS wellness_activities (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    activity_date   DATE NOT NULL DEFAULT CURRENT_DATE,
    steps           INTEGER DEFAULT 0,
    calories_burned INTEGER DEFAULT 0,
    active_minutes  INTEGER DEFAULT 0,
    distance_km     NUMERIC(6,2) DEFAULT 0,
    sleep_hours     NUMERIC(4,1),
    sleep_quality   VARCHAR(20),
    water_ml        INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_wellness_daily UNIQUE (tenant_id, patient_id, activity_date)
);

CREATE TABLE IF NOT EXISTS wellness_vitals_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    vital_type      VARCHAR(80) NOT NULL,
    value           NUMERIC(10,2) NOT NULL,
    unit            VARCHAR(20) NOT NULL,
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          VARCHAR(50) DEFAULT 'MANUAL',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_vitals_patient ON wellness_vitals_log (tenant_id, patient_id, measured_at DESC);

CREATE TABLE IF NOT EXISTS wellness_mood_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    mood_score      INTEGER NOT NULL CHECK (mood_score BETWEEN 1 AND 5),
    energy_level    INTEGER CHECK (energy_level BETWEEN 1 AND 5),
    stress_level    INTEGER CHECK (stress_level BETWEEN 1 AND 5),
    notes           TEXT,
    logged_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mood_patient ON wellness_mood_log (tenant_id, patient_id, logged_at DESC);

CREATE TABLE IF NOT EXISTS wellness_challenges (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    challenge_type  VARCHAR(50) NOT NULL DEFAULT 'STEPS',
    target_value    INTEGER NOT NULL,
    target_unit     VARCHAR(20) NOT NULL,
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    participant_count INTEGER DEFAULT 0,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wellness_challenge_participants (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    challenge_id    UUID NOT NULL REFERENCES wellness_challenges(id),
    patient_id      VARCHAR(255) NOT NULL,
    current_value   INTEGER DEFAULT 0,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_challenge_participant UNIQUE (challenge_id, patient_id)
);

CREATE TABLE IF NOT EXISTS health_wallets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL UNIQUE,
    balance         NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wallet_transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    wallet_id       UUID NOT NULL REFERENCES health_wallets(id),
    transaction_type VARCHAR(20) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    description     TEXT,
    reference       VARCHAR(100),
    status          VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wallet_transactions ON wallet_transactions (wallet_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wellness_connect_ingest_log (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) NOT NULL,
    patient_id           VARCHAR(255) NOT NULL,
    external_record_id   VARCHAR(255) NOT NULL,
    record_type          VARCHAR(64) NOT NULL,
    data_origin_platform VARCHAR(64),
    data_origin_package  VARCHAR(255),
    ingested_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hc_ingest UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_hc_ingest_patient_time
    ON wellness_connect_ingest_log (tenant_id, patient_id, ingested_at DESC);

ALTER TABLE wellness_connect_ingest_log
    ADD COLUMN IF NOT EXISTS payload JSONB;

CREATE TABLE IF NOT EXISTS wellness_sleep_segments (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(255) NOT NULL,
    patient_id            VARCHAR(255) NOT NULL,
    session_external_id   VARCHAR(255) NOT NULL,
    stage                 VARCHAR(32) NOT NULL,
    start_at              TIMESTAMPTZ NOT NULL,
    end_at                TIMESTAMPTZ NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sleep_seg_session
    ON wellness_sleep_segments (tenant_id, patient_id, session_external_id);

CREATE INDEX IF NOT EXISTS idx_sleep_seg_patient_time
    ON wellness_sleep_segments (tenant_id, patient_id, start_at DESC);

CREATE TABLE IF NOT EXISTS wellness_exercise_sessions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           VARCHAR(255) NOT NULL,
    patient_id          VARCHAR(255) NOT NULL,
    external_record_id  VARCHAR(255) NOT NULL,
    exercise_type       VARCHAR(128),
    title               TEXT,
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    energy_kcal         NUMERIC(10, 2),
    distance_m          NUMERIC(14, 2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_exercise_ext UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_exercise_patient_time
    ON wellness_exercise_sessions (tenant_id, patient_id, start_at DESC);

CREATE TABLE IF NOT EXISTS wellness_connect_extension (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            VARCHAR(255) NOT NULL,
    patient_id           VARCHAR(255) NOT NULL,
    external_record_id   VARCHAR(255) NOT NULL,
    canonical_type       VARCHAR(128) NOT NULL,
    start_at             TIMESTAMPTZ,
    end_at               TIMESTAMPTZ,
    payload              JSONB NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_hc_extension UNIQUE (tenant_id, patient_id, external_record_id)
);

CREATE INDEX IF NOT EXISTS idx_hc_extension_patient_time
    ON wellness_connect_extension (tenant_id, patient_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_hc_extension_type
    ON wellness_connect_extension (tenant_id, patient_id, canonical_type);

CREATE TABLE IF NOT EXISTS citizen_health_ids (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    health_id_number VARCHAR(50) NOT NULL UNIQUE,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    issued_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at      TIMESTAMPTZ,
    qr_code_data    TEXT,
    photo_url       VARCHAR(500),
    blood_type      VARCHAR(10),
    allergies_summary TEXT,
    emergency_contact_name VARCHAR(255),
    emergency_contact_phone VARCHAR(50),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_health_ids_patient ON citizen_health_ids (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS emergency_contacts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    relationship    VARCHAR(50) NOT NULL,
    phone           VARCHAR(50) NOT NULL,
    is_primary      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_emergency_contacts ON emergency_contacts (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS sos_alerts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    latitude        NUMERIC(10,7),
    longitude       NUMERIC(10,7),
    alert_type      VARCHAR(20) NOT NULL DEFAULT 'MEDICAL',
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    resolved_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS monitoring_devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    device_name     VARCHAR(255) NOT NULL,
    device_type     VARCHAR(50) NOT NULL,
    manufacturer    VARCHAR(100),
    model           VARCHAR(100),
    connection_type VARCHAR(20) NOT NULL DEFAULT 'BLUETOOTH',
    status          VARCHAR(20) NOT NULL DEFAULT 'PAIRED',
    last_sync_at    TIMESTAMPTZ,
    battery_level   INTEGER,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_monitoring_devices ON monitoring_devices (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS citizen_queue_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255) NOT NULL,
    facility_id     VARCHAR(255) NOT NULL,
    facility_name   VARCHAR(255),
    ticket_number   VARCHAR(20) NOT NULL,
    service_type    VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'WAITING',
    position        INTEGER,
    estimated_wait  INTEGER,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    called_at       TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_queue_tickets ON citizen_queue_tickets (tenant_id, patient_id, status);

CREATE TABLE IF NOT EXISTS wellness_clubs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    club_type       VARCHAR(50) NOT NULL DEFAULT 'FITNESS',
    category        VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
    member_count    INTEGER NOT NULL DEFAULT 0,
    max_members     INTEGER,
    is_public       BOOLEAN NOT NULL DEFAULT TRUE,
    meeting_schedule TEXT,
    location        VARCHAR(255),
    image_url       VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS wellness_club_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    club_id         UUID NOT NULL REFERENCES wellness_clubs(id),
    patient_id      VARCHAR(255) NOT NULL,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_club_member UNIQUE (club_id, patient_id)
);

CREATE TABLE IF NOT EXISTS professional_pages (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    provider_id     VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255) NOT NULL,
    title           VARCHAR(100),
    specialty       VARCHAR(100),
    bio             TEXT,
    photo_url       VARCHAR(500),
    facility_name   VARCHAR(255),
    years_experience INTEGER,
    rating          NUMERIC(3,2),
    review_count    INTEGER DEFAULT 0,
    is_accepting    BOOLEAN NOT NULL DEFAULT TRUE,
    languages       TEXT[],
    qualifications  TEXT[],
    consultation_fee NUMERIC(10,2),
    currency        VARCHAR(3) DEFAULT 'ZWL',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prof_pages_specialty ON professional_pages (tenant_id, specialty);

CREATE TABLE IF NOT EXISTS crowdfunding_campaigns (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      VARCHAR(255),
    title           VARCHAR(255) NOT NULL,
    story           TEXT NOT NULL,
    category        VARCHAR(50) NOT NULL DEFAULT 'MEDICAL',
    goal_amount     NUMERIC(12,2) NOT NULL,
    raised_amount   NUMERIC(12,2) NOT NULL DEFAULT 0,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    donor_count     INTEGER NOT NULL DEFAULT 0,
    image_url       VARCHAR(500),
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    verified        BOOLEAN NOT NULL DEFAULT FALSE,
    ends_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS crowdfunding_donations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id     UUID NOT NULL REFERENCES crowdfunding_campaigns(id),
    donor_id        VARCHAR(255) NOT NULL,
    amount          NUMERIC(12,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'ZWL',
    message         TEXT,
    is_anonymous    BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS marketplace_services (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    name            VARCHAR(500) NOT NULL,
    description     TEXT NOT NULL,
    category        VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    facility_id     UUID NOT NULL,
    facility_name   VARCHAR(500) NOT NULL DEFAULT '',
    price           DECIMAL(15,2),
    currency        VARCHAR(10) NOT NULL DEFAULT 'USD',
    available       BOOLEAN NOT NULL DEFAULT TRUE,
    rating          DECIMAL(3,2),
    image_url       TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_marketplace_services_tenant ON marketplace_services (tenant_id);
CREATE INDEX IF NOT EXISTS idx_marketplace_services_category ON marketplace_services (tenant_id, category);

CREATE TABLE IF NOT EXISTS wellness_connected_sources (
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                  VARCHAR(255) NOT NULL,
    patient_id                 VARCHAR(255) NOT NULL,
    source_type                VARCHAR(64) NOT NULL,
    source_name                VARCHAR(255) NOT NULL,
    source_device_id           VARCHAR(255),
    source_app_id              VARCHAR(255),
    source_priority            INTEGER NOT NULL DEFAULT 100,
    status                     VARCHAR(32) NOT NULL DEFAULT 'CONNECTED',
    provider_access_allowed    BOOLEAN NOT NULL DEFAULT false,
    clinical_writeback_allowed BOOLEAN NOT NULL DEFAULT false,
    consent_status             VARCHAR(32) NOT NULL DEFAULT 'GRANTED',
    sharing_scope              VARCHAR(64) NOT NULL DEFAULT 'PERSONAL_ONLY',
    category_permissions       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_sources_patient
    ON wellness_connected_sources (tenant_id, patient_id, status, updated_at DESC);

CREATE TABLE IF NOT EXISTS wellness_source_access_audit (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    patient_id       VARCHAR(255) NOT NULL,
    source_id        UUID REFERENCES wellness_connected_sources(id),
    actor_id         VARCHAR(255),
    actor_type       VARCHAR(64),
    purpose_of_use   VARCHAR(64),
    action           VARCHAR(128) NOT NULL,
    correlation_id   VARCHAR(255),
    request_id       VARCHAR(255),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_source_audit_patient
    ON wellness_source_access_audit (tenant_id, patient_id, created_at DESC);

CREATE TABLE IF NOT EXISTS wellness_remote_alerts (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             VARCHAR(255) NOT NULL,
    patient_id            VARCHAR(255) NOT NULL,
    source_id             UUID REFERENCES wellness_connected_sources(id),
    category              VARCHAR(64) NOT NULL,
    vital_type            VARCHAR(80),
    measured_at           TIMESTAMPTZ,
    observed_value        NUMERIC(12,4),
    threshold_min         NUMERIC(12,4),
    threshold_max         NUMERIC(12,4),
    unit                  VARCHAR(32),
    status                VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    severity              VARCHAR(32) NOT NULL DEFAULT 'MEDIUM',
    escalation_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING_REVIEW',
    review_notes          TEXT,
    reviewed_by_provider  VARCHAR(255),
    reviewed_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_wellness_remote_alerts_patient
    ON wellness_remote_alerts (tenant_id, patient_id, status, created_at DESC);
