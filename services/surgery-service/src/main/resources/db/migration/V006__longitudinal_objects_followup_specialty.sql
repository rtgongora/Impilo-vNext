-- =====================================================================================
-- Surgery :: V006 — longitudinal objects, discharge/follow-up, waiting-list revalidation,
-- and the specialty dimension (SB-2, surgical domain pack §17/§18/§9/§6).
--
-- §17 DRAINS/STOMAS/WOUNDS. Implants stay federated to inventory-service's implant registry
-- (Wave P8, `inv_patient_implant` + recall traceability) — REFERENCED via
-- implant_registry_ref, never copied; this migration adds NO implant table. One
-- surgical_longitudinal_object table for the three kinds that genuinely have nowhere to live
-- (confirmed absent everywhere by research before this migration), sharing site/date/operator/
-- indication/removal-or-revision shape rather than three near-identical tables.
--
-- §18 DISCHARGE/FOLLOW-UP. audit.md is explicit: "surgical discharge summary real;
-- surveillance, future surgery, restrictions, fit note, transport absent". The discharge
-- summary itself is inpatient-service's (V032) — untouched. Surveillance-plan EXECUTION is
-- PCT's (pct_care_plans/pct_care_plan_goals, CC-2 amendment) — this migration does NOT add a
-- surveillance table; it is pct-service's own attribution-column gap S1 already named, not
-- worked around here either. What genuinely has no home: restrictions, fit-note, transport,
-- future-surgery intent — surgical_followup, one row per episode, refined not versioned
-- (same S2/S3 idiom).
--
-- §9 WAITING LISTS belong to scheduling-service (`scheduling.surgical_waitlist_entry`,
-- confirmed by research) — untouched. Only "waiting-list clinical revalidation state" is
-- named in the ADR as surgery-service's own slice: surgical_waitlist_revalidation, a
-- standalone small table, references the episode only, never scheduling-service's own entry.
--
-- §6 SPECIALTY DIMENSION. audit.md: "Zero of the fifteen. There is no specialty dimension on
-- the episode at all beyond referral.target_specialty." surgical_episode.specialty (S1, V002)
-- already exists as free text; this migration HARDENS it to the 15-value vocabulary now that
-- ZIBO has coded all fifteen (zibo-service V300, this same batch) and adds two EMPTY content
-- tables — surgical_specialty_indication and surgical_operative_template — for the "content
-- over shared infrastructure" spine the audit itself demands (consequence #4: "not fifteen
-- parallel builds"). Content lands separately (SB-4 fan-out), never invented in this
-- migration.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- §17 — drains, stomas, wounds as longitudinal objects.
-- -------------------------------------------------------------------------------------
CREATE TABLE surgery.surgical_longitudinal_object (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    surgical_episode_id      UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    kind                     VARCHAR(16) NOT NULL,
    -- DRAIN | STOMA | WOUND

    site                     VARCHAR(128) NOT NULL,
    placed_at                TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    operator                 VARCHAR(255) NOT NULL,
    indication               TEXT NOT NULL,

    status                   VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    -- ACTIVE | REMOVED | REVISED

    removed_at               TIMESTAMPTZ,
    removed_by               VARCHAR(255),
    removal_reason           TEXT,

    -- A REVISED object points forward to whatever superseded it — same idiom as inventory's
    -- own implant revision (P8), not reinvented here.
    revised_to_object_id     UUID REFERENCES surgery.surgical_longitudinal_object(id),

    -- Implants stay federated to inventory-service (P8) — a reference only, and ONLY
    -- meaningful when kind values overlap with an implant conceptually (it never does for
    -- this table's own three kinds; the column exists so a future wave can link a drain/wound
    -- to a nearby implant without inventing a new join table).
    implant_registry_ref     UUID,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_longitudinal_object_kind CHECK (kind IN ('DRAIN', 'STOMA', 'WOUND')),
    CONSTRAINT chk_longitudinal_object_status CHECK (status IN ('ACTIVE', 'REMOVED', 'REVISED')),
    -- A removal is an actor and a time together, or it did not happen — same pairing idiom
    -- as mvumo's V300 withdrawal pair.
    CONSTRAINT chk_longitudinal_object_removal_pair CHECK (
        (removed_at IS NULL AND removed_by IS NULL)
        OR (removed_at IS NOT NULL AND removed_by IS NOT NULL)),
    CONSTRAINT chk_longitudinal_object_status_consistency CHECK (
        (status = 'ACTIVE' AND removed_at IS NULL)
        OR (status IN ('REMOVED', 'REVISED') AND removed_at IS NOT NULL)),
    CONSTRAINT chk_longitudinal_object_revision_needs_target CHECK (
        status <> 'REVISED' OR revised_to_object_id IS NOT NULL)
);

