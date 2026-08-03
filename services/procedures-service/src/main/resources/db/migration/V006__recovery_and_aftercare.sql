-- Procedures :: V006 — recovery settings (pipeline §15) and aftercare templates (§17).
--
-- §15 RECOVERY is rated PARTIAL by the Phase 0 audit: PACU is real and gated for theatre, but
-- non-theatre recovery (bedside, endoscopy, dialysis, a sedation-only recovery bay) has nowhere
-- to be declared. §17 AFTERCARE is rated ABSENT — nothing is generated or delivered.
--
-- HONEST SOURCING NOTE, read before touching either vocabulary below: the audit paraphrases the
-- original specification as declaring "twelve outputs and five delivery channels" for aftercare,
-- but the literal enumerated list was never vendored into this repository — only the audit's own
-- five named channels survive in docs/clinical/procedures-pipeline/audit.md line 175 (clinical
-- summary, Khuluma, Nompilo, printed/offline handout, caregiver view), which this migration reuses
-- verbatim because they come from this programme's own prior audit work, not a fabrication. The
-- twelve INSTRUCTION KINDS below are NOT a reproduction of the spec's literal list — nobody in
-- this session has read that list. They are a defensible engineering baseline grounded in R15's
-- governing standard (RESULTS.CRITICAL_ACKNOWLEDGEMENT: "no patient disappears after 'procedure
-- completed' — recovery is completed, aftercare is issued, results are reviewed") and ordinary
-- discharge-communication practice. Marked content_maturity='ENGINEERING_SEED' on every template,
-- the same honesty flag EwsCalculatorEngine already uses for content pending real reconciliation —
-- this is a repeat of the SNOMED lesson from P1 (a wrong specific value is worse than an honestly
-- marked placeholder), applied to a taxonomy instead of a code.

CREATE TABLE procedures.recovery_setting (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    setting_code                VARCHAR(32) NOT NULL,
    setting_label                VARCHAR(96) NOT NULL,
    minimum_observation_minutes  INT,
    discharge_readiness_criteria TEXT NOT NULL,
    monitoring_required          TEXT NOT NULL,
    source_citation               TEXT,
    CONSTRAINT uq_recovery_setting_code UNIQUE (tenant_id, setting_code),
    CONSTRAINT chk_recovery_setting_code CHECK (setting_code IN (
        'NONE', 'BEDSIDE_OBSERVATION', 'RECOVERY_BAY', 'PACU', 'EXTENDED_OBSERVATION'))
);

ALTER TABLE procedures.procedure_definition
    ADD COLUMN IF NOT EXISTS default_recovery_setting_code VARCHAR(32);

CREATE TABLE procedures.aftercare_template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    template_code        VARCHAR(64) NOT NULL,
    template_name        VARCHAR(255) NOT NULL,
    applicable_setting    VARCHAR(48),
    description          TEXT,
    source_citation       TEXT,
    status               VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    approving_authority   VARCHAR(128),
    -- ENGINEERING_SEED (default) | RATIFIED. Not a governance-approval field (that's `status` +
    -- `approving_authority`, matching every other template in this schema) — this specifically
    -- flags whether the instruction taxonomy has been reconciled against the literal source
    -- specification, which for every template seeded here it has not.
    content_maturity     VARCHAR(24) NOT NULL DEFAULT 'ENGINEERING_SEED',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_aftercare_template_code UNIQUE (tenant_id, template_code),
    CONSTRAINT chk_aftercare_template_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT chk_aftercare_template_content_maturity CHECK (content_maturity IN ('ENGINEERING_SEED', 'RATIFIED')),
    CONSTRAINT chk_aftercare_template_published_authority CHECK (
        status <> 'PUBLISHED'
        OR (approving_authority IS NOT NULL AND length(btrim(approving_authority)) > 0))
);

ALTER TABLE procedures.procedure_definition
    ADD COLUMN IF NOT EXISTS default_aftercare_template_code VARCHAR(64);

