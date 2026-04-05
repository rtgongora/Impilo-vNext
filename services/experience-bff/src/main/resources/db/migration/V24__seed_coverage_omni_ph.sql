-- V24: Seed data for Coverage, Omnichannel, and Public Health tables.

-- ── Omnichannel: SMS Journeys ────────────────────────────────────
INSERT INTO omni_sms_journeys (id, tenant_id, name, trigger_event, message_template, schedule_cron, is_active, sent_count)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'Appointment Reminder', 'APPOINTMENT_24HR', 'Dear {{name}}, your appointment at {{facility}} is tomorrow at {{time}}. Reply YES to confirm.', '0 8 * * *', true, 2345),
(gen_random_uuid(), 'tenant-moh-zw', 'Medication Adherence', 'PRESCRIPTION_REFILL_DUE', 'Hello {{name}}, your medication {{medication}} is due for refill. Visit your nearest pharmacy.', '0 9 * * MON', true, 1567),
(gen_random_uuid(), 'tenant-moh-zw', 'Immunization Due', 'IMMUNIZATION_SCHEDULE', 'Dear {{name}}, {{child}} is due for {{vaccine}} immunization. Please visit {{facility}}.', '0 7 * * *', true, 890),
(gen_random_uuid(), 'tenant-moh-zw', 'Claim Status Update', 'CLAIM_STATUS_CHANGED', 'Your claim {{claimId}} status: {{status}}. Contact {{phone}} for details.', NULL, true, 456),
(gen_random_uuid(), 'tenant-moh-zw', 'Outbreak Alert', 'SURVEILLANCE_ALERT', 'HEALTH ALERT: {{disease}} cases reported in {{area}}. Take precautions: {{measures}}.', NULL, false, 23);

-- ── Omnichannel: Callback Queue (sample entries) ─────────────────
INSERT INTO omni_callback_queue (id, tenant_id, channel, caller_id, caller_name, reason, priority, status)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'PHONE', '+263771234567', 'Tendai Moyo', 'Appointment rescheduling', 'NORMAL', 'PENDING'),
(gen_random_uuid(), 'tenant-moh-zw', 'WHATSAPP', '+263772345678', 'Grace Chienda', 'Medication query', 'HIGH', 'PENDING'),
(gen_random_uuid(), 'tenant-moh-zw', 'PHONE', '+263773456789', 'James Mapfumo', 'Lab results inquiry', 'URGENT', 'ASSIGNED');

-- ── Omnichannel: Disclosure Rules ────────────────────────────────
INSERT INTO omni_disclosure_rules (id, tenant_id, channel_type, data_category, disclosure_level, is_active)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'SMS', 'DEMOGRAPHICS', 'MINIMAL', true),
(gen_random_uuid(), 'tenant-moh-zw', 'SMS', 'CLINICAL', 'NONE', true),
(gen_random_uuid(), 'tenant-moh-zw', 'SMS', 'FINANCIAL', 'SUMMARY', true),
(gen_random_uuid(), 'tenant-moh-zw', 'USSD', 'DEMOGRAPHICS', 'MINIMAL', true),
(gen_random_uuid(), 'tenant-moh-zw', 'USSD', 'CLINICAL', 'NONE', true),
(gen_random_uuid(), 'tenant-moh-zw', 'IVR', 'DEMOGRAPHICS', 'SUMMARY', true),
(gen_random_uuid(), 'tenant-moh-zw', 'IVR', 'CLINICAL', 'NONE', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WHATSAPP', 'DEMOGRAPHICS', 'SUMMARY', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WHATSAPP', 'CLINICAL', 'MINIMAL', true),
(gen_random_uuid(), 'tenant-moh-zw', 'EMAIL', 'DEMOGRAPHICS', 'FULL', true),
(gen_random_uuid(), 'tenant-moh-zw', 'EMAIL', 'CLINICAL', 'SUMMARY', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WEBCHAT', 'DEMOGRAPHICS', 'FULL', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WEBCHAT', 'CLINICAL', 'FULL', true);

-- ── Omnichannel: Channel Configs ─────────────────────────────────
INSERT INTO omni_channel_configs (id, tenant_id, channel_type, name, config, is_active)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'SMS', 'Primary SMS Gateway', '{"provider":"http_sms","gateway_url":"https://sms.example.com/api","sender_id":"IMPILO"}', true),
(gen_random_uuid(), 'tenant-moh-zw', 'USSD', 'National USSD', '{"short_code":"*123#","provider":"telecel","menu_version":"1.0"}', true),
(gen_random_uuid(), 'tenant-moh-zw', 'IVR', 'Voice Gateway', '{"phone_number":"+263800123456","provider":"voip_gw","languages":["en","sn","nd"]}', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WHATSAPP', 'WhatsApp Business', '{"phone":"+263771000000","provider":"meta_cloud_api","verified":false}', false),
(gen_random_uuid(), 'tenant-moh-zw', 'EMAIL', 'SMTP Relay', '{"host":"smtp.mohcc.gov.zw","port":587,"from":"noreply@impilo.health.gov.zw"}', true),
(gen_random_uuid(), 'tenant-moh-zw', 'WEBCHAT', 'Web Chat Widget', '{"widget_id":"impilo-chat-v1","position":"bottom-right","greeting":"Welcome to Impilo Health"}', true);

-- ── Feed Items (community updates) ───────────────────────────────
INSERT INTO feed_items (id, tenant_id, type, title, body, category, author, active, published_at)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'ANNOUNCEMENT', 'COVID-19 Booster Campaign', 'Free booster vaccinations available at all provincial hospitals for citizens over 60. Bring your health card.', 'HEALTH', 'Ministry of Health', true, now() - interval '2 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'ANNOUNCEMENT', 'New Clinic Hours', 'Starting next month, all primary care clinics will extend hours to 7pm on weekdays.', 'FACILITY', 'MOH Communications', true, now() - interval '5 days'),
(gen_random_uuid(), 'tenant-moh-zw', 'ANNOUNCEMENT', 'Malaria Prevention Week', 'Sleep under treated mosquito nets. Remove standing water. Seek treatment within 24 hours of fever onset.', 'HEALTH', 'Vector Control Unit', true, now() - interval '1 week');

-- ── Community Groups ─────────────────────────────────────────────
INSERT INTO community_groups (id, tenant_id, name, description, group_type, category, is_public, member_count, status, created_by)
VALUES
(gen_random_uuid(), 'tenant-moh-zw', 'Diabetes Support Zimbabwe', 'Peer support for people living with diabetes. Share tips, recipes, and encouragement.', 'SUPPORT', 'CHRONIC_DISEASE', true, 234, 'ACTIVE', 'system'),
(gen_random_uuid(), 'tenant-moh-zw', 'New Mothers Circle', 'Support group for new and expecting mothers. Postnatal care, breastfeeding, and baby wellness.', 'SUPPORT', 'MATERNAL_HEALTH', true, 567, 'ACTIVE', 'system'),
(gen_random_uuid(), 'tenant-moh-zw', 'HIV/AIDS Awareness', 'Education, testing reminders, and stigma-free community support.', 'SUPPORT', 'HIV', true, 1234, 'ACTIVE', 'system'),
(gen_random_uuid(), 'tenant-moh-zw', 'Mental Wellness Hub', 'A safe space for mental health discussions, coping strategies, and professional resources.', 'SUPPORT', 'MENTAL_HEALTH', true, 189, 'ACTIVE', 'system');

-- ── Queue Definitions (if not already seeded by V22) ─────────────
-- V22 already seeds 4 definitions per facility, so this is supplementary.
