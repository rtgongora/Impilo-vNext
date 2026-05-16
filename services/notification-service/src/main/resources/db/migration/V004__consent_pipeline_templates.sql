-- ============================================================================
-- V004: Consent pipeline notification templates
--
-- Pre-seed SMS templates for the digital consent pipeline. These templates
-- are used when sending consent requests to feature phone users via SMS.
-- Supports English (en), Shona (sn), and Ndebele (nd).
-- ============================================================================

INSERT INTO ns_templates (
    id,
    tenant_id,
    pod_id,
    key,
    channel,
    name,
    subject,
    content,
    enabled,
    status,
    current_version,
    created_at,
    updated_at
)
VALUES
    -- English consent request
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'consent.request.en', 'SMS', 'Impilo Consent Request (EN)', 'Impilo Consent',
     'IMPILO: To use Impilo health services, please accept our Privacy Policy and Terms of Use. Reply YES to accept or NO to decline. View full policy: {{policy_url}}',
     true, 'PUBLISHED', 1, NOW(), NOW()),

    -- Shona consent request
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'consent.request.sn', 'SMS', 'Impilo Consent Request (SN)', 'Impilo Mvumo',
     'IMPILO: Kuti mushandise masevhisi eImpilo, tapota gamuchira Privacy Policy neTerms of Use. Pindurai YES kugamuchira kana NO kuramba. Onai policy: {{policy_url}}',
     true, 'PUBLISHED', 1, NOW(), NOW()),

    -- Ndebele consent request
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'consent.request.nd', 'SMS', 'Impilo Consent Request (ND)', 'Impilo Imvumo',
     'IMPILO: Ukusebenzisa ama-sevisi e-Impilo, sicela wamukele iPrivacy Policy leTerms of Use. Phendula ngo-YES ukwamukela kumbe ngo-NO ukwala. Bona policy: {{policy_url}}',
     true, 'PUBLISHED', 1, NOW(), NOW()),

    -- Consent confirmation (all languages)
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'consent.accepted.en', 'SMS', 'Impilo Consent Accepted (EN)', 'Impilo Consent Accepted',
     'IMPILO: Thank you. Your consent to the Impilo Privacy Policy and Terms of Use has been recorded. You can manage your consent at any time via Impilo vNext or support@impilo.io.',
     true, 'PUBLISHED', 1, NOW(), NOW()),

    -- Consent declined notification
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'consent.declined.en', 'SMS', 'Impilo Consent Declined (EN)', 'Impilo Consent',
     'IMPILO: We respect your choice. Without consent, some Impilo services may not be available. You can accept at any time by replying YES or contacting support@impilo.io.',
     true, 'PUBLISHED', 1, NOW(), NOW()),

    -- Account deletion confirmation
    (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'account.deletion.confirmed.en', 'SMS', 'Account Deletion Confirmed (EN)', 'Account Deletion',
     'IMPILO: Your account deletion request has been received and will be processed. Some records may be retained as required by law. Contact support@impilo.io for questions.',
     true, 'PUBLISHED', 1, NOW(), NOW())

ON CONFLICT DO NOTHING;
