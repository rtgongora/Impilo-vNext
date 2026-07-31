INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES (gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'SECURITY_RECOVERY_COMPLETED', 'EMAIL',
 'Security recovery completed', 'Your Impilo security recovery was completed',
 'Your Impilo security recovery case {{caseReference}} was completed. All sessions were ended and the selected sign-in methods were removed. Use the secure Keycloak action to enroll replacement methods. If you did not request this, contact the helpdesk immediately.',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
