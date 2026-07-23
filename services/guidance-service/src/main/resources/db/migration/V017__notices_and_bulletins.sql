-- Notices & Bulletins (PF-W2): generalize the service-advisory engine into a browsable
-- public notices surface. A category discriminates the notice types; existing service
-- advisories keep SERVICE_STATUS. SAFETY_RECALL and REGULATORY_NOTICE require an approver
-- before publication (enforced in AdvisoryAdminService) — the legal guardrail for
-- enforcement notices: only regulator-authored, publish-approved notices reach the public
-- lane, never a back-door of internal disciplinary case data.

ALTER TABLE guidance.service_advisory
    ADD COLUMN IF NOT EXISTS category VARCHAR(32) NOT NULL DEFAULT 'SERVICE_STATUS';

CREATE INDEX IF NOT EXISTS idx_service_advisory_category
    ON guidance.service_advisory (tenant_id, category, status);

-- Seed one published example per new category (national-spine public audience) so the
-- /welcome/notices archive is populated on first boot. Bodies are illustrative public
-- copy; real notices are authored by the owning domains.
INSERT INTO guidance.service_advisory
    (id, tenant_id, category, title, body, severity, variant, status, targeting, starts_at, expires_at, primary_actions, created_by, approved_by, created_at, updated_at)
VALUES
    (gen_random_uuid(), 'national-spine', 'PUBLIC_HEALTH_CAMPAIGN',
     'Measles catch-up immunisation — bring children under 5',
     'A national measles catch-up campaign is running at all public clinics. Children under five can be vaccinated free of charge. No appointment or sign-in is needed.',
     'SERVICE_NOTICE', 'BANNER', 'PUBLISHED', '{"audience":"public"}'::jsonb,
     now() - interval '2 days', now() + interval '60 days',
     '[{"label":"Find a clinic","href":"/welcome/find-care?service=IMMUNISATION"}]'::jsonb,
     'campaigns-service', 'moh-public-health', now(), now()),

    (gen_random_uuid(), 'national-spine', 'SAFETY_RECALL',
     'Product recall — check batch numbers on paracetamol syrup',
     'A batch of paracetamol syrup has been recalled. Do not use affected batches; return them to any pharmacy. Verify a product or report a concern through Impilo.',
     'IMPORTANT_DISRUPTION', 'BANNER', 'PUBLISHED', '{"audience":"public"}'::jsonb,
     now() - interval '1 day', now() + interval '30 days',
     '[{"label":"Report a concern","href":"/welcome/report"}]'::jsonb,
     'patient-safety-service', 'moh-medicines-control', now(), now()),

    (gen_random_uuid(), 'national-spine', 'DISASTER_ALERT',
     'Seasonal flooding — health service disruptions in affected districts',
     'Some facilities in flood-affected districts may have reduced services. Check facility status before travelling, and call emergency services for life-threatening situations.',
     'IMPORTANT_DISRUPTION', 'BANNER', 'PUBLISHED', '{"audience":"public"}'::jsonb,
     now() - interval '3 hours', now() + interval '14 days',
     '[{"label":"Check facility status","href":"/welcome/find-care"},{"label":"Emergency help","href":"/welcome/emergency"}]'::jsonb,
     'daidzai-service', 'moh-emergency-ops', now(), now()),

    (gen_random_uuid(), 'national-spine', 'REGULATORY_NOTICE',
     'Public notice — verify professional registration before treatment',
     'The public can verify any health professional''s registration status before receiving care. Practising without valid registration is unlawful; verify or report through Impilo.',
     'SERVICE_NOTICE', 'INLINE', 'PUBLISHED', '{"audience":"public"}'::jsonb,
     now() - interval '5 days', now() + interval '180 days',
     '[{"label":"Verify a professional","href":"/verify/practitioner"}]'::jsonb,
     'varapi-service', 'moh-registrar', now(), now()),

    (gen_random_uuid(), 'national-spine', 'PROCUREMENT_NOTICE',
     'Tender notice — supply of medical consumables (national)',
     'A public tender for the supply of medical consumables is open. Approved suppliers may view requirements and submit through the supplier portal after signing in.',
     'INFORMATIONAL', 'INLINE', 'PUBLISHED', '{"audience":"public"}'::jsonb,
     now() - interval '4 days', now() + interval '21 days',
     '[{"label":"Supplier sign-in","href":"/auth/login?returnTo=%2Fmarketplace"}]'::jsonb,
     'procurement-service', 'moh-procurement', now(), now());
