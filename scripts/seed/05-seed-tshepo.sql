-- =============================================================================
-- TSHEPO — Trust & Governance Seed Data
-- Tenant: moh-zw (UUID: 00000000-0000-4000-8000-000000000001)
-- Seeds: consent directives for golden-path patients, audit log entries
-- Policy decisions are runtime-generated; only consent and audit are seeded.
-- =============================================================================

\c tshepo

INSERT INTO tshepo.consent_directive
    (tenant_id, subject_cpid, grantor_id, grantee_scope, purpose_of_use,
     resource_scope, status, valid_from)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00001',
     'b0000000-0000-4000-8000-000000000001',
     'ALL_TREATING_PROVIDERS',
     'TREATMENT',
     'ALL',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00002',
     'b0000000-0000-4000-8000-000000000002',
     'ALL_TREATING_PROVIDERS',
     'TREATMENT',
     'ALL',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00003',
     'b0000000-0000-4000-8000-000000000003',
     'ALL_TREATING_PROVIDERS',
     'TREATMENT',
     'ALL',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00004',
     'b0000000-0000-4000-8000-000000000004',
     'ALL_TREATING_PROVIDERS',
     'TREATMENT',
     'ALL',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00005',
     'b0000000-0000-4000-8000-000000000005',
     'ALL_TREATING_PROVIDERS',
     'TREATMENT',
     'ALL',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00001',
     'b0000000-0000-4000-8000-000000000001',
     'NATIONAL_DATA_REPOSITORY',
     'PUBLIC_HEALTH',
     'AGGREGATED_ONLY',
     'ACTIVE', NOW()),

    ('00000000-0000-4000-8000-000000000001',
     'CPID-ZW-00002',
     'b0000000-0000-4000-8000-000000000002',
     'NATIONAL_DATA_REPOSITORY',
     'PUBLIC_HEALTH',
     'AGGREGATED_ONLY',
     'ACTIVE', NOW());

INSERT INTO tshepo.policy_decision_log
    (tenant_id, correlation_id, actor_id, actor_type, action,
     resource_type, resource_id, purpose_of_use, facility_id,
     workspace_id, decision, risk_score)
VALUES
    ('00000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000010',
     'c0000000-0000-4000-8000-000000000001',
     'PROVIDER',
     'READ_PATIENT_RECORD',
     'PATIENT', 'CPID-ZW-00001', 'TREATMENT',
     'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'ALLOW', 10),

    ('00000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000011',
     'c0000000-0000-4000-8000-000000000001',
     'PROVIDER',
     'CREATE_PRESCRIPTION',
     'PRESCRIPTION', NULL, 'TREATMENT',
     'a0000000-0000-4000-8000-000000000001',
     'a0000000-0000-4000-8000-000000000001',
     'ALLOW', 15),

    ('00000000-0000-4000-8000-000000000001',
     '00000000-0000-4000-8000-000000000012',
     'unknown-actor-dev',
     'UNKNOWN',
     'READ_ADMIN_PANEL',
     'ADMIN', NULL, 'OPERATIONS',
     NULL, NULL,
     'DENY', 95);
