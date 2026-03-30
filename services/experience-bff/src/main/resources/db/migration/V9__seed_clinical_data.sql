-- ============================================================================
-- Experience BFF — Full Clinical Seed Data
-- V9: Aligns tenant / facility IDs, seeds encounters, vitals, prescriptions,
--     lab orders/results, conditions, allergies, immunizations, clinical notes,
--     shifts, referrals, reminders, and clinical timeline
-- UUID prefix key (all hex):
--   e0000001 = shift        ec000001 = encounter    ab000001 = vitals
--   de000001 = prescription 1ab00001 = lab order    1bc00001 = lab result
--   cde00001 = condition    a11e0001 = allergy      face0001 = immunization
--   cafe0001 = clinical note beef0001 = referral    deed0001 = reminder
--   f0de0001 = timeline
-- ============================================================================

-- ── 1. Align existing patient + admin_user rows to tenant-moh-zw ───────────
UPDATE patients SET
    tenant_id   = 'tenant-moh-zw',
    facility_id = 'a1b2c3d4-0001-4000-8000-000000000001'
WHERE tenant_id = 'moh-zw'
  AND id IN (
    'a1000000-0000-0000-0000-000000000001',
    'a1000000-0000-0000-0000-000000000002',
    'a1000000-0000-0000-0000-000000000003'
  );

UPDATE patients SET
    tenant_id   = 'tenant-moh-zw',
    facility_id = 'a1b2c3d4-0002-4000-8000-000000000002'
WHERE tenant_id = 'moh-zw'
  AND id IN (
    'a1000000-0000-0000-0000-000000000004',
    'a1000000-0000-0000-0000-000000000005'
  );

UPDATE admin_users SET
    tenant_id   = 'tenant-moh-zw',
    facility_id = 'a1b2c3d4-0001-4000-8000-000000000001'
WHERE tenant_id = 'moh-zw';

UPDATE queue_entries SET
    tenant_id   = 'tenant-moh-zw',
    facility_id = 'a1b2c3d4-0001-4000-8000-000000000001'
WHERE tenant_id = 'moh-zw';

UPDATE registry_providers SET tenant_id = 'tenant-moh-zw' WHERE tenant_id = 'moh-zw';

UPDATE inventory_items SET
    tenant_id   = 'tenant-moh-zw',
    facility_id = 'a1b2c3d4-0001-4000-8000-000000000001'
WHERE tenant_id = 'moh-zw';

-- ── 2. Shift ────────────────────────────────────────────────────────────────
INSERT INTO shifts (id, tenant_id, facility_id, workspace_id, user_id, status, started_at)
VALUES (
    'e0000001-0000-0000-0000-000000000001',
    'tenant-moh-zw',
    'a1b2c3d4-0001-4000-8000-000000000001',
    'b1c2d3e4-0001-4000-8000-000000000001',
    'dr.mapfumo',
    'ACTIVE',
    NOW() - INTERVAL '4 hours'
) ON CONFLICT DO NOTHING;

-- ── 3. Encounters ────────────────────────────────────────────────────────────
INSERT INTO encounters (id, tenant_id, facility_id, patient_id, shift_id, encounter_type, status, chief_complaint, diagnosis, started_at)
VALUES
  ('ec000001-0000-0000-0000-000000000001','tenant-moh-zw','a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000001','e0000001-0000-0000-0000-000000000001',
   'OUTPATIENT','COMPLETED','Persistent cough and fever for 5 days',
   'J06.9 - Acute upper respiratory infection',
   NOW() - INTERVAL '3 hours'),
  ('ec000001-0000-0000-0000-000000000002','tenant-moh-zw','a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000002','e0000001-0000-0000-0000-000000000001',
   'OUTPATIENT','IN_PROGRESS','Routine antenatal check, 28 weeks gestation',
   'Z34.2 - Supervision of normal second trimester pregnancy',
   NOW() - INTERVAL '1 hour'),
  ('ec000001-0000-0000-0000-000000000003','tenant-moh-zw','a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000003','e0000001-0000-0000-0000-000000000001',
   'OUTPATIENT','COMPLETED','Headache and high blood pressure reading at home',
   'I10 - Essential (primary) hypertension',
   NOW() - INTERVAL '2 days'),
  ('ec000001-0000-0000-0000-000000000004','tenant-moh-zw','a1b2c3d4-0002-4000-8000-000000000002',
   'a1000000-0000-0000-0000-000000000004',NULL,
   'OUTPATIENT','COMPLETED','Diabetes mellitus review and medication adjustment',
   'E11.9 - Type 2 diabetes mellitus without complications',
   NOW() - INTERVAL '7 days'),
  ('ec000001-0000-0000-0000-000000000005','tenant-moh-zw','a1b2c3d4-0002-4000-8000-000000000002',
   'a1000000-0000-0000-0000-000000000005',NULL,
   'OUTPATIENT','COMPLETED','Lower back pain, 2 weeks duration',
   'M54.5 - Low back pain',
   NOW() - INTERVAL '14 days')
