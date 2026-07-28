-- =====================================================================================
-- Surgery :: V005 — complication pathway instances + prehabilitation execution (SB-1,
-- surgical domain pack §15 and §10; §16's histology closure gate is service logic on the
-- episode transition and needs no schema of its own).
--
-- §15 COMPLICATION PATHWAY INSTANCES. R8 (dak-baseline): "A complication is a managed
-- pathway — recognised, graded, owned, investigated, treated, disclosed and closed — not a
-- logged event." procedures-service (P10) owns the CONTENT side: what complications a
-- procedure class is expected to monitor for (complication_profile/complication_class) and
-- the Clavien-Dindo grading standard. P10's own V008 header explicitly left the EXECUTION
-- record ("an actual complication that occurred") out of scope for that service. THIS is
-- that execution record — a workflow instance per real complication, in the service that
-- owns the surgical management course. The complication_type and grade_code CHECKs repeat
-- P10's closed vocabularies deliberately: both derive from the same governed content, so
-- this is a shared vocabulary, not a second registry (same pattern V008 itself used for
-- typical_grade_code). The resulting PROBLEM lands in pct_problems via S1's existing
-- PctProblemContributionClient — pct_problem_ref here is the contribution's reference,
-- never a copy (CC-2 amendment, same as surgical_episode).
--
-- §10 PREHABILITATION/OPTIMISATION EXECUTION. The ADR's MAY-own grant is "prehabilitation
-- and optimisation execution" — execution rows, not a registry. The 16 domains are NOT
-- enumerated anywhere in this repository (the source spec is not vendored; audit.md carries
-- only the count), so the vocabulary below is an ENGINEERING-derived defensible set,
-- recorded as such — the domain list is content that MoHCC ratification can amend, exactly
-- like every ENGINEERING_SEED vocabulary since V006 (procedures). Underlying clinical FACTS
-- stay in their registries (smoking/alcohol status → pct_social_history; anaemia labs →
-- OROS; weight → PCT observations): an item row records the surgery-side execution status
-- of an optimisation workstream, never the fact being optimised.
-- =====================================================================================

