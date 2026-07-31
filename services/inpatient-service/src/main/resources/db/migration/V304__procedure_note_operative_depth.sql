-- =====================================================================================
-- SB-5 — operative record depth on inpatient.procedure_note.
--
-- The surgical audit (docs/clinical/surgical-domain-pack/audit.md §13) scores the operative
-- record PARTIAL and names exactly which of its 26 fields are missing: position, preparation,
-- incision, steps, technique, fluids, drains, stomas, closure, wound classification and
-- postoperative instructions. `procedure_note` (V018) carries the other fifteen and none of
-- these eleven; no migration since has touched it except V303, which added a specimen FHIR ref.
-- This migration adds the eleven, additively.
--
-- ── DUPLICATION CHECK, ELEMENT BY ELEMENT ───────────────────────────────────────────
-- Three of the eleven have same-named neighbours elsewhere in this schema. They are different
-- records captured at different moments by different people, so they are not duplicates — but
-- the names are close enough to mislead, and each new column here is named to keep them apart:
--
--   * `technique` exists on `procedure_anaesthesia_chart_entry` (V024) and means the ANAESTHETIC
--     technique (GA | REGIONAL_SPINAL | LOCAL_SEDATION | ...), recorded by the anaesthetist.
--     What the operative record needs is the SURGICAL technique — open, laparoscopic, converted,
--     robotic — recorded by the surgeon. Named `operative_technique` here.
--   * `fluids` exists on `procedure_pacu_observation` (V031) as one channel of a recovery-phase
--     time series, observed per PACU observation. The operative record needs the intraoperative
--     fluid balance for the operation as a whole. Named `intraoperative_fluids`.
--   * `drains_devices` exists on the same PACU observation, and `drain_status` on
--     `procedure_surgical_discharge` (V032) describes drains at discharge. Neither records what
--     was PLACED at operation, which is what a reoperating surgeon needs to read. Named
--     `drains_placed`.
--
-- The remaining eight (position, preparation, incision, steps, stomas, closure, wound
-- classification, postoperative instructions) have no neighbour anywhere in the inpatient schema.
--
-- ── TEMPLATE LINKAGE IS A REFERENCE, NOT A FOREIGN KEY ──────────────────────────────
-- SB-4 built `surgery.surgical_operative_template` (surgery-service V006), whose columns are the
-- mirror image of these: position, preparation, incision, key_steps, technique_options,
-- closure_plan, wound_classification, postop_instructions. A template pre-fills a note.
--
-- It lives in ANOTHER SERVICE'S DATABASE. inpatient-service and surgery-service own separate
-- schemas in separate databases, so a REFERENCES constraint is not merely undesirable here, it
-- is unsatisfiable — every theatre rig boots inpatient-service against a Postgres that has no
-- surgery schema at all. The linkage is therefore a soft reference: the template's UUID, plus its
-- `template_code` denormalised so the note stays readable when the owning service is unreachable
-- and so an audit can see which template was applied without a cross-service join.
--
-- `wound_classification` repeats the template's CHECK vocabulary deliberately: the note and the
-- template must speak the same four values or the linkage means nothing.
--
-- Migration band: this programme owns V300+ in inpatient-service; V300-V303 are taken.
-- =====================================================================================

ALTER TABLE inpatient.procedure_note
    -- How the patient was positioned. A reoperating surgeon needs this, and so does anyone
    -- investigating a positioning injury (brachial plexus, pressure area) after the fact.
    ADD COLUMN IF NOT EXISTS patient_position          TEXT,
    ADD COLUMN IF NOT EXISTS skin_preparation          TEXT,
    ADD COLUMN IF NOT EXISTS incision                  TEXT,
    -- The narrative of what was actually done, step by step. Distinct from `performed_procedure`
    -- (V018), which is the procedure's NAME.
    ADD COLUMN IF NOT EXISTS operative_steps           TEXT,
    -- Surgical technique. NOT the anaesthetic technique on procedure_anaesthesia_chart_entry.
    ADD COLUMN IF NOT EXISTS operative_technique       TEXT,
    -- Intraoperative fluid balance. NOT the PACU fluids observation channel.
    ADD COLUMN IF NOT EXISTS intraoperative_fluids     TEXT,
    -- Drains placed AT operation. NOT the PACU drains_devices observation, nor the discharge
    -- drain_status: this is what was left in, which the other two later describe the fate of.
    ADD COLUMN IF NOT EXISTS drains_placed             TEXT,
    ADD COLUMN IF NOT EXISTS stomas_formed             TEXT,
    ADD COLUMN IF NOT EXISTS closure_method            TEXT,
    -- Drives surgical-site-infection surveillance; the four-value CDC classification.
    ADD COLUMN IF NOT EXISTS wound_classification      VARCHAR(24),
    -- What must happen after, from the surgeon. Distinct from `postop_plan` (V018), which is the
    -- free-text plan, and from procedure_surgical_discharge.patient_instructions, which is
    -- written at discharge and addressed to the patient.
    ADD COLUMN IF NOT EXISTS postoperative_instructions TEXT,

    -- Soft cross-service reference to surgery.surgical_operative_template. Never an FK.
    ADD COLUMN IF NOT EXISTS operative_template_ref    UUID,
    ADD COLUMN IF NOT EXISTS operative_template_code   VARCHAR(32);

COMMENT ON COLUMN inpatient.procedure_note.operative_technique IS
    'Surgical technique (open / laparoscopic / converted / robotic). Not the anaesthetic technique, which lives on procedure_anaesthesia_chart_entry.';
COMMENT ON COLUMN inpatient.procedure_note.intraoperative_fluids IS
    'Fluid balance for the operation. Not the PACU fluids observation channel.';
COMMENT ON COLUMN inpatient.procedure_note.drains_placed IS
    'Drains placed at operation. procedure_pacu_observation.drains_devices and procedure_surgical_discharge.drain_status describe what later became of them.';
COMMENT ON COLUMN inpatient.procedure_note.operative_template_ref IS
    'surgery.surgical_operative_template.id. A reference, not a foreign key: that table is in another service''s database and is absent entirely when inpatient-service runs alone.';
COMMENT ON COLUMN inpatient.procedure_note.operative_template_code IS
    'The template''s code, denormalised so the note remains readable and auditable without a cross-service join.';

-- Same four values as chk_operative_template_wound_class in surgery-service V006. The note and
-- the template must classify a wound identically or the template linkage is decorative.
ALTER TABLE inpatient.procedure_note
    ADD CONSTRAINT chk_procedure_note_wound_classification CHECK (
        wound_classification IS NULL
        OR wound_classification IN ('CLEAN', 'CLEAN_CONTAMINATED', 'CONTAMINATED', 'DIRTY'));

-- A template reference is the pair or it is nothing: an id with no code cannot be read without
-- the owning service, and a code with no id cannot be resolved back to a row.
ALTER TABLE inpatient.procedure_note
    ADD CONSTRAINT chk_procedure_note_template_pair CHECK (
        (operative_template_ref IS NULL) = (operative_template_code IS NULL));