ON CONFLICT DO NOTHING;

-- ── 4. Vitals ────────────────────────────────────────────────────────────────
INSERT INTO vitals_records (id, tenant_id, patient_id, encounter_id, recorded_by,
  systolic, diastolic, heart_rate, temperature, respiratory_rate,
  oxygen_saturation, weight, height, bmi, pain_score, recorded_at)
VALUES
  ('ab000001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'dr.mapfumo',118,78,92,38.4,18,97,72,175,23.5,2,NOW()-INTERVAL '3 hours'),
  ('ab000001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000002','ec000001-0000-0000-0000-000000000002',
   'nurse.chienda',110,70,80,36.8,16,99,68,162,25.9,0,NOW()-INTERVAL '1 hour'),
  ('ab000001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'dr.mapfumo',158,96,88,36.6,16,98,85,178,26.8,4,NOW()-INTERVAL '2 days'),
  ('ab000001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'nurse.chienda',134,84,76,36.9,14,98,90,165,33.1,1,NOW()-INTERVAL '7 days'),
  ('ab000001-0000-0000-0000-000000000005','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000005','ec000001-0000-0000-0000-000000000005',
   'dr.mapfumo',120,80,72,36.5,15,99,78,180,24.1,6,NOW()-INTERVAL '14 days')
ON CONFLICT DO NOTHING;

-- ── 5. Prescriptions ─────────────────────────────────────────────────────────
INSERT INTO prescriptions (id, tenant_id, facility_id, patient_id, encounter_id,
  medication_name, dosage, frequency, duration, quantity, status, prescribed_by)
VALUES
  ('de000001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'Amoxicillin 500mg','500mg','Three times daily','7 days',21,'DISPENSED','dr.mapfumo'),
  ('de000001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'Paracetamol 500mg','500mg-1000mg','Every 6-8 hours as needed','5 days',20,'DISPENSED','dr.mapfumo'),
  ('de000001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'Amlodipine 5mg','5mg','Once daily','30 days',30,'PENDING','dr.mapfumo'),
  ('de000001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1b2c3d4-0002-4000-8000-000000000002',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'Metformin 850mg','850mg','Twice daily with meals','90 days',180,'DISPENSED','nurse.chienda')
ON CONFLICT DO NOTHING;

-- ── 6. Lab orders + results ──────────────────────────────────────────────────
INSERT INTO lab_orders (id, tenant_id, facility_id, patient_id, encounter_id,
  category, test_name, test_code, status, ordered_by, ordered_by_name, resulted_at)
VALUES
  ('1ab00001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'HAEMATOLOGY','Full Blood Count','FBC','RESULTED','dr.mapfumo','Dr. Tendai Mapfumo',NOW()-INTERVAL '1 day 22 hours'),
  ('1ab00001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1b2c3d4-0001-4000-8000-000000000001',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'CHEMISTRY','Urea and Electrolytes','U&E','RESULTED','dr.mapfumo','Dr. Tendai Mapfumo',NOW()-INTERVAL '1 day 22 hours'),
  ('1ab00001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1b2c3d4-0002-4000-8000-000000000002',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'CHEMISTRY','HbA1c','HBA1C','RESULTED','nurse.chienda','Nurse Chienda',NOW()-INTERVAL '6 days 18 hours'),
  ('1ab00001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1b2c3d4-0002-4000-8000-000000000002',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'CHEMISTRY','Fasting Blood Glucose','FBG','RESULTED','nurse.chienda','Nurse Chienda',NOW()-INTERVAL '6 days 18 hours')
ON CONFLICT DO NOTHING;

INSERT INTO lab_order_results (id, tenant_id, lab_order_id, analyte_name, analyte_code,
  value, unit, reference_low, reference_high, interpretation, abnormal_flag)
VALUES
  ('1bc00001-0000-0000-0000-000000000001','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000001','Haemoglobin','HGB','14.2','g/dL','13.0','17.0','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000002','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000001','White Blood Cells','WBC','8.5','x10e9/L','4.0','11.0','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000003','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000001','Platelets','PLT','220','x10e9/L','150','400','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000004','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000002','Sodium','NA','138','mmol/L','136','145','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000005','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000002','Potassium','K','3.8','mmol/L','3.5','5.0','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000006','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000002','Creatinine','CREAT','1.1','mg/dL','0.6','1.2','Normal',FALSE),
  ('1bc00001-0000-0000-0000-000000000007','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000003','HbA1c','HBA1C','8.2','%','4.0','6.4','High',TRUE),
  ('1bc00001-0000-0000-0000-000000000008','tenant-moh-zw',
   '1ab00001-0000-0000-0000-000000000004','Fasting Glucose','FBG','9.4','mmol/L','3.9','6.1','High',TRUE)
ON CONFLICT DO NOTHING;

-- ── 7. Conditions (problem list) ─────────────────────────────────────────────
INSERT INTO conditions (id, tenant_id, patient_id, encounter_id,
  condition_name, icd_code, category, clinical_status, severity, onset_date, recorded_by)
VALUES
  ('cde00001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'Acute upper respiratory infection','J06.9','ENCOUNTER_DIAGNOSIS','RESOLVED','MILD',
   CURRENT_DATE - INTERVAL '5 days','dr.mapfumo'),
  ('cde00001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'Essential hypertension','I10','CHRONIC_CONDITION','ACTIVE','MODERATE',
   CURRENT_DATE - INTERVAL '2 years','dr.mapfumo'),
  ('cde00001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'Type 2 diabetes mellitus','E11.9','CHRONIC_CONDITION','ACTIVE','MODERATE',
   CURRENT_DATE - INTERVAL '5 years','nurse.chienda'),
  ('cde00001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000005','ec000001-0000-0000-0000-000000000005',
   'Low back pain','M54.5','ENCOUNTER_DIAGNOSIS','ACTIVE','MODERATE',
   CURRENT_DATE - INTERVAL '14 days','dr.mapfumo')
ON CONFLICT DO NOTHING;

-- ── 8. Allergies ─────────────────────────────────────────────────────────────
INSERT INTO allergies (id, tenant_id, patient_id, allergen, allergen_type,
  reaction, severity, status, onset_date, recorded_by)
VALUES
  ('a11e0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','Penicillin','MEDICATION',
   'Rash and urticaria','MODERATE','ACTIVE',
   CURRENT_DATE - INTERVAL '3 years','dr.mapfumo'),
  ('a11e0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','Sulphonamides','MEDICATION',
   'Anaphylaxis','SEVERE','ACTIVE',
   CURRENT_DATE - INTERVAL '10 years','nurse.chienda'),
  ('a11e0001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','Dust mites','ENVIRONMENTAL',
   'Rhinitis and wheezing','MILD','ACTIVE',
   CURRENT_DATE - INTERVAL '1 year','dr.mapfumo')
ON CONFLICT DO NOTHING;

-- ── 9. Immunizations ─────────────────────────────────────────────────────────
INSERT INTO immunizations (id, tenant_id, patient_id, encounter_id, vaccine_name,
  vaccine_code, dose_number, dose_sequence, site, route, status, administered_by, administered_at)
VALUES
  ('face0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000002','ec000001-0000-0000-0000-000000000002',
   'Tetanus Toxoid','TT',2,'2 of 3','Left deltoid','IM','COMPLETED',
   'nurse.chienda',NOW()-INTERVAL '1 hour'),
  ('face0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001',NULL,
   'COVID-19 Vaccine Sinopharm','COVID19-SINOPHARM',2,'2 of 2','Right deltoid','IM','COMPLETED',
   'nurse.chienda',NOW()-INTERVAL '2 years'),
  ('face0001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000005',NULL,
   'Influenza Vaccine','FLU',1,'Annual','Left deltoid','IM','COMPLETED',
   'nurse.chienda',NOW()-INTERVAL '6 months')
ON CONFLICT DO NOTHING;

-- ── 10. Clinical Notes (SOAP) ────────────────────────────────────────────────
INSERT INTO clinical_notes (id, tenant_id, patient_id, encounter_id, note_type,
  subjective, objective, assessment, plan, author_id, author_name, status, signed_at)
VALUES
  ('cafe0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'SOAP',
   'Patient presents with 5-day history of cough, fever, and general malaise. Temp 38.4 at triage.',
   'Throat erythematous. Bilateral tonsillar enlargement. No lymphadenopathy. Chest clear on auscultation.',
   'Acute upper respiratory tract infection - likely viral with secondary bacterial involvement.',
   'Amoxicillin 500mg TDS x 7 days. Paracetamol PRN. Rest and adequate hydration. Return if no improvement in 48 hrs.',
   'dr.mapfumo','Dr. Tendai Mapfumo','SIGNED',NOW()-INTERVAL '2 hours 45 minutes'),
  ('cafe0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'SOAP',
   'Patient reports recurrent headaches for 2 weeks. Home BP readings consistently above 150/90.',
   'BP 158/96, HR 88, RR 16. No papilloedema. Heart sounds normal. No ankle oedema.',
   'Stage 1 hypertension. Possible white-coat effect cannot be excluded. Renal function normal.',
   'Initiate Amlodipine 5mg OD. Low-sodium diet counselling. Reduce alcohol. ABPM in 4 weeks. Follow-up in 1 month.',
   'dr.mapfumo','Dr. Tendai Mapfumo','SIGNED',NOW()-INTERVAL '1 day 22 hours')
ON CONFLICT DO NOTHING;

-- ── 11. Referrals ────────────────────────────────────────────────────────────
INSERT INTO referrals (id, tenant_id, patient_id, encounter_id, referral_type,
  specialty, referred_to, referred_to_facility, reason, urgency, status,
  clinical_summary, referred_by, referred_by_name)
VALUES
  ('beef0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'SPECIALIST','Cardiology','Dr. S. Nyamukapa',
   'Parirenyatwa Group of Hospitals',
   'Stage 1 hypertension with persistent elevation. Cardiology review and echocardiogram requested.',
   'ROUTINE','PENDING',
   'Patient: Tinashe Madziva. BP 158/96. On Amlodipine 5mg OD initiated today. FBC and U&E normal.',
   'dr.mapfumo','Dr. Tendai Mapfumo'),
  ('beef0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'SPECIALIST','Endocrinology','Dr. A. Choto',
   'Parirenyatwa Group of Hospitals',
   'HbA1c 8.2% despite Metformin 850mg BD. Specialist review for insulin initiation.',
   'ROUTINE','PENDING',
   'Patient: Nyasha Sibanda. DM2 x 5 years. FBG 9.4 mmol/L. HbA1c 8.2%.',
   'nurse.chienda','Nurse Chienda')
ON CONFLICT DO NOTHING;

-- ── 12. Reminders ────────────────────────────────────────────────────────────
INSERT INTO reminders (id, tenant_id, patient_id, title, reminder_type,
  description, scheduled_at, recurrence, enabled)
VALUES
  ('deed0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003',
   'Blood pressure review - 1 month follow-up','APPOINTMENT',
   'Follow-up for hypertension management. ABPM results review and medication adjustment if needed.',
   NOW() + INTERVAL '30 days','ONCE',TRUE),
  ('deed0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004',
   'HbA1c recheck in 3 months','LAB_RESULT',
   'Repeat HbA1c and FBG after insulin initiation review with endocrinologist.',
   NOW() + INTERVAL '90 days','ONCE',TRUE),
  ('deed0001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000002',
   'Antenatal visit - 32 weeks','APPOINTMENT',
   'Routine antenatal visit. FBC, urinalysis, fundal height measurement.',
   NOW() + INTERVAL '28 days','ONCE',TRUE),
  ('deed0001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004',
   'Take Metformin with evening meal','MEDICATION',
   'Evening dose reminder for Metformin 850mg with dinner.',
   NOW() + INTERVAL '1 day','DAILY',TRUE)
ON CONFLICT DO NOTHING;

-- ── 13. Clinical Timeline ────────────────────────────────────────────────────
INSERT INTO clinical_timeline (id, tenant_id, patient_id, encounter_id, event_type,
  title, description, source_type, source_id, actor_id, actor_name, occurred_at)
VALUES
  ('f0de0001-0000-0000-0000-000000000001','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'ENCOUNTER','OPD Consultation','Acute upper respiratory infection. Amoxicillin prescribed.',
   'ENCOUNTER','ec000001-0000-0000-0000-000000000001','dr.mapfumo','Dr. Tendai Mapfumo',
   NOW()-INTERVAL '3 hours'),
  ('f0de0001-0000-0000-0000-000000000002','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000001','ec000001-0000-0000-0000-000000000001',
   'PRESCRIPTION','Prescription issued','Amoxicillin 500mg TDS x 7 days + Paracetamol PRN',
   'PRESCRIPTION','de000001-0000-0000-0000-000000000001','dr.mapfumo','Dr. Tendai Mapfumo',
   NOW()-INTERVAL '2 hours 50 minutes'),
  ('f0de0001-0000-0000-0000-000000000003','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'ENCOUNTER','OPD Consultation','Hypertension diagnosed. Amlodipine initiated.',
   'ENCOUNTER','ec000001-0000-0000-0000-000000000003','dr.mapfumo','Dr. Tendai Mapfumo',
   NOW()-INTERVAL '2 days'),
  ('f0de0001-0000-0000-0000-000000000004','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'LAB_RESULT','FBC and U&E resulted','All values within normal limits.',
   'LAB_ORDER','1ab00001-0000-0000-0000-000000000001','dr.mapfumo','Dr. Tendai Mapfumo',
   NOW()-INTERVAL '1 day 20 hours'),
  ('f0de0001-0000-0000-0000-000000000005','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000003','ec000001-0000-0000-0000-000000000003',
   'REFERRAL','Cardiology referral','Referred to Dr. Nyamukapa at Parirenyatwa.',
   'REFERRAL','beef0001-0000-0000-0000-000000000001','dr.mapfumo','Dr. Tendai Mapfumo',
   NOW()-INTERVAL '1 day 22 hours'),
  ('f0de0001-0000-0000-0000-000000000006','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'ENCOUNTER','OPD Consultation','DM2 review. HbA1c elevated at 8.2%. Endocrinology referral.',
   'ENCOUNTER','ec000001-0000-0000-0000-000000000004','nurse.chienda','Nurse Chienda',
   NOW()-INTERVAL '7 days'),
  ('f0de0001-0000-0000-0000-000000000007','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000004','ec000001-0000-0000-0000-000000000004',
   'LAB_RESULT','HbA1c and FBG resulted','HbA1c 8.2% (high). FBG 9.4 mmol/L (high).',
   'LAB_ORDER','1ab00001-0000-0000-0000-000000000003','nurse.chienda','Nurse Chienda',
   NOW()-INTERVAL '6 days 18 hours'),
  ('f0de0001-0000-0000-0000-000000000008','tenant-moh-zw',
   'a1000000-0000-0000-0000-000000000002','ec000001-0000-0000-0000-000000000002',
   'ENCOUNTER','Antenatal Visit','28-week routine check. TT2 administered.',
   'ENCOUNTER','ec000001-0000-0000-0000-000000000002','nurse.chienda','Nurse Chienda',
   NOW()-INTERVAL '1 hour')
ON CONFLICT DO NOTHING;
