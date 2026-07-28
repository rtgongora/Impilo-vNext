-- Procedures :: V008 — complication profiles (pipeline §18).
--
-- procedure_definition.complication_profile has existed since V002 (VARCHAR(64)) with NO seed
-- value anywhere in V003 and NO resolving table — one step earlier in the same "a code with
-- nothing to resolve to" state V005 found for safety_pause_template and V007 fixed for
-- aftercare_template. Resolved HERE, from the start, against the column that already exists —
-- applying the Wave P-R2 lesson directly rather than repeating it a third time (P9 built a
-- parallel column instead of resolving aftercare_template; this migration does not repeat that).
--
-- SCOPE, per ADR-SURGERY-AND-PROCEDURES-SERVICE-BOUNDARIES: this is content/classification
-- reference data only (engine-not-store) — what complications a procedure class is expected to
-- monitor for, and how each is graded. It is NOT:
--   - a complication EXECUTION record (an actual complication that occurred) — that belongs to
--     whichever service performs the procedure (inpatient-service today), the same boundary P8
--     drew for specimens (inpatient) and implants (inventory);
--   - "reopen-episode semantics" — inpatient.procedure_episode has no REOPENED state and no
--     reopen method; a real, confirmed gap, but an inpatient-service episode-lifecycle change,
--     not a procedures-service content addition. Left as a named debt, not attempted here;
--   - complication PATHWAY INSTANCES (the workflow of managing a specific patient's specific
--     complication) — the ADR gives this to surgery-service (port 8396, not yet built; the
--     resulting problem lands in pct_problems per CC-2). Nothing to wire here until that
--     service exists.
--
-- SOURCING. The severity-grading axis (clavien_dindo_grade) is a REAL, internationally-used,
-- literature-cited standard (Dindo/Demartines/Clavien 2004, Ann Surg — declared in
-- standards-baseline.json as SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING) and is seeded as such
-- (status='PUBLISHED', a real approving citation, no content_maturity flag needed). The
-- complication TYPES within a profile (complication_class rows) have no equivalent sourced
-- taxonomy available in this repository — same honest-sourcing situation V006 already declared
-- for aftercare instruction kinds — so complication_profile itself carries the same
-- content_maturity='ENGINEERING_SEED' flag V006 introduced, applied consistently rather than
-- invented fresh here.

CREATE TABLE procedures.clavien_dindo_grade (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID NOT NULL,
    grade_code        VARCHAR(8) NOT NULL,
    grade_label       VARCHAR(255) NOT NULL,
    description       TEXT NOT NULL,
    -- Ordinal for sorting only (I < II < IIIa < IIIb < IVa < IVb < V) — NOT a linear severity
    -- score to be summed or averaged; the standard itself is an ordered categorical scale.
    display_order     INT NOT NULL,
    source_citation    TEXT NOT NULL,
    CONSTRAINT uq_clavien_dindo_grade_code UNIQUE (tenant_id, grade_code),
    CONSTRAINT chk_clavien_dindo_grade_code CHECK (grade_code IN
        ('I', 'II', 'IIIA', 'IIIB', 'IVA', 'IVB', 'V'))
);

CREATE TABLE procedures.complication_profile (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    profile_code         VARCHAR(64) NOT NULL,
    profile_name         VARCHAR(255) NOT NULL,
    applicable_setting    VARCHAR(48),
    description          TEXT,
    source_citation       TEXT,
    status               VARCHAR(24) NOT NULL DEFAULT 'PUBLISHED',
    approving_authority   VARCHAR(128),
    content_maturity     VARCHAR(24) NOT NULL DEFAULT 'ENGINEERING_SEED',
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_complication_profile_code UNIQUE (tenant_id, profile_code),
    CONSTRAINT chk_complication_profile_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT chk_complication_profile_content_maturity CHECK (content_maturity IN ('ENGINEERING_SEED', 'RATIFIED')),
    CONSTRAINT chk_complication_profile_published_authority CHECK (
        status <> 'PUBLISHED'
        OR (approving_authority IS NOT NULL AND length(btrim(approving_authority)) > 0))
);

