-- Subsidy drawdown ledger + concurrency-safe cap enforcement (Lane C money correctness).
--
-- Two money-path bugs in the subsidy annual-cap drawdown are closed here:
--
--   H1 (lost update / cap overspend under concurrency): the balance was read,
--   the cap checked, then the new total written. Two concurrent consume() calls
--   could each read the same consumed_amount, each pass the cap check, and both
--   write — overspending the cap. We now drive the drawdown with an ATOMIC
--   conditional UPDATE that only applies when consumed_amount + amount stays at
--   or under the cap, so the database serialises the check-and-apply.
--
--   H2 (non-idempotent consume): SubsidyConsumeRequest.reference was accepted but
--   never persisted, so a retried consume drew the subsidy down again. This adds a
--   drawdown ledger with a UNIQUE (enrolment_id, reference) constraint; a retry with
--   the same reference returns the prior result instead of drawing again.
--
-- A version column is also added to cv_subsidy_balances as optimistic-lock defence
-- in depth for any future read-modify-write path that does not use the atomic update.

ALTER TABLE cv_subsidy_balances
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Per-drawdown ledger: one row per successful consume, idempotent on reference.
-- The reference is the caller's idempotency key (e.g. the bill/claim line id).
CREATE TABLE cv_subsidy_drawdowns (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    enrolment_id        UUID NOT NULL REFERENCES cv_subsidy_enrolments (id),
    period_year         INT NOT NULL,
    reference           VARCHAR(120) NOT NULL,   -- caller idempotency key
    amount              NUMERIC(14,2) NOT NULL,
    -- Snapshot of the running total AFTER this drawdown applied, so a retry can
    -- reconstruct the exact response without re-reading a since-moved balance.
    consumed_after      NUMERIC(14,2) NOT NULL,
    cap_amount          NUMERIC(14,2),
    currency            VARCHAR(3) NOT NULL DEFAULT 'USD',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cv_subsidy_drawdown_ref UNIQUE (enrolment_id, reference),
    CONSTRAINT chk_cv_subsidy_drawdown_amount CHECK (amount > 0)
);

CREATE INDEX idx_cv_subsidy_drawdown_enrolment
    ON cv_subsidy_drawdowns (enrolment_id, period_year);
