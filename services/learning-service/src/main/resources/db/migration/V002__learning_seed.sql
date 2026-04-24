-- Demo / bootstrap catalog (tenant 00000000-0000-0000-0000-000000000001). Replace in production with governed content APIs.

INSERT INTO lrn_learning_resource (id, tenant_id, resource_code, source_type, title, description, resource_type, external_ref, app_code, role_tags, workflow_tags, lifecycle_tags, active, metadata_json) VALUES
('11111111-1111-1111-1111-111111111001', '00000000-0000-0000-0000-000000000001', 'RES_REGISTRY_INTAKE', 'FUNDO', 'Registry intake fundamentals', 'Client and provider intake patterns on Impilo vNext.', 'COURSE', '501', 'experience', 'REGISTRY_CLERK,REGISTRY_ADMIN', 'registry,intake', 'onboarding', true, '{"fundoCourseNumericId":"501"}'),
('11111111-1111-1111-1111-111111111002', '00000000-0000-0000-0000-000000000001', 'RES_PROVIDER_CPD_GOVERNANCE', 'FUNDO', 'CPD governance for councils', 'How Fundo completions become governed CPD candidates.', 'COURSE', '502', 'experience', 'REGISTRY_ADMIN', 'cpd,council', 'renewal', true, '{"fundoCourseNumericId":"502"}'),
('11111111-1111-1111-1111-111111111003', '00000000-0000-0000-0000-000000000001', 'RES_MARKETPLACE_EXCEPTIONS', 'SOP', 'Marketplace exceptions & refunds', 'Internal SOP for regulated ordering exceptions.', 'SOP', 'sop-msika-exceptions-v1', 'marketplace', 'COMMERCE,MSIKA_GOVERNANCE', 'orders,refunds', 'operations', true, '{"href":"/support/knowledge-base"}'),
('11111111-1111-1111-1111-111111111004', '00000000-0000-0000-0000-000000000001', 'RES_HELPDESK_TRIAGE', 'QUICK_GUIDE', 'Support triage quick guide', 'First-line diagnostics before escalation.', 'QUICK_GUIDE', 'qd-support-triage', 'support', 'REGISTRY_ADMIN,CLINICAL', 'helpdesk', 'operations', true, '{"href":"/support"}');

INSERT INTO lrn_contextual_learning_hint (id, tenant_id, app_code, route_or_component_ref, hint_type, text_summary, resource_id, active, metadata_json) VALUES
('22222222-2222-2222-2222-222222222001', '00000000-0000-0000-0000-000000000001', 'experience', '/registry/intake', 'ONBOARDING_GUIDE', 'Complete intake training before processing sensitive demographics.', '11111111-1111-1111-1111-111111111001', true, '{}'),
('22222222-2222-2222-2222-222222222002', '00000000-0000-0000-0000-000000000001', 'experience', '/home/credentials', 'MICROLEARNING', 'Fundo-linked completions appear as CPD candidates until council acceptance.', '11111111-1111-1111-1111-111111111002', true, '{}');

INSERT INTO lrn_workflow_learning_link (id, tenant_id, app_code, workflow_code, screen_or_route_ref, resource_id, path_id, link_type, active, metadata_json) VALUES
('33333333-3333-3333-3333-333333333001', '00000000-0000-0000-0000-000000000001', 'experience', 'registry_intake', '/registry/intake', '11111111-1111-1111-1111-111111111001', NULL, 'LEARN_MORE', true, '{}'),
('33333333-3333-3333-3333-333333333002', '00000000-0000-0000-0000-000000000001', 'marketplace', 'order_exception', '/marketplace/cart', '11111111-1111-1111-1111-111111111003', NULL, 'SOP', true, '{}');

INSERT INTO lrn_helpdesk_knowledge_link (id, tenant_id, issue_type_or_topic, resource_id, path_id, relation_type, active, metadata_json) VALUES
('44444444-4444-4444-4444-444444444001', '00000000-0000-0000-0000-000000000001', 'GENERAL', '11111111-1111-1111-1111-111111111004', NULL, 'TROUBLESHOOTING', true, '{}'),
('44444444-4444-4444-4444-444444444002', '00000000-0000-0000-0000-000000000001', 'ACCESS', '11111111-1111-1111-1111-111111111001', NULL, 'TRAINING', true, '{}');

INSERT INTO lrn_role_learning_requirement (id, tenant_id, role_code, resource_id, path_id, requirement_type, active, metadata_json) VALUES
('55555555-5555-5555-5555-555555555001', '00000000-0000-0000-0000-000000000001', 'REGISTRY_CLERK', '11111111-1111-1111-1111-111111111001', NULL, 'RECOMMENDED', true, '{}'),
('55555555-5555-5555-5555-555555555002', '00000000-0000-0000-0000-000000000001', 'COMMERCE', '11111111-1111-1111-1111-111111111003', NULL, 'RECOMMENDED', true, '{}');

INSERT INTO lrn_cpd_learning_link (id, tenant_id, resource_id, council_ref, cpd_eligible, default_credit_value, requires_review_flag, active, metadata_json) VALUES
('66666666-6666-6666-6666-666666666001', '00000000-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111002', NULL, true, 1, true, true, '{}');
