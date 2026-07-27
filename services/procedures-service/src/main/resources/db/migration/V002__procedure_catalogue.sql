-- =====================================================================================
-- Procedures :: V002 — the canonical procedure catalogue (pipeline §3)
--
-- The keystone. Nothing in this repository declares what a procedure REQUIRES, so §5
-- appropriateness, §7 competence, §8 readiness, §9 safety pauses and §17 aftercare have
-- had nothing to derive from and readiness has been hardcoded per setting instead.
--
-- THE DESIGN DECISION THAT MATTERS: requirements are ROWS, not columns.
--
-- The specification lists ~45 attributes for a catalogue entry, and roughly twenty of
-- them are requirements — cadre, privilege, countersignature, equipment, consumables,
-- medicines, blood, imaging, laboratory, IPC, preparation, monitoring, recovery area and
-- so on. Modelled as columns, the readiness engine must know all twenty names, which is
-- the hardcoding this wave exists to remove: adding a requirement type would mean a code
-- change and a deployment. Modelled as rows with a typed `requirement_kind`, the engine
-- iterates whatever the catalogue declares and a new requirement type is a content
-- release. That is also what makes the catalogue governed national content rather than a
-- lookup table.
--
-- Each requirement carries its own OWNER and its own RESOLVER, because §8 requires every
-- unresolved item to have an owner, and because the engine must know which peer service
-- can answer it. A requirement nobody owns is a blocker nobody clears.
--
-- WHAT IS DELIBERATELY NOT HERE:
--  * Indications and contraindications as logic — those are decision logic and belong to
--    clinical-knowledge-platform-service (ADR decision 3). The catalogue holds a REFERENCE
--    to the governing rule, never a copy of it. A rule in two places diverges.
--  * Any readiness verdict or checklist completion — engine-not-store (ADR decision 2).
--    This schema holds the INPUTS to a verdict. The executing service holds the verdict.
-- =====================================================================================

