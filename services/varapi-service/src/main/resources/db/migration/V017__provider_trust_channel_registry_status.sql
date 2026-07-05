-- =============================================================================
-- Varapi V017 — Provider trust level, onboarding channel, registry status
-- (IATG Wave 1, all additive)
--
-- Adds three nullable columns to varapi.provider:
--   * trust_level         — ProviderTrustLevel ladder (monotonic; service-governed)
--   * onboarding_channel  — OnboardingChannel (provenance, not trust)
--   * registry_status     — ProviderRegistryStatus: the HONEST registry answer.
--     Additive FIFTH status axis; the legacy four axes (status, licence_status,
--     professional_standing_status, active_flag) are untouched here and are
--     consolidated in Wave 2.
--
-- No existing column is renamed, dropped, retyped, or overwritten. External
-- provider (collaboration) tables are untouched.
-- =============================================================================

ALTER TABLE varapi.provider
    ADD COLUMN IF NOT EXISTS trust_level        VARCHAR(40),
    ADD COLUMN IF NOT EXISTS onboarding_channel VARCHAR(20),
    ADD COLUMN IF NOT EXISTS registry_status    VARCHAR(40);

COMMENT ON COLUMN varapi.provider.trust_level IS
    'ProviderTrustLevel: SELF_ASSERTED | DOCUMENT_SUPPORTED | EMPLOYMENT_MATCHED | COUNCIL_VERIFIED | FULLY_LINKED_OR_ONBOARDED. Monotonic; transitions only via ProviderTrustService with evidence.';
COMMENT ON COLUMN varapi.provider.onboarding_channel IS
    'OnboardingChannel: REGULATORY_A | WORKFORCE_B | ORGANIZATION_C | SELF. Provenance of the profile, never a trust signal by itself.';
COMMENT ON COLUMN varapi.provider.registry_status IS
    'ProviderRegistryStatus: the honest registry answer (REGISTERED_ACTIVE, REGISTERED_EXPIRED, SUSPENDED, DEREGISTERED, DECEASED, PENDING_VERIFICATION, CONFLICT, MATCHED_EMPLOYMENT_ONLY, MATCHED_BOTH). Additive fifth axis; legacy four axes consolidated in Wave 2.';

-- ── Backfill ─────────────────────────────────────────────────────────────────

-- Every provider without an evidence-backed trust position starts honest: SELF_ASSERTED.
UPDATE varapi.provider
   SET trust_level = 'SELF_ASSERTED'
 WHERE trust_level IS NULL;

-- Channel from bootstrap provenance (mirrors OnboardingChannel.fromBootstrapOrigin):
--   BULK_PRELOAD    -> WORKFORCE_B
--   COUNCIL_IMPORT  -> REGULATORY_A
--   SELF_REGISTERED -> SELF
UPDATE varapi.provider
   SET onboarding_channel = CASE bootstrap_origin
                                WHEN 'BULK_PRELOAD'    THEN 'WORKFORCE_B'
                                WHEN 'COUNCIL_IMPORT'  THEN 'REGULATORY_A'
                                WHEN 'SELF_REGISTERED' THEN 'SELF'
                            END
 WHERE onboarding_channel IS NULL
   AND bootstrap_origin IN ('BULK_PRELOAD', 'COUNCIL_IMPORT', 'SELF_REGISTERED');

-- Profiles still in a pre-verification lifecycle get the honest answer
-- PENDING_VERIFICATION; everything else stays NULL until evidence arrives
-- (no invented certainty).
UPDATE varapi.provider
   SET registry_status = 'PENDING_VERIFICATION'
 WHERE registry_status IS NULL
   AND lifecycle_status IN ('PRELOADED', 'UNDER_VERIFICATION');

CREATE INDEX IF NOT EXISTS idx_provider_trust_level
    ON varapi.provider (tenant_id, trust_level);
CREATE INDEX IF NOT EXISTS idx_provider_registry_status
    ON varapi.provider (tenant_id, registry_status);
