-- Queue lifecycle citizen templates (in-app inbox).
-- Deliberately neutral: token number, movement, and next step only —
-- no clinical detail, acuity, or escalation reason ever appears here.

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_JOINED', 'IN_APP',
 'Queue joined', 'You are in the queue',
 'You have joined the queue. Your number is {{token}}. Please stay nearby and watch for updates.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_CALLED', 'IN_APP',
 'Queue called', 'It is your turn',
 'It is your turn now. Number {{token}}, please proceed to the service point.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_TRANSFERRED', 'IN_APP',
 'Queue transferred', 'Your queue has changed',
 'You have been moved to a different queue. Your new number is {{token}}. Staff will guide you if needed.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_PRIORITISED', 'IN_APP',
 'Queue prioritised', 'Your place in the queue was updated',
 'Your place in the queue has been prioritised. Please remain where staff can reach you.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_NO_SHOW', 'IN_APP',
 'Queue missed call', 'We missed you',
 'We called number {{token}} but could not find you. Please see the front desk to rejoin the queue or reschedule.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_COMPLETED', 'IN_APP',
 'Visit completed', 'Thank you for your visit',
 'Your visit is complete. Any follow-up instructions will be shared with you separately.',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'QUEUE_CITIZEN_ON_HOLD', 'IN_APP',
 'Queue on hold', 'Your queue place is on hold',
 'Your place in the queue is on hold for now. You will be updated when service resumes.',
 true, 'PUBLISHED', 1, NOW(), NOW());
