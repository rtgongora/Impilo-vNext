-- ============================================================================
-- nhume-service V003 — Realistic seed / demo data
-- All rows are tagged is_demo = TRUE for safe identification.
-- Tenant: '00000000-0000-0000-0000-000000000001' (Demo Tenant)
-- ============================================================================

-- ---- Delivery policies -----------------------------------------------------
INSERT INTO nhume_delivery_policies (
    policy_id, tenant_id, delivery_type, display_name, description,
    requires_approval, requires_payment, requires_stock_check, requires_identity_verify,
    requires_consent, requires_chain_of_custody, cold_chain_required,
    proof_methods_required, allowed_modes, allowed_courier_kinds,
    notification_set, sla_target_minutes, escalation_rule, active, is_demo)
VALUES
('demo-policy-medicine', '00000000-0000-0000-0000-000000000001', 'MEDICINE',
 'Home medicine delivery (demo)',
 'Default policy for routine medicine deliveries to verified citizens.',
 FALSE, FALSE, TRUE, 'OTP', TRUE, FALSE, FALSE,
 '["RECIPIENT_SIGNATURE","OTP"]'::jsonb,
 '["BICYCLE","MOTORCYCLE","CAR","WALKING_COURIER","PARTNER_COURIER"]'::jsonb,
 '["COURIER","CHW","FACILITY_RUNNER"]'::jsonb,
 'NHUME_MEDICINE', 240, 'NOTIFY_DISPATCHER', TRUE, TRUE),
('demo-policy-lab-pickup', '00000000-0000-0000-0000-000000000001', 'LAB_SAMPLE_PICKUP',
 'Lab sample pickup (demo)',
 'Cold-chain aware sample pickup from facility or home to reference lab.',
 FALSE, FALSE, FALSE, 'NONE', FALSE, TRUE, TRUE,
 '["QR_CODE_SCAN","FACILITY_STAMP"]'::jsonb,
 '["MOTORCYCLE","CAR","VAN","AMBULANCE","COMMUNITY_HEALTH_WORKER"]'::jsonb,
 '["COURIER","CHW","DRIVER"]'::jsonb,
 'NHUME_LAB_PICKUP', 180, 'ESCALATE_LAB_OPS', TRUE, TRUE),
('demo-policy-cold-chain', '00000000-0000-0000-0000-000000000001', 'COLD_CHAIN',
 'Cold-chain commodity delivery (demo)',
 'Vaccine and biologic delivery with temperature monitoring.',
 TRUE, FALSE, TRUE, 'OTP', FALSE, TRUE, TRUE,
 '["RECIPIENT_SIGNATURE","FACILITY_STAMP","PHOTO"]'::jsonb,
 '["VAN","TRUCK","AMBULANCE","BOAT","DRONE"]'::jsonb,
 '["DRIVER","FACILITY_RUNNER"]'::jsonb,
 'NHUME_COLD_CHAIN', 360, 'ESCALATE_PUBLIC_HEALTH', TRUE, TRUE);

-- ---- SLAs ------------------------------------------------------------------
INSERT INTO nhume_delivery_slas (sla_id, tenant_id, name, priority, pickup_minutes, delivery_minutes, description, is_demo) VALUES
('demo-sla-standard',  '00000000-0000-0000-0000-000000000001', 'Standard',  'STANDARD',   60, 240, 'Default city/town SLA', TRUE),
('demo-sla-urgent',    '00000000-0000-0000-0000-000000000001', 'Urgent',    'URGENT',     30, 120, 'Same-day urgent care SLA', TRUE),
('demo-sla-emergency', '00000000-0000-0000-0000-000000000001', 'Emergency', 'EMERGENCY',  15,  60, 'Life-safety / emergency SLA', TRUE);

-- ---- Dispatch zones --------------------------------------------------------
INSERT INTO nhume_dispatch_zones (zone_id, tenant_id, code, name, zone_kind, jurisdiction_ref, geometry_kind, centre_lat, centre_lng, radius_m, active, is_demo)
VALUES
('aaaaaaaa-0000-0000-0000-000000000101', '00000000-0000-0000-0000-000000000001', 'HARARE-CENTRAL',
 'Harare Central Dispatch Zone', 'OPERATIONAL', 'HARARE', 'CIRCLE', -17.8252, 31.0335, 8000, TRUE, TRUE),
