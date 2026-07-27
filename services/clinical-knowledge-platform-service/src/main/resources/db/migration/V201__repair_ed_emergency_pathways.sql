-- Repair the eight shell ED_* pathways and retire the qSOFA sepsis screen (emergency pack W4b).
--
-- V005 seeded eleven ED_* pathways but only three (ED_SEPSIS, ED_ANAPHYLAXIS, ED_TRAUMA) carried
-- any pathway_steps, and four cited an unrelated source section — ED_ECTOPIC and ED_OBSTETRIC both
-- pointed at "Uncomplicated lower UTI — adult woman", ED_STATUS_EPILEPTICUS at "Fever", and
-- ED_RESP_FAILURE at "Asthma — chronic management". A clinician could start any of the eleven and
-- EdProtocolCatalog recommended all of them, so an empty shell read as a complete pathway and a
-- ruptured ectopic opened a UTI leaflet. The stepless-pathway defect that let those sessions record
-- a false COMPLETION was fixed in the CKP service itself (PathwaySessionService) and its historical
-- rows voided in V200; this migration gives the eight shells real content so they stop being shells.
--
-- Repair-in-place, not a version bump: sessions are started by pathway UUID (findByIdWithSteps) and
-- the catalogue lists ACTIVE pathways, so the same rows simply gain steps and correct citations —
-- no override record references pathway-step content, so there is nothing to preserve append-only.
--
-- PROVENANCE HONESTY. There is no WHO emergency-unit clinical guideline for ACS or stroke (WHO's
-- cardiovascular offering is the primary-care HEARTS package), and no Zimbabwe national emergency-care
-- guideline could be sourced. So the new reference document below is declared ENGINEERING_SEED with
-- issuing authority "PENDING_MOHCC_RATIFICATION" and MUST NOT be presented as WHO-derived. The
-- ABCDE/danger-sign layer follows WHO Basic Emergency Care (EMS.BEC.ABCDE); the sepsis screen follows
-- Surviving Sepsis Campaign 2026 (EMS.SSC26.SCREENING) which recommends NEWS/NEWS2/MEWS/SIRS over
-- qSOFA. Content hash is null because this is a hand-authored seed, not an ingested source.

-- ---------------------------------------------------------------------------
-- 1. A reference document for the emergency pathway seeds, honestly labelled.
-- ---------------------------------------------------------------------------
INSERT INTO clinical.source_documents
    (id, title, source_type, issuing_authority, effective_date, version_label, status, ingestion_status)
VALUES
('a0000001-0001-4001-8001-000000000200',
 'Emergency care pathway content — engineering seed (MoHCC ratification pending)',
 'CLINICAL_PATHWAY_SEED', 'PENDING_MOHCC_RATIFICATION', DATE '2026-07-27', 'seed-2026-07',
 'ACTIVE', 'SEED')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 2. Correct source sections for the four wrongly-cited pathways, plus a sepsis
--    screening section that names the EWS basis rather than qSOFA.
-- ---------------------------------------------------------------------------
INSERT INTO clinical.source_sections (id, document_id, page_number, section_title, section_path, raw_text)
VALUES
('b0000001-0001-4001-8001-000000000201', 'a0000001-0001-4001-8001-000000000200', 1,
 'Ectopic pregnancy — ED recognition and stabilisation', 'ed/ectopic',
 'Consider ectopic pregnancy in ANY woman of reproductive age presenting with abdominal or pelvic '
 || 'pain, PV bleeding, syncope or collapse — until a pregnancy test excludes pregnancy. Do a urine '
 || 'or serum beta-hCG. A positive test with pain or haemodynamic instability is a surgical emergency: '
 || 'resuscitate, do not delay for imaging in a shocked patient, and refer urgently to gynaecology/'
 || 'surgery. The trap is waiting for a "confirmed pregnancy" flag — reproductive age with these '
 || 'symptoms is the trigger, not a recorded pregnancy.'),
('b0000001-0001-4001-8001-000000000202', 'a0000001-0001-4001-8001-000000000200', 2,
 'Status epilepticus — timed benzodiazepine-first management', 'ed/status-epilepticus',
 'A seizure lasting >5 minutes, or repeated seizures without recovery, is status epilepticus. ABC, '
 || 'protect the airway, high-flow oxygen, check capillary glucose and give glucose (and thiamine in '
 || 'the malnourished/alcohol-dependent) if hypoglycaemic. First line at 5 minutes: a benzodiazepine — '
 || 'IV lorazepam or diazepam, or IM/buccal midazolam where no IV access. If seizures continue, '
 || 'second-line antiepileptic (phenytoin, valproate or levetiracetam) and prepare for airway support. '
 || 'The clock, not the drug list, is the point.'),
