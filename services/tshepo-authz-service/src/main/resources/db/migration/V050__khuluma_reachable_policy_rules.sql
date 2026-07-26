-- =====================================================================================
-- TSHEPO-authz :: V050 — make the Khuluma comms rules reachable
--
-- V017's six rules were dead on TWO independent axes, so Khuluma has been denied
-- (NO_MATCHING_RULES) for its whole life:
--
--   1. purpose — 'CARE_COORDINATION' / 'PERSONAL_CARE' were outside PurposeOfUse.
--      Fixed by extending the enum + V049.
--   2. resource_type — the rules carry invented values ('khuluma-conversation',
--      'khuluma-call', 'khuluma-patient-link') that the PDP never produces.
--      AuthzInternalRequest.deriveResourceType returns the LAST PATH SEGMENT, so
--      /internal/v1/khuluma/conversations yields 'conversations', and
--      /internal/v1/khuluma/conversations/{uuid}/links yields 'links'. V017 also set
--      no path_contains, so nothing pinned the rules to Khuluma either.
--
-- This migration fixes axis 2 and therefore ACTIVATES access that has never been live.
-- What follows is the reasoning for why that is safe, and where the real gate lives.
--
-- WHY A PATH PIN RATHER THAN ENUMERATED resource_type VALUES
-- The hub exposes ~40 endpoints, whose derived resource types include 'conversations',
-- 'messages', 'read', 'calls', 'incoming', 'accept', 'decline', 'signals', 'lobby',
-- 'cohosts', 'reactions'... Enumerating them would re-create the exact failure mode this
-- migration exists to fix: every new endpoint would silently fall outside the seed and be
-- denied, with nothing to say so. resource_type = NULL is a verified wildcard
-- (PolicyCacheService.getActiveRulesForResource), pinned by a segment-bounded
-- path_contains — the idiom V021/V022/V026 already use. It survives endpoint drift.
--
-- WHY purpose IS LEFT NULL (WILDCARD)
-- The shell's api-client derives purpose from work mode only — it emits TREATMENT for
-- /khuluma, never CARE_COORDINATION or SELF_SERVICE. Pinning purpose here would leave the
-- rules just as dead as before, in a way no test would catch. Same stance, and same
-- wording, as V021/V022/V026: RBAC now, purpose tightening once the shell can emit a
-- contextual purpose per route. The rules stay tight via actor_type + the path pin.
--
-- WHERE THE PATIENT-LINK CLINICAL GATE ACTUALLY LIVES
-- Binding a conversation to a patient is a regulated clinical action. The PDP cannot
-- express "allow the comms surface EXCEPT this sub-path" — path_contains is
-- segment-bounded containment, so any pin covering /khuluma/conversations necessarily
-- covers /khuluma/conversations/{uuid}/links, and a DENY row would block clinicians too
-- (there is no role-negation condition). Seeding a patient-link rule here would therefore
-- be theatre: it would add no enforcement.
--
-- The gate that DOES enforce it is KhulumaAccessPolicyService.requireClinicalActorForPatientLink()
-- in the experience BFF, invoked directly by KhulumaBffController.linkPatient. That check is
-- live today and is unchanged by this migration.
--
-- Note for whoever picks this up: infra/opa/impilo/khuluma.rego is NOT in the decision
-- path. AuthzProperties.opaMode defaults to OFF, PolicyEngine.shadowCompareOpa is
-- shadow-only and never authoritative, and the input it builds carries no `action` in the
-- rego's semantic form ("comms.conversation.link_patient") — the PDP derives action as
-- "METHOD:/path". So the rego's deny rules enforce nothing, despite its header comment.
-- That leaves the BFF check as the single live control on patient-link. Worth hardening,
-- but it is a Khuluma/OPA-wiring concern, not a seed fix.
--
-- SCOPE — deliberately matches V017's stated intent, no wider. Citizen and provider comms
-- only. The ops-admin surfaces (/escalations, /delivery/adapters, /on-call) and the
-- channels/meetings surfaces get NO grant here and remain denied; that breadth is the
-- Khuluma lane's work. facility_scope stays false: facility_scope=true would require an
-- X-Facility-ID on every request and silently drop providers who have no facility selected
-- — another dead-rule trap. The rego's real provider condition is an active organisation
-- assignment, which policy_rule cannot express and the service enforces.
-- =====================================================================================

-- The six V017 rules can never match; retire them rather than leaving dead rows behind.
UPDATE tshepo_authz.policy_rule
   SET active     = false,
       updated_at = now()
 WHERE name IN (
        'khuluma-comms-citizen',
        'khuluma-call-citizen',
        'khuluma-comms-provider',
        'khuluma-call-provider',
        'khuluma-patient-link-clinician',
        'khuluma-patient-link-nurse');

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- Citizens may use the comms surface in their own person context. Subject binding (a citizen
-- sees only their own conversations) is enforced in khuluma-service, not here.
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-comms-citizen-surface',
    'CITIZEN: Khuluma comms surface (conversations, messages, calls). Pinned to /khuluma/; '
        || 'subject binding in-service. Replaces the unreachable khuluma-comms-citizen (V017).',
    'CITIZEN', NULL, NULL, NULL, NULL,
    false, false, 'ALLOW', 70, '{"path_contains": "/khuluma/"}', true
),
-- Providers may use the comms surface. The active-organisation-assignment condition from
-- khuluma.rego is not expressible as a policy_rule dimension and is enforced in-service.
(
    '00000000-0000-0000-0000-000000000001'::uuid,
    'khuluma-comms-provider-surface',
    'PROVIDER: Khuluma comms surface (conversations, messages, calls). Pinned to /khuluma/; '
        || 'organisation binding in-service. Patient-link stays gated by the BFF clinical check. '
        || 'Replaces the unreachable khuluma-comms-provider (V017).',
    'PROVIDER', NULL, NULL, NULL, NULL,
    false, false, 'ALLOW', 71, '{"path_contains": "/khuluma/"}', true
);
