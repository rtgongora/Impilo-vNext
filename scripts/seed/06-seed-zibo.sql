-- =============================================================================
-- ZIBO — Terminology & Classification Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Seeds: core FHIR CodeSystems and ValueSets aligned to Zimbabwe EDLIZ/DHIS2
-- Covers: diagnoses (ICD-10), procedures, lab tests, medications, encounter types
-- =============================================================================

\c zibo

INSERT INTO zibo_artifacts
    (artifact_id, tenant_id, fhir_type, canonical_url, version, name, title,
     description, status, content_hash, publisher, jurisdiction, created_by, content_json)
VALUES
    ('d0000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     'CODE_SYSTEM',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/icd10-zw',
     '10-2025', 'ICD10ZW', 'ICD-10 Zimbabwe Edition',
     'International Classification of Diseases 10th Revision — Zimbabwe national edition',
     'ACTIVE',
     'dev-hash-icd10-zw-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"CodeSystem","id":"icd10-zw","url":"https://zibo.mohcc.gov.zw/fhir/CodeSystem/icd10-zw","version":"10-2025","name":"ICD10ZW","status":"active","content":"fragment","concept":[{"code":"J06.9","display":"Acute upper respiratory infection, unspecified"},{"code":"A09","display":"Other gastroenteritis and colitis of infectious and unspecified origin"},{"code":"B54","display":"Unspecified malaria"},{"code":"J18.9","display":"Pneumonia, unspecified organism"},{"code":"A15.0","display":"Tuberculosis of lung"},{"code":"B20","display":"Human immunodeficiency virus [HIV] disease"},{"code":"E11.9","display":"Type 2 diabetes mellitus without complications"},{"code":"I10","display":"Essential (primary) hypertension"},{"code":"O80","display":"Encounter for full-term uncomplicated delivery"},{"code":"Z34.0","display":"Encounter for supervision of normal first pregnancy"},{"code":"K29.7","display":"Gastritis, unspecified"},{"code":"M54.5","display":"Low back pain"},{"code":"F32.9","display":"Major depressive disorder, single episode, unspecified"},{"code":"J45.9","display":"Asthma, unspecified"},{"code":"S00-T98","display":"Injury, poisoning and certain other consequences of external causes"}]}'::jsonb),

    ('d0000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000001',
     'CODE_SYSTEM',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/edliz-medications',
     '2025.1', 'EDLIZMedications', 'EDLIZ Essential Medicines List Zimbabwe',
     'Zimbabwe Essential Drugs List and Formulary — 2025 edition medication codes',
     'ACTIVE',
     'dev-hash-edliz-meds-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"CodeSystem","id":"edliz-medications","url":"https://zibo.mohcc.gov.zw/fhir/CodeSystem/edliz-medications","version":"2025.1","name":"EDLIZMedications","status":"active","content":"complete","concept":[{"code":"PARA-500","display":"Paracetamol 500mg tablet"},{"code":"AMOX-250","display":"Amoxicillin 250mg capsule"},{"code":"AMOX-500","display":"Amoxicillin 500mg capsule"},{"code":"COTRI-480","display":"Cotrimoxazole 480mg tablet"},{"code":"METRO-200","display":"Metronidazole 200mg tablet"},{"code":"METRO-500","display":"Metronidazole 500mg tablet"},{"code":"ORS-SAC","display":"Oral Rehydration Salts sachet"},{"code":"ZINC-20","display":"Zinc sulphate 20mg tablet"},{"code":"ART-LUMI","display":"Artemether-Lumefantrine 20/120mg tablet"},{"code":"INDOM-25","display":"Indomethacin 25mg capsule"},{"code":"ATEN-50","display":"Atenolol 50mg tablet"},{"code":"AMLOD-5","display":"Amlodipine 5mg tablet"},{"code":"METF-500","display":"Metformin 500mg tablet"},{"code":"GLIBEN-5","display":"Glibenclamide 5mg tablet"},{"code":"SALBU-INH","display":"Salbutamol 100mcg inhaler"},{"code":"PRED-5","display":"Prednisolone 5mg tablet"},{"code":"FERR-200","display":"Ferrous sulphate 200mg tablet"},{"code":"FOLIC-5","display":"Folic acid 5mg tablet"},{"code":"DOXY-100","display":"Doxycycline 100mg capsule"},{"code":"CIPROF-500","display":"Ciprofloxacin 500mg tablet"}]}'::jsonb),

    ('d0000000-0000-4000-8000-000000000003',
     '00000000-0000-4000-8000-000000000001',
     'CODE_SYSTEM',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/lab-tests-zw',
     '2025.1', 'LabTestsZW', 'Zimbabwe Laboratory Tests Code System',
     'National laboratory test codes for Zimbabwe — LOINC-mapped where available',
     'ACTIVE',
     'dev-hash-labtests-zw-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"CodeSystem","id":"lab-tests-zw","url":"https://zibo.mohcc.gov.zw/fhir/CodeSystem/lab-tests-zw","version":"2025.1","name":"LabTestsZW","status":"active","content":"complete","concept":[{"code":"FBC","display":"Full Blood Count","property":[{"code":"loinc","valueCode":"58410-2"}]},{"code":"RBS","display":"Random Blood Sugar","property":[{"code":"loinc","valueCode":"2345-7"}]},{"code":"FBS","display":"Fasting Blood Sugar","property":[{"code":"loinc","valueCode":"1558-6"}]},{"code":"HBA1C","display":"Glycated Haemoglobin (HbA1c)","property":[{"code":"loinc","valueCode":"4548-4"}]},{"code":"UREA-CREAT","display":"Urea and Creatinine"},{"code":"LFT","display":"Liver Function Tests"},{"code":"MALA-RDT","display":"Malaria Rapid Diagnostic Test"},{"code":"HIV-RDT","display":"HIV Rapid Test"},{"code":"CD4","display":"CD4 Count"},{"code":"VL","display":"HIV Viral Load"},{"code":"SPUTUM-AFB","display":"Sputum for AFB (TB)"},{"code":"GEN-XPERT","display":"GeneXpert MTB/RIF"},{"code":"URINE-MC-S","display":"Urine Microscopy Culture and Sensitivity"},{"code":"PREG-TEST","display":"Urine Pregnancy Test"},{"code":"HB","display":"Haemoglobin"},{"code":"LIPID","display":"Lipid Profile"},{"code":"TFT","display":"Thyroid Function Tests"},{"code":"WIDAL","display":"Widal Test (Typhoid)"}]}'::jsonb),

    ('d0000000-0000-4000-8000-000000000004',
     '00000000-0000-4000-8000-000000000001',
     'VALUE_SET',
     'https://zibo.mohcc.gov.zw/fhir/ValueSet/encounter-types-zw',
     '2025.1', 'EncounterTypesZW', 'Zimbabwe Encounter Type Value Set',
     'Allowed encounter type codes for clinical encounters in Zimbabwe',
     'ACTIVE',
     'dev-hash-encounter-types-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"ValueSet","id":"encounter-types-zw","url":"https://zibo.mohcc.gov.zw/fhir/ValueSet/encounter-types-zw","version":"2025.1","name":"EncounterTypesZW","status":"active","compose":{"include":[{"system":"http://terminology.hl7.org/CodeSystem/v3-ActCode","concept":[{"code":"AMB","display":"Ambulatory / Outpatient"},{"code":"EMER","display":"Emergency"},{"code":"IMP","display":"Inpatient"},{"code":"SS","display":"Short Stay"},{"code":"VR","display":"Virtual / Teleconsult"},{"code":"HH","display":"Home Health"},{"code":"PRENC","display":"Pre-natal Care"}]}]}}'::jsonb),

    ('d0000000-0000-4000-8000-000000000005',
     '00000000-0000-4000-8000-000000000001',
     'VALUE_SET',
     'https://zibo.mohcc.gov.zw/fhir/ValueSet/workspace-types-zw',
     '2025.1', 'WorkspaceTypesZW', 'Zimbabwe Clinical Workspace Types',
     'Validated workspace type codes used in TUSO facility registry',
     'ACTIVE',
     'dev-hash-workspace-types-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"ValueSet","id":"workspace-types-zw","url":"https://zibo.mohcc.gov.zw/fhir/ValueSet/workspace-types-zw","version":"2025.1","name":"WorkspaceTypesZW","status":"active","compose":{"include":[{"system":"https://zibo.mohcc.gov.zw/fhir/CodeSystem/workspace-types","concept":[{"code":"OPD","display":"Outpatient Department"},{"code":"EMERGENCY","display":"Emergency Unit"},{"code":"IPD","display":"Inpatient Ward"},{"code":"PHARMACY","display":"Pharmacy"},{"code":"LABORATORY","display":"Laboratory"},{"code":"RADIOLOGY","display":"Radiology"},{"code":"THEATRE","display":"Operating Theatre"},{"code":"ICU","display":"Intensive Care Unit"},{"code":"MATERNITY","display":"Maternity Unit"},{"code":"ANTENATAL","display":"Antenatal Clinic"},{"code":"ART","display":"ART Clinic"},{"code":"TB","display":"TB Clinic"},{"code":"NUTRITION","display":"Nutrition Clinic"}]}]}}'::jsonb),

    ('d0000000-0000-4000-8000-000000000006',
     '00000000-0000-4000-8000-000000000001',
     'CODE_SYSTEM',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/provider-specialties-zw',
     '2025.1', 'ProviderSpecialtiesZW', 'Zimbabwe Provider Specialty Codes',
     'Clinical specialty codes for healthcare providers registered in VARAPI',
     'ACTIVE',
     'dev-hash-provider-specialties-placeholder',
     'MOHCC Zimbabwe', 'ZW', 'system',
     '{"resourceType":"CodeSystem","id":"provider-specialties-zw","url":"https://zibo.mohcc.gov.zw/fhir/CodeSystem/provider-specialties-zw","version":"2025.1","name":"ProviderSpecialtiesZW","status":"active","content":"complete","concept":[{"code":"GP","display":"General Practice"},{"code":"SURG","display":"General Surgery"},{"code":"ORTH","display":"Orthopaedics"},{"code":"OBS","display":"Obstetrics and Gynaecology"},{"code":"PAED","display":"Paediatrics"},{"code":"INT-MED","display":"Internal Medicine"},{"code":"RADIOL","display":"Radiology"},{"code":"ANAES","display":"Anaesthesia"},{"code":"PSYCH","display":"Psychiatry"},{"code":"OPTH","display":"Ophthalmology"},{"code":"ENT","display":"Ear, Nose and Throat"},{"code":"DERM","display":"Dermatology"},{"code":"PCN","display":"Primary Care Nursing"},{"code":"MHNS","display":"Mental Health Nursing"},{"code":"CLIN","display":"Clinical Pharmacy"},{"code":"PHYSIO","display":"Physiotherapy"},{"code":"DIETIT","display":"Dietetics"},{"code":"LAB-SCI","display":"Laboratory Science"}]}'::jsonb);

