-- Inbox recipient for per-actor notification filtering (citizen CPID, provider actor, staff)
ALTER TABLE ns_notifications
    ADD COLUMN IF NOT EXISTS inbox_recipient VARCHAR(256);

UPDATE ns_notifications
SET inbox_recipient = to_addr
WHERE inbox_recipient IS NULL;

CREATE INDEX IF NOT EXISTS idx_ns_notifications_inbox_recipient
    ON ns_notifications (tenant_id, inbox_recipient, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ns_notifications_inbox_unread
    ON ns_notifications (tenant_id, inbox_recipient)
    WHERE read_at IS NULL;

-- Semantic appointment templates (reject, staff-cancel, provider reschedule)
INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'APPOINTMENT_CITIZEN_REJECTED', 'IN_APP',
 'Booking not approved', 'Booking request declined',
 'Your {{appointmentType}} request for {{scheduledAt}} at {{facilityName}} was not approved. {{reason}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'APPOINTMENT_PROVIDER_CANCELLED_BY_STAFF', 'PUSH',
 'Appointment cancelled by facility', 'Appointment cancelled',
 'Appointment for {{patientCpid}} on {{scheduledAt}} was cancelled by facility staff. Reason: {{reason}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'APPOINTMENT_PROVIDER_RESCHEDULED', 'PUSH',
 'Appointment rescheduled', 'Appointment rescheduled',
 'Appointment for {{patientCpid}} rescheduled to {{scheduledAt}} at {{facilityName}} with {{providerName}}.',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