('aaaaaaaa-0000-0000-0000-000000000102', '00000000-0000-0000-0000-000000000001', 'BULAWAYO-METRO',
 'Bulawayo Metro Dispatch Zone', 'OPERATIONAL', 'BULAWAYO', 'CIRCLE', -20.1457, 28.5873, 7000, TRUE, TRUE);

-- ---- Integration providers (autonomous + courier) --------------------------
INSERT INTO nhume_delivery_integration_providers
   (provider_code, tenant_id, display_name, provider_kind, base_url, auth_kind, simulation_mode, active, is_demo, config_json)
VALUES
('sim-drone-1',   '00000000-0000-0000-0000-000000000001', 'SkyDrop Drones (Simulated)',
   'DRONE',          NULL, 'NONE', TRUE, TRUE, TRUE,
   '{"max_payload_kg":2.5,"range_km":40}'::jsonb),
('sim-robot-1',   '00000000-0000-0000-0000-000000000001', 'CampusBot Sidewalk Robot (Simulated)',
   'ROBOT',          NULL, 'NONE', TRUE, TRUE, TRUE,
   '{"max_payload_kg":15,"speed_kph":6}'::jsonb),
('sim-locker-1',  '00000000-0000-0000-0000-000000000001', 'HealthLocker Smart Lockers (Simulated)',
   'SMART_LOCKER',   NULL, 'NONE', TRUE, TRUE, TRUE,
   '{"sites":["Harare CBD","Bulawayo Mall"]}'::jsonb),
('demo-3pl-1',    '00000000-0000-0000-0000-000000000001', 'PostNet Health Courier (Demo)',
   'COURIER_API',    NULL, 'NONE', TRUE, TRUE, TRUE,
   '{"coverage":["HARARE","BULAWAYO"]}'::jsonb);

-- ---- Couriers (4) ----------------------------------------------------------
INSERT INTO nhume_driver_courier_profiles
   (courier_id, tenant_id, courier_code, display_name, courier_kind, phone, email,
    allowed_modes_json, current_status, trust_verified, council_verified,
    offline_access_allowed, active, is_demo)
VALUES
('bbbbbbbb-0000-0000-0000-000000000201', '00000000-0000-0000-0000-000000000001', 'CRR-001',
 'Tendai Moyo (Demo)',  'COURIER',          '+263772000001', 'tendai.demo@nhume.local',
 '["MOTORCYCLE","BICYCLE"]'::jsonb, 'ON_DUTY',  TRUE, FALSE, TRUE, TRUE, TRUE),
('bbbbbbbb-0000-0000-0000-000000000202', '00000000-0000-0000-0000-000000000001', 'DRV-002',
 'Rumbidzai Chiwara (Demo)','DRIVER',       '+263772000002', 'rumbidzai.demo@nhume.local',
 '["VAN","CAR"]'::jsonb,             'ON_DUTY',  TRUE, TRUE,  TRUE, TRUE, TRUE),
('bbbbbbbb-0000-0000-0000-000000000203', '00000000-0000-0000-0000-000000000001', 'CHW-003',
 'Memory Banda (Demo CHW)', 'CHW',          '+263772000003', 'memory.demo@nhume.local',
 '["WALKING_COURIER","BICYCLE"]'::jsonb,'OFF_DUTY', TRUE, TRUE, TRUE, TRUE, TRUE),
('bbbbbbbb-0000-0000-0000-000000000204', '00000000-0000-0000-0000-000000000001', 'OPR-004',
 'Operations Robot Pilot (Demo)','ROBOT_OPERATOR','+263772000004','robot.demo@nhume.local',
 '["ROBOT"]'::jsonb,                 'ON_DUTY',  TRUE, FALSE, FALSE, TRUE, TRUE);

-- ---- Fleet assets (5) ------------------------------------------------------
INSERT INTO nhume_fleet_assets
   (asset_id, tenant_id, asset_code, display_name, registration_number, vehicle_type, mode_category,
    ownership, home_facility_ref, capacity_units, weight_limit_kg, cold_chain_capable,
    temperature_monitoring, gps_capable, telematics_provider, maintenance_status, availability_status,
    last_lat, last_lng, last_seen_at, autonomous_profile_json, active, is_demo, assigned_courier_id)
