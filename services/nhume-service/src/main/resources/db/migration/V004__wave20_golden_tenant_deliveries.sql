-- Wave 21 — golden-tenant demo delivery for production-readiness probes.

INSERT INTO nhume_delivery_policies (
    policy_id, tenant_id, delivery_type, display_name, description,
    requires_approval, requires_payment, requires_stock_check, requires_identity_verify,
    requires_consent, requires_chain_of_custody, cold_chain_required,
    proof_methods_required, allowed_modes, allowed_courier_kinds,
    notification_set, sla_target_minutes, escalation_rule, active, is_demo
) VALUES (
    'wave20-policy-rx', '00000000-0000-4000-8000-000000000001', 'MEDICINE',
    'Wave 20 Rx home delivery', 'Demo policy for CPID-ZW-00001 Rx journey',
    FALSE, FALSE, TRUE, 'OTP', TRUE, FALSE, FALSE,
    '["OTP","RECIPIENT_SIGNATURE"]'::jsonb,
    '["MOTORCYCLE","BICYCLE"]'::jsonb,
    '["COURIER"]'::jsonb,
    'NHUME_MEDICINE', 240, 'NOTIFY_DISPATCHER', TRUE, TRUE
) ON CONFLICT (policy_id) DO NOTHING;

INSERT INTO nhume_delivery_slas (sla_id, tenant_id, name, priority, pickup_minutes, delivery_minutes, description, is_demo)
VALUES (
    'wave20-sla-standard', '00000000-0000-4000-8000-000000000001', 'Wave 20 Standard',
    'STANDARD', 60, 240, 'Production-readiness demo SLA', TRUE
) ON CONFLICT (sla_id) DO NOTHING;

INSERT INTO nhume_delivery_requests (
    delivery_id, tenant_id, reference_code, status, priority, delivery_type, request_source,
    requesting_facility_id, origin_kind, origin_ref, origin_label,
    destination_kind, destination_ref, destination_label,
    recipient_kind, recipient_name, recipient_phone,
    consent_required, identity_verification, allowed_modes_json, policy_id, sla_id, sla_due_at,
    metadata_json, is_demo, created_by
) VALUES (
    'dddddddd-0000-4000-8000-000000000401',
    '00000000-0000-4000-8000-000000000001',
    'NHM-W20-001',
    'IN_TRANSIT',
    'STANDARD',
    'MEDICINE',
    'PROVIDER_APP',
    'f1000000-0000-0000-0000-000000000001',
    'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy',
    'ADDRESS', 'home:CPID-ZW-00001', 'CPID-ZW-00001 home (demo)',
    'CITIZEN', 'Tatenda Moyo (Demo)', '+263772000001',
    TRUE, 'OTP', '["MOTORCYCLE"]'::jsonb, 'wave20-policy-rx', 'wave20-sla-standard', NOW() + INTERVAL '3 hours',
    '{"demo":"wave20","patientCpid":"CPID-ZW-00001"}'::jsonb, TRUE, 'wave20-demo'
) ON CONFLICT (tenant_id, reference_code) DO NOTHING;

INSERT INTO nhume_delivery_status_events (delivery_id, previous_status, new_status, actor_type, reason)
SELECT delivery_id, NULL, status, 'SYSTEM', 'wave20 golden tenant seed'
FROM nhume_delivery_requests
WHERE reference_code = 'NHM-W20-001'
  AND tenant_id = '00000000-0000-4000-8000-000000000001'::uuid
  AND NOT EXISTS (
      SELECT 1 FROM nhume_delivery_status_events e
      WHERE e.delivery_id = nhume_delivery_requests.delivery_id
  );
