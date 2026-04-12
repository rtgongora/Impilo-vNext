-- =============================================================================
-- V3: Remaining Citizen "My Life" tables (aligned with experience-bff V26 + V5 marketplace).
-- Idempotent for shared experience_bff database.
-- =============================================================================

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
