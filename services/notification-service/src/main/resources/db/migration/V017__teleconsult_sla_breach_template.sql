-- TM-B19: ops-alert template for the (previously emit-only) teleconsult queue SLA-breach stream.
-- Consumed by QueueLifecycleNotificationConsumer.onQueueSlaBreached — an aged queue becomes a
-- visible IN_APP alert on the ops inbox identity, not just an in-table escalation.
INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'TELECONSULT_SLA_BREACH', 'IN_APP',
 'Teleconsult SLA breach', 'Teleconsult queue SLA breached',
 '{{message}}',
 true, 'PUBLISHED', 1, NOW(), NOW());
