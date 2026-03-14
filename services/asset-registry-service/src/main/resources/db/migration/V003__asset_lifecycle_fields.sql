-- ============================================================================
-- asset-registry-service V003 — Asset lifecycle fields
-- Adds: assigned_to, last_seen_at columns for device assignment and telemetry
-- ============================================================================

ALTER TABLE asr_assets ADD COLUMN assigned_to VARCHAR(255);
ALTER TABLE asr_assets ADD COLUMN last_seen_at TIMESTAMPTZ;

CREATE INDEX idx_asr_assets_assigned_to ON asr_assets (assigned_to) WHERE assigned_to IS NOT NULL;
CREATE INDEX idx_asr_assets_last_seen ON asr_assets (last_seen_at DESC) WHERE last_seen_at IS NOT NULL;
