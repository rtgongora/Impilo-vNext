-- FCV-W2 — the Ministry as an organisation, and the roles that may verify a facility.
--
-- Until now org-registry held the nine professional regulators (V007) but not the Ministry itself,
-- so there was no organisation a MoHCC facility verifier could hold an appointment at, and
-- therefore no governed way to say "this person may verify facilities in Buhera district".
-- Facility verification decisions were gated only on a client-supplied X-Actor-Type header.
--
-- One organisation, not sixty-four. Provincial and district authority is carried by the
-- appointment's jurisdiction_code, not by modelling every province and district as its own
-- organisation: a district health executive officer is MoHCC staff acting within a district, not
-- an employee of a separate legal entity. This also keeps the existing org-scoped session, the
-- expiry sweep and the invitation rail working unchanged.

INSERT INTO org_registry.org_registry_organization
    (id, tenant_id, code, legal_name, org_type, status, verification_status, source) VALUES
    ('a6000000-0000-4000-8000-000000000001'::uuid, '00000000-0000-0000-0000-000000000001'::uuid,
     'MOHCC', 'Ministry of Health and Child Care', 'PUBLIC_HEALTH_AUTHORITY', 'ACTIVE', 'VERIFIED', 'NATIVE')
ON CONFLICT (id) DO NOTHING;

-- ── Verifier roles ────────────────────────────────────────────────────────────────────────────
--
-- These are facility-registry roles, distinct from the professional-regulation roles seeded in
-- V006/V009. `is_administrative` is false: verifying a facility is an operational duty, not
-- administration of the organisation itself, so these roles do not count toward the
-- two-administrator succession rule and cannot administer MoHCC's own workspace.
--
-- jurisdiction_code convention (carried on the appointment, not on the role):
--   NATIONAL                  — the whole country
--   PROVINCE:<PROVINCE NAME>  — e.g. PROVINCE:MANICALAND
--   DISTRICT:<DISTRICT NAME>  — e.g. DISTRICT:BUHERA
-- Matching a jurisdiction to a facility is done in tuso against the facility's own province and
-- district, because the policy engine can only compare a duty jurisdiction against a fixed list —
-- it cannot express "matches THIS facility's district".

INSERT INTO org_registry.org_registry_appointment_role
    (role_code, label, description, is_committee_member, is_oversight, sort_order,
     is_administrative, is_bootstrap_only, max_concurrent_per_org) VALUES

    ('MOHCC_NATIONAL_VERIFIER', 'MoHCC National Facility Verifier',
     'Issues the Ministry legitimacy verdict for any facility in the country. Held by national '
     'registry staff; a national jurisdiction satisfies any province or district.',
     FALSE, FALSE, 200, FALSE, FALSE, NULL),

    ('MOHCC_PROVINCIAL_VERIFIER', 'MoHCC Provincial Facility Verifier',
     'Issues the Ministry legitimacy verdict for facilities within the appointed province. A '
     'provincial officer cannot decide on a facility outside their province.',
     FALSE, FALSE, 201, FALSE, FALSE, NULL),

    ('MOHCC_DISTRICT_VERIFIER', 'MoHCC District Facility Verifier',
     'Issues the Ministry legitimacy verdict for facilities within the appointed district. The '
     'narrowest verifying authority, and the one closest to the facility.',
     FALSE, FALSE, 202, FALSE, FALSE, NULL),

    ('MOHCC_REGISTRY_ADMINISTRATOR', 'MoHCC Facility Registry Administrator',
     'Administers the national facility registry: reviews claims, runs governed imports and '
     'assertion batches, and manages verifier appointments. Does NOT issue legitimacy verdicts — '
     'administering the registry and deciding a facility is legitimate are separate duties.',
     FALSE, FALSE, 203, TRUE, FALSE, NULL)

ON CONFLICT (role_code) DO NOTHING;
