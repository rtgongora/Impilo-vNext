-- =============================================================================
-- khuluma-service V005 — External channel adapters & delivery attempts
-- (Phase 5 / W6, G-KH-03)
--
-- Abstracts message delivery across channels. Native in-app is the one real adapter;
-- SMS / WhatsApp / EMAIL / USSD are HONEST seams: until a provider is wired and the
-- adapter is marked CONFIGURED, a dispatch records SKIPPED_NOT_CONFIGURED — never a
-- fake "sent". delivery_attempt is the auditable per-channel outcome log.
-- =============================================================================

CREATE TABLE khuluma_channel_adapter (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   UUID NOT NULL,
    channel     VARCHAR(16) NOT NULL,       -- IN_APP | SMS | WHATSAPP | EMAIL | USSD
    status      VARCHAR(24) NOT NULL DEFAULT 'NOT_CONFIGURED', -- CONFIGURED | NOT_CONFIGURED | DISABLED
    provider    VARCHAR(64),                -- provider name once wired (e.g. 'twilio')
    config_json JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_khuluma_channel_adapter UNIQUE (tenant_id, channel)
);

CREATE TABLE khuluma_delivery_attempt (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID NOT NULL,
    message_ref  VARCHAR(255),              -- message id / conversation id / broadcast ref
    recipient    VARCHAR(255),
    channel      VARCHAR(16) NOT NULL,
    status       VARCHAR(28) NOT NULL,      -- DELIVERED | SENT | FAILED | SKIPPED_NOT_CONFIGURED | SKIPPED_NO_ADAPTER
    detail       TEXT,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_khuluma_delivery_attempt_ref ON khuluma_delivery_attempt (tenant_id, message_ref);
