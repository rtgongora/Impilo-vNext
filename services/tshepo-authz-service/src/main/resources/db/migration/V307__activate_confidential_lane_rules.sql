-- TSHEPO-authz V307 — activate confidential clinical lane rules (PO lease 2026-07-31).
--
-- PO authorized this number OUTSIDE the RMNP V048–V052 band. Activates the eight V048
-- confidential-lane policy rules seeded with active = false. Requires adolescent pack
-- RATIFIED + effective and tshepo.authz.confidentiality-mode = ENFORCE (W14-D flip).
--
-- Rule names from V048__confidential_clinical_lane_policy_rules.sql.

UPDATE tshepo_authz.policy_rule
   SET active = true
 WHERE name LIKE 'confidential-lane-%';
