-- =============================================================================
-- V32: EHR structured history — social, family, functional assessments, procedures, directives
-- Patient-scoped continuity tables (tenant_id + patient_id → patients.id).
--
-- Verified 2026-04-11: all FKs resolve, no table name conflicts with V1-V31.
-- Tables: social_history_entries, family_history_members, family_history_conditions,
--         functional_assessments, patient_procedures, advance_directives
-- Controller: StructuredHistoryController.java | Hook: useStructuredHistory.ts
-- =============================================================================

CREATE TABLE IF NOT EXISTS social_history_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    category        VARCHAR(255) NOT NULL,
    icon            VARCHAR(64) NOT NULL DEFAULT 'home',
    status          VARCHAR(512) NOT NULL,
    detail          TEXT,
    last_updated    DATE NOT NULL DEFAULT CURRENT_DATE,
    risk_level      VARCHAR(32) NOT NULL DEFAULT 'Low',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_social_history_patient ON social_history_entries (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS family_history_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       VARCHAR(255) NOT NULL,
    patient_id      UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    relationship    VARCHAR(128) NOT NULL,
    age             INTEGER,
    deceased        BOOLEAN NOT NULL DEFAULT FALSE,
    deceased_age    INTEGER,
    cause_of_death  VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_family_members_patient ON family_history_members (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS family_history_conditions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    member_id       UUID NOT NULL REFERENCES family_history_members(id) ON DELETE CASCADE,
    condition_label VARCHAR(512) NOT NULL,
    onset_age       INTEGER,
    status          VARCHAR(32) NOT NULL,
    notes           TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_family_conditions_member ON family_history_conditions (member_id);

CREATE TABLE IF NOT EXISTS functional_assessments (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    assessment_type  VARCHAR(32) NOT NULL,
    assessment_date  DATE NOT NULL,
    assessor         VARCHAR(255),
    total_score      INTEGER NOT NULL,
    max_score        INTEGER NOT NULL,
    interpretation   TEXT,
    activities_json  JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_functional_assessments_patient ON functional_assessments (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS patient_procedures (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    name             VARCHAR(512) NOT NULL,
    procedure_type   VARCHAR(255),
    procedure_date   DATE NOT NULL,
    surgeon          VARCHAR(255),
    facility         VARCHAR(255),
    status           VARCHAR(64) NOT NULL,
    notes            TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_patient_procedures_patient ON patient_procedures (tenant_id, patient_id);

CREATE TABLE IF NOT EXISTS advance_directives (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        VARCHAR(255) NOT NULL,
    patient_id       UUID NOT NULL REFERENCES patients(id) ON DELETE CASCADE,
    directive_type   VARCHAR(64) NOT NULL,
    status           VARCHAR(64) NOT NULL,
    effective_date   DATE,
    review_date      DATE,
    document_ref     VARCHAR(255),
    summary          TEXT,
    contact          VARCHAR(255),
    contact_relation VARCHAR(128),
    contact_phone    VARCHAR(64),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_advance_directives_patient ON advance_directives (tenant_id, patient_id);

-- Seed sample continuity rows for golden-path patient Tatenda Moyo (V4)
INSERT INTO social_history_entries (id, tenant_id, patient_id, category, icon, status, detail, last_updated, risk_level) VALUES
('f2000000-0000-0000-0000-000000000001', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Smoking', 'home', 'Former Smoker', 'Quit 3 years ago. 10 pack-year history.', '2026-03-15', 'Moderate'),
('f2000000-0000-0000-0000-000000000002', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Alcohol Use', 'home', 'Social Drinker', '1-2 drinks per week.', '2026-03-15', 'Low');

INSERT INTO family_history_members (id, tenant_id, patient_id, name, relationship, age, deceased, deceased_age, cause_of_death) VALUES
('f2100000-0000-0000-0000-000000000001', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'John M.', 'Father', 68, FALSE, NULL, NULL),
('f2100000-0000-0000-0000-000000000002', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Robert M.', 'Paternal Grandfather', NULL, TRUE, 72, 'Myocardial Infarction');

INSERT INTO family_history_conditions (id, member_id, condition_label, onset_age, status, notes) VALUES
('f2200000-0000-0000-0000-000000000001', 'f2100000-0000-0000-0000-000000000001', 'Type 2 Diabetes Mellitus', 52, 'Active', 'On insulin since 2018'),
('f2200000-0000-0000-0000-000000000002', 'f2100000-0000-0000-0000-000000000002', 'Coronary Artery Disease', 55, 'Deceased', 'Multiple MIs');

INSERT INTO patient_procedures (id, tenant_id, patient_id, name, procedure_type, procedure_date, surgeon, facility, status, notes) VALUES
('f2300000-0000-0000-0000-000000000001', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Colonoscopy', 'Endoscopy', '2026-04-15', 'Dr. Chikwava', 'Harare Central Hospital', 'Scheduled', 'Screening colonoscopy'),
('f2300000-0000-0000-0000-000000000002', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Appendectomy', 'General Surgery', '2026-03-10', 'Dr. Mutasa', 'Parirenyatwa Hospital', 'Completed', 'Laparoscopic');

INSERT INTO advance_directives (id, tenant_id, patient_id, directive_type, status, effective_date, review_date, document_ref, summary, contact, contact_relation, contact_phone) VALUES
('f2400000-0000-0000-0000-000000000001', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'DNR', 'Active', '2025-06-15', '2026-06-15', 'DOC-2025-0612', 'Full DNR in effect.', 'Mary M.', 'Spouse', '+263 77 234 5678'),
('f2400000-0000-0000-0000-000000000002', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'Mental Health Directive', 'Pending Review', '2024-12-01', '2025-12-01', 'DOC-2024-1188', 'Review preferences.', 'Dr. S. Chirwa', 'Psychiatrist', '+263 24 276 1234');

INSERT INTO functional_assessments (id, tenant_id, patient_id, assessment_type, assessment_date, assessor, total_score, max_score, interpretation, activities_json) VALUES
('f2500000-0000-0000-0000-000000000001', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'barthel', '2026-04-01', 'Sr. N. Phiri', 85, 100, 'Moderate dependence - needs some assistance',
 '[{"activity":"Feeding","score":10,"maxScore":10,"level":"Independent"},{"activity":"Mobility","score":10,"maxScore":15,"level":"Walks with help"}]'::jsonb),
('f2500000-0000-0000-0000-000000000002', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'katz', '2026-04-01', 'Sr. N. Phiri', 5, 6, 'Moderately independent',
 '[{"activity":"Feeding","score":0,"maxScore":1,"level":"Needs assistance"}]'::jsonb),
('f2500000-0000-0000-0000-000000000003', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'lawton', '2026-04-01', 'Sr. N. Phiri', 6, 8, 'Some dependence in instrumental activities',
 '[{"activity":"Shopping","score":0,"maxScore":1,"level":"Needs assistance"}]'::jsonb),
('f2500000-0000-0000-0000-000000000004', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'barthel', '2026-01-10', 'Sr. N. Phiri', 80, 100, 'Prior assessment',
 '[{"activity":"Mobility","score":8,"maxScore":15,"level":"Walks with help"}]'::jsonb),
('f2500000-0000-0000-0000-000000000005', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'katz', '2026-01-10', 'Sr. N. Phiri', 4, 6, 'Prior assessment', '[]'::jsonb),
('f2500000-0000-0000-0000-000000000006', 'moh-zw', 'a1000000-0000-0000-0000-000000000001', 'lawton', '2026-01-10', 'Sr. N. Phiri', 5, 8, 'Prior assessment', '[]'::jsonb);
