-- Phase 5E — Native Impilo Fundo demo content (standalone, no Moodle).
--
-- Seeds four short demo courses plus one structured pathway against the
-- canonical demo tenant 00000000-0000-0000-0000-000000000001. All content
-- is short placeholder text — no copyrighted training material. Designed
-- to demonstrate that the native Fundo LMS surface (catalog, structure,
-- pathway, enrolment, progress, certificate) operates end-to-end without
-- contacting Moodle or any external LMS.

-- ── Courses ────────────────────────────────────────────────────────────
INSERT INTO lrn_course (
    id, tenant_id, code, title, description, category, level, status,
    language, estimated_duration_minutes, mandatory, cpd_eligible, cpd_points, version
) VALUES
('aaaaaaaa-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'FUNDO-DHO-001', 'Digital Health Orientation',
    'High-level orientation to digital health in Zimbabwe and the Impilo platform.',
    'ORIENTATION', 'INTRODUCTORY', 'PUBLISHED', 'en', 45, true,  false, NULL, 1),
('aaaaaaaa-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'FUNDO-EHR-101',  'Introduction to Impilo EHR',
    'Working with the Impilo vNext One Experience Shell across roles.',
    'EHR_BASICS',   'INTRODUCTORY', 'PUBLISHED', 'en', 60, true,  true,  2, 1),
('aaaaaaaa-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001',
    'FUNDO-REG-101',  'Patient Registration Basics',
    'Demographic intake patterns, identity verification and consent flows.',
    'REGISTRATION', 'INTRODUCTORY', 'PUBLISHED', 'en', 50, false, true,  1, 1),
('aaaaaaaa-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001',
    'FUNDO-DQ-201',   'Data Quality for Facility Teams',
    'Facility-level data quality routines, error triage and corrective actions.',
    'DATA_QUALITY', 'INTERMEDIATE', 'PUBLISHED', 'en', 75, false, true,  3, 1);

-- ── Modules (Digital Health Orientation) ───────────────────────────────
INSERT INTO lrn_course_module (id, tenant_id, course_id, title, description, sequence_no, status) VALUES
('bbbbbbbb-0001-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000001',
    'What is digital health?', 'Definitions, scope and Impilo context.', 1, 'PUBLISHED'),
('bbbbbbbb-0001-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000001',
    'Privacy, consent and ethics', 'Patient rights and lawful processing.', 2, 'PUBLISHED');

-- Modules (Intro to EHR)
INSERT INTO lrn_course_module (id, tenant_id, course_id, title, description, sequence_no, status) VALUES
('bbbbbbbb-0002-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000002',
    'The One Experience Shell', 'Navigation and role-based workspaces.', 1, 'PUBLISHED'),
('bbbbbbbb-0002-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000002',
    'Search, start, and clinical tools', 'Command palette, start workflows.', 2, 'PUBLISHED');

-- Modules (Patient Registration Basics)
INSERT INTO lrn_course_module (id, tenant_id, course_id, title, description, sequence_no, status) VALUES
('bbbbbbbb-0003-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000003',
    'Identity, demographics, consent',
    'Capturing identity and consent within the registry.', 1, 'PUBLISHED');

-- Modules (Data Quality)
INSERT INTO lrn_course_module (id, tenant_id, course_id, title, description, sequence_no, status) VALUES
('bbbbbbbb-0004-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000004',
    'Daily data quality routine', 'Daily, weekly and monthly DQ checks.', 1, 'PUBLISHED'),
('bbbbbbbb-0004-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000004',
    'Triage and corrective action', 'Triage flagged issues with the facility team.', 2, 'PUBLISHED');

-- ── Lessons (one or two per module — short placeholders) ──────────────
INSERT INTO lrn_course_lesson (
    id, tenant_id, module_id, title, content_type, content_body,
    estimated_duration_minutes, sequence_no, required, status
) VALUES
-- DHO module 1
('cccccccc-0001-0001-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0001-0000-0000-000000000001',
    'Digital health in Zimbabwe', 'TEXT',
    'Short introductory text. Replace in production with governed content.',
    15, 1, true, 'PUBLISHED'),
('cccccccc-0001-0001-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0001-0000-0000-000000000001',
    'How Impilo fits in', 'TEXT',
    'Short text describing Impilo''s role in the digital health stack.',
    10, 2, true, 'PUBLISHED'),
