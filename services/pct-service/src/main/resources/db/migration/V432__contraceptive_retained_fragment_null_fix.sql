-- Corrective migration. V430's retained-fragment CHECK has a NULL hole, and V430 is already applied
-- on the estate, so it is fixed forward rather than edited.
--
-- THE BUG. chk_pct_contra_admin_retained_described was written as:
--
--   retained_fragment IS FALSE
--   OR (removal_completeness IN ('PARTIAL', 'FAILED') AND retained_fragment_detail IS NOT NULL)
--
-- It reads correctly and it refuses the wrong-value cases. It does NOT refuse the MISSING-value
-- case. With retained_fragment = TRUE and removal_completeness = NULL, `NULL IN ('PARTIAL','FAILED')`
-- evaluates to NULL, `NULL AND (detail IS NOT NULL)` is NULL, and `FALSE OR NULL` is NULL. A CHECK
-- rejects only FALSE, so a NULL verdict is ACCEPTED — a retained device fragment recorded against no
-- removal outcome at all is let through. Proven against the applied schema on the estate, not read:
-- the row inserted with retained_fragment TRUE, completeness NULL, a detail string and type INSERTION
-- was accepted.
--
-- The eleven mutation probes that cleared V430 missed it because a hand-written malicious row carries
-- a WRONG value, and this hole is a MISSING one. The fleet law now: a CHECK-constraint mutation test
-- must exercise the NULL case explicitly, because that is the case a CHECK silently admits.
--
-- THE FIX. Guard the IN against NULL so the predicate is FALSE, not NULL, when the completeness is
-- absent — and treat a retained fragment as requiring a stated PARTIAL/FAILED outcome regardless.

ALTER TABLE pct.pct_contraceptive_administrations
    DROP CONSTRAINT IF EXISTS chk_pct_contra_admin_retained_described;

ALTER TABLE pct.pct_contraceptive_administrations
    ADD CONSTRAINT chk_pct_contra_admin_retained_described CHECK (
        retained_fragment IS NOT TRUE
        OR (removal_completeness IS NOT NULL
            AND removal_completeness IN ('PARTIAL', 'FAILED')
            AND retained_fragment_detail IS NOT NULL));
