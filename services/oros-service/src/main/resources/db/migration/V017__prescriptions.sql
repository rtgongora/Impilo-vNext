-- OF-B2: the prescription aggregate (Vol II §9.2, §13.2).
-- Parent authorisation in OROS; PHARMACY orders remain the dispense EPISODES.
-- Versions are immutable (UNIQUE(prescription_id, version) = amend race guard);
-- the server-side repeats counter on the parent is the ONLY decrement path;
-- at most one ACTIVE token exists per prescription (partial unique index).
-- Legacy pharmacy rx_prescriptions is frozen (OD-13) — no linkage here.

CREATE TABLE oros_prescriptions (
    prescription_id   VARCHAR(26) PRIMARY KEY,        -- ULID (§5 PrescriptionId)
    tenant_id         UUID        NOT NULL,
    facility_id       UUID,
    patient_cpid      VARCHAR(64) NOT NULL,
    prescriber_id     VARCHAR(128) NOT NULL,
    prescriber_name   VARCHAR(255),
    encounter_ref     VARCHAR(255),
    request_source    VARCHAR(32) NOT NULL DEFAULT 'INTERNAL',
    source_ref        VARCHAR(128),                    -- e.g. teleconsult referral id
    status            VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    repeats_ceiling   INT         NOT NULL DEFAULT 1,  -- total permitted fills
    repeats_remaining INT         NOT NULL DEFAULT 1,  -- server-side counter (§13.2.3)
    validity_start    DATE,
    validity_end      DATE,
    controlled        BOOLEAN     NOT NULL DEFAULT FALSE, -- any controlled line (§13.4)
    substitution_permitted BOOLEAN NOT NULL DEFAULT TRUE,
    indication        VARCHAR(512),
    current_version_id VARCHAR(26),                    -- the only claimable version (RC-7)
    lifecycle_reason  VARCHAR(512),
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_oros_rx_tenant_patient ON oros_prescriptions(tenant_id, patient_cpid);
CREATE INDEX idx_oros_rx_tenant_status ON oros_prescriptions(tenant_id, status);
CREATE INDEX idx_oros_rx_source_ref ON oros_prescriptions(tenant_id, source_ref)
    WHERE source_ref IS NOT NULL;

CREATE TABLE oros_prescription_versions (
    prescription_version_id VARCHAR(26) PRIMARY KEY,   -- ULID (§5 PrescriptionVersionId)
    prescription_id   VARCHAR(26) NOT NULL REFERENCES oros_prescriptions(prescription_id),
    tenant_id         UUID        NOT NULL,
    version           INT         NOT NULL,
    supersedes_version_id VARCHAR(26),
    content_json      JSONB       NOT NULL,             -- canonical serialisation (signing input basis)
    content_hash      VARCHAR(64) NOT NULL,             -- SHA-256 of the canonical form
    signature_jws     TEXT,                             -- detached JWS via tshepo-keys (§8.2.1)
    signature_key_id  VARCHAR(128),
    signed_by         VARCHAR(128),
    signed_at         TIMESTAMPTZ,
    reason            VARCHAR(512),
    created_by        VARCHAR(128),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_oros_rx_versions UNIQUE (prescription_id, version)
);

CREATE INDEX idx_oros_rx_versions_rx ON oros_prescription_versions(prescription_id);

CREATE TABLE oros_prescription_items (
    item_id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    prescription_version_id VARCHAR(26) NOT NULL
        REFERENCES oros_prescription_versions(prescription_version_id),
    medication_code   VARCHAR(64) NOT NULL,             -- coded, never free-text product (§8.1)
    coding_system     VARCHAR(128) NOT NULL DEFAULT 'http://www.whocc.no/atc',
    display_name      VARCHAR(255),
    dose              VARCHAR(64),
    route             VARCHAR(64),
    frequency         VARCHAR(64),
    duration_days     INT,
    quantity          NUMERIC(12,3),
    quantity_unit     VARCHAR(32),
    instructions      TEXT,
    controlled        BOOLEAN NOT NULL DEFAULT FALSE,
    substitution_permitted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_oros_rx_items_version ON oros_prescription_items(prescription_version_id);

CREATE TABLE oros_prescription_tokens (
    token_id          VARCHAR(26) PRIMARY KEY,          -- ULID; opaque reference (§13.2.2)
    prescription_id   VARCHAR(26) NOT NULL REFERENCES oros_prescriptions(prescription_id),
    prescription_version_id VARCHAR(26) NOT NULL,
    tenant_id         UUID        NOT NULL,
    status            VARCHAR(20) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | REVOKED | EXPIRED
    token_jws         TEXT        NOT NULL,             -- compact JWS; QR carries this and nothing else
    key_id            VARCHAR(128),
    issued_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    revoke_reason     VARCHAR(255)
);

-- Single-active invariant (§13.2.1): at most one claimable token per prescription.
CREATE UNIQUE INDEX uq_oros_rx_token_single_active
    ON oros_prescription_tokens(prescription_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_oros_rx_tokens_rx ON oros_prescription_tokens(prescription_id);

CREATE TABLE oros_prescription_claims (
    claim_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id         UUID        NOT NULL,
    token_id          VARCHAR(26) NOT NULL,
    prescription_id   VARCHAR(26) NOT NULL,
    prescription_version_id VARCHAR(26) NOT NULL,
    claimed_by_actor  VARCHAR(128),
    claimed_by_facility UUID,
    vendor_ref        VARCHAR(128),
    dispense_episode_ref VARCHAR(128),                  -- bound by pharmacy intake (OF-B12)
    status            VARCHAR(20) NOT NULL DEFAULT 'CLAIMED', -- CLAIMED | RELEASED
    idempotency_key   VARCHAR(128),
    claimed_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    released_at       TIMESTAMPTZ,
    release_reason    VARCHAR(512)
);

CREATE INDEX idx_oros_rx_claims_rx ON oros_prescription_claims(prescription_id);
-- Claim idempotency (§8.9 step 6): a retried claim with the same key returns the first result.
CREATE UNIQUE INDEX uq_oros_rx_claims_idem
    ON oros_prescription_claims(tenant_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