-- Rows, not columns — same reason procedure_requirement and safety_pause_confirmation_item are
-- rows: a thirteenth instruction kind should be a content release, not a migration.
CREATE TABLE procedures.aftercare_instruction (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id       UUID NOT NULL REFERENCES procedures.aftercare_template(id) ON DELETE CASCADE,
    instruction_kind  VARCHAR(32) NOT NULL,
    instruction_text  TEXT NOT NULL,
    display_order     INT NOT NULL DEFAULT 100,
    CONSTRAINT uq_aftercare_instruction UNIQUE (template_id, instruction_kind),
    CONSTRAINT chk_aftercare_instruction_kind CHECK (instruction_kind IN (
        'MEDICATION', 'WOUND_SITE_CARE', 'ACTIVITY_RESTRICTION', 'DIET', 'WARNING_SIGNS',
        'FOLLOW_UP_APPOINTMENT', 'WHO_TO_CONTACT', 'DEVICE_CARE', 'RESULTS_FOLLOW_UP',
        'DRIVING_RESTRICTION', 'BATHING_HYGIENE', 'PAIN_MANAGEMENT', 'RETURN_TO_WORK_SCHOOL'))
);

CREATE INDEX idx_aftercare_instruction_template
    ON procedures.aftercare_instruction (template_id, display_order);

-- Which of the audit's five named channels a template is deliverable through. A join table, not
-- a column on the template, because a caregiver-view or printed-handout variant is a delivery
-- decision independent of the template's own content — the same content may reach zero, one or
-- several channels depending on what a facility or a patient's context supports.
CREATE TABLE procedures.aftercare_template_channel (
    template_id       UUID NOT NULL REFERENCES procedures.aftercare_template(id) ON DELETE CASCADE,
    delivery_channel  VARCHAR(32) NOT NULL,
    PRIMARY KEY (template_id, delivery_channel),
    CONSTRAINT chk_aftercare_delivery_channel CHECK (delivery_channel IN (
        'CLINICAL_SUMMARY', 'KHULUMA', 'NOMPILO', 'PRINTED_OFFLINE_HANDOUT', 'CAREGIVER_VIEW'))
);

-- -------------------------------------------------------------------------------------
-- Seed: five recovery settings, six aftercare templates (mirroring the safety-pause seed's
-- setting classes), each with a handful of instructions and channels. Linked to the same
-- demonstration/bellwether catalogue entries P7 linked for sedation — the "only 13 procedures
-- carry full requirement sets" debt is unchanged by this migration, not silently claimed fixed.
-- -------------------------------------------------------------------------------------
INSERT INTO procedures.recovery_setting
    (tenant_id, setting_code, setting_label, minimum_observation_minutes,
     discharge_readiness_criteria, monitoring_required, source_citation)
VALUES
('00000000-0000-4000-8000-000000000001', 'NONE', 'No recovery period required', 0,
 'None — the procedure has no recovery phase of its own.',
 'None beyond the procedure''s own observation requirement.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT'),
('00000000-0000-4000-8000-000000000001', 'BEDSIDE_OBSERVATION', 'Bedside observation', 15,
 'Vital signs stable, no acute distress, able to communicate at baseline.',
 'Intermittent vital signs at the bedside.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT'),
('00000000-0000-4000-8000-000000000001', 'RECOVERY_BAY', 'Dedicated recovery bay (non-theatre)', 30,
 'Vital signs stable, return to baseline responsiveness, pain controlled.',
 'Continuous pulse oximetry; intermittent vital signs.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT'),
('00000000-0000-4000-8000-000000000001', 'PACU', 'Post-anaesthesia care unit', 60,
 'Aldrete or equivalent discharge-readiness score met; airway and cardiovascular stability confirmed.',
 'Continuous pulse oximetry, cardiac monitoring, capnography where available.',
 'ANAESTHESIA.WFSA_WHO.MONITORING'),
('00000000-0000-4000-8000-000000000001', 'EXTENDED_OBSERVATION', 'Extended observation (beyond same-day)', 240,
 'Sustained stability over the observation window; any deferred complication risk has passed its window.',
 'Continuous or frequent-interval monitoring per the specific risk being observed for.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT')
ON CONFLICT (tenant_id, setting_code) DO NOTHING;

INSERT INTO procedures.aftercare_template
    (tenant_id, template_code, template_name, applicable_setting, description,
     source_citation, status, approving_authority)
VALUES
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-THEATRE', 'Post-surgical aftercare', 'THEATRE',
 'Aftercare for a theatre procedure with a surgical wound.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-BEDSIDE', 'Bedside procedure aftercare', 'BEDSIDE',
 'Aftercare for a bedside invasive procedure.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-SEDATION', 'Sedation aftercare', 'BEDSIDE',
 'Aftercare after any depth of sedation — recovery criteria and activity restriction.',
 'SEDATION.CONTINUUM.DEPTH', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-ENDOSCOPY', 'Endoscopy aftercare', 'ENDOSCOPY',
 'Aftercare after an endoscopic procedure, including biopsy result follow-up.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-DIALYSIS', 'Dialysis session aftercare', 'DIALYSIS',
 'Aftercare after a dialysis session, including vascular access site care.',
 'RESULTS.CRITICAL_ACKNOWLEDGEMENT', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001', 'AFTERCARE-IMPLANT', 'Implant insertion aftercare', 'THEATRE',
 'Aftercare after an implant insertion, including device-specific care and what the patient carries.',
 'MED.DEVICE.UDI_TRACEABILITY', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION')
