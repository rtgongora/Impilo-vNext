-- ============================================================================
-- V026 — Assets / Devices / Equipment & IoT policy rules (WS#5 SYS-1 enforcement)
-- Enforces the `impilo.assets` OPA matrix (infra/opa/impilo/assets.rego) at the
-- ext_authz gateway DB-rule PDP (the LIVE PDP; OPA is shadow-only). Before this,
-- the asset-registry-service /internal/v1/equipment/** and /internal/v1/assets/**
-- routes had NO matching policy_rule -> the PDP fail-closed (NO_MATCHING_RULES /
-- NO_ALLOW_RULE -> DENY) for every user purpose, so the whole asset/equipment
-- journey was never live through the gateway. These rules are STRICTLY ADDITIVE
-- (they only enable; uncovered routes stay deny-by-default; no regression).
--
-- TAXONOMY: the OPA `role_template`s reconcile to canonical realm roles. The realm
-- has FACILITY_ADMIN / ADMIN / SYSTEM_ADMIN already; the asset operational cadres
-- (biomedical engineer, technician, field officer) have no realm role yet, so —
-- exactly as V021 introduced REGULATOR — this introduces three new realm roles:
--   asset_facility_admin / asset_manager -> FACILITY_ADMIN
--   platform_admin                       -> ADMIN, SYSTEM_ADMIN
--   asset_biomedical_engineer            -> BIOMEDICAL_ENGINEER   (NEW realm role)
--   asset_technician                     -> ASSET_TECHNICIAN      (NEW realm role)
--   asset_field_officer                  -> ASSET_FIELD_OFFICER   (NEW realm role)
-- (Coordination item: add BIOMEDICAL_ENGINEER / ASSET_TECHNICIAN / ASSET_FIELD_OFFICER
--  to infra/keycloak/realm-impilo-production.json + contracts/trust/seeds — single-writer files.)
--
-- MECHANICS (mirrors V021/V022): resource_type/action/purpose = NULL wildcards,
-- each rule PINNED by `path_contains` to the asset service path so a generic last
-- path segment (e.g. `status`, `alerts`, `audits`, `transfers`) that COLLIDES across
-- services is not over-granted (the V018 collision lesson; see PolicyEngine
-- .pathContainsSegment + DENY-wins via effect DESC ordering). purpose=NULL (RBAC now;
-- purpose tightening later). facility_scope=false.
--
-- SCOPE: this seeds the gateway RBAC dimensions of the OPA matrix — operational-role
-- ALLOW, citizen DENY, low-trust(min_loa) DENY on writes, stock-as-asset DENY, and
-- retire/dispose elevated-role gating. The CONTEXTUAL OPA denies that need per-request
-- runtime facts the gateway cannot see from headers alone — technician-by-assignment,
-- cross-facility scope, elevated-approval presence, and link-device-to-clinical
-- clinical-context — are enforced IN-SERVICE by AssetAccessGuard (this wave) +
-- EquipmentOperationsService, the same way V021/V022 left subject-binding in-service.
-- ============================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES
-- ════════════════════════════════════════════════════════════════════════════
-- DENY rules (evaluated first: repository orders effect DESC, so DENY before ALLOW)
-- ════════════════════════════════════════════════════════════════════════════
-- ── Citizens never operate the asset/equipment registry (operational-staff domain) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-citizen-equipment',
 'CITIZEN: denied the operational asset/equipment domain (assets.rego is_citizen DENY).',
 NULL, 'CITIZEN', NULL, NULL, NULL, false, false, 'DENY', 10, '{"path_contains": "/internal/v1/equipment"}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-citizen-assets',
 'CITIZEN: denied the operational asset registry (assets.rego is_citizen DENY).',
 NULL, 'CITIZEN', NULL, NULL, NULL, false, false, 'DENY', 10, '{"path_contains": "/internal/v1/assets"}', true),
-- ── Dura/inventory stock functions are NEVER exposed as asset functions (boundary doctrine) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-stock-as-asset',
 'Stock functions (inventory.stock.*/assets.stock.*) are NOT asset functions — DENY (Dura owns stock).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 10,
 '{"path_contains": ["/internal/v1/equipment/stock", "/internal/v1/assets/stock", "/stock/adjust", "/stock/issue", "/stock/receive"]}', true),
-- ── Retire / dispose require an elevated approver: deny the asset cadres that lack approval authority ──
--    (FACILITY_ADMIN/ADMIN may retire WITH elevated approval — gated in-service; the field cadres may NOT) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-retire-technician',
 'ASSET_TECHNICIAN: cannot retire equipment (elevated approval required — assets.rego elevated_actions).',
 NULL, 'ASSET_TECHNICIAN', NULL, 'POST', NULL, false, false, 'DENY', 9,
 '{"path_contains": ["/internal/v1/equipment/retire", "/internal/v1/equipment/dispose"]}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-retire-field-officer',
 'ASSET_FIELD_OFFICER: cannot retire/dispose equipment (elevated approval required).',
 NULL, 'ASSET_FIELD_OFFICER', NULL, 'POST', NULL, false, false, 'DENY', 9,
 '{"path_contains": ["/internal/v1/equipment/retire", "/internal/v1/equipment/dispose"]}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-deny-retire-biomed',
 'BIOMEDICAL_ENGINEER: cannot retire/dispose equipment (elevated approval required — finance/admin only).',
 NULL, 'BIOMEDICAL_ENGINEER', NULL, 'POST', NULL, false, false, 'DENY', 9,
 '{"path_contains": ["/internal/v1/equipment/retire", "/internal/v1/equipment/dispose"]}', true),