-- Rows, not columns — same reason every other template's item list in this schema is rows.
CREATE TABLE procedures.complication_class (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id               UUID NOT NULL REFERENCES procedures.complication_profile(id) ON DELETE CASCADE,
    complication_type        VARCHAR(48) NOT NULL,
    -- The grade a complication of this type TYPICALLY reaches — a starting expectation for
    -- monitoring intensity, never a substitute for grading the complication that actually
    -- occurs (that is an execution-record concern, out of scope here).
    typical_grade_code       VARCHAR(8),
    monitoring_required      TEXT NOT NULL,
    escalation_action        TEXT NOT NULL,
    -- A never-event is monitored for but must never be treated as an acceptable outcome to
    -- plan around — the same distinction P1's site-and-side-never-overridable rule protects.
    is_never_event           BOOLEAN NOT NULL DEFAULT FALSE,
    display_order            INT NOT NULL DEFAULT 100,
    CONSTRAINT uq_complication_class UNIQUE (profile_id, complication_type),
    CONSTRAINT chk_complication_class_type CHECK (complication_type IN (
        'SURGICAL_SITE_INFECTION', 'BLEEDING', 'ANASTOMOTIC_LEAK', 'DEEP_VEIN_THROMBOSIS',
        'PULMONARY_EMBOLISM', 'WOUND_DEHISCENCE', 'ORGAN_INJURY', 'NERVE_INJURY',
        'ANAESTHESIA_COMPLICATION', 'TRANSFUSION_REACTION', 'DEVICE_MALFUNCTION',
        'RETAINED_FOREIGN_OBJECT', 'WRONG_SITE_PROCEDURE', 'READMISSION',
        'UNPLANNED_RETURN_TO_THEATRE', 'SEPSIS', 'RENAL_INJURY', 'CARDIAC_EVENT', 'DEATH')),
    -- typical_grade_code is deliberately NOT a foreign key: clavien_dindo_grade is unique on
    -- (tenant_id, grade_code), not grade_code alone, and this table has no tenant_id of its own
    -- (it inherits scope from complication_profile via profile_id) — a real composite FK would
    -- need a redundant tenant_id column here just to carry it. Resolved at read time instead,
    -- the same free-text-reference-into-governed-content pattern V005's own header describes
    -- for procedure_requirement.resolver_service. Enforced by the seed only declaring real
    -- grade codes; a runtime-proof assertion checks every seeded value actually resolves.
    CONSTRAINT chk_complication_class_grade_vocabulary CHECK (
        typical_grade_code IS NULL OR typical_grade_code IN ('I', 'II', 'IIIA', 'IIIB', 'IVA', 'IVB', 'V'))
);

-- A never-event class is queried on its own — the readiness/dashboard consumer this content
-- exists for needs "what must never happen" as a fast, separate lookup from the general
-- monitoring list, not a flag to filter client-side after fetching everything.
CREATE INDEX idx_complication_class_never_event
    ON procedures.complication_class (profile_id) WHERE is_never_event;

-- -------------------------------------------------------------------------------------
-- Seed: the seven Clavien-Dindo grades (RATIFIED — a real, cited, internationally-used
-- standard) and a handful of complication profiles mirroring the same demonstration
-- procedures P7/P9 already linked, each with a defensible complication-class subset.
-- -------------------------------------------------------------------------------------
\set tenant '00000000-0000-4000-8000-000000000001'

INSERT INTO procedures.clavien_dindo_grade (tenant_id, grade_code, grade_label, description, display_order, source_citation)
VALUES
(:'tenant', 'I', 'Grade I', 'Any deviation from the normal postoperative course, without the need for pharmacological, surgical, endoscopic or radiological intervention.', 1, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'II', 'Grade II', 'Requiring pharmacological treatment beyond that allowed for Grade I complications (e.g. blood transfusions, total parenteral nutrition).', 2, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'IIIA', 'Grade IIIa', 'Requiring surgical, endoscopic or radiological intervention, not under general anaesthesia.', 3, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'IIIB', 'Grade IIIb', 'Requiring surgical, endoscopic or radiological intervention, under general anaesthesia.', 4, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'IVA', 'Grade IVa', 'Life-threatening complication requiring intensive/critical care management, with single-organ dysfunction.', 5, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'IVB', 'Grade IVb', 'Life-threatening complication requiring intensive/critical care management, with multi-organ dysfunction.', 6, 'Dindo, Demartines, Clavien 2004, Ann Surg'),
(:'tenant', 'V', 'Grade V', 'Death of the patient.', 7, 'Dindo, Demartines, Clavien 2004, Ann Surg')
ON CONFLICT (tenant_id, grade_code) DO NOTHING;

