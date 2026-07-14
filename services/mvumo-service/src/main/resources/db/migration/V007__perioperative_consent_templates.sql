-- mvumo-service V007 — perioperative consent depth (Wave 2 elective theatre)
-- Seeds versioned PROCEDURE / ANAESTHESIA / TRANSFUSION consent templates and
-- extends consent_request with perioperative attributes. MVUMO stays the consent
-- SoR; multiple consents per episode are keyed by workflow_ref = episode:{id}.

ALTER TABLE mvumo.consent_request
    ADD COLUMN IF NOT EXISTS consenter_type        VARCHAR(24) NOT NULL DEFAULT 'PATIENT',
        -- PATIENT | GUARDIAN | PROXY
    ADD COLUMN IF NOT EXISTS interpreter_ref       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS emergency_exception   BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS withdrawn_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS replaced_by_request_id UUID,
    ADD COLUMN IF NOT EXISTS version               INTEGER NOT NULL DEFAULT 1;

-- Versioned perioperative templates (platform-default tenant).
INSERT INTO mvumo.consent_template
    (id, tenant_id, template_key, version, language, consent_type, required_assurance,
     title, body_markdown, allowed_methods, remote_allowed, offline_allowed)
VALUES
    ('22222222-2222-2222-2222-222222222201', '00000000-0000-0000-0000-000000000000',
     'perioperative_procedure', 1, 'en', 'CLINICAL_SURGICAL_PROCEDURE', 'L3_WITNESSED_HIGH_ASSURANCE',
     'Surgical procedure consent',
     E'## Surgical procedure\n\nName, indication, expected benefit, material risks, alternatives (including no treatment), and the right to withdraw at any time before the procedure.',
     '["TABLET_SIGNATURE","ASSISTED_FACILITY","PAPER_TO_DIGITAL","WITNESSED"]'::jsonb, false, true),
    ('22222222-2222-2222-2222-222222222202', '00000000-0000-0000-0000-000000000000',
     'perioperative_anaesthesia', 1, 'en', 'CLINICAL_ANAESTHESIA', 'L3_WITNESSED_HIGH_ASSURANCE',
     'Anaesthesia consent',
     E'## Anaesthesia\n\nType of anaesthesia, risks (including airway, cardiovascular and awareness risks), post-operative nausea, and the anaesthetic plan discussed at pre-assessment.',
     '["TABLET_SIGNATURE","ASSISTED_FACILITY","WITNESSED"]'::jsonb, false, true),
    ('22222222-2222-2222-2222-222222222203', '00000000-0000-0000-0000-000000000000',
     'perioperative_transfusion', 1, 'en', 'CLINICAL_BLOOD_TRANSFUSION', 'L3_WITNESSED_HIGH_ASSURANCE',
     'Blood transfusion consent',
     E'## Blood transfusion\n\nIndication for transfusion, risks and benefits, alternatives, and the right to refuse. Records whether the patient accepts blood products.',
     '["TABLET_SIGNATURE","ASSISTED_FACILITY","WITNESSED"]'::jsonb, false, true)
ON CONFLICT (tenant_id, template_key, version, language) DO NOTHING;