('b0000001-0001-4001-8001-000000000203', 'a0000001-0001-4001-8001-000000000200', 3,
 'Obstetric emergency — ED recognition before maternity handover', 'ed/obstetric',
 'Pregnant or recently-delivered woman who is acutely unwell: recognise and stabilise, then hand over '
 || 'to maternity (RMNP owns the obstetric continuum). Eclampsia/severe pre-eclampsia — magnesium '
 || 'sulfate and controlled blood-pressure lowering. Postpartum haemorrhage — uterine massage, '
 || 'uterotonics (oxytocin), tranexamic acid, resuscitate and escalate. Obstetric sepsis — sepsis '
 || 'bundle plus urgent obstetric review. Urgent obstetric referral in every case; the ED role is '
 || 'recognition and first stabilisation, not definitive management.'),
('b0000001-0001-4001-8001-000000000204', 'a0000001-0001-4001-8001-000000000200', 4,
 'Acute respiratory failure — ED stabilisation', 'ed/resp-failure',
 'ABCDE. Titrate oxygen to a target SpO2 (88-92% in those at risk of hypercapnia, otherwise 94-98%). '
 || 'Identify and treat the cause — asthma/COPD exacerbation, pneumonia, pulmonary oedema, pulmonary '
 || 'embolism, pneumothorax. Escalate early: consider non-invasive ventilation for type 2 failure and '
 || 'prepare for intubation if exhausted, obtunded or failing to improve. This is acute stabilisation, '
 || 'not the chronic-asthma maintenance pathway that was mistakenly cited before.'),
('b0000001-0001-4001-8001-000000000205', 'a0000001-0001-4001-8001-000000000200', 5,
 'Sepsis screening by early warning score, not qSOFA', 'ed/sepsis-screen',
 'Screen for sepsis with an aggregate early warning score (NEWS2/MEWS) computed server-side from the '
 || 'triage vital signs, escalating on a NEWS2 of 5 or more or any single parameter scoring 3 — this '
 || 'replaces the qSOFA >=2 screen, which Surviving Sepsis Campaign 2026 recommends against as a single '
 || 'screening tool on sensitivity grounds. A high score with suspected infection triggers the 1-hour '
 || 'Sepsis Six bundle. The score is a screen to prompt clinical assessment, never a substitute for it.')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- 3. Repoint the four wrongly-cited pathways to their correct sections.
-- ---------------------------------------------------------------------------
UPDATE clinical.pathway_definitions SET source_section_id = 'b0000001-0001-4001-8001-000000000201'
 WHERE id = 'f0000001-0001-4001-8001-000000000108'; -- ED_ECTOPIC   (was UTI)
UPDATE clinical.pathway_definitions SET source_section_id = 'b0000001-0001-4001-8001-000000000202'
 WHERE id = 'f0000001-0001-4001-8001-000000000109'; -- ED_STATUS_EPILEPTICUS (was Fever)
UPDATE clinical.pathway_definitions SET source_section_id = 'b0000001-0001-4001-8001-000000000203'
 WHERE id = 'f0000001-0001-4001-8001-000000000110'; -- ED_OBSTETRIC (was UTI)
UPDATE clinical.pathway_definitions SET source_section_id = 'b0000001-0001-4001-8001-000000000204'
 WHERE id = 'f0000001-0001-4001-8001-000000000111'; -- ED_RESP_FAILURE (was Asthma)

-- ---------------------------------------------------------------------------
-- 4. Retire the qSOFA sepsis screen. Replace ED_SEPSIS step 1 in place so the
--    pathway screens on an EWS rather than qSOFA, and cite the SSC26 section.
-- ---------------------------------------------------------------------------
UPDATE clinical.pathway_steps
   SET step_type = 'RED_FLAGS',
       -- Clinician-facing prompt screens on the EWS; the qSOFA-retirement rationale lives in the
       -- cited section (…0205), not in the prompt, so the step reads as a screen not a footnote.
       prompt = 'Sepsis screen: is the NEWS2/MEWS (computed from vitals) high, with suspected '
             || 'infection? A NEWS2 of 5 or more, or any single parameter scoring 3, is high-risk.',
       data_capture_schema = $j${"fields":[{"name":"news2_score","type":"number","label":"NEWS2 (server-computed)"},{"name":"suspected_infection","type":"boolean"},{"name":"single_parameter_3","type":"boolean","label":"Any single parameter scoring 3"}]}$j$::jsonb,
       branch_logic = '{"if_news2_ge_5_or_single_param_3_and_infection":"ACTIVATE_SEPSIS_SIX"}'::jsonb,
       source_section_id = 'b0000001-0001-4001-8001-000000000205'
 WHERE pathway_id = 'f0000001-0001-4001-8001-000000000101' AND step_order = 1;

