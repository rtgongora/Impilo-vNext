-- =====================================================================================
-- varapi :: V040 — an application a student fills in over time, and a bounded contributor
--                  (NCZ-W1C-b)
--
-- Verified before writing: V031 delivered `application_information_request` and
-- `application_case_message`, so correspondence and RFI exist. It did NOT deliver application
-- sections or contributor invites, despite build-waves.md claiming otherwise. Without sections
-- there is nothing to return individually, so a regulator's only move is to reject the whole
-- application — and a student who got one document wrong starts again.
--
-- Two rules are enforced here rather than intended:
--
--   1. A CONTRIBUTOR NEVER SEES THE WHOLE CASE. A training institution confirms enrolment. It has
--      no business reading the applicant's identity documents or the regulator's assessment, so an
--      invite must NAME the sections it covers and cannot name all of them by wildcard.
--   2. RETURNING A SECTION DOES NOT RESTART THE APPLICATION. The section reopens; the case keeps
--      its place in the queue, its history and its submitted date. Restarting is the behaviour that
--      makes applicants re-enter everything to fix one document.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.application_section (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    application_id      BIGINT NOT NULL REFERENCES varapi.provider_applications(id),
    section_key         VARCHAR(64) NOT NULL,
    label               VARCHAR(160) NOT NULL,
    sequence_no         INT NOT NULL DEFAULT 100,
    state               VARCHAR(24) NOT NULL DEFAULT 'NOT_STARTED',
    content             JSONB,
    -- Which configuration version governed this section when the applicant completed it. The case
    -- binding pins the release; this records the section's own answer for audit.
    config_version_ref  VARCHAR(128),
    completed_at        TIMESTAMPTZ,
    -- Section-level return: why it came back, from whom, and when.
    returned_reason     TEXT,
    returned_at         TIMESTAMPTZ,
    returned_by         VARCHAR(128),
    return_count        INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_application_section UNIQUE (application_id, section_key),
    CONSTRAINT chk_application_section_state CHECK (state IN
        ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETE', 'RETURNED')),
    -- A returned section must say why. "Returned" with no reason is an applicant staring at a form
    -- with no idea what to change.
    CONSTRAINT chk_application_section_return CHECK (
        state <> 'RETURNED' OR (returned_reason IS NOT NULL AND btrim(returned_reason) <> ''))
);
CREATE INDEX IF NOT EXISTS idx_application_section_app
    ON varapi.application_section (application_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_application_section_returned
    ON varapi.application_section (application_id) WHERE state = 'RETURNED';

-- ── The bounded contributor ──────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS varapi.application_contributor_invite (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    application_id      BIGINT NOT NULL REFERENCES varapi.provider_applications(id),
    contributor_kind    VARCHAR(48) NOT NULL,
    -- Who was asked. An organisation for an institution; a person for a named referee.
    contributor_org_id  UUID,
    contributor_ref     VARCHAR(128),
    -- The whole point: the sections this invite covers. Never all of them.
    scope_sections      TEXT[] NOT NULL,
    -- Hashed, never stored in the clear — the same discipline as the org-registry invitation rail.
    token_hash          VARCHAR(64),
    status              VARCHAR(24) NOT NULL DEFAULT 'INVITED',
    invited_by          VARCHAR(128),
    invited_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    accepted_at         TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    declined_reason     TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_contributor_kind CHECK (contributor_kind IN
        ('TRAINING_INSTITUTION', 'EMPLOYER', 'REFEREE', 'SUPERVISOR')),
    CONSTRAINT chk_contributor_status CHECK (status IN
        ('INVITED', 'ACCEPTED', 'COMPLETED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')),
    -- An invite that names no section grants nothing and would be a puzzle for the recipient.
    -- COALESCE is load-bearing: array_length of an EMPTY array is NULL, not 0, and a CHECK passes
    -- on NULL. Written the obvious way, this constraint accepted exactly what it existed to refuse.
    CONSTRAINT chk_contributor_scope_present CHECK (
        COALESCE(array_length(scope_sections, 1), 0) >= 1),
    -- And it may not name everything by wildcard. A contributor's scope is a list of sections,
    -- which is what keeps "bounded contribution" from decaying into "co-applicant".
    CONSTRAINT chk_contributor_scope_not_wildcard CHECK (
        NOT (scope_sections && ARRAY['*', 'ALL', 'ANY']))
);
CREATE INDEX IF NOT EXISTS idx_contributor_invite_app
    ON varapi.application_contributor_invite (application_id, status);
CREATE UNIQUE INDEX IF NOT EXISTS uq_contributor_invite_token
    ON varapi.application_contributor_invite (token_hash) WHERE token_hash IS NOT NULL;

-- Every section a contributor is invited into must actually exist on that application. An invite
-- pointing at a section nobody defined is an invitation to a door that does not open.
CREATE OR REPLACE FUNCTION varapi.assert_contributor_scope_resolves()
RETURNS TRIGGER AS $$
DECLARE
    missing TEXT;
BEGIN
    SELECT s INTO missing
      FROM unnest(NEW.scope_sections) AS s
     WHERE NOT EXISTS (
        SELECT 1 FROM varapi.application_section sec
         WHERE sec.application_id = NEW.application_id AND sec.section_key = s)
     LIMIT 1;

    IF missing IS NOT NULL THEN
        RAISE EXCEPTION
            'Contributor invite names section ''%'', which application % does not have.',
            missing, NEW.application_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_contributor_scope_resolves ON varapi.application_contributor_invite;
CREATE TRIGGER trg_contributor_scope_resolves
    BEFORE INSERT OR UPDATE OF scope_sections ON varapi.application_contributor_invite
    FOR EACH ROW EXECUTE FUNCTION varapi.assert_contributor_scope_resolves();

-- ── Section-level return, on the existing RFI rail ───────────────────────────────────────────
-- V031's information request is case-level. Pointing one at a section is what turns "your
-- application is rejected" into "this document needs replacing".
ALTER TABLE varapi.application_information_request
    ADD COLUMN IF NOT EXISTS section_key VARCHAR(64);

COMMENT ON COLUMN varapi.application_information_request.section_key IS
    'The section this request is about. NULL for a case-level request. Set, it reopens that section '
    'alone — the application keeps its place in the queue and its submitted date.';

-- Returning a section must not silently move the application. The regulator returns work; the case
-- stays where it is. Enforced so no future write path can quietly conflate the two.
CREATE OR REPLACE FUNCTION varapi.assert_section_return_preserves_case()
RETURNS TRIGGER AS $$
DECLARE
    submitted TIMESTAMPTZ;
BEGIN
    IF NEW.state = 'RETURNED' AND OLD.state IS DISTINCT FROM 'RETURNED' THEN
        SELECT submitted_at INTO submitted
          FROM varapi.provider_applications WHERE id = NEW.application_id;
        IF submitted IS NULL THEN
            RAISE EXCEPTION
                'Section %/% cannot be returned: the application has not been submitted, so there '
                'is nothing to return it from.', NEW.application_id, NEW.section_key
                USING ERRCODE = 'check_violation';
        END IF;
        NEW.returned_at := COALESCE(NEW.returned_at, NOW());
        NEW.return_count := OLD.return_count + 1;
    END IF;
    NEW.updated_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_section_return_preserves_case ON varapi.application_section;
CREATE TRIGGER trg_section_return_preserves_case
    BEFORE UPDATE ON varapi.application_section
    FOR EACH ROW EXECUTE FUNCTION varapi.assert_section_return_preserves_case();
