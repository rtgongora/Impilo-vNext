-- =====================================================================================
-- varapi :: V042 — a council fee schedule, at last (NCZ-W1C-b)
--
-- varapi has had NO fee schedule of any kind. build-waves.md records this as delivered by V032;
-- that slot is empty and, with out-of-order Flyway off, can never be filled. So the ROM-W4 gap is
-- real and this closes it, mirroring the shape tuso V021 proved: a schedule, an obligation, and a
-- gate that consults them.
--
-- THE FAIL-CLOSED RULE IS STRUCTURAL, NOT DOCUMENTARY. `NCZ-DEC-002` says an unconfigured fee must
-- surface as NOT_CONFIGURED rather than silently skipping the gate. A comment cannot enforce that,
-- so the CHECK below makes an ACTIVE fee without an amount and currency impossible to write: a fee
-- is either configured, or it is not chargeable. There is no third state in which the gate quietly
-- passes because the number happened to be NULL.
-- =====================================================================================

CREATE TABLE IF NOT EXISTS varapi.regulatory_fee_schedule (
    id                  BIGSERIAL PRIMARY KEY,
    tenant_id           UUID NOT NULL,
    council_id          BIGINT NOT NULL REFERENCES varapi.councils(id),
    fee_code            VARCHAR(64) NOT NULL,
    description         TEXT NOT NULL,
    -- NULL until the Council sets it. Fees are set by instrument, not by software.
    amount              NUMERIC(14, 2),
    currency            CHAR(3),
    amount_decision_ref VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_REGULATOR_APPROVAL',
    effective_from      DATE,
    effective_to        DATE,
    approved_by         VARCHAR(128),
    approved_at         TIMESTAMPTZ,
    -- The configuration version this schedule came from, so a case can pin the fee that governed it.
    config_version_ref  VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_fee_schedule UNIQUE (tenant_id, council_id, fee_code, effective_from),
    CONSTRAINT chk_fee_status CHECK (status IN
        ('PENDING_REGULATOR_APPROVAL', 'ACTIVE', 'SUPERSEDED', 'WITHDRAWN')),
    CONSTRAINT chk_fee_dates CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from),
    -- The rule that makes NOT_CONFIGURED real: you cannot activate a fee you have not set.
    CONSTRAINT chk_fee_active_is_configured CHECK (
        status <> 'ACTIVE' OR (amount IS NOT NULL AND currency IS NOT NULL)),
    -- An amount without a currency is not a price. Zimbabwe's councils invoice in more than one
    -- currency (NCZ-DEC-014), so a bare number would be ambiguous at the point it matters most.
    CONSTRAINT chk_fee_amount_has_currency CHECK (
        (amount IS NULL) = (currency IS NULL)),
    CONSTRAINT chk_fee_amount_not_negative CHECK (amount IS NULL OR amount >= 0)
);
CREATE INDEX IF NOT EXISTS idx_fee_schedule_lookup
    ON varapi.regulatory_fee_schedule (tenant_id, council_id, fee_code, status);

COMMENT ON COLUMN varapi.regulatory_fee_schedule.amount IS
    'NULL until the Council sets it. The gate reads this: a NULL amount on a non-ACTIVE row means '
    'NOT_CONFIGURED, which blocks the charge honestly. It never means free.';

-- The gate's verdict, as a function so no caller can invent its own reading of a NULL amount.
-- Three outcomes, and the middle one is the one that matters: a fee the Council has not set does
-- not fall through as zero, and does not fall through as "no fee applies".
CREATE OR REPLACE FUNCTION varapi.fee_verdict(p_tenant UUID, p_council BIGINT, p_fee_code VARCHAR)
RETURNS TABLE (verdict VARCHAR, amount NUMERIC, currency CHAR(3), decision_ref VARCHAR) AS $$
BEGIN
    RETURN QUERY
    SELECT CASE
             WHEN f.id IS NULL                     THEN 'NO_SUCH_FEE'::VARCHAR
             WHEN f.status <> 'ACTIVE'             THEN 'NOT_CONFIGURED'::VARCHAR
             ELSE 'PAYABLE'::VARCHAR
           END,
           f.amount, f.currency, f.amount_decision_ref
      FROM (SELECT * FROM varapi.regulatory_fee_schedule s
             WHERE s.tenant_id = p_tenant AND s.council_id = p_council
               AND s.fee_code = p_fee_code
               AND (s.effective_from IS NULL OR s.effective_from <= CURRENT_DATE)
               AND (s.effective_to IS NULL OR s.effective_to >= CURRENT_DATE)
             ORDER BY CASE WHEN s.status = 'ACTIVE' THEN 0 ELSE 1 END,
                      s.effective_from DESC NULLS LAST
             LIMIT 1) f
     RIGHT JOIN (SELECT 1) dummy ON TRUE;
END;
$$ LANGUAGE plpgsql STABLE;

-- The NCZ student index fee. Amount deliberately absent — the seam ships built, the value does not.
INSERT INTO varapi.regulatory_fee_schedule
    (tenant_id, council_id, fee_code, description, amount, currency, amount_decision_ref,
     status, config_version_ref)
SELECT c.tenant_id, c.id, 'STUDENT_INDEX',
       'Payable on admission to the Student Register.', NULL, NULL, 'NCZ-DEC-002',
       'PENDING_REGULATOR_APPROVAL', 'nurses-council/student-index-fee'
  FROM varapi.councils c
 WHERE c.council_code = 'NCZ'
   AND NOT EXISTS (SELECT 1 FROM varapi.regulatory_fee_schedule f
                    WHERE f.council_id = c.id AND f.fee_code = 'STUDENT_INDEX');
