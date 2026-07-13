-- Zero-price guardrail: a bill line whose TARIFF lookup found no configured
-- tariff used to price silently to $0 and nothing stopped the bill from
-- finalizing at the wrong total. Lines now carry an explicit pending_pricing
-- marker set at posting time; finalize refuses while any non-voided line is
-- pending pricing (explicit allowUnpriced override for genuinely-free lines).

ALTER TABLE costa_bill_lines
    ADD COLUMN IF NOT EXISTS pending_pricing BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_bill_lines_pending_pricing
    ON costa_bill_lines (bill_id) WHERE pending_pricing;
