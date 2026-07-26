-- =====================================================================================
-- TSHEPO-authz :: V051 — WARD_MANAGER is not a role anyone holds
--
-- policy_rule.role is matched against request.roles() by exact string equality
-- (PolicyEngine, Step 4). Those roles come from Keycloak's realm_access.roles claim, plus
-- the WORK_CONTEXT duty token — which folds in both the raw roleTemplateId and whatever
-- canonical role the role_template_catalog maps it to.
--
-- 'WARD_MANAGER' appears in neither. It is absent from all three Keycloak realm exports,
-- absent from the role_template_catalog, and referenced in no source file in the estate.
-- So 'inpatient-discharge-finalise-ward-manager' has never matched a request, and the
-- senior nurse it was written for has never been able to finalise a discharge summary.
--
-- The name the estate actually uses for that person is WARD_CHARGE_NURSE: V043 seeds it
-- into role_template_catalog as a Vashandi duty template. Because the PDP folds the raw
-- template id into the effective role set alongside its canonical role, a rule written
-- against WARD_CHARGE_NURSE matches a ward charge nurse holding that duty token.
--
-- Not a widening. The V027 rule set already grants `finalise` to DOCTOR and CLINICIAN, and
-- WARD_CHARGE_NURSE maps to canonical role CLINICIAN in the catalog — so every actor this
-- corrected rule can admit was already admitted by the CLINICIAN rule. What changes is that
-- the rule now says something true, and the seniority intent recorded in V027's header
-- ("senior roles (DOCTOR/WARD_MANAGER); NURSE is intentionally NOT granted") stops resting
-- on a role that does not exist.
--
-- Left alone deliberately, and reported instead:
--   * SURGEON / ANAESTHETIST (41 rows, V029/V030/V034/V035) — real clinical identities that
--     were simply missing from the realm. Added as Keycloak realm roles rather than rewritten
--     here, so theatre keeps the granularity its rules were authored with.
--   * V004 sample rules (PROVINCIAL_COORDINATOR, DISTRICT_SUPERVISOR,
--     ENVIRONMENTAL_HEALTH_OFFICER) — explicitly sample/visibility-demo seeds.
--   * V005/V008 (REGISTRY_ADMIN, IMPORT_ADMIN, IDENTITY_STEWARD, COMMERCE) — these ARE used
--     in code (REGISTRY_ADMIN in 34 files) but are missing from the realm exports. That is
--     realm-export drift, not a dead rule, and reconciling the export is separate work.
--   * COMMITTEE_MEMBER / HPA_OVERSIGHT_OFFICER (V045/V046/V047) — ROM rows seeded
--     active=false. Inert today; the ROM lane owns them before its ENFORCE flip.
-- =====================================================================================

UPDATE tshepo_authz.policy_rule
   SET role        = 'WARD_CHARGE_NURSE',
       description = 'WARD_CHARGE_NURSE: finalise a discharge summary.',
       updated_at  = now()
 WHERE name = 'inpatient-discharge-finalise-ward-manager'
   AND role = 'WARD_MANAGER';
