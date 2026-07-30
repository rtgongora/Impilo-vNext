-- =====================================================================================
-- TSHEPO-authz :: V059 — correct V055's path pins so they match records, not just
-- collection roots (Phase B, B4/B5, SHADOW).
--
-- `path_contains` is segment-bounded: PolicyEngine.pathContainsSegment requires the pin to
-- be followed by `/` or end-of-path, so a longer same-prefix route cannot satisfy it. That
-- is the property that stops `/patients` granting `/patientsummary`.
--
-- It also means a pin written WITH a trailing slash matches almost nothing. `"/patients/"`
-- is satisfied only by `/v1/patients/` (the bare collection) or the pathological
-- `/v1/patients//x` — never by `/v1/patients/{id}`, which is the record read the rule was
-- written to stop. V055's nine rules were all written that way, so as seeded they are
-- decorative: flipping them active at the D7 cutover would have produced a boundary that
-- reported enforcement while permitting every record access it names.
--
-- Nothing was broken by this in production because V055 is seeded active=false and the PDP
-- is not yet on the preview request path. It was found by executing V058's rule text through
-- the real PolicyEngine rather than reading it (ClinicalAccessBoundaryMatrixTest); the first
-- assertion failed, and the pin was the reason.
--
-- The rows are inert, so this UPDATE is inert. V055 itself is left untouched — it has been
-- applied, and editing an applied migration breaks Flyway's checksum.
--
-- The same defect exists in ~85 ALLOW rules across the theatre, rito, inpatient, confidential
-- and regulatory lanes. Those are not corrected here: an ALLOW whose condition never matches
-- fails closed, so the blast radius of a bulk rewrite is the opposite of this one's, and the
-- rules belong to lanes that must review their own path shapes. Recorded with evidence in
-- docs/architecture/POLICY_PATH_PIN_SEMANTICS.md and guarded against growth by
-- scripts/guard/check-policy-path-pins.sh.
-- =====================================================================================

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/patients/"', '"/patients"')
 WHERE name = 'work-mode-management-no-patient-record';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/clinical/"', '"/clinical"')
 WHERE name = 'work-mode-support-no-clinical-read';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/encounters/"', '"/encounters"')
 WHERE name = 'work-mode-support-no-encounter-read';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/prescriptions/"', '"/prescriptions"')
 WHERE name = 'work-mode-support-no-prescription-read';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/observations/"', '"/observations"')
 WHERE name = 'work-mode-support-no-observation-read';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/facility-mode/"', '"/facility-mode"')
 WHERE name = 'work-mode-regulator-no-facility-operation';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/queue/"', '"/queue"')
 WHERE name = 'work-mode-regulator-no-queue-operation';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/service-points/"', '"/service-points"')
 WHERE name = 'work-mode-regulator-no-service-point-operation';

UPDATE tshepo_authz.policy_rule
   SET conditions = replace(conditions, '"/workspaces/"', '"/workspaces"')
 WHERE name = 'work-mode-regulator-no-workspace-operation';