CREATE INDEX idx_longitudinal_object_episode
    ON surgery.surgical_longitudinal_object (surgical_episode_id, kind);
CREATE INDEX idx_longitudinal_object_active
    ON surgery.surgical_longitudinal_object (tenant_id, kind)
    WHERE status = 'ACTIVE';

-- -------------------------------------------------------------------------------------
-- §18 — discharge/follow-up items with no other home.
-- -------------------------------------------------------------------------------------
CREATE TABLE surgery.surgical_followup (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    surgical_episode_id      UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    restrictions             TEXT,
    fit_note_notes           TEXT,
    fit_note_issued_at       TIMESTAMPTZ,
    fit_note_issued_by       VARCHAR(255),
    transport_arranged       BOOLEAN NOT NULL DEFAULT FALSE,
    transport_notes          TEXT,
    future_surgery_intent    TEXT,
    -- The surveillance PLAN itself is pct_care_plans' (CC-2); this is a reference to it,
    -- never a copy — same reviewed-flag-plus-reference discipline as S2's assessment.
    surveillance_plan_ref    UUID,

    recorded_by              VARCHAR(255),
    recorded_at              TIMESTAMPTZ,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_surgical_followup_episode UNIQUE (surgical_episode_id),
    -- All three of fit_note_notes/issued_at/issued_by travel together, or none do — a fit
    -- note's CONTENT with no recorded issuer is exactly the same defect the pair would
    -- otherwise only catch in one direction; matches surgical_decision's own 3-way idiom (V004).
    CONSTRAINT chk_surgical_followup_fitnote_pair CHECK (
        (fit_note_notes IS NULL AND fit_note_issued_at IS NULL AND fit_note_issued_by IS NULL)
        OR (fit_note_notes IS NOT NULL AND fit_note_issued_at IS NOT NULL AND fit_note_issued_by IS NOT NULL))
);

