-- =====================================================================================
-- Procedures :: V005 — safety-pause templates and the sedation continuum (pipeline §9, §10)
--
-- CORRECTION BEFORE THIS WAS WRITTEN: earlier commits and memory in this programme said
-- "seven-level sedation continuum". The specification actually lists eight: no sedation,
-- local anaesthesia, minimal, moderate, deep, general anaesthesia, regional anaesthesia,
-- non-pharmacological support. Caught by re-reading the source text before encoding a count
-- into a CHECK constraint, which is exactly where a wrong number becomes expensive to fix.
--
-- §9 SAFETY PAUSE. procedure_definition.safety_pause_template has held string literals like
-- 'SAFETY-PAUSE-BEDSIDE' since the V003 seed with no table behind them — a code with nothing
-- to resolve to. This defines the ten class-specific templates and, for each, which of the
-- specification's fifteen confirmation items apply. Rows, not columns — the same reason
-- procedure_requirement is rows: a template that needed a sixteenth confirmation item would
-- otherwise need a schema migration instead of a content release.
--
-- §10 SEDATION. procedures-service owns the REQUIREMENTS side only (ADR decision 1) — what
-- monitoring, provider competence and rescue capability a depth of sedation demands. The
-- CAPTURE side (assessment, fasting, medicines given, start/end, complications) is an
-- execution record and stays with whichever service performs the procedure; inpatient's
-- anaesthesia chart (V024) already captures exactly that for theatre. The standard this
-- follows (WFSA-WHO International Standards for a Safe Practice of Anesthesia, declared in
-- docs/clinical-governance/procedures/standards-baseline.json as SEDATION.CONTINUUM.DEPTH)
-- states the requirement plainly: requirements follow the depth that MAY be reached, not the
-- depth intended. A moderate-sedation endoscopy needs deep-sedation rescue capability
-- available, because sedation depth is not perfectly controllable. That is what
-- rescue_capability_level encodes below — not a duplicate of the level column, but the next
-- level up's capability, required to be present regardless of the level intended.
-- =====================================================================================

CREATE TABLE procedures.safety_pause_template (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    template_code        VARCHAR(64) NOT NULL,
    template_name        VARCHAR(255) NOT NULL,
    applicable_setting    VARCHAR(48),
    description          TEXT,
    source_citation       TEXT,
    status               VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    approving_authority   VARCHAR(128),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_safety_pause_template_code UNIQUE (tenant_id, template_code),
    CONSTRAINT chk_safety_pause_template_status CHECK (status IN ('DRAFT','PUBLISHED','RETIRED')),
    -- Same governance discipline as the catalogue: unratified content says so explicitly.
    CONSTRAINT chk_safety_pause_template_published_authority CHECK (
        status <> 'PUBLISHED'
        OR (approving_authority IS NOT NULL AND length(btrim(approving_authority)) > 0))
);

-- procedure_definition.safety_pause_template is a free-text code column (V002) rather than a
-- foreign key, matching the pattern already used for consent_type and aftercare_template —
-- those are references into governed content owned by other services (mvumo, this table),
-- resolved at read time rather than enforced by a cross-schema FK. Enforcing referential
-- integrity here would require every peer's content to exist before this migration runs,
-- which is backwards for content that is meant to evolve independently.

CREATE TABLE procedures.safety_pause_confirmation_item (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id          UUID NOT NULL REFERENCES procedures.safety_pause_template(id) ON DELETE CASCADE,
    confirmation_item    VARCHAR(32) NOT NULL,
    -- PATIENT | PROCEDURE | SITE | SIDE | CONSENT | TEAM | ALLERGIES | ANTICOAGULATION |
    -- ANTIBIOTICS | IMAGING | EQUIPMENT | SPECIMEN_PLAN | IMPLANT | EXPECTED_CRITICAL_EVENTS |
    -- EMERGENCY_PLAN
    prompt_text          TEXT NOT NULL,
    display_order        INT NOT NULL DEFAULT 100,
    CONSTRAINT uq_safety_pause_confirmation_item UNIQUE (template_id, confirmation_item),
    CONSTRAINT chk_safety_pause_confirmation_item CHECK (confirmation_item IN (
        'PATIENT','PROCEDURE','SITE','SIDE','CONSENT','TEAM','ALLERGIES','ANTICOAGULATION',
        'ANTIBIOTICS','IMAGING','EQUIPMENT','SPECIMEN_PLAN','IMPLANT',
        'EXPECTED_CRITICAL_EVENTS','EMERGENCY_PLAN'))
);

