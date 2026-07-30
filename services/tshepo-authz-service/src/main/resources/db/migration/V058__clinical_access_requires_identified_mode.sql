-- =====================================================================================
-- TSHEPO-authz :: V058 — the positive companion lock V055 deferred (Phase B, B4/B5, SHADOW).
--
-- V055 shipped the negative half and said why it stopped: the positive lock needs each
-- live clinical ALLOW rule audited and UPDATE-ed individually, which is a live-rule
-- mutation rather than an additive row.
--
-- Auditing them produced a reason not to do it that way at all.
--
-- `requires_identified_clinical_access` fails when the duty context is ABSENT, not only
-- when it is present and non-clinical (PolicyEngine.evaluateConditions). Work-context
-- minting is new and TSHEPO_WORK_CONTEXT_MODE is still SHADOW, so most live clinical
-- traffic carries no work-context token at all. Adding that condition to the live clinical
-- ALLOW rules today would stop them matching for every session without a token — which is
-- nearly all of them — and clinical access would collapse estate-wide the moment the
-- migration applied. There is no active=false to hide behind: editing a live rule's
-- conditions takes effect immediately.
--
-- So the same guarantee is expressed additively instead, as DENY rows. DENY wins over any
-- ALLOW, so a clinical read under a mode that does not carry identified clinical access is
-- refused whether or not some ALLOW rule would have permitted it — and this migration never
-- has to enumerate the ALLOW rules at all.
--
-- On a DENY rule the conditions decide whether the DENY FIRES (PolicyEngine, DENY branch:
-- `if (!evaluateConditions(...)) continue;`), so the polarity is the opposite of intuition:
-- `allowed_modes` is what expresses "deny when the mode is one of these". `blocked_modes`
-- here would mean "deny every mode EXCEPT these", and because its null check treats a
-- missing mode as not-blocked it would fire on the token-less sessions that are currently
-- almost all of the traffic. `allowed_modes` fails closed the safe way instead — a session
-- with no duty context does not match, so the DENY does not fire, which is exactly the
-- property that makes this safe to seed while SHADOW is still on. V055's DENY rows use
-- `allowed_modes` for the same reason.
--
-- Where V055 covered part of the matrix (management modes on /patients/, support modes on
-- four clinical families), this covers all of it: every mode whose ClinicalDataAccess is not
-- IDENTIFIED, against every clinical resource family PolicyEngine recognises.
-- ClinicalAccessBoundaryMatrixTest derives the mode list from the WorkMode enum, so a mode
-- added later without identified access cannot quietly escape the lock.
--
-- Seeded active=false — pure SHADOW, zero runtime effect until the D7 cutover flips them,
-- following V045/V046/V047 and V055.
--
-- Modes deliberately absent (they DO carry identified clinical access, and denying them
-- would deny the clinical work the lock exists to protect):
--   CLINICAL_CARE, VIRTUAL_CARE, COMMUNITY_OUTREACH
-- =====================================================================================

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-patients-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /patients/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/patients", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-encounters-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /encounters/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/encounters", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-observations-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /observations/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/observations", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-diagnostic-reports-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /diagnostic-reports/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/diagnostic-reports", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-medication-requests-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /medication-requests/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/medication-requests", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-prescriptions-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /prescriptions/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/prescriptions", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false),

('00000000-0000-0000-0000-000000000001'::uuid, 'clinical-lock-clinical-non-identified-mode',
 'A duty mode without IDENTIFIED clinical access may not read /clinical/** (SHADOW).',
 NULL, NULL, NULL, NULL, NULL, false, false, 'DENY', 54,
 '{"path_contains": "/clinical", "allowed_modes": ["DEPARTMENT_MANAGEMENT","FACILITY_MANAGEMENT","JURISDICTION_OPERATIONS","PROGRAMME_MANAGEMENT","TECHNICAL_SUPPORT","FACILITY_SUPPORT","REGULATORY_OPERATIONS","INSPECTION_COMPLIANCE","SPECIMEN_TRANSPORT"]}',
 false)

ON CONFLICT DO NOTHING;
