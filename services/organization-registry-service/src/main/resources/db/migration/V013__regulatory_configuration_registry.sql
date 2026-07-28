-- =====================================================================================
-- org-registry :: V013 — Regulatory Configuration Registry (NCZ-W1A)
--
-- Three layers, and this table set is the middle one:
--   * code owns the configuration LANGUAGE and the execution engines;
--   * the regulator owns APPROVED RUNTIME DEFINITIONS — these tables;
--   * each regulatory case owns an IMMUTABLE REFERENCE to the exact versions that governed it.
--
-- What it replaces: varapi.council_regulatory_configs is ONE MUTABLE ROW per council, four opaque
-- JSONB blobs overwritten in place by a destructive PUT, with a config_version integer that nobody
-- increments and three blobs no code reads. A council cannot answer "what were the rules when this
-- application was submitted?" against that shape, because the answer was overwritten. This registry
-- makes that question answerable by construction.
--
-- Why org-registry: the Registry must serve varapi (professionals), tuso (institutions),
-- COSTA/Mushex (fees), Khuluma (templates) and the shell. org-registry is already the system of
-- record for the regulatory ORGANISATION and its appointments and is the only neutral org-scoped
-- home. No new microservice (ROM ruling stands).
--
-- Deliberate omission: no `regulatory_config_change_set` table. In this model a DRAFT release and
-- its draft versions ARE the change set; a separate table would have no writer and no reader.
-- Deliberate divergence: four-eyes is enforced HERE, in schema, rather than by calling
-- workforce-governance's TwoPersonApprovalService — that service is hard-bound to
-- AccessRequestEntity, so "reuse" would mean minting a fake workforce access-request for every fee
-- change. The two invariants it enforces (initiator-cannot-approve, one decision per approver) are
-- reproduced below as a trigger and a unique index, which is stricter than its service-layer check.
-- =====================================================================================

-- ── Definition-type catalogue (closed vocabulary) ────────────────────────────────────────────
-- A definition type is a KIND of configurable thing. Adding a type is a code change, because code
-- owns the language: a type with no engine behind it is configuration nobody executes.
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_definition_type (
    type_code           VARCHAR(48) PRIMARY KEY,
    label               VARCHAR(128) NOT NULL,
    description         TEXT,
    -- Fees, penalties, register-entry rules and decision workflows require two people.
    requires_four_eyes  BOOLEAN NOT NULL DEFAULT FALSE,
    -- An applicant-facing definition MUST declare a self-service route, or the release fails
    -- activation. This is the "no regulator-only capability" rule made mechanical.
    applicant_facing    BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order          INT NOT NULL DEFAULT 100
);

INSERT INTO org_registry.regulatory_config_definition_type
    (type_code, label, description, requires_four_eyes, applicant_facing, sort_order) VALUES
    ('APPLICATION_TYPE',      'Application type',       'A regulatory service an applicant may apply for.',            FALSE, TRUE,   10),
    ('WORKFLOW',              'Decision workflow',      'Stages, transitions and decision points for a case.',         TRUE,  TRUE,   20),
    ('FORM',                  'Form definition',        'Sections and questions an applicant completes.',              FALSE, TRUE,   30),
    ('EVIDENCE_REQUIREMENT',  'Evidence requirement',   'Documents required, and what evidence is acceptable.',        FALSE, TRUE,   40),
    ('ELIGIBILITY',           'Eligibility rule',       'Who may apply for a service, and on what basis.',             FALSE, TRUE,   50),
    ('FEE_SCHEDULE',          'Fee schedule',           'Fees payable, by service and currency.',                      TRUE,  TRUE,   60),
    ('PENALTY_POLICY',        'Penalty policy',         'Penalty accrual basis, rate and cap.',                        TRUE,  TRUE,   70),
    ('CURRENCY_POLICY',       'Currency policy',        'Accepted currencies and settlement rules.',                   TRUE,  FALSE,  80),
    ('NUMBERING_POLICY',      'Numbering policy',       'Format, reservation and uniqueness of issued identifiers.',   FALSE, TRUE,   90),
    ('REGISTER',              'Register',               'A statutory register the regulator maintains.',               FALSE, TRUE,  100),
    ('REGISTER_ENTRY_RULE',   'Register entry rule',    'Admission, migration, restriction and removal rules.',        TRUE,  TRUE,  110),
    ('CERTIFICATE_TEMPLATE',  'Certificate template',   'Wording and fields of an issued certificate.',                FALSE, TRUE,  120),
    ('CORRESPONDENCE',        'Correspondence template','Applicant-visible correspondence wording.',                   FALSE, TRUE,  130),
    ('APPROVAL_MATRIX',       'Approval matrix',        'Who may finally approve which decision, at what threshold.',  TRUE,  FALSE, 140),
    ('ROLE_WORKSPACE',        'Role workspace',         'Role-aware navigation and capability enablement.',            FALSE, FALSE, 150),
    ('REPORT_DEFINITION',     'Report definition',      'A report or dashboard the regulator publishes.',              FALSE, FALSE, 160),
    ('CPD_RULES',             'CPD rules',              'Cycle, required credits and mandatory categories.',           FALSE, TRUE,  170),
    ('EXAMINATION',           'Examination rules',      'Attempt limits, reset rules and discontinuation triggers.',   TRUE,  TRUE,  180),
    ('SUPERVISED_PRACTICE',   'Supervised practice',    'Duration basis, interruption handling, supervisor approval.', TRUE,  TRUE,  190),
    ('COUNCIL_IDENTITY',      'Council identity',       'Council name, terminology, branding and applicant labels.',   FALSE, TRUE,  200)