CREATE INDEX idx_safety_pause_confirmation_item_template
    ON procedures.safety_pause_confirmation_item (template_id, display_order);

-- -------------------------------------------------------------------------------------
-- Sedation continuum — eight levels, verified against the specification text rather than
-- assumed. NO_SEDATION and NON_PHARMACOLOGICAL are both zero-drug states and are kept
-- distinct: NON_PHARMACOLOGICAL names a deliberate technique (distraction, positioning,
-- swaddling), not merely the absence of one, and collapsing them would lose that.
-- -------------------------------------------------------------------------------------
CREATE TABLE procedures.sedation_level (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID NOT NULL,
    level_code                  VARCHAR(32) NOT NULL,
    level_label                 VARCHAR(96) NOT NULL,
    depth_rank                  INT NOT NULL,
    -- The rule the standard states plainly: requirements at this level are set by the NEXT
    -- level's capability, because depth is not perfectly controllable. NULL at the top of the
    -- continuum (general anaesthesia has no "next level" to rescue into).
    rescue_capability_level_code VARCHAR(32),
    monitoring_required          TEXT NOT NULL,
    provider_competence_required TEXT NOT NULL,
    typical_recovery_criteria    TEXT,
    source_citation              TEXT,
    CONSTRAINT uq_sedation_level_code UNIQUE (tenant_id, level_code),
    -- depth_rank is deliberately NOT unique. Sedation depth is not a strict linear order: the
    -- specification lists eight named states, not eight points on one scale. NO_SEDATION and
    -- NON_PHARMACOLOGICAL are both zero-drug but are alternative TECHNIQUES, not the same
    -- state; GENERAL_ANAESTHESIA and REGIONAL_ANAESTHESIA both sit at the deep end but measure
    -- depth on different axes — consciousness for one, block extent for the other — so ranking
    -- them against each other numerically would assert an ordering the standard does not make.
    -- A unique constraint here would be encoding a false precision.
    CONSTRAINT chk_sedation_level_code CHECK (level_code IN (
        'NO_SEDATION','LOCAL_ANAESTHESIA','MINIMAL_SEDATION','MODERATE_SEDATION',
        'DEEP_SEDATION','GENERAL_ANAESTHESIA','REGIONAL_ANAESTHESIA','NON_PHARMACOLOGICAL'))
);

ALTER TABLE procedures.sedation_level
    ADD CONSTRAINT fk_sedation_level_rescue
        FOREIGN KEY (tenant_id, rescue_capability_level_code)
        REFERENCES procedures.sedation_level (tenant_id, level_code);

-- -------------------------------------------------------------------------------------
-- Link sedation to the catalogue. Additive on procedure_definition, same as V004's
-- appropriateness columns — a definition may declare the sedation depth it is typically
-- performed under, without procedures-service owning the execution record for it.
-- -------------------------------------------------------------------------------------
ALTER TABLE procedures.procedure_definition
    ADD COLUMN IF NOT EXISTS default_sedation_level_code VARCHAR(32);

-- A SEDATION requirement row (requirement_kind already accepted this value since V002) may
-- name which level it requires, so the resolver can look up that level's monitoring, provider
-- and rescue-capability requirements rather than the requirement being free text.
ALTER TABLE procedures.procedure_requirement
    ADD COLUMN IF NOT EXISTS sedation_level_code VARCHAR(32);

ALTER TABLE procedures.procedure_requirement
    ADD CONSTRAINT chk_procedure_requirement_sedation_level_only_on_sedation_kind CHECK (
        sedation_level_code IS NULL OR requirement_kind = 'SEDATION');

