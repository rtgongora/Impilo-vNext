-- Sample policy rules demonstrating visibility + export posture per supervisory / site roles.
-- Copy or adapt for real tenants; tenant_id below is a documentation placeholder only.

-- District / provincial oversight: facility directory remains usable but only in aggregate representation.
INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'district-supervisor-facilities-aggregate',
    'District supervisors: organisational scope without row-level PII or clinical payloads on facilities.',
    'PROVIDER',
    'DISTRICT_SUPERVISOR',
    'facilities',
    NULL,
    NULL,
    false,
    false,
    'ALLOW',
    120,
    '{
      "visibility": {
        "visibilityTier": "AGGREGATE_ONLY",
        "aggregateOnly": true,
        "piiAccess": "NONE",
        "clinicalAccess": "NONE",
        "exportPolicy": "AGGREGATE_ONLY",
        "drillDownAllowed": false
      }
    }'::jsonb,
    true
);

INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'provincial-coordinator-reports-deidentified',
    'Provincial coordinators: analytics reports in de-identified row form, no drill to identified charts.',
    'PROVIDER',
    'PROVINCIAL_COORDINATOR',
    'reports',
    NULL,
    NULL,
    false,
    false,
    'ALLOW',
    120,
    '{
      "visibility": {
        "visibilityTier": "DEIDENTIFIED_ROW_LEVEL",
        "aggregateOnly": false,
        "piiAccess": "MASKED",
        "clinicalAccess": "SUMMARY",
        "exportPolicy": "REDACTED",
        "drillDownAllowed": false
      }
    }'::jsonb,
    true
);

-- Environmental / public health site work: operational site context without clinical chart access.
INSERT INTO tshepo_authz.policy_rule (
    tenant_id, name, description, actor_type, role, resource_type, action, purpose,
    facility_scope, workspace_scope, effect, priority, conditions, active
) VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'env-health-officer-sites-nonclinical',
    'Environmental health officers: site inspection data only; no clinical person-level payloads.',
    'PROVIDER',
    'ENVIRONMENTAL_HEALTH_OFFICER',
    'sites',
    NULL,
    NULL,
    false,
    false,
    'ALLOW',
    120,
    '{
      "visibility": {
        "visibilityTier": "IDENTIFIED_OPERATIONAL_ONLY",
        "aggregateOnly": false,
        "piiAccess": "LIMITED",
        "clinicalAccess": "NONE",
        "exportPolicy": "REDACTED",
        "drillDownAllowed": false
      }
    }'::jsonb,
    true
);
