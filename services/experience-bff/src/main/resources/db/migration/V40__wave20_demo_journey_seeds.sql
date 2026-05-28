-- Wave 20 slice 11 — demo seeds for seven production-readiness journeys (golden patient CPID-ZW-00001).

-- Journey 3: wellness activity anchor (BFF read model; wellness-service also in compose)
INSERT INTO wellness_activities (
    id,
    tenant_id,
    patient_id,
    activity_date,
    steps,
    calories_burned,
    active_minutes,
    distance_km,
    water_ml
) VALUES (
    'w3000000-0000-4000-8000-000000000001',
    'tenant-moh-zw',
    'a1000000-0000-0000-0000-000000000001',
    CURRENT_DATE,
    8420,
    310,
    45,
    5.2,
    1800
) ON CONFLICT (tenant_id, patient_id, activity_date) DO NOTHING;

-- Journey 5: telemedicine session for golden patient
INSERT INTO telemedicine_sessions (
    id,
    tenant_id,
    patient_id,
    provider_id,
    facility_id,
    session_type,
    status,
    scheduled_at,
    notes
) VALUES (
    'f3000000-0000-4000-8000-000000000001',
    'tenant-moh-zw',
    'a1000000-0000-0000-0000-000000000001'::uuid,
    'd1000000-0000-0000-0000-000000000001'::uuid,
    'a1b2c3d4-0001-4000-8000-000000000001'::uuid,
    'VIDEO',
    'SCHEDULED',
    NOW() + INTERVAL '1 day',
    'DEMO-WAVE20 teleconsult for CPID-ZW-00001 (Rx → dispatch handoff demo)'
) ON CONFLICT (id) DO NOTHING;

-- Journey 2 (BFF care-emergency read model): local admission row for nursing workbench demos
INSERT INTO admissions (
    id,
    tenant_id,
    patient_id,
    admission_type,
    admitting_diagnosis,
    status
) VALUES (
    'f2000000-0000-0000-0000-000000000001',
    'tenant-moh-zw',
    'a1000000-0000-0000-0000-000000000001'::uuid,
    'ELECTIVE',
    'DEMO-WAVE20 — observation admission for nursing workbench',
    'ACTIVE'
) ON CONFLICT (id) DO NOTHING;

-- Machine-readable journey registry (served by DemoJourneyController)
CREATE TABLE IF NOT EXISTS demo_journey_anchors (
    journey_id      VARCHAR(32) PRIMARY KEY,
    journey_number  INTEGER NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    web_route       VARCHAR(512) NOT NULL,
    mobile_tab      VARCHAR(64),
    bff_probe_path  VARCHAR(512),
    golden_patient  VARCHAR(64),
    maturity        VARCHAR(32) NOT NULL DEFAULT 'partial'
);

INSERT INTO demo_journey_anchors (
    journey_id, journey_number, title, description, web_route, mobile_tab, bff_probe_path, golden_patient, maturity
) VALUES
(
    'clinical-rx',
    1,
    'Core clinical / Rx',
    'Queue → encounter → Rx → MusheX → core transaction audit',
    '/pharmacy/transaction-journey?patientId=CPID-ZW-00001',
    'core_transaction',
    '/internal/v1/queue/entries?facility_id=f1000000-0000-0000-0000-000000000001',
    'CPID-ZW-00001',
    'partial'
),
(
    'inpatient',
    2,
    'Inpatient',
    'Admissions, ward list, nursing workbench, discharge handoff',
    '/clinical/inpatient/admissions',
    'inpatient',
    '/internal/v1/inpatient/admissions',
    'CPID-ZW-00001',
    'partial'
),
(
    'wellness',
    3,
    'Wellness (citizen parity)',
    'Wellness profile, challenges, and community clubs',
    '/wellness',
    'learning',
    '/internal/v1/wellness/challenges',
    'CPID-ZW-00001',
    'partial'
),
(
    'enterprise',
    4,
    'Enterprise resources',
    'Inventory requisitions, marketplace, and facility ops',
    '/enterprise',
    'facility',
    '/internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001',
    NULL,
    'partial'
),
(
    'tele-dispatch',
    5,
    'Telemedicine → dispatch',
    'Telehealth session → Rx journey → Nhume / Ndila ops',
    '/telemedicine',
    'telemedicine',
    '/internal/v1/mobile/provider/telemedicine/sessions',
    'CPID-ZW-00001',
    'partial'
),
(
    'public-health',
    6,
    'Public health + geo',
    'Field tasks, surveillance, and Ndila tile config',
    '/public-health',
    'ph_field_tasks',
    '/internal/v1/ndila/tiles/config',
    NULL,
    'partial'
),
(
    'data-intel',
    7,
    'Data & intelligence',
    'Integration hub routes, pipelines, audit intelligence',
    '/data-intelligence/pipelines',
    'ops_reports',
    '/internal/v1/integration-hub/routes',
    NULL,
    'partial'
)
ON CONFLICT (journey_id) DO UPDATE SET
    title = EXCLUDED.title,
    description = EXCLUDED.description,
    web_route = EXCLUDED.web_route,
    mobile_tab = EXCLUDED.mobile_tab,
    bff_probe_path = EXCLUDED.bff_probe_path,
    golden_patient = EXCLUDED.golden_patient,
    maturity = EXCLUDED.maturity;
