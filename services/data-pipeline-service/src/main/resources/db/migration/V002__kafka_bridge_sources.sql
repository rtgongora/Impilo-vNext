-- Default ingestion sources for Kafka-driven clinical/kernel feeds (national tenant).

INSERT INTO dp_ingestion_sources (source_id, tenant_id, display_name, description, source_type, enabled)
VALUES
    ('pct-service', '00000000-0000-4000-8000-000000000001'::uuid, 'PCT (Kafka)', 'Bus: clinical.pct.*', 'SERVICE', TRUE),
    ('vito-service', '00000000-0000-4000-8000-000000000001'::uuid, 'VITO (Kafka)', 'Bus: kernel.vito.*', 'SERVICE', TRUE),
    ('oros-service', '00000000-0000-4000-8000-000000000001'::uuid, 'OROS (Kafka)', 'Bus: clinical.oros.*', 'SERVICE', TRUE)
ON CONFLICT (source_id) DO NOTHING;