-- -------------------------------------------------------------------------------------
-- Seed: the ten safety-pause templates (§9) and their confirmation items, and the eight
-- sedation levels (§10) with the rescue-capability chain and WFSA-WHO citations.
-- -------------------------------------------------------------------------------------
INSERT INTO procedures.safety_pause_template
    (tenant_id, template_code, template_name, applicable_setting, description,
     source_citation, status, approving_authority)
VALUES
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-BEDSIDE','Bedside procedure safety pause','BEDSIDE',
 'Confirmation before an invasive procedure performed at the bedside.',
 'PS.WRONG_SITE.PREVENTION','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-SURGERY','WHO Surgical Safety Checklist Time Out','THEATRE',
 'The Time Out phase of the WHO Surgical Safety Checklist, before skin incision.',
 'SSC.2009.TIME_OUT','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-SEDATION','Sedation safety pause','BEDSIDE',
 'Confirmation before administering sedation of any depth.',
 'SEDATION.CONTINUUM.DEPTH','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-ENDOSCOPY','Endoscopy safety pause','ENDOSCOPY',
 'Confirmation before an endoscopic procedure, including scope reprocessing status.',
 'IPC.CORE.STERILE_PROCESSING','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-INTERVENTIONAL','Interventional imaging safety pause','INTERVENTIONAL_RADIOLOGY',
 'Confirmation before an image-guided intervention.',
 'PS.WRONG_SITE.PREVENTION','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-DIALYSIS','Dialysis safety pause','DIALYSIS',
 'Confirmation before initiating a dialysis session, including vascular access.',
 'PS.WRONG_SITE.PREVENTION','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-TRANSFUSION','Transfusion safety pause','WARD',
 'Bedside positive patient identification before transfusion, per WHO clinical transfusion guidance.',
 'BLOOD.TRANSFUSION.APPROPRIATE_USE','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-IMPLANT','Implant insertion safety pause','THEATRE',
 'Confirmation before inserting an implant, including UDI and recall check.',
 'MED.DEVICE.UDI_TRACEABILITY','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-SPECIMEN','Specimen collection safety pause','BEDSIDE',
 'Confirmation before collecting a specimen, including labelling and destination.',
 'SPECIMEN.CHAIN_OF_CUSTODY','PUBLISHED','PENDING_MOHCC_RATIFICATION'),
('00000000-0000-4000-8000-000000000001','SAFETY-PAUSE-INVASIVE','Generic invasive procedure safety pause','CLINIC',
 'The minimum confirmation for any invasive procedure not covered by a more specific template.',
 'PS.WRONG_SITE.PREVENTION','PUBLISHED','PENDING_MOHCC_RATIFICATION')
ON CONFLICT (tenant_id, template_code) DO NOTHING;

