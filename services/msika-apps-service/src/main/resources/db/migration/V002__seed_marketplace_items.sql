-- =============================================================================
-- Msika Apps — seed publishers and a real, diverse marketplace catalogue.
-- Items reference existing Impilo sovereign capabilities so the marketplace
-- is real-state from the first boot, not placeholder.
-- =============================================================================

INSERT INTO ma_publishers (id, name, legal_name, technical_contact_name, technical_contact_email, status, verified_at) VALUES
  ('pub-impilo',       'Impilo Platform',        'Impilo Technologies Private Limited',     'Platform Team',     'platform@impilo.gov.zw',     'ACTIVE', now()),
  ('pub-mohcc',        'MoHCC',                  'Ministry of Health and Child Care',       'Digital Health',    'digital-health@mohcc.gov.zw','ACTIVE', now()),
  ('pub-partner-tele', 'Akili Telemedicine',     'Akili Telehealth Pvt Ltd',                'Akili Engineering', 'integration@akili.health',   'ACTIVE', now()),
  ('pub-partner-whats','TwilioZW',               'Twilio Africa Communications Pvt Ltd',    'Twilio API Team',   'partners@twilio.com',        'ACTIVE', now()),
  ('pub-partner-ecocash','EcoCash',              'Cassava Smart Technologies Pvt Ltd',      'EcoCash Gateway',   'gateway@ecocash.co.zw',      'ACTIVE', now()),
  ('pub-partner-pacs', 'OrthancRise',            'OrthancRise PACS Solutions',              'OrthancRise Eng',   'support@orthancrise.io',     'ACTIVE', now());

-- Apps (core, publicly visible)
INSERT INTO ma_marketplace_items (
  id, item_code, name, description, type, category, publisher_id, version, compatibility_version,
  supported_environments, visibility, icon_ref, documentation_url, manifest_ref,
  security_classification, data_protection_classification, clinical_safety_classification,
  approval_status, required_scopes, required_apis, roles_allowed, health_status, last_updated
) VALUES
  ('mi-app-telemedicine', 'app.telemedicine', 'Telemedicine App',
   'Schedule, join and document virtual consultations with role-aware workflows for citizens and providers.',
   'APP', 'TELEMEDICINE', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'Video', 'https://docs.impilo.gov.zw/apps/telemedicine',
   'contracts/openapi/scheduling.openapi.yaml', 'STANDARD', 'PHI_LIMITED', 'CLINICAL_DIRECT_CARE',
   'APPROVED', 'telemedicine.session.read,telemedicine.session.write',
   '/internal/v1/mobile/citizen/telehealth,/internal/v1/scheduling/teleconsultations',
   'CITIZEN,PROVIDER,CLINICAL', 'HEALTHY', now()),

  ('mi-app-imaging-viewer', 'app.imaging_viewer', 'Imaging Viewer App',
   'DICOMweb-backed clinical imaging viewer launched from PCT and OROS contexts.',
   'APP', 'IMAGING', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'Image', 'https://docs.impilo.gov.zw/apps/imaging-viewer',
   'contracts/openapi/imaging-viewer-launch.openapi.yaml', 'STANDARD', 'PHI_LIMITED', 'CLINICAL_INFORMATIONAL',
   'APPROVED', 'imaging.report.read,imaging.study.read',
   '/internal/v1/imaging/viewer/launch', 'CLINICAL,RADIOLOGY', 'HEALTHY', now()),

  ('mi-app-nhume-courier', 'app.nhume_courier', 'Nhume Courier App',
   'Last-mile delivery + dispatch operations app for couriers and dispatchers.',
   'APP', 'LOGISTICS', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'Truck', 'https://docs.impilo.gov.zw/apps/nhume',
   'contracts/openapi/dispatch.openapi.yaml', 'STANDARD', 'NO_PHI', NULL,
   'APPROVED', 'nhume.delivery.read,nhume.delivery.write',
   '/internal/v1/mobile/provider/nhume,/internal/v1/mobile/citizen/nhume,/internal/v1/mobile/provider/deliveries',
   'COURIER,DISPATCHER,LOGISTICS', 'HEALTHY', now()),

  ('mi-app-public-health', 'app.public_health_surveillance', 'Public Health Surveillance App',
   'Outbreak signals, line lists, alerts, and rapid investigation workflows.',
   'APP', 'PUBLIC_HEALTH', 'pub-mohcc', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'MOHCC_ONLY', 'ShieldAlert', 'https://docs.impilo.gov.zw/apps/surveillance',
   'contracts/openapi/surveillance.openapi.yaml', 'ELEVATED', 'PHI_LIMITED', 'CLINICAL_INFORMATIONAL',
   'APPROVED', 'surveillance.read,surveillance.write',
   '/internal/v1/surveillance,/internal/v1/mobile/citizen/public-health',
   'PUBLIC_HEALTH,EPIDEMIOLOGIST', 'HEALTHY', now()),

  ('mi-app-claims', 'app.claims_console', 'MusheX Claims App',
   'Claims switching workspace for finance officers — submit, track, reconcile.',
   'APP', 'CLAIMS', 'pub-impilo', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'PUBLIC', 'Wallet', 'https://docs.impilo.gov.zw/apps/claims',
   'contracts/openapi/mushex.openapi.yaml', 'STANDARD', 'PHI_LIMITED', NULL,
   'APPROVED', 'claims.read,claims.write', '/internal/v1/claims',
   'FINANCE,CLAIMS_OFFICER', 'HEALTHY', now()),

  ('mi-app-fundo-training', 'app.fundo_training', 'Fundo Training App',
   'Role-aware learning, CPD, SOPs and competency tracking.',
   'APP', 'TRAINING', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'GraduationCap', 'https://docs.impilo.gov.zw/apps/fundo',
   'contracts/openapi/learning.openapi.yaml', 'STANDARD', 'NO_PHI', NULL,
   'APPROVED', 'learning.read,learning.complete', '/internal/v1/learning',
   'ALL', 'HEALTHY', now()),

  ('mi-app-wellness', 'app.wellness', 'Wellness App',
   'Citizen wellness, lifestyle assessment, Health Connect integration.',
   'APP', 'WELLNESS', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'Activity', 'https://docs.impilo.gov.zw/apps/wellness',
   'contracts/openapi/wellness.openapi.yaml', 'STANDARD', 'PHI_LIMITED', 'CLINICAL_INFORMATIONAL',
   'APPROVED', 'wellness.read,wellness.write',
   '/internal/v1/wellness,/internal/v1/mobile/citizen/wellness',
   'CITIZEN', 'HEALTHY', now());