VALUES
('cccccccc-0000-0000-0000-000000000301', '00000000-0000-0000-0000-000000000001', 'MC-001',
 'Honda CRF 125 #1 (Demo)','AAA-1001', 'MOTORCYCLE',           'MOTORCYCLE',
 'OWNED', 'TUSO:FAC-HARARE-CENTRAL', 5, 15.0, FALSE, FALSE, TRUE, 'TRACCAR', 'OK', 'AVAILABLE',
 -17.8252, 31.0335, NOW() - INTERVAL '3 minutes', '{}'::jsonb, TRUE, TRUE, 'bbbbbbbb-0000-0000-0000-000000000201'),
('cccccccc-0000-0000-0000-000000000302', '00000000-0000-0000-0000-000000000001', 'VN-002',
 'Toyota HiAce Cold-Chain Van #1 (Demo)','AAA-1002', 'VAN',     'VAN',
 'OWNED', 'TUSO:FAC-HARARE-CENTRAL', 40, 800.0, TRUE, TRUE, TRUE, 'TRACCAR', 'OK', 'IN_USE',
 -17.8421, 31.0510, NOW() - INTERVAL '1 minute',  '{}'::jsonb, TRUE, TRUE, 'bbbbbbbb-0000-0000-0000-000000000202'),
('cccccccc-0000-0000-0000-000000000303', '00000000-0000-0000-0000-000000000001', 'AMB-003',
 'Toyota Land Cruiser Ambulance (Demo)','AAA-1003', 'AMBULANCE','AMBULANCE',
 'OWNED', 'TUSO:FAC-HARARE-CENTRAL', 8, 400.0, TRUE, TRUE, TRUE, 'TRACCAR', 'OK', 'AVAILABLE',
 -17.8000, 31.0500, NOW() - INTERVAL '5 minutes', '{}'::jsonb, TRUE, TRUE, NULL),
('cccccccc-0000-0000-0000-000000000304', '00000000-0000-0000-0000-000000000001', 'DRN-004',
 'SkyDrop UAV Alpha (Demo)','AAA-DRN-04','DRONE',                 'DRONE',
 'PARTNER', 'TUSO:FAC-HARARE-CENTRAL', 1, 2.5, TRUE, TRUE, TRUE, 'SKYDROP', 'OK', 'AVAILABLE',
 -17.8252, 31.0335, NOW() - INTERVAL '12 minutes',
 '{"flight_range_km":40,"payload_kg":2.5,"autonomy_level":"L3","launch_site":"Harare Central Pad","battery_pct":92,"no_fly_zones_aware":true}'::jsonb,
 TRUE, TRUE, NULL),
('cccccccc-0000-0000-0000-000000000305', '00000000-0000-0000-0000-000000000001', 'ROB-005',
 'CampusBot Sidewalk Robot #1 (Demo)',NULL, 'ROBOT',              'ROBOT',
 'PARTNER', 'TUSO:FAC-HARARE-CENTRAL', 2, 15.0, FALSE, FALSE, TRUE, 'CAMPUSBOT', 'OK', 'AVAILABLE',
 -17.8252, 31.0335, NOW() - INTERVAL '8 minutes',
 '{"autonomy_level":"L4","payload_kg":15,"speed_kph":6,"battery_pct":78}'::jsonb,
 TRUE, TRUE, 'bbbbbbbb-0000-0000-0000-000000000204');

-- ---- Delivery requests (10) ------------------------------------------------
-- We create 10 demo delivery requests covering a mix of statuses and types.
INSERT INTO nhume_delivery_requests
   (delivery_id, tenant_id, reference_code, status, priority, delivery_type, request_source,
    requesting_actor_type, requesting_facility_id,
    origin_kind, origin_ref, origin_label, origin_address_line, origin_lat, origin_lng,
    destination_kind, destination_ref, destination_label, destination_address_line,
    destination_lat, destination_lng,
    recipient_kind, recipient_name, recipient_phone,
    consent_required, identity_verification, cold_chain_required, controlled_item,
    chain_of_custody_required, return_required,
    allowed_modes_json, policy_id, sla_id, sla_due_at,
    metadata_json, is_demo, created_by, telehealth_session_ref, marketplace_order_ref)