-- Confirmation items per template. Every template gets PATIENT, PROCEDURE and CONSENT — the
-- irreducible minimum. Others are added where the class makes them meaningful; a template
-- carrying an item that never applies to its class would train clinicians to click past it,
-- which is the exact failure the appropriateness engine (P3) was built to avoid causing.
INSERT INTO procedures.safety_pause_confirmation_item (template_id, confirmation_item, prompt_text, display_order)
SELECT t.id, x.item, x.prompt, x.ord
FROM procedures.safety_pause_template t
JOIN (VALUES
  ('SAFETY-PAUSE-BEDSIDE','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-BEDSIDE','PROCEDURE','Confirm the intended procedure.',20),
  ('SAFETY-PAUSE-BEDSIDE','SITE','Confirm the anatomical site.',30),
  ('SAFETY-PAUSE-BEDSIDE','SIDE','Confirm the side, if lateralised.',40),
  ('SAFETY-PAUSE-BEDSIDE','CONSENT','Confirm consent is recorded.',50),
  ('SAFETY-PAUSE-BEDSIDE','ALLERGIES','Confirm known allergies.',60),
  ('SAFETY-PAUSE-BEDSIDE','EQUIPMENT','Confirm required equipment is present.',70),
  ('SAFETY-PAUSE-BEDSIDE','EMERGENCY_PLAN','Confirm the plan if the patient deteriorates.',80),

  ('SAFETY-PAUSE-SURGERY','PATIENT','Confirm patient identity, out loud, with the whole team.',10),
  ('SAFETY-PAUSE-SURGERY','PROCEDURE','Confirm the procedure to be performed.',20),
  ('SAFETY-PAUSE-SURGERY','SITE','Confirm the surgical site.',30),
  ('SAFETY-PAUSE-SURGERY','SIDE','Confirm the side, if lateralised.',40),
  ('SAFETY-PAUSE-SURGERY','CONSENT','Confirm consent is recorded.',50),
  ('SAFETY-PAUSE-SURGERY','TEAM','Every team member introduces themselves by name and role.',60),
  ('SAFETY-PAUSE-SURGERY','ALLERGIES','Confirm known allergies.',70),
  ('SAFETY-PAUSE-SURGERY','ANTIBIOTICS','Confirm antibiotic prophylaxis given within the last 60 minutes.',80),
  ('SAFETY-PAUSE-SURGERY','IMAGING','Confirm essential imaging is displayed.',90),
  ('SAFETY-PAUSE-SURGERY','EQUIPMENT','Confirm equipment issues and sterility.',100),
  ('SAFETY-PAUSE-SURGERY','IMPLANT','Confirm implant availability, if applicable.',110),
  ('SAFETY-PAUSE-SURGERY','EXPECTED_CRITICAL_EVENTS','Surgeon, anaesthetist and nursing team review anticipated critical events.',120),

  ('SAFETY-PAUSE-SEDATION','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-SEDATION','PROCEDURE','Confirm the procedure and intended sedation depth.',20),
  ('SAFETY-PAUSE-SEDATION','CONSENT','Confirm sedation consent is recorded.',30),
  ('SAFETY-PAUSE-SEDATION','ALLERGIES','Confirm known allergies, including to sedative agents.',40),
  ('SAFETY-PAUSE-SEDATION','EQUIPMENT','Confirm monitoring and rescue equipment for the next level of depth.',50),
  ('SAFETY-PAUSE-SEDATION','EMERGENCY_PLAN','Confirm the reversal and rescue plan.',60),

  ('SAFETY-PAUSE-ENDOSCOPY','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-ENDOSCOPY','PROCEDURE','Confirm the intended procedure and scope.',20),
  ('SAFETY-PAUSE-ENDOSCOPY','CONSENT','Confirm consent is recorded.',30),
  ('SAFETY-PAUSE-ENDOSCOPY','ALLERGIES','Confirm known allergies.',40),
  ('SAFETY-PAUSE-ENDOSCOPY','EQUIPMENT','Confirm the scope has a traceable reprocessing cycle.',50),
  ('SAFETY-PAUSE-ENDOSCOPY','SPECIMEN_PLAN','Confirm the biopsy and specimen plan, if applicable.',60),

  ('SAFETY-PAUSE-INTERVENTIONAL','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-INTERVENTIONAL','PROCEDURE','Confirm the intended intervention.',20),
  ('SAFETY-PAUSE-INTERVENTIONAL','SITE','Confirm the anatomical site.',30),
  ('SAFETY-PAUSE-INTERVENTIONAL','SIDE','Confirm the side, if lateralised.',40),
  ('SAFETY-PAUSE-INTERVENTIONAL','CONSENT','Confirm consent is recorded.',50),
  ('SAFETY-PAUSE-INTERVENTIONAL','IMAGING','Confirm imaging guidance is available and displayed.',60),
  ('SAFETY-PAUSE-INTERVENTIONAL','SPECIMEN_PLAN','Confirm the specimen plan, if applicable.',70),

  ('SAFETY-PAUSE-DIALYSIS','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-DIALYSIS','PROCEDURE','Confirm the prescription for this session.',20),
  ('SAFETY-PAUSE-DIALYSIS','SITE','Confirm the vascular access site.',30),
  ('SAFETY-PAUSE-DIALYSIS','CONSENT','Confirm consent for ongoing dialysis is recorded.',35),
  ('SAFETY-PAUSE-DIALYSIS','EQUIPMENT','Confirm the dialyser and machine are ready.',40),
  ('SAFETY-PAUSE-DIALYSIS','EMERGENCY_PLAN','Confirm the plan for a haemodynamic event.',50),

  ('SAFETY-PAUSE-TRANSFUSION','PATIENT','Bedside positive identification against the unit, by the patient wristband.',10),
  ('SAFETY-PAUSE-TRANSFUSION','PROCEDURE','Confirm the correct blood product, unit and indication for this patient.',15),
  ('SAFETY-PAUSE-TRANSFUSION','CONSENT','Confirm transfusion consent is recorded.',20),
  ('SAFETY-PAUSE-TRANSFUSION','EQUIPMENT','Confirm the unit matches the patient and is within its use-by time.',30),
  ('SAFETY-PAUSE-TRANSFUSION','EMERGENCY_PLAN','Confirm the reaction-management plan.',40),

  ('SAFETY-PAUSE-IMPLANT','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-IMPLANT','PROCEDURE','Confirm the intended implant procedure.',20),
  ('SAFETY-PAUSE-IMPLANT','SITE','Confirm the anatomical site.',30),
  ('SAFETY-PAUSE-IMPLANT','SIDE','Confirm the side, if lateralised.',40),
  ('SAFETY-PAUSE-IMPLANT','CONSENT','Confirm implant consent, including patient information given.',50),
  ('SAFETY-PAUSE-IMPLANT','IMPLANT','Confirm implant identity, size, lot and expiry, and that it is not recalled.',60),
  ('SAFETY-PAUSE-IMPLANT','EQUIPMENT','Confirm insertion instrumentation is available and sterile.',70),

  ('SAFETY-PAUSE-SPECIMEN','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-SPECIMEN','PROCEDURE','Confirm what is being collected and why.',20),
  ('SAFETY-PAUSE-SPECIMEN','SITE','Confirm the collection site.',30),
  ('SAFETY-PAUSE-SPECIMEN','CONSENT','Confirm consent for the collection is recorded.',35),
  ('SAFETY-PAUSE-SPECIMEN','SPECIMEN_PLAN','Confirm container, fixative, labelling and destination before collection.',40),

  ('SAFETY-PAUSE-INVASIVE','PATIENT','Confirm patient identity.',10),
  ('SAFETY-PAUSE-INVASIVE','PROCEDURE','Confirm the intended procedure.',20),
  ('SAFETY-PAUSE-INVASIVE','CONSENT','Confirm consent is recorded.',30)
) AS x(template_code, item, prompt, ord) ON x.template_code = t.template_code
WHERE t.tenant_id = '00000000-0000-4000-8000-000000000001'
ON CONFLICT DO NOTHING;

