-- =============================================================================
-- Coverage V013 — Unify the two subsidy-enrolment models.
--
-- History: V010 created cv_subsidy_enrolments (member_cpid + annual-cap
-- balance/drawdown ledger — the money truth) and V012 created
-- cv_subsidy_enrollments (client_id + exemption_category — the billing-
-- category link CoveragePlanController resolution consumes). Two tables,
-- one concept.
--
-- Decision (Lane C): the LEDGERED table is canonical. exemption_category
-- moves onto cv_subsidy_enrolments; legacy rows are migrated
-- (client_id -> member_cpid), deduped in favour of existing ledgered rows;
-- cv_subsidy_enrollments becomes READ-ONLY DEPRECATED (dropped in a later
-- migration once all consumers are proven off it).
-- =============================================================================

ALTER TABLE cv_subsidy_enrolments
    ADD COLUMN IF NOT EXISTS exemption_category VARCHAR(64);

COMMENT ON COLUMN cv_subsidy_enrolments.exemption_category IS
    'Per-member billing classification (INDIGENT / ELDERLY / HEALTH_WORKER / ...) that COSTA charging rules key exemptions on. Nullable: value-only enrolments carry no billing category.';

-- Migrate legacy billing-category enrolments into the ledgered model.
-- Dedupe rule: a ledgered row for the same (tenant, programme, member) wins;
-- the legacy row then only contributes its exemption_category when the
-- ledgered row has none.
UPDATE cv_subsidy_enrolments e
SET exemption_category = l.exemption_category
FROM cv_subsidy_enrollments l
WHERE e.tenant_id = l.tenant_id
  AND e.subsidy_program_id = l.subsidy_program_id
  AND e.member_cpid = l.client_id
  AND e.exemption_category IS NULL
  AND l.status = 'ACTIVE';

INSERT INTO cv_subsidy_enrolments (
    id, tenant_id, pod_id, subsidy_program_id, member_cpid, status,
    annual_cap_override, currency, enrolled_by, effective_from, effective_to,
    created_at, updated_at, exemption_category
)
SELECT
    l.id, l.tenant_id, l.pod_id, l.subsidy_program_id, l.client_id, l.status,
    NULL, 'USD', 'MIGRATED:V013', l.effective_from, l.effective_to,
    l.created_at, l.updated_at, l.exemption_category
FROM cv_subsidy_enrollments l
WHERE NOT EXISTS (
    SELECT 1 FROM cv_subsidy_enrolments e
    WHERE e.tenant_id = l.tenant_id
      AND e.subsidy_program_id = l.subsidy_program_id
      AND e.member_cpid = l.client_id
);

CREATE INDEX IF NOT EXISTS idx_cv_subsidy_enrolment_exemption
    ON cv_subsidy_enrolments (tenant_id, member_cpid, status)
    WHERE exemption_category IS NOT NULL;

-- Deprecate the legacy table: read-only from here on (application code no
-- longer writes it; the trigger makes the posture explicit at the DB layer).
COMMENT ON TABLE cv_subsidy_enrollments IS
    'DEPRECATED (V013): unified into cv_subsidy_enrolments. READ-ONLY; retained for audit until a later drop migration.';

CREATE OR REPLACE FUNCTION cv_subsidy_enrollments_read_only()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'cv_subsidy_enrollments is deprecated and read-only (V013); write to cv_subsidy_enrolments';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_cv_subsidy_enrollments_read_only ON cv_subsidy_enrollments;
CREATE TRIGGER trg_cv_subsidy_enrollments_read_only
    BEFORE INSERT OR UPDATE OR DELETE ON cv_subsidy_enrollments
    FOR EACH STATEMENT EXECUTE FUNCTION cv_subsidy_enrollments_read_only();
