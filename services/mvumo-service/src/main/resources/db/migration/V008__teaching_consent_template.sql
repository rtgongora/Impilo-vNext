-- TM-B16 (B16-1): teaching-consent template — DISTINCT from care/telemedicine/av_recording consent.
-- Spec §17: teaching consent is its own MVUMO template/scoped directive; learning artefacts must
-- never proceed on care-consent alone (§8 rows 18-19). Live-service's replay adoption gate
-- (live.learning.teaching-consent.mode) references directives issued from this template.
INSERT INTO mvumo.consent_template (id, tenant_id, template_key, version, language, consent_type, required_assurance, title, body_markdown, allowed_methods, zibo_concept_code, remote_allowed, offline_allowed)
VALUES
    ('11111111-1111-1111-1111-111111111120', '00000000-0000-0000-0000-000000000000', 'teaching_recording', 1, 'en', 'TEACHING_RECORDING_USE', 'L2_VERIFIED',
     'Teaching & learning use of session recording',
     E'## Teaching use of this recording\n\nThis consent is SEPARATE from your consent to care and from consent to record.\n\nIf you agree, the recording of this session may be used as a **learning artefact** for training health workers. It will be held in the learning platform (never your clinical record), access is audited, and you may withdraw this permission at any time without affecting your care.',
     '["PORTAL_CONFIRM","OTP","PIN","TABLET_SIGNATURE","ASSISTED_FACILITY"]'::jsonb, 'mvumo.teaching_recording', true, true)
ON CONFLICT (id) DO NOTHING;
