-- ============================================================================
-- data-governance-service V006 — Per-user privacy display preferences
--
-- The Privacy-by-Architecture plane (§6) owns the user's PII-display posture:
-- masking level (FULL/PARTIAL/MINIMAL), inactivity auto-lock, and screen-share
-- protection. The experience-bff is stateless and only proxies here (its V39
-- privacy_display_preference table is dead — the BFF has no datasource).
-- ============================================================================

CREATE TABLE dgv_privacy_display_preference (
    id                  UUID            NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    user_id             VARCHAR(255)    NOT NULL,
    default_level       VARCHAR(10)     NOT NULL DEFAULT 'PARTIAL',   -- FULL | PARTIAL | MINIMAL
    auto_lock_minutes   INT             NOT NULL DEFAULT 5,           -- 1..60 minutes of inactivity before auto-lock
    screen_share_mode   BOOLEAN         NOT NULL DEFAULT TRUE,        -- Auto-MINIMAL when screen sharing detected
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_dgv_privacy_pref UNIQUE (tenant_id, user_id)
);

CREATE INDEX idx_dgv_privacy_pref_user ON dgv_privacy_display_preference (tenant_id, user_id);