CREATE TABLE surgery.surgical_complication_pathway (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    surgical_episode_id     UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    -- Shared closed vocabulary with procedures.complication_class (P10) — one governed list.
    complication_type       VARCHAR(48) NOT NULL,

    -- R8 stage 1: recognised. Required from birth — an unrecognised complication row is a
    -- contradiction in terms.
    recognised_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    recognised_by           VARCHAR(255) NOT NULL,

    -- R8 stage 2: graded (Clavien-Dindo, P10's cited standard). Nullable until graded; the
    -- pair travels together.
    grade_code              VARCHAR(8),
    graded_by               VARCHAR(255),
    graded_at               TIMESTAMPTZ,

    -- R8 stage 3: owned. A named responsible team/clinician, not a queue.
    owned_by                VARCHAR(255),

    -- R8 stages 4-5: investigated, treated.
    investigation           TEXT,
    treatment               TEXT,

    -- R8 stage 6: disclosed — to the patient/family, a REQUIRED stage before closure, not a
    -- courtesy. The pair travels together.
    disclosed_at            TIMESTAMPTZ,
    disclosed_by            VARCHAR(255),
    disclosure_notes        TEXT,

    -- R8 stage 7: closed, with an outcome.
    outcome                 TEXT,
    status                  VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    closed_at               TIMESTAMPTZ,
    closed_by               VARCHAR(255),

    -- The resulting problem's landing in pct_problems (CC-2: contribute, never copy).
    pct_problem_ref         UUID,
    pct_problem_contributed_at TIMESTAMPTZ,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_complication_pathway_type CHECK (complication_type IN (
        'SURGICAL_SITE_INFECTION', 'BLEEDING', 'ANASTOMOTIC_LEAK', 'DEEP_VEIN_THROMBOSIS',
        'PULMONARY_EMBOLISM', 'WOUND_DEHISCENCE', 'ORGAN_INJURY', 'NERVE_INJURY',
        'ANAESTHESIA_COMPLICATION', 'TRANSFUSION_REACTION', 'DEVICE_MALFUNCTION',
        'RETAINED_FOREIGN_OBJECT', 'WRONG_SITE_PROCEDURE', 'READMISSION',
        'UNPLANNED_RETURN_TO_THEATRE', 'SEPSIS', 'RENAL_INJURY', 'CARDIAC_EVENT', 'DEATH')),
    CONSTRAINT chk_complication_pathway_grade CHECK (
        grade_code IS NULL OR grade_code IN ('I', 'II', 'IIIA', 'IIIB', 'IVA', 'IVB', 'V')),
    CONSTRAINT chk_complication_pathway_grade_pair CHECK (
        (grade_code IS NULL AND graded_by IS NULL AND graded_at IS NULL)
        OR (grade_code IS NOT NULL AND graded_by IS NOT NULL AND graded_at IS NOT NULL)),
    CONSTRAINT chk_complication_pathway_disclosure_pair CHECK (
        (disclosed_at IS NULL AND disclosed_by IS NULL)
        OR (disclosed_at IS NOT NULL AND disclosed_by IS NOT NULL)),
    CONSTRAINT chk_complication_pathway_status CHECK (status IN ('OPEN', 'CLOSED')),
    -- R8's whole point, enforced where it cannot be forgotten: a pathway cannot CLOSE until
    -- it has been graded, owned, disclosed, and given an outcome. (Investigation/treatment
    -- text is not CHECK-enforced — "observation only" is a legitimate treatment, and an
    -- empty-string check would just invite boilerplate; grading/ownership/disclosure/outcome
    -- are the stages whose absence means the pathway was never really managed.)
    CONSTRAINT chk_complication_pathway_closure CHECK (
        status <> 'CLOSED'
        OR (grade_code IS NOT NULL AND owned_by IS NOT NULL AND disclosed_at IS NOT NULL
            AND outcome IS NOT NULL AND length(btrim(outcome)) > 0
            AND closed_at IS NOT NULL AND closed_by IS NOT NULL))
);

CREATE INDEX idx_complication_pathway_episode
    ON surgery.surgical_complication_pathway (surgical_episode_id, status);
CREATE INDEX idx_complication_pathway_open
    ON surgery.surgical_complication_pathway (tenant_id, recognised_at)
    WHERE status = 'OPEN';

-- -------------------------------------------------------------------------------------

CREATE TABLE surgery.surgical_prehab_item (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    surgical_episode_id     UUID NOT NULL REFERENCES surgery.surgical_episode(id),

    -- ENGINEERING-derived 16-domain vocabulary (see header). Ratification can amend.
    domain                  VARCHAR(48) NOT NULL,
    status                  VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    target                  TEXT,
    notes                   TEXT,
    -- Optional opaque reference into the registry that owns the underlying fact (e.g. the
    -- pct_social_history row a smoking-cessation item works against) — a pointer, never the
    -- value, same S2 discipline.
    registry_ref            VARCHAR(255),

    planned_by              VARCHAR(255),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_prehab_item_domain UNIQUE (surgical_episode_id, domain),
    CONSTRAINT chk_prehab_item_domain CHECK (domain IN (
        'ANAEMIA', 'NUTRITION', 'GLYCAEMIC_CONTROL', 'SMOKING_CESSATION', 'ALCOHOL_REDUCTION',
        'VTE_PLAN', 'ANTIBIOTIC_PROPHYLAXIS_PLAN', 'BLOOD_PREPARATION',
        'CARDIORESPIRATORY_FITNESS', 'PHYSIOTHERAPY', 'PSYCHOLOGICAL_PREPARATION',
        'MEDICATION_OPTIMISATION', 'DENTAL', 'WEIGHT_MANAGEMENT', 'FRAILTY_INTERVENTION',
        'SOCIAL_DISCHARGE_PREPARATION')),
    CONSTRAINT chk_prehab_item_status CHECK (
        status IN ('PLANNED', 'IN_PROGRESS', 'COMPLETED', 'NOT_REQUIRED', 'DECLINED'))
);

CREATE INDEX idx_prehab_item_episode ON surgery.surgical_prehab_item (surgical_episode_id);
