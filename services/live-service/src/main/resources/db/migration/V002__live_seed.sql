-- Demo seed data for Impilo Live (dev/preview)

INSERT INTO live.live_events (
    id, tenant_id, title, description, event_type, context_type, audience_type, visibility,
    status, organiser_type, organiser_id, facility_id, linked_fundo_course_id,
    start_time, end_time, registration_required, recording_allowed, replay_allowed,
    cpd_enabled, cpd_points, attendance_threshold_minutes, created_by
) VALUES
(
    'a1000001-0000-4000-8000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'National CPD: Clinical Protocol Updates Q2 2026',
    'Quarterly clinical protocol update webinar for licensed health professionals. CPD points available.',
    'CPD', 'PROFESSIONAL', 'NATIONAL', 'ROLE_BASED',
    'SCHEDULED', 'PROVIDER', 'PROV-DEMO-001', 'FAC-DEMO-001',
    NULL,
    now() + interval '7 days',
    now() + interval '7 days' + interval '90 minutes',
    true, true, true, true, 2.0, 45, 'PROV-DEMO-001'
),
(
    'a1000001-0000-4000-8000-000000000002',
    '00000000-0000-0000-0000-000000000001',
    'Maternal Health Live Talk: Safe Pregnancy',
    'Community health education session on safe pregnancy practices and antenatal care.',
    'PUBLIC_HEALTH_EDUCATION', 'CITIZEN', 'PUBLIC', 'PUBLIC',
    'SCHEDULED', 'FACILITY', 'FAC-DEMO-002', 'FAC-DEMO-002',
    NULL,
    now() + interval '3 days',
    now() + interval '3 days' + interval '60 minutes',
    false, true, true, false, NULL, 20, 'FAC-DEMO-002'
),
(
    'a1000001-0000-4000-8000-000000000003',
    '00000000-0000-0000-0000-000000000001',
    'Madi Blood Donor Mobilisation Livestream',
    'Live session encouraging blood donation. Register as a donor and find nearby drives.',
    'DONOR_MOBILISATION', 'CITIZEN', 'PUBLIC', 'PUBLIC',
    'SCHEDULED', 'FACILITY', 'FAC-DEMO-001', 'FAC-DEMO-001',
    'b2000001-0000-4000-8000-000000000001',
    now() + interval '5 days',
    now() + interval '5 days' + interval '45 minutes',
    false, true, true, false, NULL, 15, 'FAC-DEMO-001'
);

INSERT INTO live.live_event_role_assignments (event_id, user_id, participant_type, role, assigned_by) VALUES
('a1000001-0000-4000-8000-000000000001', 'PROV-DEMO-001', 'PROVIDER', 'HOST', 'PROV-DEMO-001'),
('a1000001-0000-4000-8000-000000000002', 'PROV-DEMO-002', 'PROVIDER', 'HOST', 'PROV-DEMO-002'),
('a1000001-0000-4000-8000-000000000003', 'PROV-DEMO-001', 'PROVIDER', 'HOST', 'PROV-DEMO-001');