ON CONFLICT (type_code) DO NOTHING;

-- ── Pack: the container, one per regulator ───────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_pack (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    organization_id     UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    pack_key            VARCHAR(64) NOT NULL,
    label               VARCHAR(160) NOT NULL,
    description         TEXT,
    status              VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_pack_key UNIQUE (tenant_id, organization_id, pack_key),
    CONSTRAINT chk_rcfg_pack_status CHECK (status IN ('ACTIVE', 'RETIRED'))
);
CREATE INDEX IF NOT EXISTS idx_rcfg_pack_org
    ON org_registry.regulatory_config_pack (tenant_id, organization_id, status);

-- ── Definition: the STABLE key. Its versions carry the content. ──────────────────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_definition (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pack_id             UUID NOT NULL REFERENCES org_registry.regulatory_config_pack(id),
    type_code           VARCHAR(48) NOT NULL
                            REFERENCES org_registry.regulatory_config_definition_type(type_code),
    definition_key      VARCHAR(96) NOT NULL,
    label               VARCHAR(160) NOT NULL,
    description         TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_definition_key UNIQUE (tenant_id, pack_id, type_code, definition_key)
);
CREATE INDEX IF NOT EXISTS idx_rcfg_definition_pack
    ON org_registry.regulatory_config_definition (tenant_id, pack_id, type_code);