-- DHO module 2
('cccccccc-0001-0002-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0001-0000-0000-000000000002',
    'Consent and patient rights', 'TEXT',
    'Short text on lawful basis and consent capture.',
    20, 1, true, 'PUBLISHED'),
-- EHR 101 module 1
('cccccccc-0002-0001-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0002-0000-0000-000000000001',
    'Navigating the shell', 'TEXT', 'Short text on the One Experience Shell.',
    20, 1, true, 'PUBLISHED'),
-- EHR 101 module 2
('cccccccc-0002-0002-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0002-0000-0000-000000000002',
    'Search and start command', 'TEXT', 'Short text on command palette usage.',
    20, 1, true, 'PUBLISHED'),
('cccccccc-0002-0002-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0002-0000-0000-000000000002',
    'Clinical tools overview', 'TEXT', 'Short text introducing clinical tools.',
    15, 2, true, 'PUBLISHED'),
-- Registration module 1
('cccccccc-0003-0001-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0003-0000-0000-000000000001',
    'Demographics and identity', 'TEXT', 'Short text on capturing demographics.',
    25, 1, true, 'PUBLISHED'),
('cccccccc-0003-0001-0000-000000000002', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0003-0000-0000-000000000001',
    'Consent capture in registry', 'TEXT', 'Short text on consent capture.',
    25, 2, true, 'PUBLISHED'),
-- DQ module 1
('cccccccc-0004-0001-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0004-0000-0000-000000000001',
    'Daily DQ routine', 'TEXT', 'Short text on daily DQ checks.',
    30, 1, true, 'PUBLISHED'),
-- DQ module 2
('cccccccc-0004-0002-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'bbbbbbbb-0004-0000-0000-000000000002',
    'Triaging flagged issues', 'PRACTICAL_TASK', 'Short practical-task description.',
    45, 1, true, 'PUBLISHED');

-- ── Assessment (single quiz on EHR 101) ────────────────────────────────
INSERT INTO lrn_assessment (
    id, tenant_id, course_id, module_id, title, description, assessment_type,
    pass_mark, max_attempts, status
) VALUES
('dddddddd-0002-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000002',
    'bbbbbbbb-0002-0000-0000-000000000002',
    'EHR navigation quick quiz',
    'A two-question quiz on the One Experience Shell.',
    'QUIZ', 50, 3, 'PUBLISHED');

INSERT INTO lrn_assessment_question (id, assessment_id, question_type, prompt, options_json, correct_answer_json, sequence_no, points) VALUES
('eeeeeeee-0002-0001-0000-000000000001', 'dddddddd-0002-0000-0000-000000000001',
    'TRUE_FALSE',
    'The One Experience Shell is the only entry point into Impilo vNext.',
    '["true","false"]', '"true"', 1, 1),
('eeeeeeee-0002-0001-0000-000000000002', 'dddddddd-0002-0000-0000-000000000001',
    'MULTIPLE_CHOICE',
    'Which command surfaces a global start workflow in the shell?',
    '["Tab","Ctrl+K","Esc","Shift"]', '"Ctrl+K"', 2, 1);

-- ── Pathway: Impilo EHR Basic User Pathway ─────────────────────────────
INSERT INTO lrn_fundo_pathway (
    id, tenant_id, code, title, description, target_cadres, target_roles,
    target_facility_levels, status
) VALUES
('ffffffff-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
    'FUNDO-PATH-EHR-BASIC', 'Impilo EHR Basic User Pathway',
    'Foundational pathway for new users of the Impilo EHR.',
    'NURSE,CLINICAL_OFFICER,REGISTRY_CLERK',
    'CLINICAL,REGISTRY_CLERK',
    'PRIMARY,DISTRICT',
    'PUBLISHED');

INSERT INTO lrn_fundo_pathway_item (id, pathway_id, course_id, sequence_no, required, prerequisite_course_id) VALUES
('aaaa0000-0000-0000-0000-000000000001', 'ffffffff-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000001', 1, true, NULL),
('aaaa0000-0000-0000-0000-000000000002', 'ffffffff-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000002', 2, true,
    'aaaaaaaa-0000-0000-0000-000000000001'),
('aaaa0000-0000-0000-0000-000000000003', 'ffffffff-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000003', 3, true,
    'aaaaaaaa-0000-0000-0000-000000000002'),
('aaaa0000-0000-0000-0000-000000000004', 'ffffffff-0000-0000-0000-000000000001',
    'aaaaaaaa-0000-0000-0000-000000000004', 4, false,
    'aaaaaaaa-0000-0000-0000-000000000003');
