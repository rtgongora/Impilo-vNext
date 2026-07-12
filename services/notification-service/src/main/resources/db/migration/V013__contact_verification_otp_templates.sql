-- Gateway W1 Workstream B (R1 "Reachable" rung): OTP delivery templates.
--
-- CONTACT_VERIFY_OTP / CONTACT_VERIFY_OTP_EMAIL back the experience-bff
-- contact-verification flow (/internal/v1/auth/contact/otp/*).
--
-- STEP_UP_OTP is the tshepo-authz SMS_OTP step-up default template key
-- (tshepo.authz.otp-delivery.template-key). It was never seeded, so a step-up
-- OTP enqueued via /internal/v1/notify failed at the worker with
-- "Template not found: STEP_UP_OTP" — seeding it here closes that latent gap.

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'CONTACT_VERIFY_OTP', 'SMS',
 'Contact verification code (SMS)', 'Impilo verification code',
 'Impilo: your verification code is {{otp}}. It expires in 5 minutes. If you did not request it, ignore this message.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'CONTACT_VERIFY_OTP_EMAIL', 'EMAIL',
 'Contact verification code (email)', 'Your Impilo verification code',
 'Your Impilo verification code is {{otp}}. It expires in 5 minutes. If you did not request this code, you can safely ignore this email.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'STEP_UP_OTP', 'SMS',
 'Step-up security code (SMS)', 'Impilo security code',
 'Impilo security code: {{otp}}. Valid for 5 minutes. Never share this code with anyone.',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT (key) DO NOTHING;