-- -------------------------------------------------------------------------------------
-- §9 — waiting-list clinical revalidation state (surgery's own slice only; the 18 other
-- waiting-list fields stay in scheduling.surgical_waitlist_entry, untouched here).
-- -------------------------------------------------------------------------------------
CREATE TABLE surgery.surgical_waitlist_revalidation (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    surgical_episode_id      UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    revalidated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    revalidated_by           VARCHAR(255) NOT NULL,
    still_clinically_appropriate BOOLEAN NOT NULL,
    notes                    TEXT,
    next_revalidation_due    DATE,

    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_waitlist_revalidation_episode
    ON surgery.surgical_waitlist_revalidation (surgical_episode_id, revalidated_at DESC);

-- -------------------------------------------------------------------------------------
-- §6 — specialty dimension: harden the free-text column, add empty content tables.
-- -------------------------------------------------------------------------------------
ALTER TABLE surgery.surgical_episode
    ADD CONSTRAINT chk_surgical_episode_specialty CHECK (specialty IS NULL OR specialty IN (
        'GENERAL_SURGERY', 'ORTHOPAEDICS', 'UROLOGY', 'ENT', 'OPHTHALMOLOGY',
        'COLORECTAL', 'UPPER_GI_HPB', 'BREAST_ENDOCRINE', 'VASCULAR', 'NEUROSURGERY',
        'CARDIOTHORACIC', 'MAXILLOFACIAL', 'PLASTICS', 'PAEDIATRIC_SURGERY', 'SURGICAL_ONCOLOGY'));

-- Content over shared infrastructure (audit consequence #4) — the shared spine (S1-S14) is
-- built once; specialties CONTRIBUTE content into these, never fork their own episode model.
CREATE TABLE surgery.surgical_specialty_indication (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    specialty                VARCHAR(24) NOT NULL,
    indication_code          VARCHAR(32) NOT NULL,
    condition_display        TEXT NOT NULL,
    typical_procedure        TEXT,
    urgency_guidance         TEXT,
    non_operative_alternatives TEXT,
    notes                    TEXT,
    content_maturity         VARCHAR(24) NOT NULL DEFAULT 'ENGINEERING_SEED',
    approving_authority      VARCHAR(128) NOT NULL DEFAULT 'PENDING_MOHCC_RATIFICATION',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_specialty_indication_code UNIQUE (tenant_id, indication_code),
    CONSTRAINT chk_specialty_indication_specialty CHECK (specialty IN (
        'GENERAL_SURGERY', 'ORTHOPAEDICS', 'UROLOGY', 'ENT', 'OPHTHALMOLOGY',
        'COLORECTAL', 'UPPER_GI_HPB', 'BREAST_ENDOCRINE', 'VASCULAR', 'NEUROSURGERY',
        'CARDIOTHORACIC', 'MAXILLOFACIAL', 'PLASTICS', 'PAEDIATRIC_SURGERY', 'SURGICAL_ONCOLOGY')),
    CONSTRAINT chk_specialty_indication_content_maturity CHECK (
        content_maturity IN ('ENGINEERING_SEED', 'RATIFIED'))
);

CREATE TABLE surgery.surgical_operative_template (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                UUID NOT NULL,
    specialty                VARCHAR(24) NOT NULL,
    template_code            VARCHAR(32) NOT NULL,
    template_name            TEXT NOT NULL,
    typical_procedure        TEXT,
    position                 TEXT,
    preparation              TEXT,
    incision                 TEXT,
    key_steps                TEXT,
    technique_options        TEXT,
    closure_plan             TEXT,
    wound_classification     VARCHAR(24),
    postop_instructions      TEXT,
    content_maturity         VARCHAR(24) NOT NULL DEFAULT 'ENGINEERING_SEED',
    approving_authority      VARCHAR(128) NOT NULL DEFAULT 'PENDING_MOHCC_RATIFICATION',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_operative_template_code UNIQUE (tenant_id, template_code),
    CONSTRAINT chk_operative_template_specialty CHECK (specialty IN (
        'GENERAL_SURGERY', 'ORTHOPAEDICS', 'UROLOGY', 'ENT', 'OPHTHALMOLOGY',
        'COLORECTAL', 'UPPER_GI_HPB', 'BREAST_ENDOCRINE', 'VASCULAR', 'NEUROSURGERY',
        'CARDIOTHORACIC', 'MAXILLOFACIAL', 'PLASTICS', 'PAEDIATRIC_SURGERY', 'SURGICAL_ONCOLOGY')),
    CONSTRAINT chk_operative_template_wound_class CHECK (
        wound_classification IS NULL
        OR wound_classification IN ('CLEAN', 'CLEAN_CONTAMINATED', 'CONTAMINATED', 'DIRTY')),
    CONSTRAINT chk_operative_template_content_maturity CHECK (
        content_maturity IN ('ENGINEERING_SEED', 'RATIFIED'))
);

CREATE INDEX idx_specialty_indication_specialty ON surgery.surgical_specialty_indication (tenant_id, specialty);
CREATE INDEX idx_operative_template_specialty ON surgery.surgical_operative_template (tenant_id, specialty);
