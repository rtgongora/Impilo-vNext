-- =============================================================================
-- Varapi V010 — Impilo ID (Health ID) as canonical person anchor + linked identifiers
-- Experience Doctrine: provider profiles are anchored to impilo_health_id; domain
-- identifiers (national ID, council reg, Fundo, MusheX, etc.) live in provider_identifiers.
-- =============================================================================

-- 1) Anchor provider rows to the longitudinal Impilo / Health identity (UUID).
ALTER TABLE varapi.provider
    ADD COLUMN IF NOT EXISTS impilo_health_id UUID;

UPDATE varapi.provider
SET impilo_health_id = provider_ref
WHERE impilo_health_id IS NULL;

ALTER TABLE varapi.provider
    ALTER COLUMN impilo_health_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_provider_tenant_impilo_health
    ON varapi.provider (tenant_id, impilo_health_id);

COMMENT ON COLUMN varapi.provider.impilo_health_id IS
    'Canonical Impilo / Health person identity (UUID). Registry provider profile is anchored here; not a competing primary key.';

-- 2) Extend linked-identifier model for type, verification, primacy, effective dating.
ALTER TABLE varapi.provider_identifiers
    ADD COLUMN IF NOT EXISTS identifier_type VARCHAR(80) NOT NULL DEFAULT 'LEGACY',
    ADD COLUMN IF NOT EXISTS verification_state VARCHAR(40) NOT NULL DEFAULT 'UNVERIFIED',
    ADD COLUMN IF NOT EXISTS is_primary BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS effective_from DATE,
    ADD COLUMN IF NOT EXISTS effective_to DATE,
    ADD COLUMN IF NOT EXISTS metadata JSONB;

COMMENT ON COLUMN varapi.provider_identifiers.identifier_system IS
    'Issuing or namespacing authority (e.g. MDPCZ_REG, FUNDO, MUSHEX, EMPLOYER_HR).';
COMMENT ON COLUMN varapi.provider_identifiers.identifier_type IS
    'Semantic type: NATIONAL_ID, COUNCIL_REGISTRATION, PROVIDER_PUBLIC_ID, FUNDO_USER, MUSHEX_PARTY, EMPLOYEE_NUMBER, etc.';
COMMENT ON COLUMN varapi.provider_identifiers.verification_state IS
    'UNVERIFIED | PENDING | VERIFIED | REJECTED — governance state for this linked identifier.';

-- 3) Bootstrap linked rows from legacy scalar columns (idempotent inserts).
INSERT INTO varapi.provider_identifiers (
    provider_id, tenant_id, identifier_system, identifier_type, identifier_value,
    verification_state, is_primary, status, issued_date, expiry_date, created_at
)
SELECT p.id,
       p.tenant_id,
       'ZW_NATIONAL_ID',
       'NATIONAL_ID',
       p.national_id,
       'UNVERIFIED',
       false,
       'ACTIVE',
       NULL,
       NULL,
       now()
FROM varapi.provider p
WHERE p.national_id IS NOT NULL
  AND btrim(p.national_id) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM varapi.provider_identifiers x
        WHERE x.provider_id = p.id
          AND x.identifier_system = 'ZW_NATIONAL_ID'
    )
ON CONFLICT (identifier_system, identifier_value) DO NOTHING;

INSERT INTO varapi.provider_identifiers (
    provider_id, tenant_id, identifier_system, identifier_type, identifier_value,
    verification_state, is_primary, status, issued_date, expiry_date, created_at
)
SELECT p.id,
       p.tenant_id,
       'VARAPI_PRACTICE_NUMBER',
       'PRACTICE_NUMBER',
       p.practice_number,
       'UNVERIFIED',
       false,
       'ACTIVE',
       NULL,
       NULL,
       now()
FROM varapi.provider p
WHERE p.practice_number IS NOT NULL
  AND btrim(p.practice_number) <> ''
  AND NOT EXISTS (
        SELECT 1 FROM varapi.provider_identifiers x
        WHERE x.provider_id = p.id
          AND x.identifier_system = 'VARAPI_PRACTICE_NUMBER'
    )
ON CONFLICT (identifier_system, identifier_value) DO NOTHING;
