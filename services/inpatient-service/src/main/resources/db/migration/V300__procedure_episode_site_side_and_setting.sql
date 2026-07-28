-- =====================================================================================
-- Inpatient :: V300 — generalise the procedure episode, and verify site and side
--
-- Migration band V300-V329 belongs to the Surgery + Procedures programme
-- (docs/registry/iatg-surgery-procedures-leases.md §3). Emergency holds V200-V229.
--
-- ADDITIVE ONLY. `status` is untouched and every existing query, projection, Kafka
-- consumer and runtime-proof rig keeps working unchanged. Ten theatre rigs and two Kafka
-- consumers depend on this table; the generalisation is a widening, never a replacement.
--
-- ── 1. SITE AND SIDE ────────────────────────────────────────────────────────────────
-- The most serious finding of the pipeline audit: NOTHING in this platform verifies site
-- and side. They are recorded on referral.surgical_referral and displayed on the theatre
-- case banner, but no structured field held them on the episode and no gate refused to
-- start a procedure whose side was never confirmed. The WHO Time Out asks the question
-- and there was nowhere to put the answer.
--
-- The design constraint comes from the emergency lane, and it is stronger than the
-- elective case would have produced. Elective surgery has a consent form, a marked limb
-- and a scheduled Time Out — three redundant checks. Chest drain, needle and tube
-- thoracostomy, central and arterial line, thoracotomy, joint reduction, escharotomy and
-- burr hole are done at the bedside, at speed, frequently on an unidentified patient, by a
-- lone clinician with NO second checker and NO marking step. So the verification has to
-- work with one person and no marking: a single structured confirmation, by a named actor,
-- at a recorded time, that the start gate reads. Not a co-signature, not a marking event.
--
-- Wrong-side surgery is a never event, so unlike almost everything else about an
-- emergency, this is NOT waivable by the emergency override. That is enforced by the gate
-- in ProcedureEpisodeService, and by the CHECK below for anything writing around it.
--
-- ── 2. SETTING AND THE PIPELINE LINKS ───────────────────────────────────────────────
-- `setting` is the discriminator that lets a bedside, clinic, endoscopy, dialysis or
-- interventional-radiology procedure execute on this engine at all. It defaults to THEATRE
-- so every existing row keeps its current meaning exactly.
-- =====================================================================================

ALTER TABLE inpatient.procedure_episode
    -- Structured site and side. laterality is NOT free text: 'left' and 'Left' and 'L' are
    -- the same side and three different strings, and a wrong-side check that compares
    -- strings is a check that fails open.
    ADD COLUMN IF NOT EXISTS anatomical_site        VARCHAR(128),
    ADD COLUMN IF NOT EXISTS laterality             VARCHAR(24),
    ADD COLUMN IF NOT EXISTS site_side_confirmed_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS site_side_confirmed_at TIMESTAMPTZ,

    -- Pipeline generalisation.
    ADD COLUMN IF NOT EXISTS setting                VARCHAR(48) NOT NULL DEFAULT 'THEATRE',
    ADD COLUMN IF NOT EXISTS catalogue_ref          UUID,
    ADD COLUMN IF NOT EXISTS request_ref            VARCHAR(64),
    -- The generalised lifecycle sits BESIDE the theatre `status`, never replacing it.
    ADD COLUMN IF NOT EXISTS lifecycle_state        VARCHAR(32);

COMMENT ON COLUMN inpatient.procedure_episode.laterality IS
    'Structured side. Closed vocabulary because a wrong-side check that compares free text fails open.';
COMMENT ON COLUMN inpatient.procedure_episode.site_side_confirmed_by IS
    'Who confirmed site and side. One named actor is sufficient by design: the procedures most at risk are done by a lone clinician with no second checker.';
COMMENT ON COLUMN inpatient.procedure_episode.setting IS
    'Where the procedure is executed. Defaults to THEATRE so every pre-existing row keeps its exact current meaning.';

ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_laterality CHECK (
        laterality IS NULL OR laterality IN
            ('LEFT','RIGHT','BILATERAL','MIDLINE','NOT_APPLICABLE','MULTIPLE'));

ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_setting CHECK (
        setting IN ('THEATRE','BEDSIDE','CLINIC','WARD','CRITICAL_CARE','ENDOSCOPY','CATH_LAB',
                    'INTERVENTIONAL_RADIOLOGY','DIALYSIS','INFUSION','DENTAL','OPHTHALMIC',
                    'DERMATOLOGICAL','EMERGENCY','DAY_CASE'));

-- A confirmation is a pair or it is nothing: an actor with no timestamp, or a timestamp with
-- no actor, is not an auditable confirmation.
ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_site_side_confirmation_pair CHECK (
        (site_side_confirmed_by IS NULL AND site_side_confirmed_at IS NULL)
        OR (site_side_confirmed_by IS NOT NULL AND site_side_confirmed_at IS NOT NULL));

-- Confirming site and side without recording a side is the failure this whole wave exists
-- to prevent — a tick that asserts a check nobody performed.
ALTER TABLE inpatient.procedure_episode
    ADD CONSTRAINT chk_procedure_episode_confirmation_needs_a_side CHECK (
        site_side_confirmed_at IS NULL OR laterality IS NOT NULL);

CREATE INDEX IF NOT EXISTS idx_procedure_episode_setting
    ON inpatient.procedure_episode (tenant_id, setting, status);
CREATE INDEX IF NOT EXISTS idx_procedure_episode_request_ref
    ON inpatient.procedure_episode (request_ref) WHERE request_ref IS NOT NULL;

-- Backfill: every pre-existing episode is a theatre episode and says so explicitly. Site and
-- side stay NULL rather than being invented — an unverified side written by a migration is
-- exactly the confident-but-unchecked value this wave exists to eliminate.
UPDATE inpatient.procedure_episode SET setting = 'THEATRE' WHERE setting IS NULL;
