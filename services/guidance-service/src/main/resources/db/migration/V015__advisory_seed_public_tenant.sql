-- =============================================================================
-- guidance-service V015 — re-tenant the seeded launch advisory to the public tenant.
--
-- V014 seeded the "Help us build the future of health" advisory under tenant
-- 'national-spine', but the public gateway (PublicGatewayAnonymousDefaultsFilter)
-- resolves anonymous requests under PUBLIC_DEFAULT_TENANT
-- '00000000-0000-0000-0000-000000000001'. So the advisory never matched public
-- resolves and the banner was empty. Align any national-spine advisory to the
-- public tenant so the public advisory lane returns it. Idempotent / forward-only.
-- =============================================================================

UPDATE guidance.service_advisory
   SET tenant_id = '00000000-0000-0000-0000-000000000001'
 WHERE tenant_id = 'national-spine';
