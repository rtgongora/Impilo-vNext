-- Nompilo guidance seeds for the Full Inpatient Suite domain (WS#6).
-- Reuses the existing guidance.guidance_item registry (V004). inpatient-service + PCT own the inpatient
-- truth; Nompilo (guidance-service) owns the guidance catalogue. Routes point at real WS#6 surfaces under
-- /clinical/inpatient. Each item routes to a sovereign owner (inpatient / oros / inventory / rito /
-- khuluma / butano) — Nompilo never owns the underlying transaction and never overrides clinical judgement.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Bed board orientation -------------------------------------------------------------
    ('INPATIENT_BEDBOARD_WELCOME', 'inpatient', 'ORIENTATION', '/clinical/inpatient/ward-board', 'PROVIDER', 2, 'first_time',
     'The bed board is your live ward census',
     'Beds show real occupancy from the inpatient census. Assignment runs a safety check (same-sex bay, age group, isolation, oxygen, monitoring, ICU) and will block an unsafe placement. Facility and ward identity stay with Tuso; the census stays with inpatient.',
     'Open bed board', '/clinical/inpatient/ward-board', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    -- Admission decision vs census ------------------------------------------------------
    ('INPATIENT_ADMISSION_HANDSHAKE', 'inpatient', 'STATUS_EXPLANATION', '/clinical/inpatient/admissions', 'PROVIDER', 2, 'context_view',
     'Admission decision and bed census are two steps',
     'PCT owns the admission decision (request → approve). On approval the inpatient census opens the bed automatically and links back. If a patient is approved but not yet on a bed, the handshake is mid-flight — it is not a duplicate record.',
     'View admissions', '/clinical/inpatient/admissions', 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 45, FALSE),

    -- Deterioration / EWS escalation ----------------------------------------------------
    ('INPATIENT_EWS_ESCALATION', 'inpatient', 'ESCALATION', '/clinical/inpatient/nursing', 'PROVIDER', 2, 'ews_high',
     'A high EWS raises a timed escalation',
     'Recording an Early Warning Score at or above the threshold raises a deterioration escalation with a response deadline and a ward alert. If no clinician responds in time it becomes overdue and a Rito safety case is opened. Acknowledge and respond promptly — Nompilo never overrides your judgement.',
     'Open nursing board', '/clinical/inpatient/nursing', 'rito', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 20, TRUE),

    -- eMAR five-rights + stock ----------------------------------------------------------
    ('INPATIENT_EMAR_SAFETY', 'inpatient', 'LOCKED_STATE', '/clinical/inpatient/nursing', 'PROVIDER', 2, 'med_round',
     'Confirm the five rights before administering',
     'Medication administration requires confirming the five rights (right patient, drug, dose, route, time). A drug that does not match the chart is rejected. On administration the ward stock decrement is recorded against inventory (Dura) — inpatient records the administration, inventory owns the stock.',
     'Open medication round', '/clinical/inpatient/nursing', 'inventory', 'nompilo.guidance.locked.explain', FALSE, NULL, 'WARN', 25, TRUE),

    -- Orders route to OROS --------------------------------------------------------------
    ('INPATIENT_ORDERS_OROS', 'inpatient', 'STATUS_EXPLANATION', '/clinical/inpatient/rounds', 'PROVIDER', 2, 'order_placed',
     'Ward orders route to OROS',
     'Labs, imaging, medications and consults placed from the ward are routed to OROS, the orders engine — inpatient never owns orders. Results return to the patient chart. Blood requests route on to Madi.',
     'Open ward round', '/clinical/inpatient/rounds', 'oros', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, FALSE),

    -- Discharge gating ------------------------------------------------------------------
    ('INPATIENT_DISCHARGE_GATING', 'inpatient', 'LOCKED_STATE', '/clinical/inpatient/discharge/[admissionId]', 'PROVIDER', 2, 'discharge_blocked',
     'Discharge is blocked until clearances are complete',
     'A discharge summary cannot be finalised while any clearance (clinical, nursing, pharmacy, lab, imaging, financial, administrative, records, CRVS) is still pending. Clear or waive each one. On finalisation the summary is projected to the shared record (Butano) and a follow-up is requested from Khuluma.',
     'Open discharge', '/clinical/inpatient/discharge/[admissionId]', 'inpatient', 'nompilo.guidance.locked.explain', TRUE, NULL, 'WARN', 20, TRUE),

    -- Patient self-view -----------------------------------------------------------------
    ('INPATIENT_MY_STAY', 'inpatient', 'ORIENTATION', '/citizen/inpatient/[admissionRef]', 'CITIZEN', 1, 'first_time',
     'Your inpatient stay',
     'This shows your current admission — your ward, your care team and your discharge plan as the ward records them. Your clinical detail stays governed; you see a person-centred summary of your stay.',
     'View my stay', '/citizen/inpatient/[admissionRef]', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 55, FALSE);