ON CONFLICT (tenant_id, template_code) DO NOTHING;

INSERT INTO procedures.aftercare_instruction (template_id, instruction_kind, instruction_text, display_order)
SELECT t.id, x.kind, x.text, x.ord
FROM procedures.aftercare_template t
JOIN (VALUES
  ('AFTERCARE-THEATRE', 'WOUND_SITE_CARE', 'Keep the wound clean and dry; change the dressing as instructed.', 10),
  ('AFTERCARE-THEATRE', 'ACTIVITY_RESTRICTION', 'Avoid heavy lifting and strenuous activity for the period advised.', 20),
  ('AFTERCARE-THEATRE', 'PAIN_MANAGEMENT', 'Take pain medication as prescribed; do not exceed the stated dose.', 30),
  ('AFTERCARE-THEATRE', 'WARNING_SIGNS', 'Return immediately if you develop fever, spreading redness, worsening pain or wound discharge.', 40),
  ('AFTERCARE-THEATRE', 'FOLLOW_UP_APPOINTMENT', 'Attend the wound-check appointment on the date given.', 50),
  ('AFTERCARE-THEATRE', 'WHO_TO_CONTACT', 'Contact details for the operating team and the nearest facility if a warning sign appears.', 60),

  ('AFTERCARE-BEDSIDE', 'WOUND_SITE_CARE', 'Keep the site clean and dry as instructed.', 10),
  ('AFTERCARE-BEDSIDE', 'WARNING_SIGNS', 'Return if bleeding does not stop, or if you develop fever or spreading redness.', 20),
  ('AFTERCARE-BEDSIDE', 'WHO_TO_CONTACT', 'Contact details for the performing team.', 30),

  ('AFTERCARE-SEDATION', 'ACTIVITY_RESTRICTION', 'Do not drive, operate machinery or make important decisions for 24 hours.', 10),
  ('AFTERCARE-SEDATION', 'DRIVING_RESTRICTION', 'Arrange for someone else to take you home; do not drive yourself.', 20),
  ('AFTERCARE-SEDATION', 'DIET', 'Start with clear fluids; resume a normal diet as tolerated.', 30),
  ('AFTERCARE-SEDATION', 'WARNING_SIGNS', 'Return if you experience prolonged drowsiness, difficulty breathing or confusion beyond the expected recovery window.', 40),

  ('AFTERCARE-ENDOSCOPY', 'DIET', 'Resume your normal diet gradually, starting with light meals.', 10),
  ('AFTERCARE-ENDOSCOPY', 'RESULTS_FOLLOW_UP', 'Biopsy results, if taken, will be discussed at your follow-up appointment.', 20),
  ('AFTERCARE-ENDOSCOPY', 'WARNING_SIGNS', 'Return if you develop severe abdominal pain, persistent bleeding or fever.', 30),
  ('AFTERCARE-ENDOSCOPY', 'FOLLOW_UP_APPOINTMENT', 'Attend the results appointment on the date given.', 40),

  ('AFTERCARE-DIALYSIS', 'WOUND_SITE_CARE', 'Keep the vascular access site clean and watch for signs of infection.', 10),
  ('AFTERCARE-DIALYSIS', 'ACTIVITY_RESTRICTION', 'Avoid pressure or heavy lifting on the access-site limb.', 20),
  ('AFTERCARE-DIALYSIS', 'WARNING_SIGNS', 'Return if the access site bleeds, swells or becomes painful, or if you feel unusually unwell before your next session.', 30),
  ('AFTERCARE-DIALYSIS', 'FOLLOW_UP_APPOINTMENT', 'Your next session is scheduled; do not miss it without contacting the unit.', 40),

  ('AFTERCARE-IMPLANT', 'DEVICE_CARE', 'What was implanted, and any device-specific precautions (e.g. MRI compatibility, activity limits).', 10),
  ('AFTERCARE-IMPLANT', 'WOUND_SITE_CARE', 'Keep the insertion site clean and dry as instructed.', 20),
  ('AFTERCARE-IMPLANT', 'WARNING_SIGNS', 'Return if the site becomes red, swollen, painful or discharges.', 30),
  ('AFTERCARE-IMPLANT', 'FOLLOW_UP_APPOINTMENT', 'Attend the scheduled device check.', 40),
  ('AFTERCARE-IMPLANT', 'WHO_TO_CONTACT', 'Carry your device information and know who to contact for device-related concerns.', 50)
) AS x(template_code, kind, text, ord) ON x.template_code = t.template_code
WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'
ON CONFLICT DO NOTHING;

