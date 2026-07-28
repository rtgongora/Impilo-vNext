-- FCV-W6 — the facility journey speaks to the people in it.
--
-- V012 seeded FACILITY_CLAIM_DECISION and its email twin and nothing has ever sent them. These add
-- the rest of the journey's moments and are dispatched by the tuso outbox consumer added in the
-- same wave, so the whole set becomes live rather than growing the pile of templates nobody uses.
--
-- The V012 binding rule holds: external channels carry "you have a message — sign in", never the
-- content. A correction request can name a facility; it must not carry evidence, reviewer notes or
-- anything about a named person's claim.

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content,
                          enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_PROFILE_CORRECTION_REQUIRED', 'IN_APP',
 'Facility profile needs correction', 'Your facility profile needs a correction',
 'The review of {{facilityName}} asked for a change before it can be accepted: {{correctionNote}}. Open the facility profile to update and resubmit.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_PROFILE_CORRECTION_REQUIRED_EMAIL', 'EMAIL',
 'Facility profile correction email', 'Action needed on your facility profile',
 'Hello {{displayName}}, a review of your facility profile needs your attention. Sign in to Impilo to see what to change and resubmit. This message deliberately carries no details.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_PROFILE_ACCEPTED', 'IN_APP',
 'Facility profile accepted', 'Your facility profile was accepted',
 'The changes you submitted for {{facilityName}} have been accepted and are now part of the facility record.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_LEGITIMACY_DECIDED', 'IN_APP',
 'Ministry legitimacy decision', 'A Ministry decision was recorded for your facility',
 'The Ministry recorded a decision for {{facilityName}}: {{verdict}}. {{nextStep}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_LEGITIMACY_DECIDED_EMAIL', 'EMAIL',
 'Ministry legitimacy decision email', 'A Ministry decision was recorded for your facility',
 'Hello {{displayName}}, the Ministry has recorded a decision about a facility you administer. Sign in to Impilo to read it.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_LEGITIMACY_EXPIRING', 'IN_APP',
 'Facility permission expiring', 'Your facility''s operating permission is due for review',
 'The Ministry decision for {{facilityName}} is due for review on {{reviewDate}} and expires on {{expiresAt}}. A verifier must confirm it again before then.',
 true, 'PUBLISHED', 1, NOW(), NOW()),

(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'FACILITY_CAMPAIGN_INVITATION', 'IN_APP',
 'Facility onboarding invitation', 'Claim and complete your facility on Impilo',
 '{{facilityName}} is listed on the national facility register. If you work at or manage it, claim it on Impilo and complete its profile.',
 true, 'PUBLISHED', 1, NOW(), NOW())

ON CONFLICT DO NOTHING;
