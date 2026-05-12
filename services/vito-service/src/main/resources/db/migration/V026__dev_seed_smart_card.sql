-- =============================================================================
-- VITO — Dev seed: one smart card in REQUESTED state for Tatenda Moyo
-- Card ID will be 1 on a fresh database; use this ID when testing print jobs.
-- Safe to run in any environment: skipped if the client record does not exist
-- and idempotent (ON CONFLICT DO NOTHING on the unique card_number per tenant).
-- =============================================================================

INSERT INTO vito.smart_card
    (tenant_id, card_number, health_id, did_uri, public_key,
     status, requested_by, expires_at)
SELECT
    '00000000-0000-4000-8000-000000000001',
    'CARD-DEV-001',
    'b0000000-0000-4000-8000-000000000001',
    'did:impilo:dev:b000000000004000800000000000001',
    '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VdAyEADEVELOPMENTKEYNOTFORPRODUCTIONUSE0000000000
-----END PUBLIC KEY-----',
    'REQUESTED',
    'system-seed',
    NOW() + INTERVAL '5 years'
WHERE EXISTS (
    SELECT 1 FROM vito.client
    WHERE health_id = 'b0000000-0000-4000-8000-000000000001'
)
ON CONFLICT (tenant_id, card_number) DO NOTHING;
