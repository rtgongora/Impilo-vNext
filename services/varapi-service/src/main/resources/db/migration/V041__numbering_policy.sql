-- =====================================================================================
-- varapi :: V041 — issued identifiers, safely (NCZ-W1C-b)
--
-- A student index number is a statutory artefact: it is printed, quoted and relied on, and once
-- issued to a real person it cannot be quietly corrected. So the MECHANISM is built here and the
-- FORMAT is not invented — that is NCZ-DEC-001, and the numbering_policy definition in the NCZ
-- configuration pack carries `format: null` until the Council sets it.
--
-- Three properties this table set exists to guarantee, none of which a naive `max(number)+1` has:
--   * unique under concurrency — two officers admitting two students at the same moment must not
--     produce one number twice;
--   * reserve-then-issue — a number is claimed before the work that uses it completes, so a
--     failure halfway does not leave a gap nobody can explain;
--   * no reuse — a released or retired number is never handed to a second person. A number that
--     once meant someone is never re-pointed at someone else.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.numbering_policy (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    council_id          BIGINT NOT NULL REFERENCES varapi.councils(id),
    policy_key          VARCHAR(64) NOT NULL,
    label               VARCHAR(160) NOT NULL,
    -- NULL until the Council sets it (NCZ-DEC-001). A provisional pattern may be configured for
    -- testing, but issuance against an unset format is refused below rather than guessed.
    format_pattern      VARCHAR(160),
    format_decision_ref VARCHAR(64),
    next_value          BIGINT NOT NULL DEFAULT 1,
    -- The configuration version that governs this policy, so a case can pin what issued its number.
    config_version_ref  VARCHAR(128),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_REGULATOR_APPROVAL',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_numbering_policy UNIQUE (tenant_id, council_id, policy_key),
    CONSTRAINT chk_numbering_policy_status CHECK (status IN
        ('PENDING_REGULATOR_APPROVAL', 'ACTIVE', 'RETIRED')),
    -- An ACTIVE policy must have a format. This is the structural half of "the seam ships built and
    -- the value absent": you cannot activate issuance and leave the format unset, so nothing can
    -- quietly start minting numbers in a shape nobody approved.
    CONSTRAINT chk_numbering_policy_active_has_format CHECK (
        status <> 'ACTIVE' OR (format_pattern IS NOT NULL AND btrim(format_pattern) <> ''))
);

CREATE TABLE IF NOT EXISTS varapi.issued_number (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    policy_id           BIGINT NOT NULL REFERENCES varapi.numbering_policy(id),
    -- The rendered identifier as it will appear to a human. Unique per policy, forever.
    number              VARCHAR(64) NOT NULL,
    ordinal             BIGINT NOT NULL,
    state               VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
    -- What it was issued for, and under which application.
    subject_kind        VARCHAR(32),
    subject_ref         VARCHAR(128),
    application_id      BIGINT REFERENCES varapi.provider_applications(id),
    -- The exact configuration version that produced this number, pinned at issuance.
    config_version_ref  VARCHAR(128),
    reserved_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reserved_by         VARCHAR(128),
    issued_at           TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    release_reason      TEXT,
    CONSTRAINT uq_issued_number UNIQUE (policy_id, number),
    CONSTRAINT uq_issued_ordinal UNIQUE (policy_id, ordinal),
    CONSTRAINT chk_issued_number_state CHECK (state IN ('RESERVED', 'ISSUED', 'RELEASED')),
    CONSTRAINT chk_issued_number_issued CHECK (state <> 'ISSUED' OR issued_at IS NOT NULL),
    CONSTRAINT chk_issued_number_released CHECK (
        state <> 'RELEASED' OR (released_at IS NOT NULL
                                AND release_reason IS NOT NULL AND btrim(release_reason) <> ''))
);
CREATE INDEX IF NOT EXISTS idx_issued_number_subject
    ON varapi.issued_number (policy_id, subject_kind, subject_ref);

