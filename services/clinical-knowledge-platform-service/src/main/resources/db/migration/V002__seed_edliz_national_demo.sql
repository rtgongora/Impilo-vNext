-- =============================================================================
-- National seed data — representative DEMO excerpts for engineering (not full PDF text).
-- Authoritative PDF: docs/reference/edliz-2025/EDLIZ-2025-final-for-circulation.pdf
-- Document row metadata is aligned to that bundle in V003__edliz_2025_reference_bundle.sql
-- Replace section raw_text with curator-approved extracts after ingestion pipeline.
-- =============================================================================

INSERT INTO clinical.source_documents (
    id, title, source_type, issuing_authority, effective_date, publication_date,
    version_label, status, language, ingestion_status
) VALUES (
    'a0000001-0001-4001-8001-000000000001',
    'EDLIZ 2025 — Essential Medicines and Standard Treatment Guidelines (engineering seed)',
    'NATIONAL_GUIDELINE',
    'MoHCC Zimbabwe',
    DATE '2025-01-01',
    DATE '2025-03-01',
    '2025',
    'ACTIVE',
    'en',
    'INDEXED'
);

INSERT INTO clinical.source_sections (id, document_id, page_number, section_title, section_path, raw_text)
VALUES
('b0000001-0001-4001-8001-000000000001', 'a0000001-0001-4001-8001-000000000001', 120, 'Asthma — chronic management',
 'asthma/chronic',
 'In stable asthma, short-acting beta-agonist (SABA) monotherapy is not recommended. Inhaled corticosteroid (ICS) containing therapy is first-line; consider ICS/LABA where appropriate. Review inhaler technique and adherence.'),
('b0000001-0001-4001-8001-000000000002', 'a0000001-0001-4001-8001-000000000001', 210, 'Gonorrhoea — uncomplicated urogenital',
 'sti/gonorrhoea',
 'Ceftriaxone 500 mg IM as a single dose is recommended for uncomplicated gonorrhoea. Lower legacy doses are not recommended. Test and treat partners per national STI programme.'),
('b0000001-0001-4001-8001-000000000003', 'a0000001-0001-4001-8001-000000000001', 340, 'Neonatal sepsis — suspected, first 48 hours',
 'neonatal/sepsis',
 'Suspected neonatal sepsis within the first 48 hours requires urgent assessment. Empiric antibiotic choice must follow neonatal dosing tables; gentamicin dosing is weight- and age-sensitive with monitoring for toxicity. Escalate per local neonatal unit protocol.'),
('b0000001-0001-4001-8001-000000000004', 'a0000001-0001-4001-8001-000000000001', 95, 'Uncomplicated lower UTI — adult woman',
 'uti/uncomplicated',
 'Nitrofurantoin or trimethoprim-sulfamethoxazole where local resistance patterns permit; follow local formulary and culture guidance when available.'),
('b0000001-0001-4001-8001-000000000005', 'a0000001-0001-4001-8001-000000000001', 400, 'Type 2 diabetes — pharmacologic stepwise care',
 'diabetes/t2',
 'Metformin remains foundational first-line where tolerated. Escalation may require specialist review for insulin initiation and advanced therapies.'),
('b0000001-0001-4001-8001-000000000006', 'a0000001-0001-4001-8001-000000000001', 430, 'Heart failure — optimisation bundle',
 'cvd/hf',
 'Guideline-directed medical therapy includes ACE inhibitor/ARB/ARNI, beta-blocker, MRA where appropriate, and SGLT2 inhibitor where indicated — subject to specialist availability and monitoring.'),
('b0000001-0001-4001-8001-000000000007', 'a0000001-0001-4001-8001-000000000001', 50, 'Fever — initial assessment principles',
 'general/fever',
 'Assess severity, red flags, hydration, and focus of infection. Investigations should be proportional to risk. Antipyretics for comfort; antimicrobials only when clinically indicated.');

INSERT INTO clinical.medicine_guidance (
    id, medicine_name, generic_name, route, dose_expression, dose_unit,
    frequency_expression, duration_expression, level_of_care, ven_class,
    indication, alternative_rank, specialist_only, notes, source_section_id
) VALUES
('c0000001-0001-4001-8001-000000000001', 'Beclometasone inhaler', 'beclometasone', 'INHALATION',
 'Low-medium dose per national inhaler chart', 'mcg', 'BD', 'ongoing', 'C', 'V',
 'Asthma maintenance (ICS-containing therapy)', 1, false,
 'Use as part of ICS-containing regimen; not for acute monotherapy rescue alone.',
 'b0000001-0001-4001-8001-000000000001'),
