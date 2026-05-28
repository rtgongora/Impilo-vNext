-- Wave 20 slice 13 — sovereign domain probe anchors (Rx-path + enterprise).

CREATE TABLE IF NOT EXISTS sovereign_demo_anchors (
    domain_id       VARCHAR(32) PRIMARY KEY,
    service_name    VARCHAR(128) NOT NULL,
    bff_probe_path  VARCHAR(512) NOT NULL,
    compose_service VARCHAR(64),
    host_port       INTEGER,
    maturity        VARCHAR(32) NOT NULL DEFAULT 'partial',
    notes           TEXT
);

INSERT INTO sovereign_demo_anchors (domain_id, service_name, bff_probe_path, compose_service, host_port, maturity, notes) VALUES
    ('pharmacy', 'pharmacy-service', '/internal/v1/pharmacy/prescriptions?patient_id=CPID-ZW-00001', 'pharmacy-service', 8096, 'partial', 'V003 dispense order seed when sovereign overlay up'),
    ('mushex', 'mushex-service', '/internal/v1/finance/billing?page=0&size=1', 'mushex-service', 8102, 'partial', 'V005 payment intent seed when sovereign overlay up'),
    ('dispatch', 'dispatch-service', '/internal/v1/dispatch/deliveries', 'dispatch-service', 8320, 'partial', 'V003 dispatch job seed when sovereign overlay up'),
    ('nhume', 'nhume-service', '/internal/v1/nhume/deliveries', NULL, 8210, 'not_wired', 'Service not in repo; host optional'),
    ('ndila', 'ndila-service', '/internal/v1/ndila/tiles/config', NULL, 8155, 'not_wired', 'Service not in repo; host optional'),
    ('inventory', 'inventory-service', '/internal/v1/inventory/requisitions?facility_id=a1b2c3d4-0001-4000-8000-000000000001', NULL, NULL, 'partial', 'BFF V31 table seeds'),
    ('surveillance', 'surveillance-service', '/internal/v1/public-health/field-tasks', NULL, 8180, 'partial', 'BFF proxy only')
ON CONFLICT (domain_id) DO NOTHING;