-- Connectors (external system integrations) — listed but not auto-installed
INSERT INTO ma_marketplace_items (
  id, item_code, name, description, type, category, publisher_id, version, compatibility_version,
  supported_environments, visibility, manifest_ref,
  security_classification, data_protection_classification, approval_status,
  required_scopes, health_status, last_updated
) VALUES
  ('mi-conn-whatsapp', 'conn.whatsapp', 'WhatsApp Communications Connector',
   'Approved WhatsApp Business Service Provider connector for omnichannel messaging.',
   'CONNECTOR', 'COMMUNICATIONS', 'pub-partner-whats', '1.0.0', '1.0.0',
   'SANDBOX,STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/connector-manifest.schema.json',
   'STANDARD', 'PSEUDONYMOUS', 'APPROVED',
   'comms.send.whatsapp', 'HEALTHY', now()),

  ('mi-conn-ecocash', 'conn.ecocash', 'EcoCash Payment Connector',
   'EcoCash mobile money payment connector behind PaymentIntegrationPort.',
   'CONNECTOR', 'PAYMENT', 'pub-partner-ecocash', '1.0.0', '1.0.0',
   'SANDBOX,STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/connector-manifest.schema.json',
   'STANDARD', 'NO_PHI', 'APPROVED',
   'payments.intent.create,payments.intent.read', 'HEALTHY', now()),

  ('mi-conn-pacs-vendor', 'conn.pacs_orthancrise', 'OrthancRise PACS Connector',
   'DICOMweb connector for OrthancRise vendor PACS via ImagingIntegrationPort.',
   'CONNECTOR', 'IMAGING', 'pub-partner-pacs', '1.0.0', '1.0.0',
   'SANDBOX,STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/connector-manifest.schema.json',
   'STANDARD', 'PHI_LIMITED', 'APPROVED',
   'imaging.study.read,imaging.report.read', 'HEALTHY', now());