('c0000001-0001-4001-8001-000000000002', 'Salbutamol inhaler', 'salbutamol', 'INHALATION',
 '2 puffs', 'dose', 'PRN', 'acute symptoms', 'C', 'E',
 'Asthma reliever', 1, false,
 'Reliever; should not be sole maintenance therapy in persistent asthma.',
 'b0000001-0001-4001-8001-000000000001'),
('c0000001-0001-4001-8001-000000000003', 'Ceftriaxone', 'ceftriaxone', 'IM',
 '500', 'mg', 'single dose', 'once', 'B', 'E',
 'Uncomplicated gonorrhoea', 1, false,
 'National programme may adjust for co-infections; verify local circular.',
 'b0000001-0001-4001-8001-000000000002'),
('c0000001-0001-4001-8001-000000000004', 'Gentamicin injection', 'gentamicin', 'IV',
 'Per neonatal mg/kg table', 'mg/kg', 'Per protocol', 'Per protocol', 'S', 'E',
 'Neonatal sepsis (empiric component)', 1, true,
 'Neonatal specialist centre; therapeutic drug monitoring where available.',
 'b0000001-0001-4001-8001-000000000003'),
('c0000001-0001-4001-8001-000000000005', 'Nitrofurantoin', 'nitrofurantoin', 'PO',
 '100', 'mg', 'BD', '3-5 days', 'C', 'V',
 'Uncomplicated lower UTI (adult)', 1, false,
 'Avoid if eGFR significantly reduced; confirm local resistance data.',
 'b0000001-0001-4001-8001-000000000004'),
('c0000001-0001-4001-8001-000000000006', 'Metformin', 'metformin', 'PO',
 'Start low titrate', 'mg', 'BD-TDS', 'chronic', 'C', 'V',
 'Type 2 diabetes first-line', 1, false,
 'Contraindications and renal monitoring per guideline.',
 'b0000001-0001-4001-8001-000000000005');

INSERT INTO clinical.condition_guidance (
    id, condition_name, severity, age_group, sex_constraints, pregnancy_constraints,
    first_line_guidance_id, investigations_json, red_flags_json, counselling_json, source_section_id
) VALUES
('d0000001-0001-4001-8001-000000000001', 'Asthma — persistent', 'variable', 'all', 'any', 'ICS safety per trimester',
 'c0000001-0001-4001-8001-000000000001',
 '["peak flow where available","oxygen saturation"]'::jsonb,
 '["silent chest","cyanosis","altered consciousness","SpO2 persistent drop"]'::jsonb,
 '["demonstrate inhaler technique","written action plan"]'::jsonb,
 'b0000001-0001-4001-8001-000000000001'),
('d0000001-0001-4001-8001-000000000002', 'Gonorrhoea — uncomplicated urogenital', 'uncomplicated', 'adult', 'any', 'use ceftriaxone per programme',
 'c0000001-0001-4001-8001-000000000003',
 '["NAAT where available"]'::jsonb,
 '["disseminated infection","arthritis","pregnancy"]'::jsonb,
 '["partner notification","abstain until treated"]'::jsonb,
 'b0000001-0001-4001-8001-000000000002'),
('d0000001-0001-4001-8001-000000000003', 'Neonatal sepsis — suspected', 'suspected', 'neonate', 'any', 'neonatal formulary only',
 'c0000001-0001-4001-8001-000000000004',
 '["FBC","CRP","blood culture","lumbar puncture per risk"]'::jsonb,
 '["shock","apnoea","bulging fontanelle","temperature instability"]'::jsonb,
 '["parent information per neonatal unit"]'::jsonb,
 'b0000001-0001-4001-8001-000000000003');

INSERT INTO clinical.rule_definitions (
    id, code, name, rule_type, trigger_context, severity, message_template,
    explanation_template, interruptive, override_allowed, effective_start, logic_expression
) VALUES
('e0000001-0001-4001-8001-000000000001', 'ASTHMA_SABA_MONOTHERAPY',
 'SABA monotherapy in asthma', 'PRESCRIBING', 'asthma_maintenance', 'HIGH',
 'SABA monotherapy is not recommended for persistent asthma. Add or review ICS-containing therapy per EDLIZ.',
 'Short-acting beta-agonists are relievers; maintenance requires ICS (or ICS/LABA) per national standard treatment guidelines.',
 true, true, DATE '2025-01-01', 'JAVA:AsthmaSabaMonotherapyRule'),
('e0000001-0001-4001-8001-000000000002', 'GONORRHOEA_CEFTRIAXONE_LEGACY_DOSE',
 'Ceftriaxone dose below recommended for gonorrhoea', 'PRESCRIBING', 'gonorrhoea', 'HIGH',
 'Ceftriaxone 500 mg IM single dose is recommended for uncomplicated gonorrhoea. Review dose and programme circulars.',
 'Legacy lower doses are discouraged due to reduced efficacy and resistance risk.',
 true, true, DATE '2025-01-01', 'JAVA:GonorrhoeaCeftriaxoneDoseRule'),
