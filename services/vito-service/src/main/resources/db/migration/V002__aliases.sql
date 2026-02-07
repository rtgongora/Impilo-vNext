-- VITO — Impilo ID alias storage (no plaintext impilo_id)

CREATE TABLE vito.identity_alias (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID         NOT NULL,
    health_id       UUID         NOT NULL REFERENCES vito.client(health_id),
    alias_type      VARCHAR(50)  NOT NULL,  -- IMPILO_ID, NATIONAL_ID_LINK, etc.
    lookup_hash     VARCHAR(128) NOT NULL,  -- HMAC(pepper, normalize(id))
    verifier        VARCHAR(255) NOT NULL,  -- Argon2id(normalize(id))
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE, REVOKED, ROTATED
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    revoked_at      TIMESTAMPTZ,
    UNIQUE (tenant_id, alias_type, lookup_hash)
);

CREATE INDEX idx_alias_lookup ON vito.identity_alias (tenant_id, alias_type, status);
