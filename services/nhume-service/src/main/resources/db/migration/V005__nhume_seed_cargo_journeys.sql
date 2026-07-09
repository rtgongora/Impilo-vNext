-- Cargo-variety demo journeys — proves Nhume moves more than medicines/patients.
-- Mirrors the exact column contract of V003 (known-good). Rows are is_demo=TRUE
-- and carry metadata_json.links so the delivery-detail "Linked records" panel,
-- the inbound handover queue and analytics all show realistic multi-cargo data.
-- Zimbabwe referral geography (Harare / district).

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
    clinical_context_ref, programme_context_ref,
    metadata_json, is_demo, created_by, telehealth_session_ref, marketplace_order_ref)
VALUES
-- Specimen: TB sputum, clinic -> district lab (en route; chain of custody).
('dddddddd-0000-0000-0000-000000000501', '00000000-0000-0000-0000-000000000001', 'NHM-2001',
 'IN_TRANSIT', 'URGENT', 'LAB_SAMPLE_PICKUP', 'LABORATORY',
 'FACILITY', 'TUSO:FAC-MBARE-POLY',
 'FACILITY', 'TUSO:FAC-MBARE-POLY', 'Mbare Polyclinic', 'Mbare, Harare', -17.8560, 31.0330,
 'FACILITY', 'TUSO:FAC-HARARE-CENTRAL-LAB', 'Harare Central Lab', 'Harare Central Hospital', -17.8300, 31.0480,
 'FACILITY', 'Harare Central Laboratory', '+263772200001',
 FALSE, 'NONE', FALSE, FALSE, TRUE, FALSE,
 '["MOTORCYCLE","SPECIMEN_TRANSPORT_VEHICLE"]'::jsonb, 'demo-policy-specimen', 'demo-sla-urgent', NOW() + INTERVAL '2 hours',
 'OROS:ORD-TB-4471', NULL,
 '{"is_seed":true,"cargoProfile":"SPECIMEN","cargo":{"specimenType":"SPUTUM","numberOfSpecimens":4},"links":{"orosOrderRef":"OROS:ORD-TB-4471"}}'::jsonb,
 TRUE, 'seed', NULL, NULL),

-- Blood: O-negative emergency, provincial blood bank -> district hospital (en route; cold-chain + custody).
('dddddddd-0000-0000-0000-000000000502', '00000000-0000-0000-0000-000000000001', 'NHM-2002',
 'IN_TRANSIT', 'EMERGENCY', 'BLOOD_PRODUCT', 'FACILITY_OPERATIONS',
 'FACILITY', 'TUSO:FAC-NBSZ-HARARE',
 'FACILITY', 'TUSO:FAC-NBSZ-HARARE', 'NBSZ Harare Blood Bank', 'Harare', -17.8210, 31.0410,
 'FACILITY', 'TUSO:FAC-CHITUNGWIZA-CENTRAL', 'Chitungwiza Central Hospital', 'Chitungwiza', -18.0130, 31.0750,
 'FACILITY', 'Chitungwiza Blood Fridge (authorised staff)', '+263772200002',
 FALSE, 'OTP', TRUE, FALSE, TRUE, FALSE,
 '["BLOOD_TRANSPORT_VEHICLE","COLD_CHAIN_VEHICLE"]'::jsonb, 'demo-policy-blood', 'demo-sla-urgent', NOW() + INTERVAL '90 minutes',
 'MADI:BLD-9920', NULL,
 '{"is_seed":true,"cargoProfile":"BLOOD","cargo":{"bloodGroup":"O_NEG","units":2,"productType":"PACKED_CELLS"},"links":{"madiOrderRef":"MADI:BLD-9920"}}'::jsonb,
 TRUE, 'seed', NULL, NULL),

-- Vaccine: measles-rubella, district cold room -> outreach site (arrived; awaiting handover; cold-chain).
('dddddddd-0000-0000-0000-000000000503', '00000000-0000-0000-0000-000000000001', 'NHM-2003',
 'AT_DESTINATION', 'STANDARD', 'VACCINE', 'PUBLIC_HEALTH',
 'FACILITY', 'TUSO:FAC-EPI-DISTRICT-STORE',
 'FACILITY', 'TUSO:FAC-EPI-DISTRICT-STORE', 'District EPI Cold Room', 'Harare', -17.8600, 31.0300,
 'ADDRESS', 'site:epworth-outreach', 'Epworth Outreach Point', 'Epworth', -17.8900, 31.1470,
 'FACILITY', 'Cold-chain focal person', '+263772200003',
 FALSE, 'NONE', TRUE, FALSE, FALSE, FALSE,
 '["COLD_CHAIN_VEHICLE","MOTORCYCLE"]'::jsonb, 'demo-policy-vaccine', 'demo-sla-standard', NOW() + INTERVAL '3 hours',
 NULL, 'EPI:CAMPAIGN-MR-2026',
 '{"is_seed":true,"cargoProfile":"VACCINE","cargo":{"vaccineType":"Measles-Rubella","doses":200,"batchNumber":"MR-2026-118","temperatureRange":"2C_TO_8C"},"links":{"campaignRef":"EPI:CAMPAIGN-MR-2026"}}'::jsonb,
 TRUE, 'seed', NULL, NULL),