-- ---------------------------------------------------------------------------
-- 5. Give the eight stepless pathways real steps. Every pathway follows the WHO
--    Basic Emergency Care shape: recognise (ABCDE / danger signs) -> time-critical
--    action -> disposition. data_capture is transient session capture (NOT new
--    pct columns) so this adds no schema and no mega-form.
-- ---------------------------------------------------------------------------
INSERT INTO clinical.pathway_steps
    (pathway_id, step_order, step_type, prompt, data_capture_schema, branch_logic, recommendation_logic, source_section_id)
VALUES
-- ED_ACS (adopted-by-reference, NOT WHO-derived)
('f0000001-0001-4001-8001-000000000103', 1, 'ASSESSMENT',
 'Chest pain / ACS features: 12-lead ECG within 10 minutes of arrival. STEMI, NSTEMI/unstable angina, or non-cardiac?',
 '{"fields":[{"name":"ecg_done_within_10min","type":"boolean"},{"name":"ecg_pattern","type":"enum","values":["STEMI","NSTEMI_UA","NON_DIAGNOSTIC"]}]}'::jsonb,
 '{"if_stemi":"ACTIVATE_REPERFUSION"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000103'),
('f0000001-0001-4001-8001-000000000103', 2, 'TREATMENT',
 'Aspirin 300 mg (unless allergy/contraindication); analgesia; anticoagulation and reperfusion per STEMI/NSTEMI pathway; serial troponin. Reperfusion clock runs from symptom onset.',
 '{"fields":[{"name":"aspirin_given","type":"boolean"},{"name":"reperfusion_plan","type":"enum","values":["THROMBOLYSIS","PCI_REFERRAL","MEDICAL"]}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000103"}'::jsonb, 'b0000001-0001-4001-8001-000000000103'),
('f0000001-0001-4001-8001-000000000103', 3, 'DISPOSITION',
 'CCU/HDU, transfer for PCI, or ward — document reperfusion decision and handover.',
 '{"fields":[{"name":"disposition","type":"enum","values":["CCU","TRANSFER_PCI","WARD","HDU"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000103'),

-- ED_STROKE (adopted-by-reference, NOT WHO-derived)
('f0000001-0001-4001-8001-000000000104', 1, 'ASSESSMENT',
 'Time last known well; FAST/NIHSS; capillary glucose (exclude hypoglycaemia mimicking stroke); non-contrast CT head urgently.',
 '{"fields":[{"name":"last_known_well","type":"datetime"},{"name":"glucose_checked","type":"boolean"},{"name":"ct_ordered","type":"boolean"}]}'::jsonb,
 '{"if_within_thrombolysis_window":"ASSESS_ELIGIBILITY"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000104'),
('f0000001-0001-4001-8001-000000000104', 2, 'TREATMENT',
 'Thrombolysis/thrombectomy eligibility measured from last known well; blood-pressure management per pathway; nil by mouth until swallow screen.',
 '{"fields":[{"name":"swallow_screen_done","type":"boolean"},{"name":"bp_managed","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000104"}'::jsonb, 'b0000001-0001-4001-8001-000000000104'),
('f0000001-0001-4001-8001-000000000104', 3, 'DISPOSITION',
 'Stroke unit, transfer for thrombectomy, or ward — activate stroke team and document.',
 '{"fields":[{"name":"disposition","type":"enum","values":["STROKE_UNIT","TRANSFER","WARD"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000104'),

-- ED_DKA
('f0000001-0001-4001-8001-000000000105', 1, 'ASSESSMENT',
 'Confirm DKA: glucose, ketones, venous gas (pH/bicarbonate), potassium. Assess severity and conscious level.',
 '{"fields":[{"name":"glucose","type":"number"},{"name":"ketones","type":"number"},{"name":"ph","type":"number"},{"name":"potassium","type":"number"}]}'::jsonb,
 '{"if_ph_lt_7_1_or_altered":"HDU_ICU"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000105'),