-- ── Definition version: IMMUTABLE once it leaves DRAFT ───────────────────────────────────────
-- content_hash is the checksum-immutability discipline proven in tuso's InspectionContentSeeder:
-- the same (definition, semantic_version) must always carry the same content, and a source pack
-- that changes content under a fixed version fails fast rather than silently mutating the rules a
-- live case is pinned to.
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_definition_version (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    definition_id         UUID NOT NULL REFERENCES org_registry.regulatory_config_definition(id),
    semantic_version      VARCHAR(32) NOT NULL,
    schema_version        INT NOT NULL DEFAULT 1,
    payload               JSONB NOT NULL,
    -- VARCHAR, not CHAR: Hibernate runs with ddl-auto=validate, and a bpchar column against a
    -- String field fails the whole service at boot. The CHECK below fixes the length anyway.
    content_hash          VARCHAR(64) NOT NULL,
    lifecycle_state       VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    -- Applicant-facing types must name the route an applicant uses. Activation validates it.
    self_service_route    VARCHAR(256),
    effective_from        DATE,
    effective_to          DATE,
    -- The values a council has not yet decided are ABSENT, never guessed. A version whose policy
    -- values are unset carries PENDING_REGULATOR_APPROVAL and a decision reference, mirroring the
    -- tuso V021 fee-schedule discipline (amounts NULL by design, not by oversight).
    policy_status         VARCHAR(40),
    policy_decision_ref   VARCHAR(64),
    authored_by           VARCHAR(128) NOT NULL,
    approved_at           TIMESTAMPTZ,
    supersedes_version_id UUID REFERENCES org_registry.regulatory_config_definition_version(id),
    source_pack_ref       VARCHAR(256),
    created_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_version UNIQUE (tenant_id, definition_id, semantic_version),
    CONSTRAINT chk_rcfg_version_semver CHECK (semantic_version ~ '^[0-9]+\.[0-9]+\.[0-9]+$'),
    CONSTRAINT chk_rcfg_version_hash CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT chk_rcfg_version_state CHECK (lifecycle_state IN
        ('DRAFT', 'IN_REVIEW', 'APPROVED', 'ACTIVE', 'RETIRED',
         'REJECTED', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT chk_rcfg_version_policy_status CHECK (policy_status IS NULL OR policy_status IN
        ('CONFIRMED', 'PENDING_REGULATOR_APPROVAL')),
    CONSTRAINT chk_rcfg_version_window CHECK (effective_to IS NULL OR effective_from IS NULL
        OR effective_to >= effective_from)
);
-- At most one ACTIVE version per definition. A change is a new version, never an edit.
CREATE UNIQUE INDEX IF NOT EXISTS uq_rcfg_version_active
    ON org_registry.regulatory_config_definition_version (tenant_id, definition_id)
    WHERE lifecycle_state = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_rcfg_version_definition
    ON org_registry.regulatory_config_definition_version (tenant_id, definition_id, lifecycle_state);

-- ── Dependency: what a version references, for release-level referential validation ──────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_dependency (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    definition_version_id   UUID NOT NULL
        REFERENCES org_registry.regulatory_config_definition_version(id) ON DELETE CASCADE,
    required_type_code      VARCHAR(48) NOT NULL
        REFERENCES org_registry.regulatory_config_definition_type(type_code),
    required_definition_key VARCHAR(96) NOT NULL,
    is_optional             BOOLEAN NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_dependency
        UNIQUE (definition_version_id, required_type_code, required_definition_key)
);
CREATE INDEX IF NOT EXISTS idx_rcfg_dependency_version
    ON org_registry.regulatory_config_dependency (definition_version_id);

-- ── Release: the set of versions that go live together ───────────────────────────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_release (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    pack_id             UUID NOT NULL REFERENCES org_registry.regulatory_config_pack(id),
    release_key         VARCHAR(64) NOT NULL,
    label               VARCHAR(160) NOT NULL,
    notes               TEXT,
    lifecycle_state     VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
    submitted_by        VARCHAR(128),
    submitted_at        TIMESTAMPTZ,
    scheduled_for       TIMESTAMPTZ,
    activated_at        TIMESTAMPTZ,
    activated_by        VARCHAR(128),
    retired_at          TIMESTAMPTZ,
    retired_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_release_key UNIQUE (tenant_id, pack_id, release_key),
    CONSTRAINT chk_rcfg_release_state CHECK (lifecycle_state IN
        ('DRAFT', 'IN_REVIEW', 'APPROVED', 'SCHEDULED', 'ACTIVE', 'RETIRED',
         'REJECTED', 'SUPERSEDED', 'WITHDRAWN'))
);
-- One ACTIVE release per pack. Activation supersedes its predecessor in the same transaction.
CREATE UNIQUE INDEX IF NOT EXISTS uq_rcfg_release_active
    ON org_registry.regulatory_config_release (tenant_id, pack_id)
    WHERE lifecycle_state = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_rcfg_release_pack
    ON org_registry.regulatory_config_release (tenant_id, pack_id, lifecycle_state);

-- definition_id is carried denormalised so the database — not the service — refuses a release
-- that contains two versions of the same definition.
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_release_item (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    release_id              UUID NOT NULL
        REFERENCES org_registry.regulatory_config_release(id) ON DELETE CASCADE,
    definition_id           UUID NOT NULL REFERENCES org_registry.regulatory_config_definition(id),
    definition_version_id   UUID NOT NULL
        REFERENCES org_registry.regulatory_config_definition_version(id),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_release_item UNIQUE (release_id, definition_id)
);
CREATE INDEX IF NOT EXISTS idx_rcfg_release_item_release
    ON org_registry.regulatory_config_release_item (release_id);

-- ── Approval: four-eyes, enforced in schema ──────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_approval (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    release_id          UUID NOT NULL REFERENCES org_registry.regulatory_config_release(id),
    approver_health_id  VARCHAR(128) NOT NULL,
    approver_role       VARCHAR(48),
    decision            VARCHAR(16) NOT NULL,
    note                TEXT,
    decided_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_rcfg_approval_once UNIQUE (release_id, approver_health_id),
    CONSTRAINT chk_rcfg_approval_decision CHECK (decision IN ('APPROVE', 'REJECT'))
);
CREATE INDEX IF NOT EXISTS idx_rcfg_approval_release
    ON org_registry.regulatory_config_approval (release_id, decision);

-- ── Activation: append-only log, with the validation report that justified it ────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_activation (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id            UUID NOT NULL,
    pack_id              UUID NOT NULL REFERENCES org_registry.regulatory_config_pack(id),
    release_id           UUID NOT NULL REFERENCES org_registry.regulatory_config_release(id),
    previous_release_id  UUID REFERENCES org_registry.regulatory_config_release(id),
    activated_by         VARCHAR(128) NOT NULL,
    activated_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    validation_report    JSONB NOT NULL,
    correlation_id       VARCHAR(128)
);
CREATE INDEX IF NOT EXISTS idx_rcfg_activation_pack
    ON org_registry.regulatory_config_activation (tenant_id, pack_id, activated_at DESC);

-- ── Case binding: the immutable reference a case owns ────────────────────────────────────────
-- This is the answer to "what were the rules when this case was submitted?". It is written once,
-- at the moment the case is created, and never rewritten — so activating a new release cannot
-- retroactively change the rules a live or completed case was decided under.
CREATE TABLE IF NOT EXISTS org_registry.regulatory_case_config_binding (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    case_system         VARCHAR(32) NOT NULL,
    case_type           VARCHAR(64) NOT NULL,
    case_ref            VARCHAR(128) NOT NULL,
    organization_id     UUID NOT NULL REFERENCES org_registry.org_registry_organization(id),
    pack_id             UUID NOT NULL REFERENCES org_registry.regulatory_config_pack(id),
    release_id          UUID NOT NULL REFERENCES org_registry.regulatory_config_release(id),
    bound_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    bound_by            VARCHAR(128),
    correlation_id      VARCHAR(128),
    CONSTRAINT uq_rcfg_case_binding UNIQUE (tenant_id, case_system, case_type, case_ref),
    CONSTRAINT chk_rcfg_case_system CHECK (case_system IN ('VARAPI', 'TUSO', 'ORG_REGISTRY'))
);
CREATE INDEX IF NOT EXISTS idx_rcfg_case_binding_release
    ON org_registry.regulatory_case_config_binding (release_id);

-- A late pin: some definitions are pinned at a later moment than case creation because the rule
-- says so — a penalty policy is pinned at liability assessment, a certificate template at
-- issuance. Those moments are named here rather than assumed to be case creation.
CREATE TABLE IF NOT EXISTS org_registry.regulatory_case_config_pin (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id             UUID NOT NULL,
    binding_id            UUID NOT NULL
        REFERENCES org_registry.regulatory_case_config_binding(id),
    definition_id         UUID NOT NULL REFERENCES org_registry.regulatory_config_definition(id),
    definition_version_id UUID NOT NULL
        REFERENCES org_registry.regulatory_config_definition_version(id),
    pinned_at_moment      VARCHAR(64) NOT NULL,
    pinned_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    pinned_by             VARCHAR(128),
    CONSTRAINT uq_rcfg_case_pin UNIQUE (binding_id, definition_id, pinned_at_moment)
);
CREATE INDEX IF NOT EXISTS idx_rcfg_case_pin_binding
    ON org_registry.regulatory_case_config_pin (binding_id);

-- ── Audit: append-only ───────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS org_registry.regulatory_config_audit_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    organization_id     UUID,
    pack_id             UUID,
    subject_type        VARCHAR(48) NOT NULL,
    subject_id          UUID,
    event_type          VARCHAR(64) NOT NULL,
    actor_health_id     VARCHAR(128),
    actor_role          VARCHAR(48),
    before_state        JSONB,
    after_state         JSONB,
    correlation_id      VARCHAR(128),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_rcfg_audit_subject
    ON org_registry.regulatory_config_audit_event (tenant_id, subject_type, subject_id, occurred_at DESC);

-- =====================================================================================
-- Invariants the database enforces, so no code path can bypass them
-- =====================================================================================

-- 1. A version's CONTENT is immutable once it leaves DRAFT. Lifecycle may still advance.
CREATE OR REPLACE FUNCTION org_registry.assert_config_version_immutable()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.lifecycle_state <> 'DRAFT' THEN
        IF NEW.payload IS DISTINCT FROM OLD.payload
           OR NEW.content_hash IS DISTINCT FROM OLD.content_hash
           OR NEW.semantic_version IS DISTINCT FROM OLD.semantic_version
           OR NEW.schema_version IS DISTINCT FROM OLD.schema_version
           OR NEW.definition_id IS DISTINCT FROM OLD.definition_id THEN
            RAISE EXCEPTION
                'Configuration version %/% has left DRAFT (state %) — its content is immutable. '
                'A change to an approved or active definition is a NEW version, never an edit.',
                OLD.definition_id, OLD.semantic_version, OLD.lifecycle_state
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.lifecycle_state = 'DRAFT' THEN
            RAISE EXCEPTION
                'Configuration version %/% cannot return to DRAFT from %.',
                OLD.definition_id, OLD.semantic_version, OLD.lifecycle_state
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_config_version_immutable
    ON org_registry.regulatory_config_definition_version;
CREATE TRIGGER trg_config_version_immutable
    BEFORE UPDATE ON org_registry.regulatory_config_definition_version
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_version_immutable();

-- 2. A version that has been live, or is pinned by a case, may never be deleted — deleting it
--    would erase the rules a decided case was decided under.
CREATE OR REPLACE FUNCTION org_registry.assert_config_version_deletable()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.lifecycle_state <> 'DRAFT' THEN
        RAISE EXCEPTION
            'Configuration version %/% is in state % and cannot be deleted. Retire it instead.',
            OLD.definition_id, OLD.semantic_version, OLD.lifecycle_state
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_config_version_deletable
    ON org_registry.regulatory_config_definition_version;
CREATE TRIGGER trg_config_version_deletable
    BEFORE DELETE ON org_registry.regulatory_config_definition_version
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_version_deletable();

-- 3. Four-eyes: the person who submitted a release for review may not approve it.
CREATE OR REPLACE FUNCTION org_registry.assert_config_approval_four_eyes()
RETURNS TRIGGER AS $$
DECLARE
    submitter VARCHAR(128);
BEGIN
    SELECT submitted_by INTO submitter
      FROM org_registry.regulatory_config_release
     WHERE id = NEW.release_id;

    IF submitter IS NOT NULL AND submitter = NEW.approver_health_id THEN
        RAISE EXCEPTION
            'Four-eyes rule: % submitted this configuration release and cannot approve it.',
            NEW.approver_health_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_config_approval_four_eyes
    ON org_registry.regulatory_config_approval;
CREATE TRIGGER trg_config_approval_four_eyes
    BEFORE INSERT ON org_registry.regulatory_config_approval
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_approval_four_eyes();

-- 4. Append-only tables. An activation record, a case binding, a case pin and an audit event are
--    statements about what happened; rewriting one is rewriting history.
CREATE OR REPLACE FUNCTION org_registry.assert_config_append_only()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Table % is append-only — % is not permitted. Record a superseding row instead.',
        TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_config_activation_append_only
    ON org_registry.regulatory_config_activation;
CREATE TRIGGER trg_config_activation_append_only
    BEFORE UPDATE OR DELETE ON org_registry.regulatory_config_activation
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_append_only();

DROP TRIGGER IF EXISTS trg_config_case_binding_append_only
    ON org_registry.regulatory_case_config_binding;
CREATE TRIGGER trg_config_case_binding_append_only
    BEFORE UPDATE OR DELETE ON org_registry.regulatory_case_config_binding
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_append_only();

DROP TRIGGER IF EXISTS trg_config_case_pin_append_only
    ON org_registry.regulatory_case_config_pin;
CREATE TRIGGER trg_config_case_pin_append_only
    BEFORE UPDATE OR DELETE ON org_registry.regulatory_case_config_pin
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_append_only();

DROP TRIGGER IF EXISTS trg_config_audit_append_only
    ON org_registry.regulatory_config_audit_event;
CREATE TRIGGER trg_config_audit_append_only
    BEFORE UPDATE OR DELETE ON org_registry.regulatory_config_audit_event
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_config_append_only();

-- 5. A release that is live or has ever governed a case may not have its item set changed. The
--    binding in (4) references the release, so its contents must be as fixed as the reference.
CREATE OR REPLACE FUNCTION org_registry.assert_release_items_frozen()
RETURNS TRIGGER AS $$
DECLARE
    target_release UUID;
    state          VARCHAR(24);
BEGIN
    target_release := COALESCE(NEW.release_id, OLD.release_id);
    SELECT lifecycle_state INTO state
      FROM org_registry.regulatory_config_release
     WHERE id = target_release;

    IF state IS NOT NULL AND state NOT IN ('DRAFT', 'IN_REVIEW') THEN
        RAISE EXCEPTION
            'Release % is in state % — its contents are frozen. Create a new release instead.',
            target_release, state
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_release_items_frozen
    ON org_registry.regulatory_config_release_item;
CREATE TRIGGER trg_release_items_frozen
    BEFORE INSERT OR UPDATE OR DELETE ON org_registry.regulatory_config_release_item
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_release_items_frozen();
