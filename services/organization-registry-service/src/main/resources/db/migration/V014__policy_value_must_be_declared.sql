-- =====================================================================================
-- org-registry :: V014 — an unset policy value must be DECLARED unset (NCZ-W1A)
--
-- The hole this closes: V013 made policy_status optional, and the release validator only
-- reacted to the literal 'PENDING_REGULATOR_APPROVAL'. So an author who shipped a fee schedule
-- with amount NULL and declared nothing got a completely clean validation report — the release
-- read as fully configured. The author who honestly declared PENDING got a warning.
--
-- The discipline penalised honesty, which is the opposite of what it is for. Worse, it did so at
-- exactly the point where it matters: "build the seam, leave the value unset, and say so" is only
-- meaningful if NOT saying so is impossible.
--
-- Two rules, enforced at write time so an undeclared policy value cannot be authored at all —
-- not merely cannot be activated:
--   1. a definition whose TYPE carries a policy value must declare policy_status;
--   2. anything declared PENDING_REGULATOR_APPROVAL must name the decision it is pending on.
--
-- "Carries a policy value" means the definition's STRUCTURE can be complete while a VALUE the
-- regulator alone may set is absent — a fee schedule with no amount is a well-formed fee
-- schedule; an evidence requirement with no requirements is simply not a definition. Every type
-- flagged below maps to a real entry in
-- docs/requirements/regulators/nurses-council/COUNCIL_DECISIONS_REQUIRED.md, so the flag is
-- grounded in the decision register rather than in taste.
-- =====================================================================================

ALTER TABLE org_registry.regulatory_config_definition_type
    ADD COLUMN IF NOT EXISTS carries_policy_value BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN org_registry.regulatory_config_definition_type.carries_policy_value IS
    'The structure can be complete while a regulator-only value is absent. Such a definition must '
    'declare policy_status: CONFIRMED, or PENDING_REGULATOR_APPROVAL with a decision reference.';

UPDATE org_registry.regulatory_config_definition_type
   SET carries_policy_value = TRUE
 WHERE type_code IN (
    'FEE_SCHEDULE',         -- amount and currency        (NCZ-DEC-002)
    'PENALTY_POLICY',       -- accrual basis, rate, cap   (NCZ-DEC-003)
    'NUMBERING_POLICY',     -- issued-identifier format   (NCZ-DEC-001)
    'EXAMINATION',          -- attempt limit, reset rule  (NCZ-DEC-004, NCZ-DEC-005)
    'SUPERVISED_PRACTICE',  -- duration basis, part-time  (NCZ-DEC-006)
    'CPD_RULES',            -- cycle, credits, categories (NCZ-DEC-012)
    'CERTIFICATE_TEMPLATE', -- statutory wording          (NCZ-DEC-009)
    'APPROVAL_MATRIX',      -- levels and thresholds      (NCZ-DEC-010)
    'CURRENCY_POLICY',      -- accepted currencies, rate  (NCZ-DEC-014)
    'REGISTER_ENTRY_RULE'   -- admission and migration    (NCZ-DEC-006, NCZ-DEC-013)
 );

CREATE OR REPLACE FUNCTION org_registry.assert_policy_value_declared()
RETURNS TRIGGER AS $$
DECLARE
    carries BOOLEAN;
    tcode   VARCHAR(48);
    dkey    VARCHAR(96);
BEGIN
    SELECT t.carries_policy_value, d.type_code, d.definition_key
      INTO carries, tcode, dkey
      FROM org_registry.regulatory_config_definition d
      JOIN org_registry.regulatory_config_definition_type t ON t.type_code = d.type_code
     WHERE d.id = NEW.definition_id;

    IF COALESCE(carries, FALSE) AND NEW.policy_status IS NULL THEN
        RAISE EXCEPTION
            '% :% carries a policy value the regulator alone may set, so it must declare '
            'policy_status — CONFIRMED, or PENDING_REGULATOR_APPROVAL with a decision reference. '
            'An undeclared value is indistinguishable from a configured one.',
            tcode, dkey
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.policy_status = 'PENDING_REGULATOR_APPROVAL'
       AND (NEW.policy_decision_ref IS NULL OR btrim(NEW.policy_decision_ref) = '') THEN
        RAISE EXCEPTION
            '% :% is pending a regulator decision but does not say which one. Reference the entry '
            'in the council decision register, so the outstanding decision is traceable.',
            tcode, dkey
            USING ERRCODE = 'check_violation';
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_policy_value_declared
    ON org_registry.regulatory_config_definition_version;
CREATE TRIGGER trg_policy_value_declared
    BEFORE INSERT OR UPDATE ON org_registry.regulatory_config_definition_version
    FOR EACH ROW EXECUTE FUNCTION org_registry.assert_policy_value_declared();