-- -------------------------------------------------------------------------------------
-- Procedure definition — one row per (code, version). An immutable version chain, not an
-- overwrite: a published definition is what some completed procedure was judged against,
-- so amending it in place would rewrite the standard a past procedure was held to.
-- -------------------------------------------------------------------------------------
CREATE TABLE procedures.procedure_definition (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,

    -- Identity (§3: canonical identifier, clinical name, synonyms)
    definition_code         VARCHAR(64) NOT NULL,
    version                 INT NOT NULL DEFAULT 1,
    clinical_name           VARCHAR(255) NOT NULL,
    synonyms                JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Classification (§2 classes, §3 category and specialty ownership)
    category                VARCHAR(48) NOT NULL,
    owning_specialty        VARCHAR(64) NOT NULL,   -- zibo clinical specialty code
    purpose                 VARCHAR(24) NOT NULL,   -- DIAGNOSTIC | THERAPEUTIC | BOTH | SCREENING | MONITORING

    -- Where it is done. A definition may permit several settings; readiness and the
    -- safety-pause template are selected by the setting actually chosen at request time.
    permitted_settings      JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Anatomy (§3 anatomical site, laterality)
    anatomical_site         VARCHAR(128),
    -- Whether this procedure is lateralised at all. This is the field that lets the
    -- pipeline demand a side: a procedure marked LATERALISED cannot start without one.
    -- NOT_APPLICABLE is an explicit answer, never a default, because "no side recorded"
    -- and "side does not apply" are different facts and conflating them is how a
    -- wrong-side procedure passes a check.
    laterality_applicability VARCHAR(24) NOT NULL DEFAULT 'NOT_APPLICABLE',
    -- LATERALISED | MIDLINE | BILATERAL_BY_DEFAULT | MULTI_SITE | NOT_APPLICABLE

    technique_variants      JSONB NOT NULL DEFAULT '[]'::jsonb,

    -- Applicability (§3 age and pregnancy constraints). Age in DAYS, following the CKP
    -- rules framework: paediatric applicability changes within days of birth and months
    -- cannot express that.
    age_min_days            INTEGER,
    age_max_days            INTEGER,
    pregnancy_applicability VARCHAR(24) NOT NULL DEFAULT 'NO_CONSTRAINT',
    -- NO_CONSTRAINT | CONTRAINDICATED | CAUTION | REQUIRES_TEST

    -- Clinical governance by reference, never by copy (ADR decision 3).
    indication_rule_ref     VARCHAR(128),   -- CKP rule_definitions code
    contraindication_rule_ref VARCHAR(128),

    -- Execution shape (§3 duration, recovery, observation)
    expected_duration_min   INTEGER,
    recovery_required       BOOLEAN NOT NULL DEFAULT FALSE,
    observation_minutes     INTEGER,

    -- Templates this definition governs. References to governed content, resolved at use.
    consent_type            VARCHAR(48),    -- mvumo consent template code
    safety_pause_template   VARCHAR(64),    -- §9 class-specific checklist
    findings_template       VARCHAR(64),
    aftercare_template      VARCHAR(64),
    complication_profile    VARCHAR(64),
    follow_up_policy        VARCHAR(64),

    -- Coding (§3). ZIBO owns the code system; this is the reference into it.
    zibo_code               VARCHAR(64),
    snomed_ct_code          VARCHAR(32),
    icd9cm_procedure_code   VARCHAR(16),

    -- Governance (§3 version, approval). A definition is only usable when PUBLISHED, and
    -- publishing names who approved it. Unratified content says so explicitly rather than
    -- leaving the authority blank — the same rule the clinical traceability guard enforces
    -- on decision-support content.
    status                  VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    -- DRAFT | IN_REVIEW | PUBLISHED | RETIRED
    approving_authority     VARCHAR(128),
    approved_by             VARCHAR(128),
    approved_at             TIMESTAMPTZ,
    effective_from          TIMESTAMPTZ,
    effective_to            TIMESTAMPTZ,
    supersedes_id           UUID REFERENCES procedures.procedure_definition(id),
    source_citation         TEXT,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    row_version             BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uq_procedure_definition_code_version UNIQUE (tenant_id, definition_code, version),
    CONSTRAINT chk_procedure_definition_status CHECK (
        status IN ('DRAFT','IN_REVIEW','PUBLISHED','RETIRED')),
    CONSTRAINT chk_procedure_definition_purpose CHECK (
        purpose IN ('DIAGNOSTIC','THERAPEUTIC','BOTH','SCREENING','MONITORING')),
    CONSTRAINT chk_procedure_definition_laterality CHECK (
        laterality_applicability IN ('LATERALISED','MIDLINE','BILATERAL_BY_DEFAULT','MULTI_SITE','NOT_APPLICABLE')),
    CONSTRAINT chk_procedure_definition_pregnancy CHECK (
        pregnancy_applicability IN ('NO_CONSTRAINT','CONTRAINDICATED','CAUTION','REQUIRES_TEST')),
    -- A published definition names its approving authority. Blank authority on published
    -- content is how unratified guidance acquires the appearance of ratification.
    CONSTRAINT chk_procedure_definition_published_authority CHECK (
        status <> 'PUBLISHED'
        OR (approving_authority IS NOT NULL AND length(btrim(approving_authority)) > 0
            AND approved_at IS NOT NULL)),
    CONSTRAINT chk_procedure_definition_age_range CHECK (
        age_min_days IS NULL OR age_max_days IS NULL OR age_min_days <= age_max_days)
);

CREATE INDEX idx_procedure_definition_lookup
    ON procedures.procedure_definition (tenant_id, definition_code, status);
CREATE INDEX idx_procedure_definition_specialty
    ON procedures.procedure_definition (tenant_id, owning_specialty, status);
CREATE INDEX idx_procedure_definition_category
    ON procedures.procedure_definition (tenant_id, category, status);
-- Exactly one PUBLISHED version of a code may be current at a time. Two current versions
-- means two different answers to "what does this procedure require".
CREATE UNIQUE INDEX uq_procedure_definition_one_published
    ON procedures.procedure_definition (tenant_id, definition_code)
    WHERE status = 'PUBLISHED';