('f0000001-0001-4001-8001-000000000105', 2, 'TREATMENT',
 'IV fluid resuscitation FIRST; check potassium before starting insulin; fixed-rate insulin infusion; hourly glucose and ketones; find and treat the precipitant.',
 '{"fields":[{"name":"fluids_started","type":"boolean"},{"name":"potassium_checked_before_insulin","type":"boolean"},{"name":"insulin_started","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000105"}'::jsonb, 'b0000001-0001-4001-8001-000000000105'),
('f0000001-0001-4001-8001-000000000105', 3, 'DISPOSITION',
 'HDU/ICU if severe acidosis or altered consciousness; otherwise ward with DKA monitoring.',
 '{"fields":[{"name":"disposition","type":"enum","values":["ICU","HDU","WARD"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000105'),

-- ED_POISONING (named antidotes only where a formulary supports them; traditional-remedy toxicity is supportive-only elsewhere per R9)
('f0000001-0001-4001-8001-000000000107', 1, 'ASSESSMENT',
 'Identify agent and time of ingestion/exposure; ABC with airway protection; conscious level; ECG and glucose. Contact the poison centre.',
 '{"fields":[{"name":"agent","type":"string"},{"name":"time_of_exposure","type":"datetime"},{"name":"airway_protected","type":"boolean"}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000107'),
('f0000001-0001-4001-8001-000000000107', 2, 'TREATMENT',
 'Supportive care and specific antidote where one exists and is available (e.g. naloxone for opioid, acetylcysteine for paracetamol); activated charcoal only if early and the agent is appropriate. Do NOT name antidotes for traditional-remedy toxicity — supportive management and referral only.',
 '{"fields":[{"name":"antidote_given","type":"string"},{"name":"charcoal_given","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000107"}'::jsonb, 'b0000001-0001-4001-8001-000000000107'),
('f0000001-0001-4001-8001-000000000107', 3, 'DISPOSITION',
 'Observe per agent and toxicology advice; HDU/ICU for airway or haemodynamic compromise; psychiatric review where intentional.',
 '{"fields":[{"name":"disposition","type":"enum","values":["ICU","HDU","OBSERVE","WARD","PSYCH_REVIEW"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000107'),

-- ED_ECTOPIC — the safety-critical gate: reproductive age until pregnancy excluded, NOT pregnant==true
('f0000001-0001-4001-8001-000000000108', 1, 'RED_FLAGS',
 'Woman of reproductive age with abdominal/pelvic pain, PV bleeding, syncope or collapse? Do a beta-hCG. Consider ectopic until pregnancy is EXCLUDED — do not require a recorded pregnancy first.',
 '{"fields":[{"name":"reproductive_age_female","type":"boolean"},{"name":"bhcg_result","type":"enum","values":["POSITIVE","NEGATIVE","PENDING"]},{"name":"haemodynamically_unstable","type":"boolean"}]}'::jsonb,
 '{"gate":"reproductive_age_female_until_pregnancy_excluded","if_positive_and_pain_or_unstable":"URGENT_GYNAE_SURGICAL"}'::jsonb,
 NULL, 'b0000001-0001-4001-8001-000000000201'),
('f0000001-0001-4001-8001-000000000108', 2, 'INTERVENTION',
 'Positive beta-hCG with pain or instability: resuscitate (IV access, fluids/blood), urgent transvaginal ultrasound, urgent gynaecology/surgical referral. In a shocked patient do NOT delay definitive care for imaging.',
 '{"fields":[{"name":"resuscitation_started","type":"boolean"},{"name":"gynae_referred","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000201"}'::jsonb, 'b0000001-0001-4001-8001-000000000201'),
('f0000001-0001-4001-8001-000000000108', 3, 'DISPOSITION',
 'Theatre for rupture/instability, or gynaecology admission — handover to the accepting team.',
 '{"fields":[{"name":"disposition","type":"enum","values":["THEATRE","GYNAE_ADMISSION","OBSERVE"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000201'),

-- ED_STATUS_EPILEPTICUS — the clock drives it
('f0000001-0001-4001-8001-000000000109', 1, 'ASSESSMENT',
 'Seizure >5 minutes or repeated without recovery = status. ABC, protect airway, high-flow oxygen, check glucose; give glucose (+ thiamine if malnourished/alcohol) if hypoglycaemic.',
 '{"fields":[{"name":"seizure_duration_min","type":"number"},{"name":"glucose_checked","type":"boolean"},{"name":"airway_protected","type":"boolean"}]}'::jsonb,
 '{"if_duration_ge_5":"GIVE_BENZODIAZEPINE"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000202'),
