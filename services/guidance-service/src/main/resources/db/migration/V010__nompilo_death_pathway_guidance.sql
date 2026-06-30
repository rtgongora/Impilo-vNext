-- Nompilo guidance seeds for the Death & Post-Death Pathway domain (WS#8). Reuses the existing
-- guidance.guidance_item registry (V004). pct-service owns the clinical DeathCase; ubomi-service owns
-- CRVS; Rito owns mortality review; Khuluma owns family comms. Nompilo owns ONLY the guidance
-- catalogue — it never owns the underlying transaction and never overrides clinician judgement.
-- Death guidance is always calm, plain, and respectful ("death confirmed", "your next steps") — never
-- cold or clinical-cold wording. Routes point at real WS#8 surfaces under /work. No payment is ever
-- mentioned — payment never gates death documentation, certification, or registration.

INSERT INTO guidance.guidance_item
    (guidance_key, domain, guidance_type, applies_route, applies_user_type, min_trust_loa, trigger_condition,
     title, body, cta_label, cta_route, owner_service, required_policy, khuluma_follow_up, fundo_content_ref, severity, priority, audit_required)
VALUES
    -- Death cases board: orient the clinician with care -------------------------------------
    ('DEATH_CASES_OVERVIEW', 'death', 'ORIENTATION', '/work/clinical/death-cases', 'PROVIDER', 2, 'first_time',
     'Death cases',
     'This is where deaths confirmed in your facility are recorded and followed through with dignity. Each case moves through confirmation, any required medico-legal or public-health screening, cause-of-death certification, mortuary care, and civil registration. Take the steps in order — Nompilo will flag what is needed next.',
     'Open a death case', '/work/clinical/death-cases', 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 50, FALSE),

    -- Confirmation: the human, careful framing -------------------------------------------
    ('DEATH_CONFIRM_GUIDANCE', 'death', 'NEXT_BEST_ACTION', '/work/clinical/death-cases/[id]', 'PROVIDER', 2, 'context_view',
     'Confirming a death',
     'Record the time, place and context of death, who was present, and whether resuscitation was attempted. If the identity is not known, a temporary reference is created so the person is never lost in the system. Confirmation can only be done by an authorised clinician and every entry is recorded.',
     'Record confirmation', '/work/clinical/death-cases/[id]', 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 40, TRUE),

    -- Medico-legal trigger: explain the hold plainly -------------------------------------
    ('DEATH_MEDICO_LEGAL_HOLD', 'death', 'RISK_PROMPT', '/work/clinical/death-cases/[id]', 'PROVIDER', 2, 'medico_legal_open',
     'This death needs the coroner',
     'Because of how or when this death occurred, the law requires a referral to the competent authority before a routine certificate can be issued and before the body can be released. This is normal and protects the family. The case is held until the authority responds — record the referral and keep the family informed gently.',
     'View referral details', '/work/clinical/death-cases/[id]', 'pct', 'nompilo.guidance.context.view', TRUE, NULL, 'WARN', 15, TRUE),

    -- Cause-of-death certification: WHO sequence help ------------------------------------
    ('DEATH_CERTIFICATION_GUIDANCE', 'death', 'PROTOCOL', '/work/clinical/mortality-certification', 'PROVIDER', 2, 'context_view',
     'Certifying the cause of death',
     'Record the sequence that led to death: the immediate cause, the conditions that gave rise to it, and the single underlying cause. Avoid recording only a mode of dying such as "cardiac arrest" — name the disease or injury behind it. Nompilo will gently flag entries that may need more detail; the judgement is always yours.',
     'Open certification', '/work/clinical/mortality-certification', 'pct', 'nompilo.guidance.context.view', FALSE, 'fundo-death-certification', 'INFO', 35, TRUE),

    -- Mortuary / body release: dignity + the hard block ----------------------------------
    ('DEATH_BODY_RELEASE_GUIDANCE', 'death', 'STATUS_EXPLANATION', '/work/mortuary', 'PROVIDER', 2, 'context_view',
     'Caring for the body and release',
     'The body is received, tagged and stored with respect. Release can only be recorded to a named, authorised recipient, and never while a legal, coroner, or post-mortem hold is in place. When everything is clear, confirm who the body is released to.',
     'Open mortuary', '/work/mortuary', 'pct', 'nompilo.guidance.context.view', FALSE, NULL, 'INFO', 45, TRUE),

    -- CRVS registration: the family-facing next step -------------------------------------
    ('DEATH_CRVS_GUIDANCE', 'death', 'NEXT_BEST_ACTION', '/work/crvs/death-registration', 'PROVIDER', 2, 'context_view',
     'Registering the death',
     'Once the cause of death is certified, the registration package is prepared for the Registrar General so the family can receive a death certificate. Check that the required details are complete — Nompilo will list anything still missing. The registration itself is completed by the civil registry.',
     'Open registration', '/work/crvs/death-registration', 'ubomi', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 40, FALSE),

    -- Family next-of-kin: the gentlest card ----------------------------------------------
    ('DEATH_FAMILY_SUPPORT', 'death', 'ROUTING', '/work/clinical/death-cases/[id]', 'PROVIDER', 2, 'context_view',
     'Supporting the family',
     'When you are ready, a careful, plain-language notification and next-steps guide can be shared with the next of kin through Khuluma. Forensic or post-mortem detail is kept private and is not shown to the family. Take the time the family needs.',
     'Prepare family update', '/work/clinical/death-cases/[id]', 'khuluma', 'nompilo.guidance.context.view', TRUE, NULL, 'INFO', 30, TRUE);
