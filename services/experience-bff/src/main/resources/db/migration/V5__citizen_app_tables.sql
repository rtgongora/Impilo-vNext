-- ============================================================================
-- Experience BFF — Citizen App Tables
-- V5: Appointments, Lab Results, Coverage, Feed, Marketplace Services,
--     Service Requests, Telehealth Sessions (citizen-facing), Consents
-- ============================================================================

-- Appointments (citizen-facing appointment requests)
CREATE TABLE IF NOT EXISTS appointments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    facility_id     UUID NOT NULL,
    facility_name   VARCHAR(500) NOT NULL DEFAULT '',
    provider_id     UUID,
    provider_name   VARCHAR(500),
    appointment_type VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    status          VARCHAR(50) NOT NULL DEFAULT 'SCHEDULED',
    scheduled_at    TIMESTAMPTZ NOT NULL,
    duration        INTEGER NOT NULL DEFAULT 30,
    reason          TEXT,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_appointments_tenant ON appointments (tenant_id);
CREATE INDEX idx_appointments_patient ON appointments (tenant_id, patient_id);
CREATE INDEX idx_appointments_status ON appointments (tenant_id, patient_id, status);

-- Lab results (citizen-facing view of diagnostic results)
CREATE TABLE IF NOT EXISTS lab_results (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    test_name       VARCHAR(500) NOT NULL,
    category        VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    status          VARCHAR(50) NOT NULL DEFAULT 'ORDERED',
    value           VARCHAR(500),
    unit            VARCHAR(100),
    reference_range VARCHAR(255),
    interpretation  TEXT,
    ordered_by      VARCHAR(255) NOT NULL,
    facility_name   VARCHAR(500) NOT NULL DEFAULT '',
    collected_at    TIMESTAMPTZ,
    result_at       TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_lab_results_tenant ON lab_results (tenant_id);
CREATE INDEX idx_lab_results_patient ON lab_results (tenant_id, patient_id);
CREATE INDEX idx_lab_results_status ON lab_results (tenant_id, patient_id, status);

-- Coverage / insurance plans
CREATE TABLE IF NOT EXISTS coverage_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    plan_name       VARCHAR(500) NOT NULL,
    plan_type       VARCHAR(100) NOT NULL DEFAULT 'PUBLIC',
    member_id       VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    effective_from  DATE NOT NULL,
    effective_to    DATE,
    copay           DECIMAL(15,2),
    deductible      DECIMAL(15,2),
    out_of_pocket_max DECIMAL(15,2),
    currency        VARCHAR(10) NOT NULL DEFAULT 'USD',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_coverage_plans_patient ON coverage_plans (tenant_id, patient_id);

-- Social feed items
CREATE TABLE IF NOT EXISTS feed_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    type            VARCHAR(50) NOT NULL DEFAULT 'ANNOUNCEMENT',
    title           VARCHAR(500) NOT NULL,
    body            TEXT NOT NULL,
    image_url       TEXT,
    author          VARCHAR(255),
    category        VARCHAR(100) NOT NULL DEFAULT 'GENERAL',
    published_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    likes_count     INTEGER NOT NULL DEFAULT 0,
    comments_count  INTEGER NOT NULL DEFAULT 0,
    action_url      TEXT,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_feed_items_tenant ON feed_items (tenant_id, published_at DESC);
CREATE INDEX idx_feed_items_category ON feed_items (tenant_id, category);

-- Feed likes tracking
CREATE TABLE IF NOT EXISTS feed_likes (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    feed_item_id    UUID NOT NULL REFERENCES feed_items(id),
    patient_id      UUID NOT NULL REFERENCES patients(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_feed_likes UNIQUE (feed_item_id, patient_id)
);

-- Marketplace services catalog
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

CREATE INDEX idx_marketplace_services_tenant ON marketplace_services (tenant_id);
CREATE INDEX idx_marketplace_services_category ON marketplace_services (tenant_id, category);

-- Service requests (citizen bookings)
CREATE TABLE IF NOT EXISTS service_requests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    service_id      UUID NOT NULL REFERENCES marketplace_services(id),
    service_name    VARCHAR(500) NOT NULL,
    facility_name   VARCHAR(500) NOT NULL DEFAULT '',
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    requested_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    scheduled_at    TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    notes           TEXT,
    tracking_number VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_service_requests_patient ON service_requests (tenant_id, patient_id);
CREATE INDEX idx_service_requests_status ON service_requests (tenant_id, patient_id, status);

-- Citizen telehealth sessions (linked to but distinct from provider telemedicine_sessions)
CREATE TABLE IF NOT EXISTS citizen_telehealth_sessions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    provider_id     UUID,
    provider_name   VARCHAR(500) NOT NULL DEFAULT '',
    session_type    VARCHAR(50) NOT NULL DEFAULT 'VIDEO',
    status          VARCHAR(50) NOT NULL DEFAULT 'REQUESTED',
    scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    started_at      TIMESTAMPTZ,
    ended_at        TIMESTAMPTZ,
    room_url        TEXT,
    notes           TEXT,
    reason          TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_citizen_telehealth_patient ON citizen_telehealth_sessions (tenant_id, patient_id);
CREATE INDEX idx_citizen_telehealth_status ON citizen_telehealth_sessions (tenant_id, patient_id, status);

-- Consent preferences
CREATE TABLE IF NOT EXISTS consent_preferences (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    category        VARCHAR(255) NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    granted         BOOLEAN NOT NULL DEFAULT FALSE,
    granted_at      TIMESTAMPTZ,
    revoked_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_consent_prefs UNIQUE (tenant_id, patient_id, category)
);

CREATE INDEX idx_consent_prefs_patient ON consent_preferences (tenant_id, patient_id);

-- Citizen support tickets (citizen-facing, distinct from facility-based provider tickets)
CREATE TABLE IF NOT EXISTS citizen_support_tickets (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    category        VARCHAR(100) NOT NULL,
    subject         VARCHAR(500) NOT NULL,
    description     TEXT NOT NULL,
    priority        VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    resolution      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_citizen_support_patient ON citizen_support_tickets (tenant_id, patient_id);
