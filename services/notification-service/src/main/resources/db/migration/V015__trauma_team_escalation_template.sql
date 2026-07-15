-- Trauma-team escalation template (W4). Paired with the existing ED_TRAUMA_TEAM activation template
-- (V008): when a rostered trauma-team member does not acknowledge within the timeout, PCT raises a
-- fresh escalation delivery-intent to the on-call supervisor using this template.
INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ED_TRAUMA_ESCALATION', 'PUSH',
 'ED Trauma Team Escalation', 'Trauma team NO-ACK escalation',
 'ESCALATION: {{role}} ({{workforceProfileId}}) did not acknowledge the trauma activation. {{message}}',
 true, 'PUBLISHED', 1, NOW(), NOW());