('f0000001-0001-4001-8001-000000000109', 2, 'TREATMENT',
 'First line at 5 min: benzodiazepine (IV lorazepam/diazepam, or IM/buccal midazolam if no IV). Ongoing at ~20 min: second-line antiepileptic (phenytoin/valproate/levetiracetam) and prepare airway support.',
 '{"fields":[{"name":"benzodiazepine_given","type":"boolean"},{"name":"second_line_given","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000202"}'::jsonb, 'b0000001-0001-4001-8001-000000000202'),
('f0000001-0001-4001-8001-000000000109', 3, 'DISPOSITION',
 'ICU/HDU if refractory or airway support needed; otherwise admit for observation and cause workup.',
 '{"fields":[{"name":"disposition","type":"enum","values":["ICU","HDU","WARD"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000202'),

-- ED_OBSTETRIC — recognise + stabilise, then hand to maternity (RMNP owns the continuum)
('f0000001-0001-4001-8001-000000000110', 1, 'ASSESSMENT',
 'Pregnant or recently delivered and acutely unwell: eclampsia/severe pre-eclampsia, postpartum haemorrhage, or obstetric sepsis? ABC and urgent obstetric referral in every case.',
 '{"fields":[{"name":"syndrome","type":"enum","values":["ECLAMPSIA","PPH","OBSTETRIC_SEPSIS","OTHER"]},{"name":"gestation_or_postpartum","type":"string"}]}'::jsonb,
 '{"if_eclampsia":"MAGNESIUM_SULFATE","if_pph":"UTEROTONICS_TXA"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000203'),
('f0000001-0001-4001-8001-000000000110', 2, 'TREATMENT',
 'Eclampsia: magnesium sulfate + controlled BP lowering. PPH: uterine massage, oxytocin, tranexamic acid, resuscitate. Sepsis: 1-hour bundle. Stabilise for handover; definitive management is maternity''s.',
 '{"fields":[{"name":"magnesium_given","type":"boolean"},{"name":"uterotonic_given","type":"boolean"},{"name":"txa_given","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000203"}'::jsonb, 'b0000001-0001-4001-8001-000000000203'),
('f0000001-0001-4001-8001-000000000110', 3, 'DISPOSITION',
 'Handover to maternity/obstetrics (theatre, HDU or labour ward) — RMNP owns the obstetric continuum.',
 '{"fields":[{"name":"disposition","type":"enum","values":["THEATRE","OBSTETRIC_HDU","LABOUR_WARD","TRANSFER"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000203'),

-- ED_RESP_FAILURE
('f0000001-0001-4001-8001-000000000111', 1, 'ASSESSMENT',
 'ABCDE. SpO2 and work of breathing; titrate oxygen to target (88-92% if hypercapnia risk, else 94-98%). Identify the cause.',
 '{"fields":[{"name":"spo2","type":"number"},{"name":"oxygen_target","type":"enum","values":["88_92","94_98"]},{"name":"suspected_cause","type":"string"}]}'::jsonb,
 '{"if_exhausted_or_obtunded":"PREPARE_VENTILATION"}'::jsonb, NULL, 'b0000001-0001-4001-8001-000000000204'),
('f0000001-0001-4001-8001-000000000111', 2, 'TREATMENT',
 'Treat the cause (bronchodilators, antibiotics, diuresis, decompression as indicated). Escalate early: NIV for type 2 failure; prepare intubation if failing.',
 '{"fields":[{"name":"cause_treated","type":"boolean"},{"name":"niv_started","type":"boolean"}]}'::jsonb,
 NULL, '{"reference":"b0000001-0001-4001-8001-000000000204"}'::jsonb, 'b0000001-0001-4001-8001-000000000204'),
('f0000001-0001-4001-8001-000000000111', 3, 'DISPOSITION',
 'ICU/HDU for ventilatory support; otherwise ward with monitoring.',
 '{"fields":[{"name":"disposition","type":"enum","values":["ICU","HDU","WARD"]}]}'::jsonb,
 NULL, NULL, 'b0000001-0001-4001-8001-000000000204')
ON CONFLICT (pathway_id, step_order) DO NOTHING;
