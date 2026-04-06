-- =============================================================================
-- V26: Citizen My Life Features — Health ID, Wellness, Wallet, SOS,
--      Remote Monitoring, Queue Status, Clubs, Professional Pages, Crowdfunding
-- =============================================================================

-- ── Health ID ───────────────────────────────────────────────────────
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

CREATE INDEX idx_health_ids_patient ON citizen_health_ids (tenant_id, patient_id);

-- ── Wellness Tracking ───────────────────────────────────────────────
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
    vital_type      VARCHAR(50) NOT NULL,
    value           NUMERIC(10,2) NOT NULL,
    unit            VARCHAR(20) NOT NULL,
    measured_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    source          VARCHAR(50) DEFAULT 'MANUAL',
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_vitals_patient ON wellness_vitals_log (tenant_id, patient_id, measured_at DESC);

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

CREATE INDEX idx_mood_patient ON wellness_mood_log (tenant_id, patient_id, logged_at DESC);

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

-- ── Health Wallet ───────────────────────────────────────────────────
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

CREATE INDEX idx_wallet_transactions ON wallet_transactions (wallet_id, created_at DESC);

-- ── Emergency SOS ───────────────────────────────────────────────────
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

CREATE INDEX idx_emergency_contacts ON emergency_contacts (tenant_id, patient_id);

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

-- ── Remote Monitoring (Wearable Devices) ────────────────────────────
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

CREATE INDEX idx_monitoring_devices ON monitoring_devices (tenant_id, patient_id);

-- ── Citizen Queue Status ────────────────────────────────────────────
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

CREATE INDEX idx_queue_tickets ON citizen_queue_tickets (tenant_id, patient_id, status);

-- ── Wellness Clubs ──────────────────────────────────────────────────
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

-- ── Professional Pages ──────────────────────────────────────────────
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

CREATE INDEX idx_prof_pages_specialty ON professional_pages (tenant_id, specialty);

-- ── Crowdfunding Campaigns ──────────────────────────────────────────
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

-- ── Seed Data ───────────────────────────────────────────────────────

-- Wellness challenges
INSERT INTO wellness_challenges (id, tenant_id, title, description, challenge_type, target_value, target_unit, start_date, end_date, participant_count)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', '10K Steps Daily', 'Walk 10,000 steps every day for a week', 'STEPS', 70000, 'steps', CURRENT_DATE, CURRENT_DATE + 7, 42),
(gen_random_uuid(), 'tenant-moh-zw', 'Hydration Hero', 'Drink 2L of water daily for 30 days', 'WATER', 60000, 'ml', CURRENT_DATE, CURRENT_DATE + 30, 28),
(gen_random_uuid(), 'tenant-moh-zw', 'Mindful Minutes', 'Log your mood every day for 2 weeks', 'MOOD', 14, 'entries', CURRENT_DATE, CURRENT_DATE + 14, 15);

-- Wellness clubs
INSERT INTO wellness_clubs (id, tenant_id, name, description, club_type, category, member_count, meeting_schedule, location)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'Harare Runners Club', 'Weekly group runs around Harare parks. All fitness levels welcome.', 'FITNESS', 'RUNNING', 67, 'Every Saturday 6:00 AM', 'Harare Gardens'),
(gen_random_uuid(), 'tenant-moh-zw', 'Diabetes Support Circle', 'Peer support for Type 1 & Type 2 diabetes management.', 'SUPPORT', 'CHRONIC_CARE', 34, 'Bi-weekly Wednesday 14:00', 'Virtual / Parirenyatwa'),
(gen_random_uuid(), 'tenant-moh-zw', 'Prenatal Yoga Group', 'Gentle yoga and breathing for expecting mothers.', 'WELLNESS', 'MATERNAL', 22, 'Tuesday & Thursday 10:00', 'Chitungwiza Central'),
(gen_random_uuid(), 'tenant-moh-zw', 'Mental Wellness Collective', 'Safe space for mental health conversations and coping strategies.', 'SUPPORT', 'MENTAL_HEALTH', 51, 'Every Monday 17:00', 'Virtual');

-- Professional pages
INSERT INTO professional_pages (id, tenant_id, provider_id, display_name, title, specialty, bio, facility_name, years_experience, rating, review_count, is_accepting, consultation_fee)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'prov-001', 'Dr. Tendai Moyo', 'Senior Consultant', 'Internal Medicine', 'Experienced internist specializing in diabetes, hypertension, and infectious diseases.', 'Parirenyatwa Group of Hospitals', 15, 4.8, 124, true, 50.00),
(gen_random_uuid(), 'tenant-moh-zw', 'prov-002', 'Dr. Rudo Chikwanha', 'Paediatrician', 'Paediatrics', 'Passionate about child health, immunization, and developmental screening.', 'Harare Central Hospital', 10, 4.9, 87, true, 45.00),
(gen_random_uuid(), 'tenant-moh-zw', 'prov-003', 'Sr. Nyasha Dube', 'Nurse Practitioner', 'Primary Care', 'Community health specialist with focus on preventive care and chronic disease management.', 'Chitungwiza Central Hospital', 8, 4.7, 63, true, 25.00);

-- Crowdfunding campaigns
INSERT INTO crowdfunding_campaigns (id, tenant_id, title, story, category, goal_amount, raised_amount, donor_count, verified, ends_at)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'Heart Surgery for Baby Tafara', 'Baby Tafara was born with a congenital heart defect and needs urgent surgery at Parirenyatwa Hospital. The family cannot afford the procedure. Every contribution helps save this precious life.', 'MEDICAL', 15000.00, 8750.00, 142, true, NOW() + interval '30 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'Prosthetic Leg for Tendai', 'Tendai lost his leg in a road accident and dreams of walking again. Help fund a modern prosthetic so he can return to work and support his family.', 'MEDICAL', 5000.00, 3200.00, 67, true, NOW() + interval '45 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'Community Clinic Equipment', 'Our rural clinic in Muzarabani serves 5,000 patients but lacks basic diagnostic equipment. Help us purchase a vital signs monitor, glucometer, and nebulizer.', 'COMMUNITY', 3000.00, 1100.00, 38, true, NOW() + interval '60 days');