-- -------------------------------------------------------------------------------------
-- Procedure requirement — the rows the readiness engine iterates.
--
-- `owner_role` answers §8's "each unresolved item must have an owner". `resolver_service`
-- tells the engine which peer can answer it, so a new requirement kind does not need the
-- engine to learn a new integration by name.
--
-- `overridable_in_emergency` is the fail-safe seam and is FALSE by default. A requirement
-- is only bypassable under an audited emergency override if the catalogue says so — so
-- "confirm the site and side" cannot be waived by an override that was meant to waive
-- "obtain a group and screen".
-- -------------------------------------------------------------------------------------
CREATE TABLE procedures.procedure_requirement (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    definition_id           UUID NOT NULL REFERENCES procedures.procedure_definition(id) ON DELETE CASCADE,

    requirement_kind        VARCHAR(32) NOT NULL,
    -- CADRE | PRIVILEGE | COUNTERSIGNATURE | SUPERVISION | EQUIPMENT | CONSUMABLE |
    -- MEDICINE | BLOOD | IMAGING | LABORATORY | IPC | STERILE_PROCESSING | PREPARATION |
    -- DATA | CONSENT | SEDATION | MONITORING | RECOVERY_AREA | STAFFING | TRANSPORT |
    -- POST_PROCEDURE_BED | INTERPRETER | EMERGENCY_BACKUP | FACILITY_CAPABILITY |
    -- SITE_SIDE_VERIFICATION | PATIENT_IDENTITY | FASTING | ANTICOAGULATION | PREGNANCY

    requirement_code        VARCHAR(96) NOT NULL,
    requirement_label       VARCHAR(255) NOT NULL,

    obligation              VARCHAR(16) NOT NULL DEFAULT 'MANDATORY',
    -- MANDATORY | CONDITIONAL | RECOMMENDED
    condition_expression    TEXT,          -- required when obligation = CONDITIONAL

    -- §8: every unresolved item has an owner.
    owner_role              VARCHAR(64) NOT NULL,
    -- Which peer answers it. NULL means a human attests rather than a system resolving.
    resolver_service        VARCHAR(64),

    -- Fail-safe seam. FALSE by default: a requirement is bypassable only if declared so.
    overridable_in_emergency BOOLEAN NOT NULL DEFAULT FALSE,
    -- What the engine does when the resolver cannot be reached. BLOCK is the default and
    -- the safe answer: an unreadable requirement is not a satisfied requirement.
    on_resolver_unavailable VARCHAR(16) NOT NULL DEFAULT 'BLOCK',
    -- BLOCK | WARN

    quantity                NUMERIC(10,2),
    unit                    VARCHAR(32),
    notes                   TEXT,
    display_order           INT NOT NULL DEFAULT 100,

    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_procedure_requirement UNIQUE (definition_id, requirement_kind, requirement_code),
    CONSTRAINT chk_procedure_requirement_kind CHECK (requirement_kind IN (
        'CADRE','PRIVILEGE','COUNTERSIGNATURE','SUPERVISION','EQUIPMENT','CONSUMABLE',
        'MEDICINE','BLOOD','IMAGING','LABORATORY','IPC','STERILE_PROCESSING','PREPARATION',
        'DATA','CONSENT','SEDATION','MONITORING','RECOVERY_AREA','STAFFING','TRANSPORT',
        'POST_PROCEDURE_BED','INTERPRETER','EMERGENCY_BACKUP','FACILITY_CAPABILITY',
        'SITE_SIDE_VERIFICATION','PATIENT_IDENTITY','FASTING','ANTICOAGULATION','PREGNANCY')),
    CONSTRAINT chk_procedure_requirement_obligation CHECK (
        obligation IN ('MANDATORY','CONDITIONAL','RECOMMENDED')),
    CONSTRAINT chk_procedure_requirement_unavailable CHECK (
        on_resolver_unavailable IN ('BLOCK','WARN')),
    -- A conditional requirement without a condition is a mandatory one that looks optional.
    CONSTRAINT chk_procedure_requirement_condition CHECK (
        obligation <> 'CONDITIONAL'
        OR (condition_expression IS NOT NULL AND length(btrim(condition_expression)) > 0)),
    -- Site and side verification may never be waived by an emergency override. The
    -- emergency lane's procedures — chest drain, thoracostomy, central line, thoracotomy,
    -- escharotomy, burr hole — are exactly the lateralised ones done at speed by a lone
    -- clinician with no second checker and no marking step, which is where wrong-side
    -- harm happens. Everything else about an emergency may be waived; not this.
    CONSTRAINT chk_procedure_requirement_site_side_never_overridable CHECK (
        requirement_kind <> 'SITE_SIDE_VERIFICATION' OR overridable_in_emergency = FALSE)
);

CREATE INDEX idx_procedure_requirement_definition
    ON procedures.procedure_requirement (definition_id, display_order);
CREATE INDEX idx_procedure_requirement_kind
    ON procedures.procedure_requirement (requirement_kind);

-- -------------------------------------------------------------------------------------
-- Catalogue link to the execution index: which definition governed an execution. Applied
-- now that the catalogue exists, so every routed request resolves a definition.
-- -------------------------------------------------------------------------------------
ALTER TABLE procedures.procedure_execution_index
    ADD CONSTRAINT fk_procedure_execution_catalogue
        FOREIGN KEY (catalogue_ref) REFERENCES procedures.procedure_definition(id);
