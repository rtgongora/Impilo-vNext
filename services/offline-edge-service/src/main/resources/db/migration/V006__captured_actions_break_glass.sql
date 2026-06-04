-- Wave 22: break-glass flag on captured offline actions (CapturedActionEntity.break_glass)

ALTER TABLE ofe_captured_actions
    ADD COLUMN IF NOT EXISTS break_glass BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX IF NOT EXISTS idx_ofe_actions_break_glass
    ON ofe_captured_actions (tenant_id, break_glass, captured_at)
    WHERE break_glass = true;
