-- Wave 20 slice 13 — Costa billing anchor for sovereign overlay.

INSERT INTO sovereign_demo_anchors (domain_id, service_name, bff_probe_path, compose_service, host_port, maturity, notes) VALUES
    ('costa', 'costing-engine-service', '/internal/v1/finance/billing?page=0&size=1', 'costing-engine-service', 8101, 'partial', 'V011 demo bill when sovereign overlay up')
ON CONFLICT (domain_id) DO UPDATE SET
    service_name = EXCLUDED.service_name,
    bff_probe_path = EXCLUDED.bff_probe_path,
    compose_service = EXCLUDED.compose_service,
    host_port = EXCLUDED.host_port,
    notes = EXCLUDED.notes;
