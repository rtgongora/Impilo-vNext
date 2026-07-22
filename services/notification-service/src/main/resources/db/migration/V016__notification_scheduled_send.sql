-- TM-B9/B1: scheduled (future-dated) notifications.
-- Reuse the PENDING status + a not_before gate rather than adding a new status:
-- simpler and backward compatible. not_before NULL = send immediately (preserves
-- all existing behaviour); not_before in the future = held until due.

ALTER TABLE ns_notifications ADD COLUMN IF NOT EXISTS not_before TIMESTAMPTZ;

-- Partial index makes the worker due-query (status='PENDING' AND not_before <= now)
-- efficient without scanning already-dispatched rows.
CREATE INDEX IF NOT EXISTS idx_ns_notifications_due
    ON ns_notifications (status, not_before)
    WHERE status = 'PENDING';

-- Teleconsult reminder / device-check / feedback templates for the scheduled
-- telemedicine comms workflow. IN_APP primary + _FALLBACK_SMS SMS variant
-- (the workflow appends _FALLBACK_SMS on primary-channel failure).
INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_REMINDER', 'IN_APP',
 'Teleconsult reminder', 'Your teleconsult is coming up',
 'Reminder: your teleconsult, {{patientName}}, is scheduled for {{scheduledAt}}. Join here: {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_REMINDER_FALLBACK_SMS', 'SMS',
 'Teleconsult reminder SMS', 'Impilo teleconsult reminder',
 'Impilo: teleconsult {{scheduledAt}} for {{patientName}}. Join: {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_DEVICE_CHECK', 'IN_APP',
 'Teleconsult device check', 'Test your device before the call',
 'Before your teleconsult at {{scheduledAt}}, {{patientName}}, please test your camera and microphone: {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_DEVICE_CHECK_FALLBACK_SMS', 'SMS',
 'Teleconsult device check SMS', 'Impilo device check',
 'Impilo: test your device before your {{scheduledAt}} teleconsult. {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_FEEDBACK_PROMPT', 'IN_APP',
 'Teleconsult feedback prompt', 'How was your teleconsult?',
 'Thank you for your teleconsult, {{patientName}}. Please share your feedback: {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_FEEDBACK_PROMPT_FALLBACK_SMS', 'SMS',
 'Teleconsult feedback prompt SMS', 'Impilo teleconsult feedback',
 'Impilo: how was your teleconsult, {{patientName}}? Share feedback: {{joinUrl}}',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
