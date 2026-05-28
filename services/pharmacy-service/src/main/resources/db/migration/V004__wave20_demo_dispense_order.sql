-- Wave 20 slice 13 — golden-patient dispense order for Rx-path demo probes.

INSERT INTO rx_dispense_orders (
    order_id,
    tenant_id,
    facility_id,
    patient_cpid,
    oros_order_id,
    status,
    priority,
    prescriber_notes
) VALUES (
    'a3000000-0000-4000-8000-000000000001'::uuid,
    '00000000-0000-4000-8000-000000000001'::uuid,
    'f1000000-0000-0000-0000-000000000001'::uuid,
    'CPID-ZW-00001',
    'OROS-WAVE20-DEMO-0001',
    'PENDING',
    'ROUTINE',
    'DEMO-WAVE20 Rx dispense for production-readiness golden patient'
) ON CONFLICT (oros_order_id) DO NOTHING;
