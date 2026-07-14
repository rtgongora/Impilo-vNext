-- Wave 1 clinical-safety (theatre) — PATIENT delivery type governance.
-- The operating-theatre program moves PATIENTS between ward/theatre/PACU as governed NHUME deliveries
-- (leg-aware: WARD_TO_THEATRE | THEATRE_TO_PACU | THEATRE_TO_WARD). NHUME already stores delivery_type
-- as a free VARCHAR, so the app-side DeliveryType.PATIENT literal needs no enum/type migration; what is
-- DB-backed is the delivery POLICY that gates and shapes a PATIENT movement. This seed adds an ACTIVE
-- PATIENT policy per the canonical governance tenant so create-delivery policy resolution
-- (findByTenantIdAndDeliveryTypeAndActiveTrue) has a real PATIENT row to bind — chain-of-custody and
-- identity-verification on, no payment/stock/cold-chain. Idempotent (ON CONFLICT DO NOTHING).

INSERT INTO nhume_delivery_policies (
    policy_id, tenant_id, delivery_type, display_name, description,
    requires_approval, requires_payment, requires_stock_check, requires_identity_verify,
    requires_consent, requires_chain_of_custody, cold_chain_required,
    proof_methods_required, allowed_modes, allowed_courier_kinds,
    notification_set, sla_target_minutes, escalation_rule, active, is_demo
) VALUES (
    'theatre-policy-patient', '00000000-0000-0000-0000-000000000001', 'PATIENT',
    'Intra-facility patient movement', 'Governed patient transport between ward, theatre and PACU',
    FALSE, FALSE, FALSE, 'WRISTBAND', TRUE, TRUE, FALSE,
    '["WRISTBAND","RECIPIENT_SIGNATURE"]'::jsonb,
    '["PORTER","WHEELCHAIR","TROLLEY","BED"]'::jsonb,
    '["PORTER"]'::jsonb,
    'NHUME_PATIENT_MOVEMENT', 30, 'NOTIFY_DISPATCHER', TRUE, FALSE
) ON CONFLICT (policy_id) DO NOTHING;