('e0000001-0001-4001-8001-000000000003', 'NEONATAL_GENTAMICIN_SPECIALIST',
 'Gentamicin in neonate requires specialist dosing', 'PRESCRIBING', 'neonatal', 'HIGH',
 'Gentamicin in neonates must follow weight- and age-based dosing with monitoring; specialist centre recommended.',
 'Neonatal pharmacokinetics differ; avoid adult dosing logic.',
 true, false, DATE '2025-01-01', 'JAVA:NeonatalGentamicinRule'),
('e0000001-0001-4001-8001-000000000004', 'STEWARDSHIP_BROAD_SPECTRUM_DURATION',
 'Empiric broad-spectrum therapy duration review', 'STEWARDSHIP', 'antimicrobial', 'MEDIUM',
 'Broad-spectrum empiric therapy has exceeded the stewardship review threshold. Reassess diagnosis, narrow therapy, and document plan.',
 'Prolonged empiric broad-spectrum use increases resistance and adverse events.',
 false, true, DATE '2025-01-01', 'JAVA:AntimicrobialStewardshipRule');

INSERT INTO clinical.pathway_definitions (id, pathway_name, condition_code, version, status, source_section_id)
VALUES
('f0000001-0001-4001-8001-000000000001', 'Asthma — adult primary care', 'ASTHMA', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000001'),
('f0000001-0001-4001-8001-000000000002', 'Uncomplicated UTI — adult woman', 'UTI_UNCOMPLICATED', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000004'),
('f0000001-0001-4001-8001-000000000003', 'Suspected neonatal sepsis — first 48h', 'NEO_SEPSIS', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000003'),
('f0000001-0001-4001-8001-000000000004', 'Gonorrhoea — urogenital', 'GONORRHOEA', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000002'),
('f0000001-0001-4001-8001-000000000005', 'Type 2 diabetes — stepwise pharmacotherapy', 'DM_T2', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000005'),
('f0000001-0001-4001-8001-000000000006', 'Heart failure — optimisation prompts', 'HF', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000006'),
('f0000001-0001-4001-8001-000000000007', 'Fever — risk stratification', 'FEVER', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000007'),
('f0000001-0001-4001-8001-000000000008', 'TB preventive therapy — navigation', 'TPT', 1, 'ACTIVE', 'b0000001-0001-4001-8001-000000000007');

INSERT INTO clinical.pathway_steps (pathway_id, step_order, step_type, prompt, data_capture_schema, branch_logic, recommendation_logic)
VALUES
('f0000001-0001-4001-8001-000000000001', 1, 'ASSESSMENT', 'Current symptoms and daytime/night-time frequency?',
 '{"fields":[{"name":"symptom_burden","type":"enum","values":["LOW","MODERATE","HIGH"]}]}'::jsonb,
 '{"if_high_escalate":true}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000001', 2, 'RED_FLAGS', 'Any red flags: silent chest, cyanosis, altered consciousness?',
 '{"fields":[{"name":"red_flags","type":"boolean"}]}'::jsonb,
 '{"if_true":"EMERGENCY"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000001', 3, 'TREATMENT', 'Maintenance therapy should include ICS-containing regimen; review reliever overuse.',
 '{}'::jsonb, '{}'::jsonb,
 '{"structured_refs":["c0000001-0001-4001-8001-000000000001","c0000001-0001-4001-8001-000000000002"]}'::jsonb),

('f0000001-0001-4001-8001-000000000002', 1, 'ASSESSMENT', 'Dysuria, frequency, fever, flank pain?',
 '{"fields":[{"name":"flank_pain","type":"boolean"},{"name":"fever","type":"boolean"}]}'::jsonb,
 '{"if_flank_or_fever":"ESCALATE_PYELONEPHRITIS"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000002', 2, 'TREATMENT', 'First-line oral therapy per local resistance; duration typically short course.',
 '{}'::jsonb, '{}'::jsonb,
 '{"medicine_refs":["c0000001-0001-4001-8001-000000000005"]}'::jsonb),

('f0000001-0001-4001-8001-000000000003', 1, 'ASSESSMENT', 'Age in hours, risk factors, clinical instability?',
 '{"fields":[{"name":"age_hours","type":"number"},{"name":"risk_factors","type":"text"}]}'::jsonb, '{}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000003', 2, 'TREATMENT', 'Empiric neonatal regimen per unit protocol; gentamicin dosing weight-based.',
 '{}'::jsonb, '{}'::jsonb,
 '{"medicine_refs":["c0000001-0001-4001-8001-000000000004"]}'::jsonb);
