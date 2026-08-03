-- Procedures :: V007 — a defect fix, found while wiring P7/P9 content into the catalogue UI
-- (Wave P-R2), corrected forward rather than by rewriting V006 (already pushed, shared branch).
--
-- THE DEFECT: procedure_definition.aftercare_template has existed since V002, and V003 seeded
-- it with 34 DISTINCT specific per-procedure-class codes (AFTERCARE-LAPAROTOMY,
-- AFTERCARE-CENTRAL-LINE, AFTERCARE-LUMBAR-PUNCTURE, AFTERCARE-CAESAREAN,
-- AFTERCARE-ARTHROPLASTY, ...) — string literals with no table behind them, exactly the same
-- "a code with nothing to resolve to" state V005's own header describes for
-- safety_pause_template before P7 fixed it by resolving that EXISTING column. P9 (V006) missed
-- that aftercare_template already existed and built a SEPARATE, coarser, six-value column
-- (default_aftercare_template_code, setting-class templates: AFTERCARE-THEATRE,
-- AFTERCARE-BEDSIDE, etc.) instead of resolving the pre-existing specific one — a parallel
-- system, not the fix P7 already modelled.
--
-- THE FIX, scoped honestly rather than attempting all 34 in one pass (the "only 13 of 66
-- procedures carry full requirement sets" catalogue-depth debt is a real precedent for why not
-- to fake completeness here): seed real templates for the SPECIFIC codes the demonstration
-- procedures P7/P9 already linked actually reference — AFTERCARE-LAPAROTOMY,
-- AFTERCARE-CAESAREAN, AFTERCARE-ARTHROPLASTY, AFTERCARE-CENTRAL-LINE,
-- AFTERCARE-LUMBAR-PUNCTURE. (AFTERCARE-ENDOSCOPY and AFTERCARE-DIALYSIS already exist from V006
-- and happen to match V003's specific codes for those procedures too — coincidental alignment,
-- not by design.) default_aftercare_template_code is NOT dropped (already shipped, other rows
-- may reference it) but is now documented as a coarse FALLBACK: a caller should resolve the
-- specific procedure_definition.aftercare_template code first, and fall back to
-- default_aftercare_template_code only when the specific code has no template row (most of the
-- other ~55 procedures, honestly reflecting the catalogue-depth debt rather than hiding it
-- behind an always-populated coarse value).
--
-- The remaining ~27 specific AFTERCARE-* codes seeded in V003 (AFTERCARE-ABDOMINAL,
-- AFTERCARE-AMPUTATION, AFTERCARE-ANGIOGRAPHY, AFTERCARE-ANORECTAL, AFTERCARE-BIOPSY,
-- AFTERCARE-BREAST, AFTERCARE-CHEMOTHERAPY, AFTERCARE-CHEST-DRAIN, AFTERCARE-DENTAL,
-- AFTERCARE-DRAIN, AFTERCARE-ENT, AFTERCARE-GRAFT, AFTERCARE-GYNAECOLOGICAL, AFTERCARE-HERNIA,
-- AFTERCARE-NECK, AFTERCARE-NEUROSURGICAL, AFTERCARE-OPHTHALMIC, AFTERCARE-ORTHOPAEDIC,
-- AFTERCARE-PARACENTESIS, AFTERCARE-STOMA, AFTERCARE-THORACIC, AFTERCARE-TRACHEOSTOMY,
-- AFTERCARE-TRANSFUSION, AFTERCARE-UROLOGY, AFTERCARE-VASCULAR, AFTERCARE-VASCULAR-ACCESS,
-- AFTERCARE-WOUND) remain UNRESOLVED — recorded here as a named debt, not silently left.

INSERT INTO procedures.aftercare_template
    (tenant_id, template_code, template_name, applicable_setting, description,
     source_citation, status, approving_authority)
VALUES
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-LAPAROTOMY', 'Laparotomy aftercare', 'THEATRE',
 'Aftercare specific to an open abdominal (laparotomy) wound.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-CAESAREAN', 'Caesarean section aftercare', 'THEATRE',
 'Aftercare specific to a caesarean section, including neonatal and lactation guidance.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-ARTHROPLASTY', 'Joint arthroplasty aftercare', 'THEATRE',
 'Aftercare specific to a joint replacement, including weight-bearing and rehabilitation guidance.',
 'MED.DEVICE.UDI_TRACEABILITY', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-CENTRAL-LINE', 'Central venous catheter aftercare', 'BEDSIDE',
 'Aftercare specific to a central line, including dressing and line-care instructions.',
 'IPC.CORE.ASEPTIC_TECHNIQUE', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-LUMBAR-PUNCTURE', 'Lumbar puncture aftercare', 'BEDSIDE',
 'Aftercare specific to a lumbar puncture, including post-dural-puncture headache guidance.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION')
ON CONFLICT (tenant_id, template_code) DO NOTHING;

