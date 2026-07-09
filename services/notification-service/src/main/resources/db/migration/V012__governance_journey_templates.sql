-- Governance + journey-completion templates (provider onboarding, access decisions,
-- learning assignment, teleconsult invitations, diagnostic result readiness, facility claims).
--
-- Patient-safe rule (binding for every patient-facing SMS/EMAIL body below):
-- external channels carry ONLY a "you have a message / result is ready — sign in" prompt.
-- No clinical content, no diagnosis, no provider identity, and never a credential or password.
-- Activation links/codes are time-boxed and single-use; credentials are owned by Keycloak.

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'GOVERNANCE_ONBOARDING_INVITATION', 'EMAIL',
 'Onboarding invitation', 'Your Impilo onboarding invitation',
 'Hello {{displayName}}, you have been invited to Impilo ({{invitationType}}). Open {{activationUrl}} to activate your account and set your own password. This invitation expires {{expiresAt}}. If you did not expect this, contact the helpdesk.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'PROVIDER_ID_ISSUED', 'IN_APP',
 'Provider ID issued', 'Your Provider ID has been issued',
 'Your Impilo Provider ID {{providerId}} is now active. Open My Professional to review your profile, licensure and workplace context.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'PROVIDER_ID_ISSUED_EMAIL', 'EMAIL',
 'Provider ID issued email', 'Your Impilo Provider ID',
 'Hello {{displayName}}, your Impilo Provider ID {{providerId}} has been issued. Sign in to activate your professional profile and select your workplace. If you do not yet have an account, use your activation invitation. Expiry for pending activation: {{expiresAt}}.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ACCESS_REQUEST_APPROVED', 'IN_APP',
 'Access request approved', 'Your access request was approved',
 'Your {{requestType}} access request has been approved. {{nextStep}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ACCESS_REQUEST_APPROVED_EMAIL', 'EMAIL',
 'Access request approved email', 'Impilo access request approved',
 'Hello {{displayName}}, your {{requestType}} access request has been approved. Sign in to Impilo to continue. {{nextStep}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ACCESS_REQUEST_REJECTED', 'IN_APP',
 'Access request declined', 'Your access request was declined',
 'Your {{requestType}} access request was declined. Reason: {{reason}}. You may revise and resubmit, or contact your administrator.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ACCESS_REQUEST_REJECTED_EMAIL', 'EMAIL',
 'Access request declined email', 'Impilo access request decision',
 'Hello {{displayName}}, your {{requestType}} access request was declined. Sign in to Impilo to view the reason and next steps.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'COURSE_ASSIGNED', 'IN_APP',
 'Course assigned', 'A course was assigned to you',
 '{{courseTitle}} has been assigned to you{{dueClause}}. Open Impilo Fundo to start learning.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_INVITE', 'IN_APP',
 'Teleconsultation invitation', 'You are invited to a teleconsultation',
 'You have a teleconsultation scheduled for {{scheduledAt}}. Open Impilo Telemedicine to join when it starts.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_INVITE_SMS', 'SMS',
 'Teleconsultation invite SMS', 'Impilo teleconsultation',
 'Impilo: you have a teleconsultation at {{scheduledAt}}. Sign in to Impilo to join.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'DIAGNOSTIC_RESULT_READY', 'IN_APP',
 'Result ready', 'A result is ready for you',
 'A result from your recent visit is ready. Open your Impilo record to view it, or ask at your facility.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'DIAGNOSTIC_RESULT_READY_SMS', 'SMS',
 'Result ready SMS', 'Impilo message',
 'Impilo: you have a new message about your recent visit. Sign in to Impilo to view it.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_CLAIM_DECISION', 'IN_APP',
 'Facility claim decision', 'Facility claim decision',
 'Your administration claim for {{facilityName}} is {{decision}}. {{nextStep}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_CLAIM_DECISION_EMAIL', 'EMAIL',
 'Facility claim decision email', 'Impilo facility claim decision',
 'Hello {{displayName}}, your administration claim for {{facilityName}} is {{decision}}. Sign in to Impilo to view details and next steps.',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
