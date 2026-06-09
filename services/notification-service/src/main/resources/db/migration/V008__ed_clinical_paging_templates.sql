-- ED clinical paging templates — code activations and team consults

INSERT INTO ns_templates (id, tenant_id, pod_id, key, channel, name, subject, content, enabled, status, current_version, created_at, updated_at)
VALUES
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ED_CODE_BLUE', 'PUSH',
 'ED Code Blue', 'CODE BLUE activated',
 '{{location}} — CODE BLUE. Patient {{patientId}}. Team leader {{teamLeader}}. {{message}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ED_TRAUMA_TEAM', 'PUSH',
 'ED Trauma Team', 'Trauma team activation Level {{traumaLevel}}',
 '{{location}} — Trauma Level {{traumaLevel}} ({{mechanism}}). Patient {{patientId}}. {{message}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ED_STAT_CONSULT', 'PUSH',
 'ED Stat Consult', 'Urgent ED consult requested',
 '{{recipientRole}} requested for {{patientId}} in {{zone}}: {{message}}',
 true, 'PUBLISHED', 1, NOW(), NOW()),
(gen_random_uuid()::text, 'tenant-moh-zw', 'national', 'ED_TEAM_PAGE', 'SMS',
 'ED Team Page', 'ED team page',
 'ED PAGE [{{pageType}}] {{location}}: {{message}} — respond via Impilo.',
 true, 'PUBLISHED', 1, NOW(), NOW())
ON CONFLICT DO NOTHING;
