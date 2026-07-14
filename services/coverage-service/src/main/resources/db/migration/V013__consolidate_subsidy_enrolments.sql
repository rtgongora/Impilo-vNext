-- Consolidate the two subsidy-enrolment models onto cv_subsidy_enrolments (SoR).
--
-- V010 created cv_subsidy_enrolments (value lane: annual-cap drawdown engine).
-- V012 created cv_subsidy_enrollments (exemption lane: per-member exemption_category
-- that COSTA billing-category resolution keys waivers on). One member<->programme
-- link should carry both concerns. This migration:
--   1. widens member_cpid so legacy client_id values (VARCHAR 255) can never truncate;
--   2. adds a nullable exemption_category to cv_subsidy_enrolments;
--   3. folds legacy rows in: rows colliding with the unique key
--      (tenant_id, subsidy_program_id, member_cpid, effective_from) update the
--      existing enrolment's exemption_category; the rest are inserted (deduped,
--      newest per key wins);
--   4. drops cv_subsidy_enrollments.
-- Both tables are empty in every environment at the time of writing; the data moves
-- are defensive. Portable SQL only (no DO $$ / DISTINCT ON / UPDATE..FROM), so a
-- future Flyway-on-H2 test path does not have to rewrite it.

-- 1) Widen member identifier to match the legacy column width.
ALTER TABLE cv_subsidy_enrolments ALTER COLUMN member_cpid TYPE VARCHAR(255);

-- 2) Exemption category (nullable: value-only enrolments carry no exemption).
ALTER TABLE cv_subsidy_enrolments ADD COLUMN exemption_category VARCHAR(64);

-- 3a) Collision case: an enrolment already exists for the same
--     (tenant, programme, member, effective_from) — carry the exemption category
--     onto the existing row (newest legacy row per key wins).
UPDATE cv_subsidy_enrolments
SET exemption_category = (
        SELECT s.exemption_category
        FROM cv_subsidy_enrollments s
        WHERE s.tenant_id = cv_subsidy_enrolments.tenant_id
          AND s.subsidy_program_id = cv_subsidy_enrolments.subsidy_program_id
          AND s.client_id = cv_subsidy_enrolments.member_cpid
          AND s.effective_from = cv_subsidy_enrolments.effective_from
        ORDER BY s.created_at DESC, s.id DESC
        LIMIT 1
    ),
    updated_at = now()
WHERE EXISTS (
        SELECT 1 FROM cv_subsidy_enrollments s
        WHERE s.tenant_id = cv_subsidy_enrolments.tenant_id
          AND s.subsidy_program_id = cv_subsidy_enrolments.subsidy_program_id
          AND s.client_id = cv_subsidy_enrolments.member_cpid
          AND s.effective_from = cv_subsidy_enrolments.effective_from
    );

-- 3b) Non-colliding legacy rows: insert, deduped to the newest row per unique key
--     (the legacy table had no unique constraint). Currency falls back to the
--     value-lane default; there is no cap override or enrolling actor on legacy rows.
INSERT INTO cv_subsidy_enrolments (
    id, tenant_id, pod_id, subsidy_program_id, member_cpid, status,
    annual_cap_override, currency, enrolled_by, exemption_category,
    effective_from, effective_to, created_at, updated_at)
SELECT s.id, s.tenant_id, s.pod_id, s.subsidy_program_id, s.client_id, s.status,
       NULL, 'USD', NULL, s.exemption_category,
       s.effective_from, s.effective_to, s.created_at, s.updated_at
FROM cv_subsidy_enrollments s
WHERE s.status IN ('ACTIVE', 'SUSPENDED', 'ENDED')   -- satisfy chk_cv_subsidy_enrolment_status
  AND NOT EXISTS (
        SELECT 1 FROM cv_subsidy_enrolments e
        WHERE e.tenant_id = s.tenant_id
          AND e.subsidy_program_id = s.subsidy_program_id
          AND e.member_cpid = s.client_id
          AND e.effective_from = s.effective_from)
  AND NOT EXISTS (        -- keep only the newest legacy row per unique key
        SELECT 1 FROM cv_subsidy_enrollments d
        WHERE d.tenant_id = s.tenant_id
          AND d.subsidy_program_id = s.subsidy_program_id
          AND d.client_id = s.client_id
          AND d.effective_from = s.effective_from
          AND (d.created_at > s.created_at
               OR (d.created_at = s.created_at AND d.id > s.id)));

-- 4) Retire the legacy table (its indexes drop with it).
DROP TABLE cv_subsidy_enrollments;
