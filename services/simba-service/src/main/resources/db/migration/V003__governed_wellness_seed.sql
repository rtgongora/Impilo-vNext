-- Governed MOHCC national wellness catalogue for compose, preview, and journey e2e.

INSERT INTO simba.clubs (
    club_id, tenant_id, name, description, club_type, visibility, created_by
) VALUES (
    'aaaaaaaa-0003-0001-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'Harare Morning Walkers',
    'Community walking club — Avondale to CBD loop',
    'Walking',
    'PUBLIC',
    'MOHCC-WELLNESS'
)
ON CONFLICT DO NOTHING;

INSERT INTO simba.challenges (
    challenge_id, tenant_id, title, description, challenge_type, target_value, unit, start_date, end_date, status, created_by
) VALUES (
    'aaaaaaaa-0003-0002-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    '10K Steps Daily — June',
    'National steps challenge for citizen wellness pilots',
    'STEPS',
    10000,
    'steps',
    CURRENT_DATE,
    (CURRENT_DATE + INTERVAL '30 days')::date,
    'ACTIVE',
    'MOHCC-WELLNESS'
)
ON CONFLICT DO NOTHING;

CREATE TABLE IF NOT EXISTS simba.screening_programmes (
    id              BIGSERIAL PRIMARY KEY,
    programme_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    programme_code  VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    age_min         INT,
    age_max         INT,
    frequency_months INT NOT NULL DEFAULT 12,
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_screening_code UNIQUE (tenant_id, programme_code)
);

CREATE TABLE IF NOT EXISTS simba.coaching_nudges (
    id              BIGSERIAL PRIMARY KEY,
    nudge_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128),
    nudge_type      VARCHAR(32) NOT NULL,
    message         TEXT NOT NULL,
    channel         VARCHAR(16) NOT NULL DEFAULT 'IN_APP',
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS simba.wellness_routes (
    id              BIGSERIAL PRIMARY KEY,
    route_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    route_code      VARCHAR(64) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    facility_name   VARCHAR(255),
    category        VARCHAR(64),
    distance_km     DECIMAL(8,2),
    elevation_m     INT,
    difficulty      VARCHAR(16),
    status          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_route_code UNIQUE (tenant_id, route_code)
);

INSERT INTO simba.screening_programmes (
    programme_id, tenant_id, programme_code, title, description, age_min, age_max, frequency_months
) VALUES (
    'bbbbbbbb-0003-0001-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'SCR-HIV-ANNUAL',
    'HIV screening (annual)',
    'MOHCC preventive HIV screening programme for adults 18–64',
    18,
    64,
    12
),
(
    'bbbbbbbb-0003-0001-0000-000000000002'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'SCR-CERVICAL-3Y',
    'Cervical cancer screening',
    'Cervical cytology every 3 years for eligible women 25–49',
    25,
    49,
    36
)
ON CONFLICT (tenant_id, programme_code) DO NOTHING;

INSERT INTO simba.coaching_nudges (
    nudge_id, tenant_id, person_cpid, nudge_type, message, channel
) VALUES (
    'bbbbbbbb-0003-0002-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    NULL,
    'HABIT',
    'Log 10 minutes of movement before noon — small wins build streaks.',
    'IN_APP'
)
ON CONFLICT DO NOTHING;

INSERT INTO simba.wellness_routes (
    route_id, tenant_id, route_code, title, facility_name, category, distance_km, elevation_m, difficulty
) VALUES (
    'bbbbbbbb-0003-0003-0000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'ROUTE-AVONDALE-LOOP',
    'Avondale wellness loop',
    'Harare Central Hospital grounds',
    'Walking',
    4.20,
    45,
    'EASY'
)
ON CONFLICT (tenant_id, route_code) DO NOTHING;