-- Sedation levels, depth-ranked, each requiring the NEXT level's rescue capability — the
-- specification's own governing rule, encoded as data rather than left as a sentence.
INSERT INTO procedures.sedation_level
    (tenant_id, level_code, level_label, depth_rank, monitoring_required,
     provider_competence_required, typical_recovery_criteria, source_citation)
VALUES
('00000000-0000-4000-8000-000000000001','NO_SEDATION','No sedation',0,
 'Standard vital signs per the procedure''s own observation requirement.',
 'The performing clinician''s ordinary competence for the procedure.',
 'No sedation-specific recovery — the procedure''s own aftercare applies.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','NON_PHARMACOLOGICAL','Non-pharmacological support',0,
 'Standard vital signs; behavioural distress monitored throughout.',
 'A person trained in the technique used (e.g. distraction, positioning, swaddling).',
 'No sedation-specific recovery.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','LOCAL_ANAESTHESIA','Local anaesthesia',1,
 'Standard vital signs; observation for local anaesthetic systemic toxicity.',
 'The performing clinician, competent in the local anaesthetic technique used.',
 'Resolution of any LAST symptoms; the procedure''s own aftercare otherwise applies.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','MINIMAL_SEDATION','Minimal sedation (anxiolysis)',2,
 'Continuous verbal contact; intermittent vital signs.',
 'A provider able to rescue from moderate sedation should depth exceed intention.',
 'Return to baseline verbal responsiveness and orientation.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','MODERATE_SEDATION','Moderate sedation',3,
 'Continuous pulse oximetry; continuous verbal or tactile response monitoring.',
 'A provider able to rescue from deep sedation should depth exceed intention.',
 'Aldrete or equivalent discharge-readiness score met; airway and cardiovascular stability confirmed.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','DEEP_SEDATION','Deep sedation',4,
 'Continuous pulse oximetry, continuous cardiac monitoring, capnography where available.',
 'A provider able to rescue from general anaesthesia should depth exceed intention.',
 'Aldrete or equivalent discharge-readiness score met; airway reflexes and cardiovascular stability confirmed.',
 'SEDATION.CONTINUUM.DEPTH'),
