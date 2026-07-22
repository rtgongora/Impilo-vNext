-- TM-B10: Nompilo telemedicine guidance — route-bound §P items across the teleconsult stages
-- (spec §P sections; gap R27). Doctrine: guidance ONLY — Nompilo never diagnoses, prescribes,
-- alters clinical facts or creates unreviewed orders; danger-sign items are ESCALATE-ONLY
-- (route to the emergency path, audited, zero diagnostic wording). Every CTA points at a real
-- sovereign surface. Rides the V004 registry + prefix-match engine unchanged.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Stage 1 — entry / identity (citizen virtual-care entry) ---------------------------
    ('TELEMED_IDENTITY_CONFIRM', 'telemedicine', 'ORIENTATION', '/citizen/virtual-care', 'CITIZEN', NULL, 'always',
     'Why we confirm who you are',
     'Confirming your identity keeps your health record yours — the clinician you meet sees the right history, and no one else''s. It takes a moment and protects you.',
     NULL, NULL, 'vito', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 60, FALSE),

    ('TELEMED_ID_RECOVERY', 'telemedicine', 'RECOVERY', '/citizen/virtual-care', 'CITIZEN', NULL, 'id_unknown',
     'Lost track of your Impilo ID?',
     'You can recover your Impilo ID with the phone number or email you registered. Recovery never blocks urgent care — you can still ask for help first.',
     'Recover my ID', '/citizen/health-id', 'vito', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 50, FALSE),

    ('TELEMED_PROXY_ACCESS', 'telemedicine', 'ORIENTATION', '/citizen/virtual-care', 'CITIZEN', NULL, 'proxy_request',
     'Asking for someone else?',
     'A parent, guardian or caregiver can request a consult on someone''s behalf. The visit stays on the patient''s record and your role is noted — that protects both of you.',
     NULL, NULL, 'mvumo', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 45, TRUE),

    ('TELEMED_CONSENT_PLAIN', 'telemedicine', 'STATUS_EXPLANATION', '/citizen/virtual-care', 'CITIZEN', NULL, 'consent_pending',
     'What you are agreeing to',
     'Consent here means: you agree to a remote consultation, and to the clinician seeing the notes you share. You can change your mind at any time — even during the call — without losing your care.',
     NULL, NULL, 'mvumo', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 55, FALSE),

    -- Stage 5 — waiting room + in-consult (patient) -------------------------------------
    ('TELEMED_WAITING_STATUS', 'telemedicine', 'STATUS_EXPLANATION', '/my/telehealth', 'CITIZEN', NULL, 'waiting_room',
     'What your place in the queue means',
     'Your request is with the care team. The wait shown is an honest estimate, not a promise — urgent cases may be seen first. You will be admitted by the clinician when it is your turn.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, FALSE),

    ('TELEMED_DEVICE_CHECK', 'telemedicine', 'CHECKLIST_ITEM', '/my/telehealth', 'CITIZEN', NULL, 'device_check',
     'Quick camera and sound check',
     'Before the call: is your camera uncovered, your volume up, and your browser allowed to use the microphone? If video struggles, the visit can step down to audio or secure messages — you will not lose your place.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 42, FALSE),

    ('TELEMED_PRIVACY', 'telemedicine', 'ORIENTATION', '/my/telehealth', 'CITIZEN', NULL, 'always',
     'Find a private spot',
     'Your consult is confidential. If you can, move somewhere others cannot overhear. Headphones help. Only people you have agreed to (like a caregiver or interpreter) join the call.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 38, FALSE),

    ('TELEMED_DANGER_SIGNS', 'telemedicine', 'ESCALATION', '/my/telehealth', 'CITIZEN', NULL, 'always',
     'If this becomes an emergency',
     'If you have crushing chest pain, severe bleeding, serious trouble breathing, or someone becomes unresponsive — do not wait for the video call. Use the emergency option now. Nompilo cannot judge symptoms; emergency responders can.',
     'Get emergency help', '/emergency', 'daidzai', 'nompilo.guidance.context.view', FALSE, NULL, 'CRITICAL', 5, TRUE),

    ('TELEMED_RECONNECT', 'telemedicine', 'RECOVERY', '/my/telehealth', 'CITIZEN', NULL, 'connection_lost',
     'Lost the connection?',
     'Rejoin from this page — your visit is still open. If video will not come back, the consult continues by audio or secure message and everything you send is saved to your record.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'WARN', 20, FALSE),

    ('TELEMED_POST_SESSION', 'telemedicine', 'NEXT_BEST_ACTION', '/my/telehealth', 'CITIZEN', NULL, 'session_ended',
     'What happens after your visit',
     'Your clinician''s summary, any tasks (like tests to attend), and follow-up messages appear here and in your record. If something was promised and does not arrive, you can report it.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 35, FALSE),

    -- Stage 2 — referral composition (provider) -----------------------------------------
    ('TELEMED_GOOD_QUESTION', 'telemedicine', 'CHECKLIST_ITEM', '/telemedicine/new', 'PROVIDER', NULL, 'always',
     'A sharp clinical question travels well',
     'One clear question, the working context, and what you have already tried — that is what makes a remote colleague fast and useful. Advisory only: you decide what goes in the package.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, FALSE),

    ('TELEMED_ATTACH_QUALITY', 'telemedicine', 'CHECKLIST_ITEM', '/telemedicine/new', 'PROVIDER', NULL, 'attachments_present',
     'Attachment quality check',
     'Well-lit, in-focus photos and legible documents prevent a round-trip for re-capture. Check each attachment renders before you submit.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 30, FALSE),

    -- Stage 4/5 — provider console (advisory-only) ---------------------------------------
    ('TELEMED_PROVIDER_REDFLAG', 'telemedicine', 'RISK_PROMPT', '/telemedicine/session', 'PROVIDER', NULL, 'always',
     'Review stated danger signs',
     'The package and chat may contain patient-stated danger signs. This prompt is advisory only — it never replaces your clinical judgement, and Nompilo makes no clinical assessment.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'WARN', 15, TRUE),

    ('TELEMED_DECLINE_EXPLAIN', 'telemedicine', 'STATUS_EXPLANATION', '/work/telemedicine', 'PROVIDER', NULL, 'declined_cases',
     'Declines help the referrer when explained',
     'A decline with a clear reason (wrong specialty, more information needed) routes the case onward fast. Unexplained declines strand patients — the reason you record is shown to the referrer.',
     NULL, NULL, 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 25, FALSE);
