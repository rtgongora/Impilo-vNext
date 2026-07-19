-- =============================================================================
-- Varapi V024 — Provider authorization link (Provider+Place identity program W1, D-P1)
--
-- The authoritative HID <-> Provider ID binding record. Until now the binding
-- was two columns on varapi.provider (impilo_health_id — SYNTHETIC for
-- preloaded skeletons since V010 backfilled it from provider_ref — and
-- claimed_health_id, added V016). A column cannot carry binding history,
-- evidence, or an assurance outcome, and cannot serve dispute (PJ18) or
-- recover-not-reissue (PJ17) journeys. This table mirrors the client-side
-- vito.client_authorization_link pattern.
--
-- claimed_health_id stays as a denormalized current-binding pointer for cheap
-- reads; every bind/rebind now ALSO writes an authorization-link row in the
-- same transaction. Exactly one ACTIVE link per provider (partial unique).
-- =============================================================================

CREATE TABLE IF NOT EXISTS varapi.provider_authorization_link (
    link_id             UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    provider_id         BIGINT          NOT NULL REFERENCES varapi.provider (id),
    provider_public_id  VARCHAR(40)     NOT NULL,
    impilo_health_id    UUID            NOT NULL,
    -- CLAIM | RECOVERY | ADJUDICATED_REBIND | GOVERNANCE_UNBIND | LEGACY_SEED
    link_type           VARCHAR(32)     NOT NULL,
    -- ACTIVE | SUPERSEDED | REVOKED
    status              VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    -- What proved this binding: claim-token ref, recovery-token ref,
    -- adjudication case id, migration marker. Never a secret value.
    evidence_ref        VARCHAR(255),
    -- Assurance outcome ladder from the Identity Journey Doctrine §4
    -- (SELF_ASSERTED / RECORD_LINKED / DOCUMENT_VERIFIED / ...).
    assurance_outcome   VARCHAR(40),
    proofing_channel    VARCHAR(40),
    bound_at            TIMESTAMPTZ     NOT NULL DEFAULT now(),
    superseded_at       TIMESTAMPTZ,
    actor_ref           VARCHAR(80),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now()
);

COMMENT ON TABLE varapi.provider_authorization_link IS
    'Authoritative, append-history HID<->ProviderID binding records (D-P1). One ACTIVE row per provider; rebinds supersede, never overwrite.';

-- Exactly one ACTIVE binding per provider.
CREATE UNIQUE INDEX IF NOT EXISTS ux_provider_auth_link_active
    ON varapi.provider_authorization_link (tenant_id, provider_id)
    WHERE status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_provider_auth_link_health_id
    ON varapi.provider_authorization_link (tenant_id, impilo_health_id);

-- Seed ACTIVE links from the existing denormalized binding. claimed_health_id
-- is the honest person anchor (impilo_health_id is synthetic for unclaimed
-- preloads, so unclaimed skeletons correctly get NO link row).
INSERT INTO varapi.provider_authorization_link
    (link_id, tenant_id, provider_id, provider_public_id, impilo_health_id,
     link_type, status, evidence_ref, assurance_outcome, proofing_channel, bound_at, actor_ref)
SELECT
    gen_random_uuid(), p.tenant_id, p.id, p.provider_public_id, p.claimed_health_id,
    'LEGACY_SEED', 'ACTIVE', 'migration:V024', 'RECORD_LINKED', 'BOOTSTRAP_CLAIM',
    COALESCE(p.claimed_at, now()), 'migration:V024'
FROM varapi.provider p
WHERE p.claimed_health_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM varapi.provider_authorization_link l
      WHERE l.tenant_id = p.tenant_id AND l.provider_id = p.id AND l.status = 'ACTIVE');