-- Adapters
INSERT INTO ma_marketplace_items (
  id, item_code, name, description, type, category, publisher_id, version, compatibility_version,
  supported_environments, visibility, manifest_ref,
  security_classification, data_protection_classification, approval_status,
  required_scopes, health_status, last_updated
) VALUES
  ('mi-adapter-ecocash', 'adapter.payment.ecocash', 'EcoCash Adapter',
   'MobileMoneyAdapter implementation for PaymentIntegrationPort.',
   'ADAPTER', 'PAYMENT', 'pub-partner-ecocash', '1.0.0', '1.0.0',
   'SANDBOX,STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/adapter-manifest.schema.json',
   'STANDARD', 'NO_PHI', 'APPROVED',
   'payments.intent.create', 'HEALTHY', now()),

  ('mi-adapter-akili-video', 'adapter.telemedicine.akili', 'Akili Telemedicine Adapter',
   'ExternalVideoProviderAdapter for TelemedicineIntegrationPort.',
   'ADAPTER', 'TELEMEDICINE', 'pub-partner-tele', '1.0.0', '1.0.0',
   'STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/adapter-manifest.schema.json',
   'STANDARD', 'PHI_LIMITED', 'APPROVED',
   'telemedicine.session.write', 'HEALTHY', now()),

  ('mi-adapter-osm-maps', 'adapter.maps.openstreetmap', 'OpenStreetMap Adapter',
   'OpenStreetMapAdapter for MapsIntegrationPort.',
   'ADAPTER', 'OTHER', 'pub-impilo', '1.0.0', '1.0.0',
   'SANDBOX,STAGING,PRODUCTION', 'PUBLIC',
   'contracts/schemas/adapter-manifest.schema.json',
   'LOW', 'NO_PHI', 'APPROVED',
   'maps.read', 'HEALTHY', now());

-- Workflow + Content packs
INSERT INTO ma_marketplace_items (
  id, item_code, name, description, type, category, publisher_id, version, compatibility_version,
  supported_environments, visibility, manifest_ref,
  security_classification, data_protection_classification, clinical_safety_classification,
  approval_status, required_scopes, health_status, last_updated
) VALUES
  ('mi-wf-antenatal', 'pack.workflow.antenatal_care', 'Antenatal Care Workflow Pack',
   'Pre-defined ANC workflows, queues, forms, dashboards aligned with MoHCC guidelines.',
   'WORKFLOW_PACK', 'CLINICAL', 'pub-mohcc', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'PUBLIC', 'contracts/schemas/workflow-pack-manifest.schema.json',
   'STANDARD', 'PHI_LIMITED', 'CLINICAL_DIRECT_CARE',
   'APPROVED', 'workflow.read,workflow.execute', 'HEALTHY', now()),

  ('mi-wf-tb', 'pack.workflow.tb_programme', 'TB Programme Workflow Pack',
   'TB programme contact tracing, treatment monitoring and reporting workflows.',
   'WORKFLOW_PACK', 'CLINICAL', 'pub-mohcc', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'PROGRAMME_ONLY', 'contracts/schemas/workflow-pack-manifest.schema.json',
   'STANDARD', 'PHI_LIMITED', 'CLINICAL_DIRECT_CARE',
   'APPROVED', 'workflow.read,workflow.execute,surveillance.read', 'HEALTHY', now()),

  ('mi-content-cholera', 'pack.content.public_health_cholera', 'Cholera Awareness Content Pack',
   'Multi-language SMS, WhatsApp and IVR content for cholera awareness campaigns.',
   'CONTENT_PACK', 'PUBLIC_HEALTH', 'pub-mohcc', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'MOHCC_ONLY', 'contracts/schemas/content-pack-manifest.schema.json',
   'LOW', 'NO_PHI', NULL,
   'APPROVED', 'notification.template.use', 'HEALTHY', now());

