-- Nompilo guidance seeds for the Theatre & Perioperative Depth domain (WS#6 theatre-depth seam).
-- Reuses the existing guidance.guidance_item registry (V004). inpatient-service owns the procedure
-- episode; OROS owns the order; Madi owns blood; asset-registry owns equipment; Dura owns stock;
-- Varapi owns provider scope; Vashandi owns the team; PCT owns the death pathway; Rito owns safety.
-- Nompilo owns ONLY the guidance catalogue — it never owns the underlying transaction and never
-- overrides clinician judgement. Routes point at real theatre surfaces under /work. Payment is never
-- mentioned — charges never gate clinical theatre care.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Theatre dashboard / queue orientation --------------------------------------------------
    ('THEATRE_QUEUE_OVERVIEW', 'theatre', 'ORIENTATION', '/work/clinical/theatre', 'PROVIDER', 2, 'first_time',
     'Theatre list',
     'This is the theatre and perioperative board. Cases arrive from a clinician''s PROCEDURE order, are triaged by urgency, then checked for readiness — room, team, equipment, anaesthesia, packs and blood — before booking. Take each step in order; Nompilo will flag what is still blocking a case from going to theatre.',
     'Open the theatre list', '/work/clinical/theatre', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 50, FALSE),

    -- Triage --------------------------------------------------------------------------------
    ('THEATRE_TRIAGE_GUIDANCE', 'theatre', 'NEXT_BEST_ACTION', '/work/clinical/theatre/[id]', 'PROVIDER', 2, 'context_view',
     'Triaging the case',
     'Set the urgency honestly: immediate and emergency cases go ahead of elective and day-case work, and shorten or waive parts of the pre-op pathway under an audited override. The triage you set drives the queue order and the readiness gates that follow.',
     'Set triage priority', '/work/clinical/theatre/[id]', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, TRUE),

    -- Readiness blockers — explain the safe-fail plainly ------------------------------------
    ('THEATRE_READINESS_BLOCKED', 'theatre', 'RISK_PROMPT', '/work/clinical/theatre/[id]', 'PROVIDER', 2, 'readiness_blocked',
     'This case is not ready yet',
     'One or more owners report a blocker — a missing room, an unconfirmed team or surgeon scope, equipment not ready, no anaesthesia clearance, or blood not arranged. Each blocker names its owner so you know who to chase. The booking will not pretend a resource is ready; resolve the blockers or, for a true emergency, book with a recorded override and reason.',
     'View readiness blockers', '/work/clinical/theatre/[id]', 'inpatient', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 15, TRUE),

    -- Consent + emergency exception ---------------------------------------------------------
    ('THEATRE_CONSENT_GUIDANCE', 'theatre', 'PROTOCOL', '/work/clinical/theatre/[id]', 'PROVIDER', 2, 'context_view',
     'Confirming consent',
     'Surgical consent must be granted with evidence before a planned case starts. In a life-saving emergency where consent cannot be obtained, you may proceed under the emergency exception — record the reason; it is audited. Consent is never skipped silently.',
     'Review consent', '/work/clinical/theatre/[id]', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 35, TRUE),

    -- WHO Surgical Safety Checklist ---------------------------------------------------------
    ('THEATRE_WHO_CHECKLIST', 'theatre', 'PROTOCOL', '/work/clinical/theatre/[id]/checklist', 'PROVIDER', 2, 'context_view',
     'WHO Surgical Safety Checklist',
     'Complete Sign In before induction, Time Out before incision, and Sign Out before the patient leaves theatre. The case will not start until Sign In is complete unless you record an emergency override with a reason. The checklist is the single biggest preventer of avoidable surgical harm — do not rush it.',
     'Open the checklist', '/work/clinical/theatre/[id]/checklist', 'inpatient', 'nompilo.guidance.context.view', FALSE, 'fundo-who-surgical-checklist', 'WARN', 20, TRUE),

    -- Operative note ------------------------------------------------------------------------
    ('THEATRE_OPERATIVE_NOTE', 'theatre', 'NEXT_BEST_ACTION', '/work/clinical/theatre/[id]/note', 'PROVIDER', 2, 'context_view',
     'Writing the operative note',
     'Record what was done: the procedure performed, findings, any specimens, implants, estimated blood loss, complications, the count, and the post-operative plan. Draft it, then sign it — only a clinician with an active surgical scope can sign, and signing writes the procedure to the patient''s clinical record.',
     'Open the operative note', '/work/clinical/theatre/[id]/note', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 35, TRUE),

    -- PACU / recovery -----------------------------------------------------------------------
    ('THEATRE_PACU_GUIDANCE', 'theatre', 'NEXT_BEST_ACTION', '/work/clinical/theatre/[id]/recovery', 'PROVIDER', 2, 'context_view',
     'Recovery and handover',
     'Record the recovery handover, observations and recovery score, and decide the destination — ward, ICU, discharge, or transfer. Confirm the patient meets discharge-from-recovery criteria before they leave. If the patient deteriorates, escalate; if a death occurs, it routes to the death pathway with dignity.',
     'Open recovery', '/work/clinical/theatre/[id]/recovery', 'inpatient', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 35, TRUE),

    -- Safety event + death routing ----------------------------------------------------------
    ('THEATRE_SAFETY_EVENT', 'theatre', 'RISK_PROMPT', '/work/clinical/theatre/[id]', 'PROVIDER', 2, 'safety_event',
     'Reporting a theatre safety event',
     'If something went wrong — an adverse event, a transfusion reaction, a device incident, a medication error, a retained item, or a death — report it. It is routed to the right owner (Rito for quality and safety, Madi for blood, asset-registry for devices, the death pathway for a death). Reporting is never blocked and never counts against you; it is how harm is prevented next time.',
     'Report a safety event', '/work/clinical/theatre/[id]', 'rito', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 15, TRUE);
