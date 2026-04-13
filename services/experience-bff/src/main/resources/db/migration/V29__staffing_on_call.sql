-- ============================================================================
-- Experience BFF — Staffing / on-call persistence (Wave 8)
-- ============================================================================

CREATE TABLE on_call_assignments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    facility_id      UUID NOT NULL,
    assignment_date  DATE NOT NULL,
    specialty        VARCHAR(200) NOT NULL,
    shift_kind       VARCHAR(20) NOT NULL DEFAULT '24hr',
    primary_staff_name   VARCHAR(500) NOT NULL,
    primary_phone        VARCHAR(100),
    backup_staff_name    VARCHAR(500) NOT NULL,
    backup_phone         VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_on_call_day_spec UNIQUE (tenant_id, facility_id, assignment_date, specialty)
);

CREATE INDEX idx_on_call_tenant_facility_date ON on_call_assignments (tenant_id, facility_id, assignment_date);

CREATE TABLE on_call_swap_requests (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    facility_id      UUID NOT NULL,
    requestor_name   VARCHAR(500) NOT NULL,
    requestee_name   VARCHAR(500) NOT NULL,
    original_date    DATE NOT NULL,
    swap_date        DATE NOT NULL,
    specialty        VARCHAR(200),
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_on_call_swaps_tenant_facility ON on_call_swap_requests (tenant_id, facility_id);

-- Seed for golden-path facility (Harare Central) — tenant moh-zw
INSERT INTO on_call_assignments (
    id, tenant_id, facility_id, assignment_date, specialty, shift_kind,
    primary_staff_name, primary_phone, backup_staff_name, backup_phone
) VALUES
(
    'f2000000-0000-0000-0000-000000000001',
    'moh-zw',
    'f1000000-0000-0000-0000-000000000001',
    DATE '2026-04-06',
    'Internal Medicine',
    '24hr',
    'Dr. Tendai Mapfumo',
    '+263771111111',
    'Dr. Grace Musekwa',
    '+263772222222'
),
(
    'f2000000-0000-0000-0000-000000000002',
    'moh-zw',
    'f1000000-0000-0000-0000-000000000001',
    DATE '2026-04-06',
    'Surgery',
    '24hr',
    'Dr. Simba Nyamukapa',
    '+263773333333',
    'Dr. Tendai Mapfumo',
    '+263771111111'
),
(
    'f2000000-0000-0000-0000-000000000003',
    'moh-zw',
    'f1000000-0000-0000-0000-000000000001',
    DATE '2026-04-07',
    'Internal Medicine',
    '24hr',
    'Dr. Grace Musekwa',
    '+263772222222',
    'Dr. Tendai Mapfumo',
    '+263771111111'
);

INSERT INTO on_call_swap_requests (
    id, tenant_id, facility_id, requestor_name, requestee_name,
    original_date, swap_date, specialty, status
) VALUES
(
    'f2000000-0000-0000-0000-000000000010',
    'moh-zw',
    'f1000000-0000-0000-0000-000000000001',
    'Dr. Tendai Mapfumo',
    'Dr. Grace Musekwa',
    DATE '2026-04-10',
    DATE '2026-04-12',
    'Internal Medicine',
    'PENDING'
),
(
    'f2000000-0000-0000-0000-000000000011',
    'moh-zw',
    'f1000000-0000-0000-0000-000000000001',
    'Dr. Simba Nyamukapa',
    'Dr. Tendai Mapfumo',
    DATE '2026-04-08',
    DATE '2026-04-09',
    'Surgery',
    'APPROVED'
);