-- Commodity: emergency oxytocin resupply, district store -> clinic (assigned; Dura requisition link).
('dddddddd-0000-0000-0000-000000000504', '00000000-0000-0000-0000-000000000001', 'NHM-2004',
 'ASSIGNED', 'URGENT', 'PROGRAMME_COMMODITY', 'PHARMACY',
 'FACILITY', 'TUSO:FAC-DISTRICT-PHARMACY-STORE',
 'FACILITY', 'TUSO:FAC-DISTRICT-PHARMACY-STORE', 'District Pharmacy Store', 'Harare', -17.8400, 31.0350,
 'FACILITY', 'TUSO:FAC-HATCLIFFE-CLINIC', 'Hatcliffe Clinic', 'Hatcliffe, Harare', -17.6800, 31.0900,
 'FACILITY', 'Hatcliffe Clinic pharmacy', '+263772200004',
 FALSE, 'NONE', FALSE, TRUE, FALSE, FALSE,
 '["VAN","MOTORCYCLE"]'::jsonb, 'demo-policy-commodity', 'demo-sla-urgent', NOW() + INTERVAL '5 hours',
 NULL, 'DURA:REQ-OXY-3310',
 '{"is_seed":true,"cargoProfile":"COMMODITY","cargo":{"commodityCategory":"Oxytocin 10IU","stockoutRisk":true},"links":{"duraRequisitionRef":"DURA:REQ-OXY-3310"}}'::jsonb,
 TRUE, 'seed', NULL, NULL),

-- Patient transfer: obstructed labour, clinic -> district hospital (en route; PCT referral link).
('dddddddd-0000-0000-0000-000000000505', '00000000-0000-0000-0000-000000000001', 'NHM-2005',
 'IN_TRANSIT', 'EMERGENCY', 'FACILITY_TRANSFER', 'FACILITY_OPERATIONS',
 'FACILITY', 'TUSO:FAC-HATCLIFFE-CLINIC',
 'FACILITY', 'TUSO:FAC-HATCLIFFE-CLINIC', 'Hatcliffe Clinic', 'Hatcliffe, Harare', -17.6800, 31.0900,
 'FACILITY', 'TUSO:FAC-HARARE-CENTRAL', 'Harare Central Hospital', 'Harare', -17.8300, 31.0480,
 'FACILITY', 'Maternity receiving (Harare Central)', '+263772200005',
 TRUE, 'OTP', FALSE, FALSE, FALSE, FALSE,
 '["AMBULANCE"]'::jsonb, 'demo-policy-patient', 'demo-sla-urgent', NOW() + INTERVAL '1 hour',
 'PCT:REF-MAT-8801', NULL,
 '{"is_seed":true,"cargoProfile":"PATIENT","cargo":{"chiefComplaint":"Obstructed labour","receivingService":"Maternity"},"links":{"pctReferralRef":"PCT:REF-MAT-8801"}}'::jsonb,
 TRUE, 'seed', NULL, NULL),

-- Equipment: oxygen concentrator, district hospital -> clinic (delivered; feeds analytics; fragile + return).
('dddddddd-0000-0000-0000-000000000506', '00000000-0000-0000-0000-000000000001', 'NHM-2006',
 'DELIVERED', 'STANDARD', 'EQUIPMENT', 'FACILITY_OPERATIONS',
 'FACILITY', 'TUSO:FAC-HARARE-CENTRAL',
 'FACILITY', 'TUSO:FAC-HARARE-CENTRAL', 'Harare Central Hospital (biomed)', 'Harare', -17.8300, 31.0480,
 'FACILITY', 'TUSO:FAC-MBARE-POLY', 'Mbare Polyclinic', 'Mbare, Harare', -17.8560, 31.0330,
 'FACILITY', 'Mbare Polyclinic (biomed technician)', '+263772200006',
 FALSE, 'NONE', FALSE, FALSE, FALSE, TRUE,
 '["VAN","UTILITY_VEHICLE"]'::jsonb, 'demo-policy-equipment', 'demo-sla-standard', NOW() - INTERVAL '1 hour',
 NULL, NULL,
 '{"is_seed":true,"cargoProfile":"EQUIPMENT","cargo":{"equipmentType":"Oxygen concentrator","requiresTechnician":true,"returnRequired":true}}'::jsonb,
 TRUE, 'seed', NULL, NULL);
