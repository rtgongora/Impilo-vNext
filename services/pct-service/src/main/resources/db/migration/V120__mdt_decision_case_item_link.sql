-- Wave 5.4 — optional link from governed MDT decision (V114) to board case item (V051).
-- One-directional: a case may produce zero, one, or many decisions over time.

ALTER TABLE pct.pct_mdt_decisions ADD COLUMN IF NOT EXISTS case_item_id UUID;

COMMENT ON COLUMN pct.pct_mdt_decisions.case_item_id IS
  'Nullable FK to pct_mdt_case_items when a telemedicine board case produced this decision. '
  'V051 owns the session; V114 owns the decision.';