('00000000-0000-4000-8000-000000000001','GENERAL_ANAESTHESIA','General anaesthesia',5,
 'Continuous pulse oximetry, capnography, cardiac monitoring, temperature.',
 'An anaesthesia provider throughout, per WFSA-WHO mandatory-tier standards.',
 'Full Aldrete or equivalent discharge-readiness score; airway, ventilation and cardiovascular stability confirmed.',
 'ANAESTHESIA.WFSA_WHO.MONITORING'),
('00000000-0000-4000-8000-000000000001','REGIONAL_ANAESTHESIA','Regional anaesthesia',5,
 'Continuous pulse oximetry, cardiac monitoring; block level and motor/sensory function assessed.',
 'A provider able to rescue to general anaesthesia should the block be inadequate or fail.',
 'Adequate return of motor and sensory function per the block performed, or discharge criteria met with documented residual block.',
 'SEDATION.CONTINUUM.DEPTH')
ON CONFLICT (tenant_id, level_code) DO NOTHING;

-- The rescue-capability chain: each level (above minimal) requires the capability of the
-- level one depth-rank higher. General anaesthesia sits at the top with no level above it to
-- rescue into — its own monitoring and provider requirements ARE the ceiling.
UPDATE procedures.sedation_level SET rescue_capability_level_code = 'MODERATE_SEDATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND level_code = 'MINIMAL_SEDATION';
UPDATE procedures.sedation_level SET rescue_capability_level_code = 'DEEP_SEDATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND level_code = 'MODERATE_SEDATION';
UPDATE procedures.sedation_level SET rescue_capability_level_code = 'GENERAL_ANAESTHESIA'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND level_code IN ('DEEP_SEDATION','REGIONAL_ANAESTHESIA');

-- Link the seeded endoscopy, dialysis and central-line procedures to their typical sedation
-- depth, and their SEDATION requirement row to that level — proving the linkage the resolver
-- will use, not leaving it as schema with nothing pointed at it.
UPDATE procedures.procedure_definition SET default_sedation_level_code = 'MODERATE_SEDATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-OGD','PROC-COLONOSCOPY','PROC-BRONCHOSCOPY');
UPDATE procedures.procedure_definition SET default_sedation_level_code = 'NO_SEDATION'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-HAEMODIALYSIS','PROC-PERITONEAL-DIALYSIS');
UPDATE procedures.procedure_definition SET default_sedation_level_code = 'LOCAL_ANAESTHESIA'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-CENTRAL-LINE','PROC-ARTERIAL-LINE','PROC-LUMBAR-PUNCTURE');
UPDATE procedures.procedure_definition SET default_sedation_level_code = 'GENERAL_ANAESTHESIA'
    WHERE tenant_id = '00000000-0000-4000-8000-000000000001' AND definition_code IN ('PROC-LAPAROTOMY','PROC-CAESAREAN-SECTION','PROC-TOTAL-HIP-REPLACEMENT');

UPDATE procedures.procedure_requirement r
   SET sedation_level_code = 'MODERATE_SEDATION'
  FROM procedures.procedure_definition d
 WHERE r.definition_id = d.id
   AND d.tenant_id = '00000000-0000-4000-8000-000000000001'
   AND d.definition_code = 'PROC-OGD'
   AND r.requirement_kind = 'SEDATION';
