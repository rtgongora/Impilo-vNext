-- B4: close the revoke fail-open when a VITO card is revoked/suspended BEFORE a
-- money card links to it (link-after-event race). VitoCardEventConsumer's freeze
-- is a no-op when no money card is linked yet, so the revoke was silently lost and
-- a later link would leave the money facet spendable. We now record a freeze
-- tombstone keyed by the canonical VITO cardNumber; linkVitoCard checks it and
-- freezes the newly linked card immediately (backfill reconciliation).
CREATE TABLE IF NOT EXISTS mushe.vito_card_freeze (
    vito_card_number VARCHAR(64) PRIMARY KEY,
    event_type       VARCHAR(48)  NOT NULL,
    reason           VARCHAR(256),
    received_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