INSERT INTO zibo_packs
    (pack_id, tenant_id, name, description, version, status, created_by)
VALUES
    ('f0000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000001',
     'Zimbabwe Core Clinical Pack',
     'Core terminology pack: ICD-10, EDLIZ medications, lab tests, encounter types',
     '2025.1', 'ACTIVE', 'system'),

    ('f0000000-0000-4000-8000-000000000002',
     '00000000-0000-4000-8000-000000000001',
     'Zimbabwe Provider Registry Pack',
     'Provider specialty codes and workspace type value sets',
     '2025.1', 'ACTIVE', 'system');

INSERT INTO zibo_pack_artifacts (pack_id, artifact_id)
VALUES
    ('f0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001'),
    ('f0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000002'),
    ('f0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000003'),
    ('f0000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000004'),
    ('f0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000005'),
    ('f0000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000006');

INSERT INTO zibo_assignments
    (tenant_id, scope_type, scope_id, pack_id, pack_version, policy_mode, assigned_by)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'TENANT', '00000000-0000-4000-8000-000000000001',
     'f0000000-0000-4000-8000-000000000001', '2025.1', 'LENIENT', 'system'),

    ('00000000-0000-4000-8000-000000000001',
     'TENANT', '00000000-0000-4000-8000-000000000001',
     'f0000000-0000-4000-8000-000000000002', '2025.1', 'LENIENT', 'system');

INSERT INTO zibo_mappings_index
    (tenant_id, source_system, source_code, source_display,
     target_system, target_code, target_display, equivalence)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/icd10-zw', 'J06.9',
     'Acute upper respiratory infection, unspecified',
     'http://hl7.org/fhir/sid/icd-10', 'J06.9',
     'Acute upper respiratory infections of multiple and unspecified sites',
     'equivalent'),

    ('00000000-0000-4000-8000-000000000001',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/edliz-medications', 'PARA-500',
     'Paracetamol 500mg tablet',
     'http://snomed.info/sct', '387517004',
     'Paracetamol (substance)',
     'wider'),

    ('00000000-0000-4000-8000-000000000001',
     'https://zibo.mohcc.gov.zw/fhir/CodeSystem/lab-tests-zw', 'FBC',
     'Full Blood Count',
     'http://loinc.org', '58410-2',
     'CBC panel - Blood by Automated count',
     'equivalent');