VALUES
('dddddddd-0000-0000-0000-000000000401', '00000000-0000-0000-0000-000000000001', 'NHM-1001',
 'SUBMITTED', 'STANDARD', 'MEDICINE', 'CITIZEN_APP',
 'CITIZEN', NULL,
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'ADDRESS', 'home:dddddddd-1001', 'Patient home (privacy-safe)', 'Mt Pleasant, Harare',
 -17.7600, 31.0500,
 'CITIZEN', 'Patient A (Demo)', '+263772100001',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["MOTORCYCLE","BICYCLE"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() + INTERVAL '4 hours',
 '{"is_seed":true,"notes":"Demo medicine refill via citizen app"}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000402', '00000000-0000-0000-0000-000000000001', 'NHM-1002',
 'ASSIGNED', 'STANDARD', 'MEDICINE', 'PROVIDER_APP',
 'PROVIDER', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'ADDRESS', 'home:dddddddd-1002', 'Patient home', 'Borrowdale, Harare',
 -17.7200, 31.1000,
 'CITIZEN', 'Patient B (Demo)', '+263772100002',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["MOTORCYCLE"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() + INTERVAL '3 hours',
 '{"is_seed":true}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000403', '00000000-0000-0000-0000-000000000001', 'NHM-1003',
 'IN_TRANSIT', 'URGENT', 'COLD_CHAIN', 'PUBLIC_HEALTH',
 'OPERATOR', 'TUSO:FAC-HARARE-CENTRAL',
 'WAREHOUSE', 'TUSO:WH-NATPHARM', 'NatPharm Central Warehouse', 'Lytton Rd, Harare',
 -17.8500, 31.0200,
 'FACILITY', 'TUSO:FAC-BULAWAYO-MPILO', 'Mpilo Central Hospital', 'Bulawayo',
 -20.1457, 28.5873,
 'FACILITY', 'Mpilo Cold Chain Officer', '+263772100003',
 FALSE, 'NONE', TRUE, FALSE, TRUE, FALSE,
 '["VAN","TRUCK"]'::jsonb, 'demo-policy-cold-chain', 'demo-sla-urgent', NOW() + INTERVAL '6 hours',
 '{"is_seed":true,"campaign":"MEASLES_2026_Q1"}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000404', '00000000-0000-0000-0000-000000000001', 'NHM-1004',
 'PICKED_UP', 'STANDARD', 'LAB_SAMPLE_PICKUP', 'FACILITY_OPERATIONS',
 'OPERATOR', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-CHITUNGWIZA-CLINIC', 'Chitungwiza Clinic', 'Chitungwiza',
 -18.0090, 31.0810,
 'FACILITY', 'TUSO:LAB-NMRL', 'National Microbiology Reference Lab', 'Harare',
 -17.8200, 31.0500,
 'FACILITY', 'NMRL Receiving', '+263772100004',
 FALSE, 'NONE', TRUE, FALSE, TRUE, FALSE,
 '["MOTORCYCLE","CAR","AMBULANCE"]'::jsonb, 'demo-policy-lab-pickup', 'demo-sla-standard', NOW() + INTERVAL '2 hours',
 '{"is_seed":true,"specimens":["EDTA tube","viral swab"]}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000405', '00000000-0000-0000-0000-000000000001', 'NHM-1005',
 'DELIVERED', 'STANDARD', 'MEDICINE', 'TELEHEALTH',
 'PROVIDER', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'ADDRESS', 'home:dddddddd-1005', 'Patient home', 'Avondale, Harare',
 -17.7820, 31.0410,
 'CITIZEN', 'Patient E (Demo)', '+263772100005',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["BICYCLE","WALKING_COURIER"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() - INTERVAL '2 hours',
 '{"is_seed":true,"telehealth":true}'::jsonb, TRUE, 'seed', 'tele:demo-001', NULL),

('dddddddd-0000-0000-0000-000000000406', '00000000-0000-0000-0000-000000000001', 'NHM-1006',
 'FAILED', 'STANDARD', 'MEDICINE', 'CITIZEN_APP',
 'CITIZEN', NULL,
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'ADDRESS', 'home:dddddddd-1006', 'Patient home', 'Mabvuku, Harare',
 -17.8420, 31.1900,
 'CITIZEN', 'Patient F (Demo)', '+263772100006',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["MOTORCYCLE"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() - INTERVAL '4 hours',
 '{"is_seed":true,"failure_reason":"recipient unreachable"}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000407', '00000000-0000-0000-0000-000000000001', 'NHM-1007',
 'FAILED', 'STANDARD', 'MARKETPLACE', 'MARKETPLACE',
 'CITIZEN', NULL,
 'WAREHOUSE', 'MSIKA:VENDOR-OPTICS', 'EyeCare Optics Vendor', 'Harare CBD',
 -17.8252, 31.0335,
 'ADDRESS', 'home:dddddddd-1007', 'Patient home', 'Greendale, Harare',
 -17.8000, 31.1200,
 'CITIZEN', 'Patient G (Demo)', '+263772100007',
 FALSE, 'NONE', FALSE, FALSE, FALSE, TRUE,
 '["MOTORCYCLE","CAR"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() - INTERVAL '6 hours',
 '{"is_seed":true,"failure_reason":"address invalid"}'::jsonb, TRUE, 'seed', NULL, 'MSIKA:ORDER-9007'),

('dddddddd-0000-0000-0000-000000000408', '00000000-0000-0000-0000-000000000001', 'NHM-1008',
 'IN_TRANSIT', 'STANDARD', 'MARKETPLACE', 'MARKETPLACE',
 'CITIZEN', NULL,
 'WAREHOUSE', 'MSIKA:VENDOR-HEALTHSHOP', 'HealthShop Vendor', 'Bulawayo',
 -20.1500, 28.5800,
 'ADDRESS', 'home:dddddddd-1008', 'Patient home', 'Khumalo, Bulawayo',
 -20.1457, 28.6000,
 'CITIZEN', 'Patient H (Demo)', '+263772100008',
 FALSE, 'NONE', FALSE, FALSE, FALSE, FALSE,
 '["MOTORCYCLE","CAR"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() + INTERVAL '5 hours',
 '{"is_seed":true}'::jsonb, TRUE, 'seed', NULL, 'MSIKA:ORDER-9008'),

('dddddddd-0000-0000-0000-000000000409', '00000000-0000-0000-0000-000000000001', 'NHM-1009',
 'IN_TRANSIT', 'STANDARD', 'MEDICINE', 'PROVIDER_APP',
 'PROVIDER', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'ADDRESS', 'home:dddddddd-1009', 'Patient home', 'Glen Norah, Harare',
 -17.9000, 30.9800,
 'CITIZEN', 'Patient I (Demo)', '+263772100009',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["DRONE","MOTORCYCLE"]'::jsonb, 'demo-policy-medicine', 'demo-sla-urgent', NOW() + INTERVAL '90 minutes',
 '{"is_seed":true,"drone_mission":true}'::jsonb, TRUE, 'seed', NULL, NULL),

('dddddddd-0000-0000-0000-000000000410', '00000000-0000-0000-0000-000000000001', 'NHM-1010',
 'ASSIGNED', 'STANDARD', 'MEDICINE', 'PROVIDER_APP',
 'PROVIDER', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-PHARMACY-CBD', 'Avondale Pharmacy', '12 King George Rd, Harare',
 -17.7900, 31.0410,
 'PICKUP_POINT', 'LOCKER:HRE-CBD-01', 'HealthLocker Harare CBD #01', 'Harare CBD',
 -17.8252, 31.0335,
 'CITIZEN', 'Patient J (Demo)', '+263772100010',
 FALSE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["ROBOT","BICYCLE"]'::jsonb, 'demo-policy-medicine', 'demo-sla-standard', NOW() + INTERVAL '4 hours',
 '{"is_seed":true,"locker":true}'::jsonb, TRUE, 'seed', NULL, NULL);

-- ---- Status events (one per delivery, current status) ----------------------
INSERT INTO nhume_delivery_status_events
   (delivery_id, previous_status, new_status, actor_type, reason)
SELECT delivery_id, NULL, status, 'SYSTEM', 'seed initial status'
FROM nhume_delivery_requests WHERE is_demo = TRUE;

-- ---- Assignments for ASSIGNED / PICKED_UP / IN_TRANSIT / DELIVERED ---------
INSERT INTO nhume_delivery_assignments
   (assignment_id, delivery_id, tenant_id, courier_id, asset_id, mode_category,
    assignment_kind, assigned_by, status, accepted_at)
VALUES
('eeeeeeee-0000-0000-0000-000000000501', 'dddddddd-0000-0000-0000-000000000402',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000201',
 'cccccccc-0000-0000-0000-000000000301', 'MOTORCYCLE', 'SUGGESTED', 'seed', 'ASSIGNED', NULL),
('eeeeeeee-0000-0000-0000-000000000503', 'dddddddd-0000-0000-0000-000000000403',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000202',
 'cccccccc-0000-0000-0000-000000000302', 'VAN',        'MANUAL',    'seed', 'ACCEPTED', NOW() - INTERVAL '1 hour'),
('eeeeeeee-0000-0000-0000-000000000504', 'dddddddd-0000-0000-0000-000000000404',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000202',
 'cccccccc-0000-0000-0000-000000000303', 'AMBULANCE',  'MANUAL',    'seed', 'ACCEPTED', NOW() - INTERVAL '45 minutes'),
('eeeeeeee-0000-0000-0000-000000000505', 'dddddddd-0000-0000-0000-000000000405',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000203',
 NULL,                                    'WALKING_COURIER','MANUAL','seed', 'COMPLETED', NOW() - INTERVAL '4 hours'),
('eeeeeeee-0000-0000-0000-000000000508', 'dddddddd-0000-0000-0000-000000000408',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000201',
 'cccccccc-0000-0000-0000-000000000301', 'MOTORCYCLE', 'SUGGESTED', 'seed', 'ACCEPTED', NOW() - INTERVAL '30 minutes'),
('eeeeeeee-0000-0000-0000-000000000509', 'dddddddd-0000-0000-0000-000000000409',
 '00000000-0000-0000-0000-000000000001', NULL,
 'cccccccc-0000-0000-0000-000000000304', 'DRONE',      'AUTOMATIC', 'seed', 'ACCEPTED', NOW() - INTERVAL '10 minutes'),
('eeeeeeee-0000-0000-0000-000000000510', 'dddddddd-0000-0000-0000-000000000410',
 '00000000-0000-0000-0000-000000000001', 'bbbbbbbb-0000-0000-0000-000000000204',
 'cccccccc-0000-0000-0000-000000000305', 'ROBOT',      'AUTOMATIC', 'seed', 'ASSIGNED', NULL);

-- ---- Tracking events (light dusting) ---------------------------------------
INSERT INTO nhume_delivery_tracking_events
   (delivery_id, captured_at, event_kind, lat, lng, source)
VALUES
('dddddddd-0000-0000-0000-000000000403', NOW() - INTERVAL '40 minutes', 'PICKED_UP', -17.8500, 31.0200, 'NHUME'),
('dddddddd-0000-0000-0000-000000000403', NOW() - INTERVAL '20 minutes', 'LOCATION',  -18.5000, 30.5000, 'NHUME'),
('dddddddd-0000-0000-0000-000000000403', NOW() - INTERVAL '5 minutes',  'LOCATION',  -19.5000, 29.5000, 'NHUME'),
('dddddddd-0000-0000-0000-000000000404', NOW() - INTERVAL '15 minutes', 'PICKED_UP', -18.0090, 31.0810, 'NHUME'),
('dddddddd-0000-0000-0000-000000000408', NOW() - INTERVAL '20 minutes', 'PICKED_UP', -20.1500, 28.5800, 'NHUME'),
('dddddddd-0000-0000-0000-000000000408', NOW() - INTERVAL '2 minutes',  'LOCATION',  -20.1475, 28.5900, 'NHUME'),
('dddddddd-0000-0000-0000-000000000409', NOW() - INTERVAL '8 minutes',  'LAUNCHED', -17.7900, 31.0410, 'AUTONOMOUS:sim-drone-1'),
('dddddddd-0000-0000-0000-000000000409', NOW() - INTERVAL '4 minutes',  'LOCATION', -17.8400, 31.0100, 'AUTONOMOUS:sim-drone-1');

-- ---- Proof of delivery (one delivered) -------------------------------------
INSERT INTO nhume_delivery_proofs
   (proof_id, delivery_id, proof_stage, proof_method, captured_by, otp_code, verified)
VALUES
('ffffffff-0000-0000-0000-000000000601', 'dddddddd-0000-0000-0000-000000000405',
 'DELIVERY', 'OTP', 'bbbbbbbb-0000-0000-0000-000000000203', '417823', TRUE);

-- ---- Chain-of-custody for cold-chain and lab samples -----------------------
INSERT INTO nhume_chain_of_custody_events
   (coc_id, delivery_id, sequence_no, event_kind, custody_holder, custody_location, seal_id, temperature_c, actor_type)
VALUES
('11111111-0000-0000-0000-000000000701', 'dddddddd-0000-0000-0000-000000000403', 1, 'SEALED',
 'WH:NATPHARM', 'Lytton Rd, Harare', 'SEAL-COLD-1003', 5.5, 'OPERATOR'),
('11111111-0000-0000-0000-000000000702', 'dddddddd-0000-0000-0000-000000000403', 2, 'PICKED_UP',
 'DRV:Rumbidzai', 'Lytton Rd, Harare', 'SEAL-COLD-1003', 5.7, 'COURIER'),
('11111111-0000-0000-0000-000000000703', 'dddddddd-0000-0000-0000-000000000404', 1, 'SEALED',
 'Chitungwiza Clinic Lab', 'Chitungwiza', 'SEAL-LAB-1004', 4.2, 'PROVIDER'),
('11111111-0000-0000-0000-000000000704', 'dddddddd-0000-0000-0000-000000000404', 2, 'PICKED_UP',
 'DRV:Rumbidzai', 'Chitungwiza Clinic', 'SEAL-LAB-1004', 4.8, 'COURIER');

-- ---- Exceptions (failed deliveries + a route deviation) --------------------
INSERT INTO nhume_delivery_exceptions
   (exception_id, delivery_id, exception_kind, severity, summary, raised_by)
VALUES
('22222222-0000-0000-0000-000000000801', 'dddddddd-0000-0000-0000-000000000406',
 'DELIVERY_FAILED', 'WARNING', 'Recipient unreachable at delivery window.', 'seed'),
('22222222-0000-0000-0000-000000000802', 'dddddddd-0000-0000-0000-000000000407',
 'DELIVERY_FAILED', 'WARNING', 'Address invalid; vendor requested return.', 'seed');

-- ---- Notifications ledger --------------------------------------------------
INSERT INTO nhume_delivery_notification_events
   (delivery_id, event_code, channel, recipient_ref, template_code, delivery_state)
VALUES
('dddddddd-0000-0000-0000-000000000402', 'DELIVERY_ASSIGNED', 'SMS',   'CRR-001@nhume', 'nhume.delivery.assigned',   'SENT'),
('dddddddd-0000-0000-0000-000000000403', 'DELIVERY_IN_TRANSIT','PUSH', 'Mpilo Cold Chain Officer', 'nhume.delivery.in_transit','SENT'),
('dddddddd-0000-0000-0000-000000000405', 'DELIVERY_COMPLETED','SMS',   '+263772100005', 'nhume.delivery.completed',  'SENT');

-- ---- Autonomous missions ---------------------------------------------------
INSERT INTO nhume_autonomous_missions
   (mission_id, tenant_id, delivery_id, asset_id, provider_code, mission_kind, status, launch_site, landing_site, scheduled_for, simulation_mode, telemetry_json)
VALUES
('33333333-0000-0000-0000-000000000901', '00000000-0000-0000-0000-000000000001',
 'dddddddd-0000-0000-0000-000000000409', 'cccccccc-0000-0000-0000-000000000304',
 'sim-drone-1', 'DRONE', 'EN_ROUTE',
 'Harare Central Pad', 'Glen Norah Helipad', NOW() - INTERVAL '8 minutes',
 TRUE, '{"battery_pct":88,"altitude_m":80,"speed_kph":42}'::jsonb),
('33333333-0000-0000-0000-000000000902', '00000000-0000-0000-0000-000000000001',
 'dddddddd-0000-0000-0000-000000000410', 'cccccccc-0000-0000-0000-000000000305',
 'sim-robot-1', 'ROBOT', 'SCHEDULED',
 'Avondale Pharmacy bay', 'HealthLocker Harare CBD #01', NOW() + INTERVAL '20 minutes',
 TRUE, '{"battery_pct":78,"autonomy_level":"L4"}'::jsonb);
