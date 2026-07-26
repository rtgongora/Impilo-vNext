-- Corrective migration for V430. RMNP band.
--
-- V430 shipped chk_pct_contra_admin_retained_described to enforce the retained-rod case: a device
-- fragment left behind must be described and must sit on a partial or failed removal. It does not.
--
-- A CHECK constraint rejects a row only when the predicate evaluates to FALSE; UNKNOWN (NULL) passes.
-- The predicate was:
--
--     retained_fragment IS FALSE
--     OR (removal_completeness IN ('PARTIAL', 'FAILED') AND retained_fragment_detail IS NOT NULL)
--
-- With retained_fragment = TRUE and removal_completeness = NULL — which is reachable, because a
-- retained fragment on an INSERTION row is not caught by the removal-states-outcome constraint —
-- the left side is FALSE and `NULL IN ('PARTIAL','FAILED')` is NULL, so the whole predicate is NULL
-- and the row is accepted. A retained fragment recorded against no removal outcome is exactly the
-- silent gap the constraint was written to close.
--
-- PROVEN, not assumed: inserting such a row against the applied V430 schema on the preview estate
-- succeeded. The mistake was mine — I mutation-tested V430's constraints with rows that set
-- removal_completeness, so the NULL branch was never exercised. The sibling V431 constraint written
-- the same day had the identical flaw and was fixed before it shipped; this repairs the one that had
-- already landed.
--
-- The correct form pins removal_completeness to a definite value before the IN test, so a NULL makes
-- the predicate FALSE rather than UNKNOWN.

ALTER TABLE pct.pct_contraceptive_administrations
    DROP CONSTRAINT IF EXISTS chk_pct_contra_admin_retained_described;

ALTER TABLE pct.pct_contraceptive_administrations
    ADD CONSTRAINT chk_pct_contra_admin_retained_described CHECK (
        retained_fragment IS NOT TRUE
        OR (removal_completeness IS NOT NULL
            AND removal_completeness IN ('PARTIAL', 'FAILED')
            AND retained_fragment_detail IS NOT NULL));
