-- =====================================================================================
-- TSHEPO-authz :: V052 — the regulator administration roles, and the country root's limits
--
-- Companion to org-registry V009, which established administration as a flagged class of
-- appointment role. This maps those roles into the policy plane and, more importantly, states what
-- the platform's root trust function may NOT do.
--
-- WHY THE COUNTRY ROOT IS A DENY AND NOT AN ABSENCE.
--
-- NATIONAL_TRUST_ADMINISTRATOR activates regulators and adjudicates bootstrap requests. It is a
-- trust function, not a superuser, and must never read a council's case data. It is tempting to
-- rely on fail-closed — grant nothing and the absence does the work — but absence is fragile in
-- exactly the situation that matters: the country root is the role most likely to be handed a
-- broad administrative ALLOW by some later migration written by someone who has not read this
-- file. An ALLOW added later silently out-ranks an absence. It cannot out-rank a DENY, because
-- PolicyEngine returns on the first matching DENY before any ALLOW is considered.
--
-- So the limit is written down as a rule rather than left as a gap.
--
-- WHY THESE PATHS AND NOT A BROAD PREFIX.
--
-- Every condition key used here is one PolicyEngine actually implements (`path_contains`).
-- That matters more than it looks: evaluateConditions checks the keys it knows and then returns
-- true, so an UNRECOGNISED key is silently ignored rather than failing the match. A DENY carrying
-- an unimplemented key therefore does not sit inert — it fires on whatever remains, which for a
-- coarse prefix means denying far more than intended. These rules name the case-data lanes
-- exactly, so the deny cannot widen into the oversight reads the same role legitimately needs.
--
-- Seeded ACTIVE, deliberately, unlike the V045–V047 shadow rows. A DENY can only ever reduce what
-- an actor may do: it cannot grant anything, and it cannot break a caller who was already being
-- refused. There is nothing to soak — and a protective rule that ships inactive is a protection
-- nobody has.
-- =====================================================================================

-- ---- Administration roles → canonical policy roles --------------------------------------
-- The WORK_CONTEXT token carries the appointment's role_code as its role template; the catalog
-- (V043) resolves that to canonical role(s), folded additively into the effective role set. Named
-- one-to-one here: an administration role is its own canonical role, so a policy can speak about
-- "the security administrator" without inferring it from a string prefix.

INSERT INTO tshepo_authz.role_template_catalog (tenant_id, role_template_id, canonical_role)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'FOUNDING_REGULATOR_ADMINISTRATOR',  'FOUNDING_REGULATOR_ADMINISTRATOR'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_BUSINESS_OWNER',          'REGULATOR_BUSINESS_OWNER'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_SECURITY_ADMINISTRATOR',  'REGULATOR_SECURITY_ADMINISTRATOR'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_WORKSPACE_ADMINISTRATOR', 'REGULATOR_WORKSPACE_ADMINISTRATOR'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_RECORDS_ADMINISTRATOR',   'REGULATOR_RECORDS_ADMINISTRATOR'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'REGULATOR_PROCESS_OWNER',           'REGULATOR_PROCESS_OWNER'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'NATIONAL_TRUST_ADMINISTRATOR',      'NATIONAL_TRUST_ADMINISTRATOR')
ON CONFLICT DO NOTHING;

-- ---- The country root may not read a regulator's case data ------------------------------
-- One rule per case-data lane, each pinned to a path PolicyEngine can actually match. Priority is
-- high so these are reached early; DENY-wins means the first match returns without consulting any
-- ALLOW.

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-disciplinary-cases',
 'The country root is a trust function, not a case reader: disciplinary proceedings belong to the '
 'council conducting them.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/providers/disciplinary-cases"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-disciplinary-lane',
 'The country root may not enter the regulatory disciplinary lane.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/regulatory/disciplinary"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-hearings',
 'Committee hearings are docket-scoped to their members; the country root is not a member.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/regulatory/hearings"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-provider-applications',
 'A registration application is a person''s file with their council, not platform-trust business.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/providers/applications"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-application-correspondence',
 'Correspondence on a regulatory application — including internal assessment notes — is closed to '
 'the country root.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/regulatory/applications"}', true),

('00000000-0000-0000-0000-000000000001'::uuid, 'national-trust-no-facility-verification-cases',
 'Facility verification cases belong to the regulator running them.',
 NULL, 'NATIONAL_TRUST_ADMINISTRATOR', NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": "/verification-cases"}', true)

ON CONFLICT DO NOTHING;

-- NOTE deliberately absent:
--   * no ALLOW for any of the seven roles. What an administrator may DO is the next wave's work
--     (the bootstrap rail and the user-administration surface); granting broadly now and trimming
--     later would ship a privileged class before the thing that governs it exists. Fail-closed
--     means the absence is safe in the meantime — which is precisely why the country root's DENY
--     is the one thing that could NOT wait: it is the rule that survives a later careless ALLOW.
--   * no jurisdiction condition. `allowed_jurisdictions` is not implemented in
--     PolicyEngine.evaluateConditions, and an unimplemented key is silently IGNORED rather than
--     failing closed — so seeding a jurisdiction-scoped rule today would produce a rule that
--     matches on everything else it names and quietly ignores the jurisdiction. It lands with the
--     engine change, through the CZO channel, or not at all.