-- ════════════════════════════════════════════════════════════════════════════
-- ALLOW rules — equipment-operations tree (/internal/v1/equipment/**)
-- Covers: list/register/detail, metadata/status, transfers→approve→receive,
-- maintenance open/update/complete/due, calibration record/due, faults report/link-rito,
-- alerts raise/resolve/active/notify, readiness profiles/compute, deployment-kits
-- create/deploy/return, audits open/items/complete. min_loa>=2 on the write surface
-- (assets.rego write/elevated DENY when assurance_loa < 2).
-- ════════════════════════════════════════════════════════════════════════════
-- ── Facility admin (asset_facility_admin / asset_manager): full facility asset operations ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-facility-admin',
 'FACILITY_ADMIN: full facility equipment ops — register/transfer/maintenance/calibration/fault/alert/readiness/kit/audit.',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
-- ── Platform admin (platform_admin): full ops + config across facilities (cross-facility gated in-service) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-admin',
 'ADMIN: platform-wide equipment ops + config (cross-facility scope checked in-service).',
 NULL, 'ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-system-admin',
 'SYSTEM_ADMIN: platform-wide equipment ops + config.',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
-- ── Biomedical engineer: maintenance / calibration / fault / alert / detail (no retire — DENY above) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-biomedical-engineer',
 'BIOMEDICAL_ENGINEER: maintenance/calibration/fault/alert/equipment ops (retire denied above).',
 NULL, 'BIOMEDICAL_ENGINEER', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
-- ── Technician: maintenance/calibration/fault/alert (by-assignment enforced in-service; retire denied above) ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-technician',
 'ASSET_TECHNICIAN: assigned maintenance/calibration/fault/alert ops (by-assignment + scope in-service).',
 NULL, 'ASSET_TECHNICIAN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
-- ── Field officer: deployment kits + field deployment + readiness + fault report ──
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-ops-field-officer',
 'ASSET_FIELD_OFFICER: deployment-kit/field-deployment/readiness/fault ops (retire denied above).',
 NULL, 'ASSET_FIELD_OFFICER', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),

-- ════════════════════════════════════════════════════════════════════════════
-- ALLOW rules — asset registry tree (/internal/v1/assets/**)
-- Covers: register/list/detail (PUT/GET /internal/v1/assets, /{id}, /{id}/status).
-- ════════════════════════════════════════════════════════════════════════════
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-facility-admin',
 'FACILITY_ADMIN: asset registry register/edit/status/detail/list.',
 NULL, 'FACILITY_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-admin',
 'ADMIN: asset registry register/edit/status/detail/list.',
 NULL, 'ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-system-admin',
 'SYSTEM_ADMIN: asset registry register/edit/status/detail/list.',
 NULL, 'SYSTEM_ADMIN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-biomedical-engineer',
 'BIOMEDICAL_ENGINEER: asset registry detail/list + status (operational visibility).',
 NULL, 'BIOMEDICAL_ENGINEER', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-technician',
 'ASSET_TECHNICIAN: asset registry detail/list + status (by-assignment/scope in-service).',
 NULL, 'ASSET_TECHNICIAN', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-registry-field-officer',
 'ASSET_FIELD_OFFICER: asset registry detail/list (field readiness visibility).',
 NULL, 'ASSET_FIELD_OFFICER', NULL, NULL, NULL, false, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/assets", "min_loa": 2}', true),

-- ════════════════════════════════════════════════════════════════════════════
-- ALLOW — provider equipment-use by facility context (assets.rego provider use rule).
-- A provider may use/view equipment within their facility context; scoped GET, facility_scope=true.
-- ════════════════════════════════════════════════════════════════════════════
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-equipment-use-clinician',
 'CLINICIAN (provider): use/view equipment in facility context (assets.equipment.use).',
 'PROVIDER', 'CLINICIAN', NULL, 'GET', NULL, true, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-equipment-use-doctor',
 'DOCTOR (provider): use/view equipment in facility context.',
 'PROVIDER', 'DOCTOR', NULL, 'GET', NULL, true, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true),
('00000000-0000-0000-0000-000000000001'::uuid, 'asset-equipment-use-nurse',
 'NURSE (provider): use/view equipment in facility context.',
 'PROVIDER', 'NURSE', NULL, 'GET', NULL, true, false, 'ALLOW', 60,
 '{"path_contains": "/internal/v1/equipment", "min_loa": 2}', true);