-- Every template reaches the clinical summary at minimum (results ch. §16 precedent: the
-- record is never optional); the rest depend on what the setting can plausibly reach.
INSERT INTO procedures.aftercare_template_channel (template_id, delivery_channel)
SELECT t.id, x.channel
FROM procedures.aftercare_template t
JOIN (VALUES
  ('AFTERCARE-THEATRE', 'CLINICAL_SUMMARY'), ('AFTERCARE-THEATRE', 'KHULUMA'),
  ('AFTERCARE-THEATRE', 'PRINTED_OFFLINE_HANDOUT'), ('AFTERCARE-THEATRE', 'CAREGIVER_VIEW'),
  ('AFTERCARE-BEDSIDE', 'CLINICAL_SUMMARY'), ('AFTERCARE-BEDSIDE', 'PRINTED_OFFLINE_HANDOUT'),
  ('AFTERCARE-SEDATION', 'CLINICAL_SUMMARY'), ('AFTERCARE-SEDATION', 'PRINTED_OFFLINE_HANDOUT'),
  ('AFTERCARE-SEDATION', 'CAREGIVER_VIEW'),
  ('AFTERCARE-ENDOSCOPY', 'CLINICAL_SUMMARY'), ('AFTERCARE-ENDOSCOPY', 'KHULUMA'),
  ('AFTERCARE-ENDOSCOPY', 'NOMPILO'),
  ('AFTERCARE-DIALYSIS', 'CLINICAL_SUMMARY'), ('AFTERCARE-DIALYSIS', 'KHULUMA'),
  ('AFTERCARE-IMPLANT', 'CLINICAL_SUMMARY'), ('AFTERCARE-IMPLANT', 'KHULUMA'),
  ('AFTERCARE-IMPLANT', 'PRINTED_OFFLINE_HANDOUT')
) AS x(template_code, channel) ON x.template_code = t.template_code
WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'
ON CONFLICT DO NOTHING;

-- Link the same demonstration/bellwether procedures P7 linked for sedation, so the linkage is
-- proven end to end for at least a real subset rather than left as schema nothing points at.
UPDATE procedures.procedure_definition SET default_recovery_setting_code = 'PACU'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-LAPAROTOMY', 'PROC-CAESAREAN-SECTION', 'PROC-TOTAL-HIP-REPLACEMENT');
UPDATE procedures.procedure_definition SET default_recovery_setting_code = 'RECOVERY_BAY'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-OGD', 'PROC-COLONOSCOPY', 'PROC-BRONCHOSCOPY');
UPDATE procedures.procedure_definition SET default_recovery_setting_code = 'EXTENDED_OBSERVATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-HAEMODIALYSIS', 'PROC-PERITONEAL-DIALYSIS');
UPDATE procedures.procedure_definition SET default_recovery_setting_code = 'BEDSIDE_OBSERVATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-CENTRAL-LINE', 'PROC-ARTERIAL-LINE', 'PROC-LUMBAR-PUNCTURE');

UPDATE procedures.procedure_definition SET default_aftercare_template_code = 'AFTERCARE-THEATRE'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-LAPAROTOMY', 'PROC-CAESAREAN-SECTION', 'PROC-TOTAL-HIP-REPLACEMENT');
UPDATE procedures.procedure_definition SET default_aftercare_template_code = 'AFTERCARE-ENDOSCOPY'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-OGD', 'PROC-COLONOSCOPY', 'PROC-BRONCHOSCOPY');
UPDATE procedures.procedure_definition SET default_aftercare_template_code = 'AFTERCARE-DIALYSIS'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-HAEMODIALYSIS', 'PROC-PERITONEAL-DIALYSIS');
UPDATE procedures.procedure_definition SET default_aftercare_template_code = 'AFTERCARE-BEDSIDE'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-CENTRAL-LINE', 'PROC-ARTERIAL-LINE', 'PROC-LUMBAR-PUNCTURE');
