-- ============================================================================
-- data-governance-service V007 — display settings on the per-user display-preference row
--
-- The same dgv_privacy_display_preference row (already the per-user display-preference
-- store, V006) also carries UI ergonomics (theme/font/density), so the BFF
-- /internal/v1/settings/display endpoint persists instead of echoing (G048).
-- ============================================================================

ALTER TABLE dgv_privacy_display_preference
    ADD COLUMN IF NOT EXISTS theme      VARCHAR(10)  NOT NULL DEFAULT 'system',
    ADD COLUMN IF NOT EXISTS font_size  VARCHAR(10)  NOT NULL DEFAULT 'medium',
    ADD COLUMN IF NOT EXISTS density    VARCHAR(16)  NOT NULL DEFAULT 'comfortable';
