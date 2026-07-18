-- Identity Contract §7 / Wave B4 (preview reseed): seed the HID↔CPID mapping for
-- the dev-seed VITO client so banner (HID→VITO) + clinical (CPID→PCT/BUTANO)
-- split retrieval works out of the box after the CPID decoupling cutover.
--
-- The CPID is a fixed, obviously-synthetic UUID that is intentionally DIFFERENT
-- from the health_id — seeded data must never exhibit the retired cpid==health_id
-- equality, or the estate audit (scripts/identity/audit-no-healthid-in-clinical.sh)
-- and journey 12 of the identity pack would pass vacuously.
INSERT INTO tshepo_identity.id_mapping (tenant_id, health_id, cpid, mapping_status, created_at)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'b0000000-0000-4000-8000-000000000001',  -- VITO V026 dev-seed client
    'c0000000-0000-4000-8000-000000000001',  -- seed CPID (distinct by construction)
    'ACTIVE',
    now()
)
ON CONFLICT (tenant_id, health_id) DO NOTHING;
