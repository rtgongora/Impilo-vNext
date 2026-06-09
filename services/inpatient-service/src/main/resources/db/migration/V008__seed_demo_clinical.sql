-- Demo clinical depth for CPID-ZW-00001 admission f2000000-0000-0000-0000-000000000001

UPDATE inpatient.admission
SET admitting_diagnosis = 'Community-acquired pneumonia',
    admission_type = 'EMERGENCY',
    diet_orders = 'DIABETIC',
    activity_level = 'BED_REST',
    ward_id = '11111111-1111-1111-1111-111111111101',
    bed_id = '22222222-2222-2222-2222-222222222201'
WHERE admission_ref = 'f2000000-0000-0000-0000-000000000001';

INSERT INTO inpatient.care_plan (plan_id, tenant_id, subject_cpid, admission_ref, title, plan_type, status, created_by)
VALUES ('c3000000-0000-0000-0000-000000000001',
        '00000000-0000-4000-8000-000000000001',
        'CPID-ZW-00001',
        'f2000000-0000-0000-0000-000000000001',
        'Nursing care plan — pneumonia',
        'NURSING', 'ACTIVE', 'Sr. Moyo')
ON CONFLICT (plan_id) DO NOTHING;

INSERT INTO inpatient.care_plan_goal (goal_id, plan_id, category, description, status)
VALUES ('c3000000-0000-0000-0000-000000000002',
        'c3000000-0000-0000-0000-000000000001',
        'CLINICAL', 'Maintain SpO2 >= 94% on supplemental O2', 'IN_PROGRESS')
ON CONFLICT (goal_id) DO NOTHING;

INSERT INTO inpatient.care_plan_intervention (intervention_id, plan_id, goal_id, category, description, frequency, responsible_role, status)
VALUES ('c3000000-0000-0000-0000-000000000003',
        'c3000000-0000-0000-0000-000000000001',
        'c3000000-0000-0000-0000-000000000002',
        'MONITORING', 'Record observations every 4 hours', '4-hourly', 'NURSE', 'ACTIVE')
ON CONFLICT (intervention_id) DO NOTHING;

INSERT INTO inpatient.fluid_balance_record (record_id, tenant_id, subject_cpid, admission_ref, entry_type, category, volume_ml, recorded_by)
VALUES ('c3000000-0000-0000-0000-000000000004',
        '00000000-0000-4000-8000-000000000001',
        'CPID-ZW-00001', 'f2000000-0000-0000-0000-000000000001',
        'INTAKE', 'IV', 500, 'Sr. Moyo')
ON CONFLICT (record_id) DO NOTHING;

INSERT INTO inpatient.clinical_chart_entry (entry_id, tenant_id, subject_cpid, admission_ref, chart_type, parameters_json, recorded_by)
VALUES ('c3000000-0000-0000-0000-000000000005',
        '00000000-0000-4000-8000-000000000001',
        'CPID-ZW-00001', 'f2000000-0000-0000-0000-000000000001',
        'obs-chart', '{"TIME":"06:00","RR":18,"SPO2":97,"TEMP":36.8,"SBP":125,"DBP":78,"HR":72,"NEWS_SCORE":1}',
        'Sr. Moyo')
ON CONFLICT (entry_id) DO NOTHING;

INSERT INTO inpatient.mar_entry (mar_id, tenant_id, subject_cpid, admission_ref, drug_name, dose, route, time_due, status)
VALUES ('c3000000-0000-0000-0000-000000000006',
        '00000000-0000-4000-8000-000000000001',
        'CPID-ZW-00001', 'f2000000-0000-0000-0000-000000000001',
        'Ceftriaxone 1 g', '1 g', 'IV', '08:00', 'SCHEDULED')
ON CONFLICT (mar_id) DO NOTHING;