INSERT INTO procedures.aftercare_instruction (template_id, instruction_kind, instruction_text, display_order)
SELECT t.id, x.kind, x.text, x.ord
FROM procedures.aftercare_template t
JOIN (VALUES
  ('AFTERCARE-LAPAROTOMY', 'WOUND_SITE_CARE', 'Keep the abdominal wound clean and dry; watch the dressing for strike-through.', 10),
  ('AFTERCARE-LAPAROTOMY', 'ACTIVITY_RESTRICTION', 'Avoid heavy lifting and abdominal straining for the period advised.', 20),
  ('AFTERCARE-LAPAROTOMY', 'WARNING_SIGNS', 'Return if fever, spreading redness, wound discharge or increasing abdominal pain develop.', 30),
  ('AFTERCARE-LAPAROTOMY', 'FOLLOW_UP_APPOINTMENT', 'Attend the wound-check appointment on the date given.', 40),

  ('AFTERCARE-CAESAREAN', 'WOUND_SITE_CARE', 'Keep the caesarean wound clean and dry.', 10),
  ('AFTERCARE-CAESAREAN', 'ACTIVITY_RESTRICTION', 'Avoid heavy lifting beyond the baby''s weight for the period advised.', 20),
  ('AFTERCARE-CAESAREAN', 'WARNING_SIGNS', 'Return if fever, heavy bleeding, wound discharge or severe abdominal pain develop.', 30),
  ('AFTERCARE-CAESAREAN', 'FOLLOW_UP_APPOINTMENT', 'Attend the postnatal and wound-check appointments on the dates given.', 40),

  ('AFTERCARE-ARTHROPLASTY', 'WOUND_SITE_CARE', 'Keep the joint wound clean and dry.', 10),
  ('AFTERCARE-ARTHROPLASTY', 'ACTIVITY_RESTRICTION', 'Follow the weight-bearing and movement precautions given by the surgical and rehabilitation team.', 20),
  ('AFTERCARE-ARTHROPLASTY', 'DEVICE_CARE', 'What was implanted, and any device-specific precautions (e.g. MRI compatibility, dislocation precautions).', 30),
  ('AFTERCARE-ARTHROPLASTY', 'WARNING_SIGNS', 'Return if the joint becomes red, swollen, painful or unstable, or if fever develops.', 40),
  ('AFTERCARE-ARTHROPLASTY', 'FOLLOW_UP_APPOINTMENT', 'Attend the scheduled device and rehabilitation checks.', 50),

  ('AFTERCARE-CENTRAL-LINE', 'WOUND_SITE_CARE', 'Keep the line insertion site clean and dry; the dressing is changed per the aseptic technique schedule.', 10),
  ('AFTERCARE-CENTRAL-LINE', 'WARNING_SIGNS', 'Return if the site becomes red, swollen, painful or discharges, or if you develop fever or rigors.', 20),
  ('AFTERCARE-CENTRAL-LINE', 'WHO_TO_CONTACT', 'Contact details for line-related concerns.', 30),

  ('AFTERCARE-LUMBAR-PUNCTURE', 'ACTIVITY_RESTRICTION', 'Lie flat for the period advised, if instructed, to reduce headache risk.', 10),
  ('AFTERCARE-LUMBAR-PUNCTURE', 'WARNING_SIGNS', 'Return if you develop a severe headache worse on sitting up, fever, or leg weakness.', 20),
  ('AFTERCARE-LUMBAR-PUNCTURE', 'RESULTS_FOLLOW_UP', 'CSF results will be discussed at your follow-up appointment.', 30)
) AS x(template_code, kind, text, ord) ON x.template_code = t.template_code
WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'
ON CONFLICT DO NOTHING;

INSERT INTO procedures.aftercare_template_channel (template_id, delivery_channel)
SELECT t.id, x.channel
FROM procedures.aftercare_template t
JOIN (VALUES
  ('AFTERCARE-LAPAROTOMY', 'CLINICAL_SUMMARY'), ('AFTERCARE-LAPAROTOMY', 'KHULUMA'),
  ('AFTERCARE-LAPAROTOMY', 'PRINTED_OFFLINE_HANDOUT'),
  ('AFTERCARE-CAESAREAN', 'CLINICAL_SUMMARY'), ('AFTERCARE-CAESAREAN', 'KHULUMA'),
  ('AFTERCARE-CAESAREAN', 'PRINTED_OFFLINE_HANDOUT'), ('AFTERCARE-CAESAREAN', 'CAREGIVER_VIEW'),
  ('AFTERCARE-ARTHROPLASTY', 'CLINICAL_SUMMARY'), ('AFTERCARE-ARTHROPLASTY', 'KHULUMA'),
  ('AFTERCARE-ARTHROPLASTY', 'PRINTED_OFFLINE_HANDOUT'),
  ('AFTERCARE-CENTRAL-LINE', 'CLINICAL_SUMMARY'),
  ('AFTERCARE-LUMBAR-PUNCTURE', 'CLINICAL_SUMMARY'), ('AFTERCARE-LUMBAR-PUNCTURE', 'PRINTED_OFFLINE_HANDOUT')
) AS x(template_code, channel) ON x.template_code = t.template_code
WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'
ON CONFLICT DO NOTHING;
