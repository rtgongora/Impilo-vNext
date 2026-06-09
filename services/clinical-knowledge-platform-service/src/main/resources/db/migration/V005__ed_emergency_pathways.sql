-- ED / casualty emergency management pathways and reference excerpts

INSERT INTO clinical.source_sections (id, document_id, page_number, section_title, section_path, raw_text)
VALUES
('b0000001-0001-4001-8001-000000000101', 'a0000001-0001-4001-8001-000000000001', 12, 'Sepsis — ED initial management',
 'ed/sepsis',
 'Within 1 hour: obtain cultures before antibiotics if safe; administer IV broad-spectrum antibiotic; measure lactate; begin 30 ml/kg crystalloid for hypotension or lactate ≥4. Reassess perfusion after fluids.'),
('b0000001-0001-4001-8001-000000000102', 'a0000001-0001-4001-8001-000000000001', 18, 'Anaphylaxis — ED',
 'ed/anaphylaxis',
 'Remove trigger; IM adrenaline 0.5 mg (1:1000) repeat every 5 min; high-flow oxygen; IV fluids; adjunct antihistamine and steroid per local protocol; observe minimum 6 hours after reaction.'),
('b0000001-0001-4001-8001-000000000103', 'a0000001-0001-4001-8001-000000000001', 25, 'Acute coronary syndrome',
 'ed/acs',
 'MONA pathway where not contraindicated; 12-lead ECG within 10 minutes; aspirin unless allergy; anticoagulation and reperfusion per STEMI/NSTEMI pathway; serial troponin and reassessment.'),
('b0000001-0001-4001-8001-000000000104', 'a0000001-0001-4001-8001-000000000001', 30, 'Acute stroke',
 'ed/stroke',
 'Time last known well; CT head non-contrast urgently; blood glucose; BP management per thrombolysis/thrombectomy eligibility; activate stroke team; swallow screen before oral intake.'),
('b0000001-0001-4001-8001-000000000106', 'a0000001-0001-4001-8001-000000000001', 45, 'Major trauma — primary survey',
 'ed/trauma/primary',
 'ABCDE approach with C-spine control; control catastrophic haemorrhage; two large-bore IV; blood group; activate trauma team for mechanism or physiology triggers; log roll only when safe.'),
('b0000001-0001-4001-8001-000000000107', 'a0000001-0001-4001-8001-000000000001', 52, 'Poisoning / overdose',
 'ed/toxicology',
 'Identify agent and time; protect airway; activated charcoal if early presentation and appropriate agent; antidotes where available (e.g. naloxone, acetylcysteine); contact poison centre.'),
('b0000001-0001-4001-8001-000000000105', 'a0000001-0001-4001-8001-000000000001', 38, 'DKA — adult ED',
 'ed/dka',
 'IV fluids resuscitation; insulin infusion after potassium check; monitor glucose and ketones hourly; search precipitant; ICU/HDU if severe acidosis or altered consciousness.')
ON CONFLICT DO NOTHING;

INSERT INTO clinical.pathway_definitions (id, pathway_name, condition_code, version, status, source_section_id)
VALUES
('f0000001-0001-4001-8001-000000000101', 'ED — Sepsis bundle', 'ED_SEPSIS', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000101'),
('f0000001-0001-4001-8001-000000000102', 'ED — Anaphylaxis', 'ED_ANAPHYLAXIS', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000102'),
('f0000001-0001-4001-8001-000000000103', 'ED — Acute coronary syndrome', 'ED_ACS', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000103'),
('f0000001-0001-4001-8001-000000000104', 'ED — Acute stroke', 'ED_STROKE', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000104'),
('f0000001-0001-4001-8001-000000000105', 'ED — Diabetic ketoacidosis', 'ED_DKA', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000105'),
('f0000001-0001-4001-8001-000000000106', 'ED — Major trauma', 'ED_TRAUMA', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000106'),
('f0000001-0001-4001-8001-000000000107', 'ED — Poisoning / overdose', 'ED_POISONING', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000107'),
('f0000001-0001-4001-8001-000000000108', 'ED — Ectopic pregnancy', 'ED_ECTOPIC', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000004'),
('f0000001-0001-4001-8001-000000000109', 'ED — Status epilepticus', 'ED_STATUS_EPILEPTICUS', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000007'),
('f0000001-0001-4001-8001-000000000110', 'ED — Obstetric emergency', 'ED_OBSTETRIC', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000004'),
('f0000001-0001-4001-8001-000000000111', 'ED — Acute respiratory failure', 'ED_RESP_FAILURE', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000001')
ON CONFLICT DO NOTHING;

INSERT INTO clinical.pathway_steps (pathway_id, step_order, step_type, prompt, data_capture_schema, branch_logic, recommendation_logic)
VALUES
('f0000001-0001-4001-8001-000000000101', 1, 'RED_FLAGS', 'qSOFA ≥2 or lactate ordered?',
 '{"fields":[{"name":"qsofa_score","type":"number"},{"name":"lactate_ordered","type":"boolean"}]}'::jsonb,
 '{"if_qsofa_ge_2":"ACTIVATE_SEPSIS_BUNDLE"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000101', 2, 'TREATMENT', 'Within 1 hour: cultures, antibiotics, fluids per sepsis bundle.',
 '{}'::jsonb, '{}'::jsonb, '{"reference":"b0000001-0001-4001-8001-000000000101"}'::jsonb),

('f0000001-0001-4001-8001-000000000102', 1, 'ASSESSMENT', 'Airway compromise, urticaria, hypotension after exposure?',
 '{"fields":[{"name":"airway_compromise","type":"boolean"},{"name":"hypotension","type":"boolean"}]}'::jsonb,
 '{"if_any_true":"GIVE_ADRENALINE"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000102', 2, 'TREATMENT', 'IM adrenaline 0.5 mg; repeat q5min; oxygen; fluids; observe ≥6h.',
 '{}'::jsonb, '{}'::jsonb, '{"reference":"b0000001-0001-4001-8001-000000000102"}'::jsonb),

('f0000001-0001-4001-8001-000000000106', 1, 'ASSESSMENT', 'Primary survey ABCDE — complete each section before proceeding.',
 '{"fields":[{"name":"airway_patent","type":"boolean"},{"name":"breathing_adequate","type":"boolean"},{"name":"circulation_stable","type":"boolean"}]}'::jsonb,
 '{}'::jsonb, '{"reference":"b0000001-0001-4001-8001-000000000106"}'::jsonb),
('f0000001-0001-4001-8001-000000000106', 2, 'INTERVENTION', 'Haemorrhage control, IV access, imaging as indicated, secondary survey.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000106', 3, 'DISPOSITION', 'Theatre, ICU, ward, transfer — document ISS and team handover.',
 '{"fields":[{"name":"disposition","type":"enum","values":["THEATRE","ICU","WARD","TRANSFER"]}]}'::jsonb,
 '{}'::jsonb, '{}'::jsonb);