INSERT INTO procedures.complication_profile
    (tenant_id, profile_code, profile_name, applicable_setting, description, source_citation, status, approving_authority)
VALUES
(:'tenant', 'COMPLICATIONS-LAPAROTOMY', 'Laparotomy complication profile', 'THEATRE',
 'Expected complications to monitor for after an open abdominal procedure.',
 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
(:'tenant', 'COMPLICATIONS-ARTHROPLASTY', 'Joint arthroplasty complication profile', 'THEATRE',
 'Expected complications to monitor for after a joint replacement, including device-specific risks.',
 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
(:'tenant', 'COMPLICATIONS-ENDOSCOPY', 'Endoscopy complication profile', 'ENDOSCOPY',
 'Expected complications to monitor for after an endoscopic procedure.',
 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
(:'tenant', 'COMPLICATIONS-DIALYSIS', 'Dialysis complication profile', 'DIALYSIS',
 'Expected complications to monitor for around a dialysis session and its vascular access.',
 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION'),
(:'tenant', 'COMPLICATIONS-BEDSIDE-LINE', 'Bedside line insertion complication profile', 'BEDSIDE',
 'Expected complications to monitor for after a bedside line or catheter insertion.',
 'SURGERY.CLAVIEN_DINDO.SEVERITY_GRADING', 'PUBLISHED', 'PENDING_MOHCC_RATIFICATION')
ON CONFLICT (tenant_id, profile_code) DO NOTHING;

INSERT INTO procedures.complication_class
    (profile_id, complication_type, typical_grade_code, monitoring_required, escalation_action, is_never_event, display_order)
SELECT p.id, x.ctype, x.grade, x.monitoring, x.escalation, x.never_event, x.ord
FROM procedures.complication_profile p
JOIN (VALUES
  ('COMPLICATIONS-LAPAROTOMY', 'SURGICAL_SITE_INFECTION', 'II', 'Wound review at each dressing change; temperature and inflammatory markers.', 'Escalate to the surgical team for wound reassessment and antibiotic review.', FALSE, 10),
  ('COMPLICATIONS-LAPAROTOMY', 'BLEEDING', 'IIIA', 'Vital signs, drain output, haemoglobin trend.', 'Escalate immediately to the surgical team; consider return to theatre.', FALSE, 20),
  ('COMPLICATIONS-LAPAROTOMY', 'ANASTOMOTIC_LEAK', 'IIIB', 'Abdominal pain, fever, drain output character, inflammatory markers.', 'Escalate immediately; imaging and likely return to theatre.', FALSE, 30),
  ('COMPLICATIONS-LAPAROTOMY', 'WOUND_DEHISCENCE', 'IIIA', 'Wound integrity at each dressing change.', 'Escalate to the surgical team for wound closure assessment.', FALSE, 40),
  ('COMPLICATIONS-LAPAROTOMY', 'DEEP_VEIN_THROMBOSIS', 'II', 'Calf symptoms, prophylaxis compliance.', 'Escalate for imaging and anticoagulation review.', FALSE, 50),
  ('COMPLICATIONS-LAPAROTOMY', 'RETAINED_FOREIGN_OBJECT', NULL, 'Surgical count discrepancy at closure (RITO-routed, see procedure_count).', 'Immediate imaging before the patient leaves theatre; never accepted as a risk to monitor rather than prevent.', TRUE, 60),

  ('COMPLICATIONS-ARTHROPLASTY', 'DEVICE_MALFUNCTION', 'IIIB', 'Joint stability, imaging per follow-up schedule.', 'Escalate to the surgical team; implant registry recall check.', FALSE, 10),
  ('COMPLICATIONS-ARTHROPLASTY', 'SURGICAL_SITE_INFECTION', 'IIIB', 'Wound review, inflammatory markers, joint aspiration if suspected.', 'Escalate immediately — a prosthetic joint infection is time-critical.', FALSE, 20),
  ('COMPLICATIONS-ARTHROPLASTY', 'DEEP_VEIN_THROMBOSIS', 'II', 'Limb symptoms, prophylaxis compliance.', 'Escalate for imaging and anticoagulation review.', FALSE, 30),
  ('COMPLICATIONS-ARTHROPLASTY', 'NERVE_INJURY', 'II', 'Neurovascular observations of the operated limb.', 'Escalate to the surgical team for assessment.', FALSE, 40),

  ('COMPLICATIONS-ENDOSCOPY', 'BLEEDING', 'II', 'Vital signs, stool/aspirate character.', 'Escalate to the endoscopy team; consider repeat endoscopy.', FALSE, 10),
  ('COMPLICATIONS-ENDOSCOPY', 'ORGAN_INJURY', 'IIIB', 'Abdominal pain, vital signs.', 'Escalate immediately — perforation is time-critical.', FALSE, 20),
  ('COMPLICATIONS-ENDOSCOPY', 'ANAESTHESIA_COMPLICATION', 'II', 'Recovery-phase respiratory and cardiovascular observations.', 'Escalate to the sedation/anaesthesia provider.', FALSE, 30),

  ('COMPLICATIONS-DIALYSIS', 'BLEEDING', 'II', 'Access-site observation during and after the session.', 'Escalate to the dialysis unit; direct pressure and review.', FALSE, 10),
  ('COMPLICATIONS-DIALYSIS', 'CARDIAC_EVENT', 'IVA', 'Intradialytic vital signs and fluid balance.', 'Escalate immediately; stop the session if indicated.', FALSE, 20),
  ('COMPLICATIONS-DIALYSIS', 'SEPSIS', 'IVA', 'Access-site signs of infection, temperature trend.', 'Escalate immediately for septic screen and antibiotics.', FALSE, 30),

  ('COMPLICATIONS-BEDSIDE-LINE', 'BLEEDING', 'I', 'Insertion-site observation.', 'Escalate to the inserting team if bleeding does not settle with pressure.', FALSE, 10),
  ('COMPLICATIONS-BEDSIDE-LINE', 'DEVICE_MALFUNCTION', 'II', 'Line function checks per protocol.', 'Escalate for line review or replacement.', FALSE, 20),
  ('COMPLICATIONS-BEDSIDE-LINE', 'RENAL_INJURY', 'II', 'Renal function trend where contrast or nephrotoxic exposure applies.', 'Escalate to the requesting team for renal function review.', FALSE, 30)
) AS x(profile_code, ctype, grade, monitoring, escalation, never_event, ord) ON x.profile_code = p.profile_code
WHERE p.tenant_id = :'tenant'
ON CONFLICT DO NOTHING;

-- Link the same demonstration procedures P7/P9 already sedation/recovery/aftercare-linked.
UPDATE procedures.procedure_definition SET complication_profile = 'COMPLICATIONS-LAPAROTOMY'
    WHERE tenant_id = :'tenant' AND definition_code IN ('PROC-LAPAROTOMY', 'PROC-CAESAREAN-SECTION');
UPDATE procedures.procedure_definition SET complication_profile = 'COMPLICATIONS-ARTHROPLASTY'
    WHERE tenant_id = :'tenant' AND definition_code = 'PROC-TOTAL-HIP-REPLACEMENT';
UPDATE procedures.procedure_definition SET complication_profile = 'COMPLICATIONS-ENDOSCOPY'
    WHERE tenant_id = :'tenant' AND definition_code IN ('PROC-OGD', 'PROC-COLONOSCOPY', 'PROC-BRONCHOSCOPY');
UPDATE procedures.procedure_definition SET complication_profile = 'COMPLICATIONS-DIALYSIS'
    WHERE tenant_id = :'tenant' AND definition_code IN ('PROC-HAEMODIALYSIS', 'PROC-PERITONEAL-DIALYSIS');
UPDATE procedures.procedure_definition SET complication_profile = 'COMPLICATIONS-BEDSIDE-LINE'
    WHERE tenant_id = :'tenant' AND definition_code IN ('PROC-CENTRAL-LINE', 'PROC-ARTERIAL-LINE', 'PROC-LUMBAR-PUNCTURE');
