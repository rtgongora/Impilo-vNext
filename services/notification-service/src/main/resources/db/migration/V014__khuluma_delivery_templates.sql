-- G31: canonical khuluma external-delivery templates. khuluma delegates SMS/WhatsApp/EMAIL/USSD
-- delivery to notification-service (template-driven); without a template a khuluma external dispatch
-- records SKIPPED_NO_TEMPLATE. These starter templates make the delegation path produce live sends.
--
-- Templates are resolved by `key` alone (WorkerService.findByKey) — the NotifyRequest's `channel`
-- drives the actual delivery provider — so ONE template per key serves every channel (the unique
-- index on ns_templates(key) also requires this). Callers supply {{body}} (message text) and, for a
-- broadcast, {{facility}}. Neutral wording — no clinical detail is carried on external channels.

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'KHULUMA_MESSAGE', 'SMS',
 'Khuluma message', 'You have a new secure message',
 '{{body}}', true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'KHULUMA_BROADCAST', 'SMS',
 'Khuluma broadcast', '{{facility}} announcement',
 '{{facility}}: {{body}}', true, 'PUBLISHED', 1, NOW(), NOW());
