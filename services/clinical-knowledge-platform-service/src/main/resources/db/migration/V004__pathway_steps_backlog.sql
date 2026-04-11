-- Pathway steps for definitions that previously had registry rows only (national demo).
-- Aligns guided flows with backlog: gonorrhoea, T2DM, HF, fever, TPT.

INSERT INTO clinical.pathway_steps (pathway_id, step_order, step_type, prompt, data_capture_schema, branch_logic, recommendation_logic)
VALUES
-- Gonorrhoea — urogenital (f0000001-0001-4001-8001-000000000004)
('f0000001-0001-4001-8001-000000000004', 1, 'ASSESSMENT',
 'Confirm uncomplicated urogenital infection; pregnancy or disseminated features?',
 '{"fields":[{"name":"pregnancy","type":"boolean"},{"name":"systemic_features","type":"boolean"}]}'::jsonb,
 '{"if_pregnancy_or_systemic":"ESCALATE_SPECIALIST"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000004', 2, 'TREATMENT',
 'Ceftriaxone 500 mg IM single dose for uncomplicated infection; partner management per programme.',
 '{}'::jsonb, '{}'::jsonb,
 '{"medicine_refs":["c0000001-0001-4001-8001-000000000003"]}'::jsonb),

-- Type 2 diabetes — stepwise (f0000001-0001-4001-8001-000000000005)
('f0000001-0001-4001-8001-000000000005', 1, 'ASSESSMENT',
 'HbA1c or glucose pattern known? Renal function and contraindications to metformin?',
 '{"fields":[{"name":"hba1c_known","type":"boolean"},{"name":"egfr_ok_for_metformin","type":"boolean"}]}'::jsonb,
 '{}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000005', 2, 'TREATMENT',
 'Metformin foundational first-line where tolerated; titrate; counsel lifestyle.',
 '{}'::jsonb, '{}'::jsonb,
 '{"medicine_refs":["c0000001-0001-4001-8001-000000000006"]}'::jsonb),
('f0000001-0001-4001-8001-000000000005', 3, 'ESCALATION',
 'Insulin initiation and advanced therapies require specialist oversight per national guidance.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb),

-- Heart failure — optimisation (f0000001-0001-4001-8001-000000000006)
('f0000001-0001-4001-8001-000000000006', 1, 'ASSESSMENT',
 'NYHA class, blood pressure, potassium, renal function; known LVEF if available.',
 '{"fields":[{"name":"nyha_class","type":"enum","values":["I","II","III","IV"]}]}'::jsonb,
 '{"if_iv":"INPATIENT_PROTOCOL"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000006', 2, 'TREATMENT',
 'Guideline-directed medical therapy: RAAS pathway, beta-blocker, MRA where appropriate, SGLT2 where indicated — subject to monitoring and specialist availability.',
 '{}'::jsonb, '{}'::jsonb, '{"education":"GDMT_bundle_per_EDLIZ"}'::jsonb),

-- Fever — risk stratification (f0000001-0001-4001-8001-000000000007)
('f0000001-0001-4001-8001-000000000007', 1, 'RED_FLAGS',
 'Severe sepsis features, altered consciousness, non-blanching rash, respiratory distress?',
 '{"fields":[{"name":"red_flags","type":"boolean"}]}'::jsonb,
 '{"if_true":"EMERGENCY"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000007', 2, 'INVESTIGATION',
 'Targeted investigations proportional to risk; identify focus of infection before empiric antimicrobials when safe to defer.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000007', 3, 'MANAGEMENT',
 'Antipyretics for comfort; antimicrobials only when clinically indicated; document review plan.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb),

-- TB preventive therapy — navigation (f0000001-0001-4001-8001-000000000008)
('f0000001-0001-4001-8001-000000000008', 1, 'ELIGIBILITY',
 'Latent TB infection confirmed or high-risk contact? HIV status and ART interaction checks per programme.',
 '{"fields":[{"name":"latent_tb_eligible","type":"boolean"}]}'::jsonb,
 '{"if_not_eligible":"DOCUMENT_REASON"}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000008', 2, 'REGIMEN',
 'Select TPT regimen per national TB/HIV programme circular; screen for hepatotoxicity risk.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb),
('f0000001-0001-4001-8001-000000000008', 3, 'FOLLOW_UP',
 'Adherence support; schedule completion review and document adverse events.',
 '{}'::jsonb, '{}'::jsonb, '{}'::jsonb);