-- AI Skills + Device integrations
INSERT INTO ma_marketplace_items (
  id, item_code, name, description, type, category, publisher_id, version, compatibility_version,
  supported_environments, visibility, manifest_ref,
  security_classification, data_protection_classification, clinical_safety_classification,
  approval_status, required_scopes, health_status, last_updated
) VALUES
  ('mi-aiskill-marketplace-helper', 'aiskill.marketplace_helper', 'Nompilo Marketplace Helper',
   'Guided marketplace discovery, explanation of unavailable capabilities, request-access assistance.',
   'AI_SKILL', 'AI', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'contracts/schemas/ai-skill-manifest.schema.json',
   'STANDARD', 'NO_PHI', 'NON_CLINICAL',
   'APPROVED', 'marketplace.read,launcher.read,guidance.ask',
   'HEALTHY', now()),

  ('mi-aiskill-encounter-summary', 'aiskill.encounter_summary', 'Patient Journey Summariser',
   'Summarises a patient encounter timeline (PCT) into a structured handover note.',
   'AI_SKILL', 'AI', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING',
   'PUBLIC', 'contracts/schemas/ai-skill-manifest.schema.json',
   'ELEVATED', 'PHI_LIMITED', 'CLINICAL_INFORMATIONAL',
   'APPROVED', 'pct.encounter.read,guidance.ask',
   'HEALTHY', now()),

  ('mi-device-glucometer', 'device.glucometer_bt', 'Bluetooth Glucometer',
   'BLE-paired glucometer; readings flow through DeviceIntegrationPort into vitals.',
   'DEVICE_INTEGRATION', 'DEVICE', 'pub-impilo', '1.0.0', '1.0.0', 'SANDBOX,STAGING,PRODUCTION',
   'PUBLIC', 'contracts/schemas/device-integration-manifest.schema.json',
   'STANDARD', 'PHI_LIMITED', 'CLINICAL_INFORMATIONAL',
   'APPROVED', 'iot.ingest', 'HEALTHY', now()),

  ('mi-device-coldchain', 'device.coldchain_sensor', 'Cold Chain Sensor',
   'IoT temperature sensor for vaccine cold-chain monitoring; emits alerts when out of range.',
   'DEVICE_INTEGRATION', 'DEVICE', 'pub-impilo', '1.0.0', '1.0.0', 'STAGING,PRODUCTION',
   'PROGRAMME_ONLY', 'contracts/schemas/device-integration-manifest.schema.json',
   'STANDARD', 'NO_PHI', NULL,
   'APPROVED', 'iot.ingest,inventory.stock.read', 'HEALTHY', now());

-- A small set of sample installations so the launcher and admin dashboards have real state.
INSERT INTO ma_installations (
  id, item_id, item_version, tenant_id, facility_ids, roles_enabled, lifecycle,
  installed_by, installed_at, activated_at, health_status, pod_id, configuration_json
) VALUES
  ('inst-tele-001',  'mi-app-telemedicine',     '1.0.0', 'tenant-mohcc', NULL, NULL, 'ACTIVE', 'system', now(), now(), 'HEALTHY', 'pod-national', '{}'),
  ('inst-courier-001','mi-app-nhume-courier',   '1.0.0', 'tenant-mohcc', NULL, NULL, 'ACTIVE', 'system', now(), now(), 'HEALTHY', 'pod-national', '{}'),
  ('inst-fundo-001', 'mi-app-fundo-training',   '1.0.0', 'tenant-mohcc', NULL, NULL, 'ACTIVE', 'system', now(), now(), 'HEALTHY', 'pod-national', '{}'),
  ('inst-wellness-001','mi-app-wellness',       '1.0.0', 'tenant-mohcc', NULL, NULL, 'ACTIVE', 'system', now(), now(), 'HEALTHY', 'pod-national', '{}'),
  ('inst-imaging-001','mi-app-imaging-viewer',  '1.0.0', 'tenant-mohcc', NULL, NULL, 'ACTIVE', 'system', now(), now(), 'HEALTHY', 'pod-national', '{}'),
  ('inst-aisearch-001','mi-aiskill-marketplace-helper','1.0.0','tenant-mohcc',NULL,NULL,'ACTIVE','system',now(),now(),'HEALTHY','pod-national','{}');

INSERT INTO ma_audit_events (id, item_id, installation_id, event_type, actor_id, actor_type, tenant_id, correlation_id, occurred_at)
SELECT 'audit-seed-' || installation_id, item_id, installation_id, 'INSTALLATION_ACTIVATED', 'system', 'SYSTEM', 'tenant-mohcc', installation_id, now()
FROM (VALUES
  ('inst-tele-001','mi-app-telemedicine'),
  ('inst-courier-001','mi-app-nhume-courier'),
  ('inst-fundo-001','mi-app-fundo-training'),
  ('inst-wellness-001','mi-app-wellness'),
  ('inst-imaging-001','mi-app-imaging-viewer'),
  ('inst-aisearch-001','mi-aiskill-marketplace-helper')
) AS s(installation_id, item_id);
