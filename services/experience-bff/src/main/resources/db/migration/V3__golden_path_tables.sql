-- ============================================================================
-- Experience BFF — Golden Path Tables
-- V3: Patients, Queue, Shifts, Encounters, Admin Users, Pharmacy, Inventory
-- ============================================================================

-- Patients (BFF-local patient index — subset synced from VITO)
CREATE TABLE patients (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    cpid            VARCHAR(100) NOT NULL,
    given_name      VARCHAR(255) NOT NULL,
    family_name     VARCHAR(255) NOT NULL,
    date_of_birth   DATE NOT NULL,
    sex             VARCHAR(20) NOT NULL,
    national_id     VARCHAR(100),
    phone           VARCHAR(100),
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    facility_id     UUID,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_patients_tenant_cpid UNIQUE (tenant_id, cpid)
);

CREATE INDEX idx_patients_tenant ON patients (tenant_id);
CREATE INDEX idx_patients_name ON patients (tenant_id, family_name, given_name);
CREATE INDEX idx_patients_search ON patients (tenant_id, LOWER(family_name), LOWER(given_name));

-- Queue entries (clinical queue management)
CREATE TABLE queue_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    workspace_id    UUID,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    queue_type      VARCHAR(50) NOT NULL DEFAULT 'WALK_IN',
    priority        VARCHAR(20) NOT NULL DEFAULT 'NORMAL',
    status          VARCHAR(50) NOT NULL DEFAULT 'WAITING',
    triage_category VARCHAR(50),
    reason          TEXT,
    notes           TEXT,
    assigned_to     VARCHAR(255),
    arrival_time    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    called_at       TIMESTAMPTZ,
    seen_at         TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_queue_entries_tenant ON queue_entries (tenant_id);
CREATE INDEX idx_queue_entries_facility ON queue_entries (tenant_id, facility_id);
CREATE INDEX idx_queue_entries_status ON queue_entries (tenant_id, facility_id, status);
CREATE INDEX idx_queue_entries_patient ON queue_entries (patient_id);

-- Shifts
CREATE TABLE shifts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    workspace_id    UUID,
    user_id         VARCHAR(255) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    handover_notes  TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_shifts_tenant ON shifts (tenant_id);
CREATE INDEX idx_shifts_user ON shifts (tenant_id, user_id, status);
CREATE INDEX idx_shifts_facility ON shifts (tenant_id, facility_id);

-- Encounters (clinical encounters)
CREATE TABLE encounters (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    shift_id        UUID REFERENCES shifts(id),
    encounter_type  VARCHAR(100) NOT NULL DEFAULT 'OUTPATIENT',
    status          VARCHAR(50) NOT NULL DEFAULT 'IN_PROGRESS',
    chief_complaint TEXT,
    diagnosis       TEXT,
    notes           JSONB NOT NULL DEFAULT '[]'::jsonb,
    vitals          JSONB NOT NULL DEFAULT '{}'::jsonb,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ended_at        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_encounters_tenant ON encounters (tenant_id);
CREATE INDEX idx_encounters_patient ON encounters (patient_id);
CREATE INDEX idx_encounters_facility ON encounters (tenant_id, facility_id);
CREATE INDEX idx_encounters_status ON encounters (tenant_id, status);

-- Admin users (BFF-local user directory for admin zone)
CREATE TABLE admin_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    username        VARCHAR(255) NOT NULL,
    email           VARCHAR(255) NOT NULL,
    display_name    VARCHAR(500) NOT NULL,
    role            VARCHAR(100) NOT NULL DEFAULT 'USER',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    facility_id     UUID,
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_admin_users_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT uq_admin_users_tenant_email UNIQUE (tenant_id, email)
);

CREATE INDEX idx_admin_users_tenant ON admin_users (tenant_id);
CREATE INDEX idx_admin_users_role ON admin_users (tenant_id, role);

-- Audit log (admin audit trail)
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    actor_id        VARCHAR(255) NOT NULL,
    action          VARCHAR(255) NOT NULL,
    resource_type   VARCHAR(100) NOT NULL,
    resource_id     VARCHAR(255),
    details         JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address      VARCHAR(100),
    occurred_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_log_tenant ON audit_log (tenant_id);
CREATE INDEX idx_audit_log_actor ON audit_log (tenant_id, actor_id);
CREATE INDEX idx_audit_log_occurred ON audit_log (tenant_id, occurred_at DESC);

-- Prescriptions (pharmacy)
CREATE TABLE prescriptions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id),
    encounter_id    UUID REFERENCES encounters(id),
    medication_name VARCHAR(500) NOT NULL,
    dosage          VARCHAR(255),
    frequency       VARCHAR(255),
    duration        VARCHAR(255),
    quantity        INTEGER,
    status          VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    prescribed_by   VARCHAR(255) NOT NULL,
    dispensed_by    VARCHAR(255),
    dispensed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_prescriptions_tenant ON prescriptions (tenant_id);
CREATE INDEX idx_prescriptions_patient ON prescriptions (patient_id);
CREATE INDEX idx_prescriptions_status ON prescriptions (tenant_id, status);

-- Inventory items
CREATE TABLE inventory_items (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    product_code    VARCHAR(100) NOT NULL,
    product_name    VARCHAR(500) NOT NULL,
    category        VARCHAR(100),
    quantity_on_hand INTEGER NOT NULL DEFAULT 0,
    reorder_level   INTEGER NOT NULL DEFAULT 0,
    unit            VARCHAR(50) NOT NULL DEFAULT 'EACH',
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    last_counted_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_inventory_items_tenant ON inventory_items (tenant_id);
CREATE INDEX idx_inventory_items_facility ON inventory_items (tenant_id, facility_id);

-- Marketplace orders
CREATE TABLE marketplace_orders (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    facility_id     UUID NOT NULL,
    vendor_id       UUID,
    order_number    VARCHAR(100) NOT NULL,
    status          VARCHAR(50) NOT NULL DEFAULT 'DRAFT',
    total_amount    DECIMAL(15,2),
    currency        VARCHAR(10) NOT NULL DEFAULT 'USD',
    items           JSONB NOT NULL DEFAULT '[]'::jsonb,
    ordered_by      VARCHAR(255) NOT NULL,
    ordered_at      TIMESTAMPTZ,
    delivered_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_marketplace_orders_tenant ON marketplace_orders (tenant_id);
CREATE INDEX idx_marketplace_orders_facility ON marketplace_orders (tenant_id, facility_id);
CREATE INDEX idx_marketplace_orders_status ON marketplace_orders (tenant_id, status);

-- Registry providers (provider directory)
CREATE TABLE registry_providers (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    provider_number VARCHAR(100) NOT NULL,
    given_name      VARCHAR(255) NOT NULL,
    family_name     VARCHAR(255) NOT NULL,
    qualification   VARCHAR(255),
    speciality      VARCHAR(255),
    facility_id     UUID,
    status          VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_registry_providers_number UNIQUE (tenant_id, provider_number)
);

CREATE INDEX idx_registry_providers_tenant ON registry_providers (tenant_id);