-- No reuse, and no rewriting. A number row may advance RESERVED -> ISSUED or RESERVED -> RELEASED,
-- and nothing else. A RELEASED number is a scar, not a free slot: leaving the row in place is what
-- makes reuse impossible, because the unique index still holds the number.
CREATE OR REPLACE FUNCTION varapi.assert_issued_number_transition()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.number IS DISTINCT FROM OLD.number OR NEW.ordinal IS DISTINCT FROM OLD.ordinal
       OR NEW.policy_id IS DISTINCT FROM OLD.policy_id THEN
        RAISE EXCEPTION
            'Issued number % cannot be re-pointed. A number that once meant someone is never '
            'handed to a second person.', OLD.number
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.state IS DISTINCT FROM OLD.state AND NOT (
            (OLD.state = 'RESERVED' AND NEW.state IN ('ISSUED', 'RELEASED'))) THEN
        RAISE EXCEPTION
            'Issued number % cannot move from % to %.', OLD.number, OLD.state, NEW.state
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_issued_number_transition ON varapi.issued_number;
CREATE TRIGGER trg_issued_number_transition
    BEFORE UPDATE ON varapi.issued_number
    FOR EACH ROW EXECUTE FUNCTION varapi.assert_issued_number_transition();

CREATE OR REPLACE FUNCTION varapi.assert_issued_number_never_deleted()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Issued number % cannot be deleted — deleting it would free the number for reuse, which is '
        'the one thing this table exists to prevent. Release it instead.', OLD.number
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_issued_number_never_deleted ON varapi.issued_number;
CREATE TRIGGER trg_issued_number_never_deleted
    BEFORE DELETE ON varapi.issued_number
    FOR EACH ROW EXECUTE FUNCTION varapi.assert_issued_number_never_deleted();

-- Reserve the next number atomically. The UPDATE ... RETURNING takes a row lock on the policy, so
-- two concurrent callers serialise on it and cannot receive the same ordinal.
CREATE OR REPLACE FUNCTION varapi.reserve_number(
    p_tenant UUID, p_policy BIGINT, p_subject_kind VARCHAR, p_subject_ref VARCHAR,
    p_application BIGINT, p_by VARCHAR)
RETURNS varapi.issued_number AS $$
DECLARE
    pol       varapi.numbering_policy;
    next_ord  BIGINT;
    rendered  VARCHAR(64);
    row_out   varapi.issued_number;
BEGIN
    UPDATE varapi.numbering_policy
       SET next_value = next_value + 1, updated_at = NOW()
     WHERE id = p_policy AND tenant_id = p_tenant
    RETURNING * INTO pol;

    IF pol.id IS NULL THEN
        RAISE EXCEPTION 'No numbering policy % in tenant %', p_policy, p_tenant;
    END IF;
    IF pol.status <> 'ACTIVE' THEN
        RAISE EXCEPTION
            'Numbering policy % is % — no identifier is issued until the Council has set its format '
            '(%). The mechanism is built; the value is not set.',
            pol.policy_key, pol.status, COALESCE(pol.format_decision_ref, 'a council decision')
            USING ERRCODE = 'check_violation';
    END IF;

    -- UPDATE ... RETURNING yields the row AFTER the update, so pol.next_value is already the NEXT
    -- one. The ordinal just claimed is one behind it. Written the obvious way this issued 2 first
    -- and silently burned ordinal 1 — harmless-looking, and wrong on the first record a council
    -- would ever check.
    next_ord := pol.next_value - 1;
    rendered := replace(pol.format_pattern, '{seq}', lpad(next_ord::text, 6, '0'));

    INSERT INTO varapi.issued_number
        (tenant_id, policy_id, number, ordinal, state, subject_kind, subject_ref,
         application_id, config_version_ref, reserved_by)
    VALUES (p_tenant, p_policy, rendered, next_ord, 'RESERVED', p_subject_kind, p_subject_ref,
            p_application, pol.config_version_ref, p_by)
    RETURNING * INTO row_out;
    RETURN row_out;
END;
$$ LANGUAGE plpgsql;

-- The NCZ student index policy. Format deliberately absent, so the policy is not ACTIVE and
-- issuance refuses rather than inventing a shape that would be printed on real people's records.
INSERT INTO varapi.numbering_policy
    (tenant_id, council_id, policy_key, label, format_pattern, format_decision_ref, status,
     config_version_ref)
SELECT c.tenant_id, c.id, 'student-index', 'Student index number', NULL, 'NCZ-DEC-001',
       'PENDING_REGULATOR_APPROVAL', 'nurses-council/student-index'
  FROM varapi.councils c
 WHERE c.council_code = 'NCZ'
   AND NOT EXISTS (SELECT 1 FROM varapi.numbering_policy n
                    WHERE n.council_id = c.id AND n.policy_key = 'student-index');
