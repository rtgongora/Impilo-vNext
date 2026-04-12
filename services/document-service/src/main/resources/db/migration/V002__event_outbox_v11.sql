-- v1.1 companion fields for transactional outbox (CompanionOutboxPublisher)
ALTER TABLE document_store.event_outbox ADD COLUMN tenant_id VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN pod_id VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN correlation_id VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN causation_id VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN idempotency_key VARCHAR(128);
ALTER TABLE document_store.event_outbox ADD COLUMN schema_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE document_store.event_outbox ADD COLUMN producer VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN subject_type VARCHAR(64);
ALTER TABLE document_store.event_outbox ADD COLUMN subject_id VARCHAR(255);
ALTER TABLE document_store.event_outbox ADD COLUMN partition_key VARCHAR(255);
ALTER TABLE document_store.event_outbox ADD COLUMN occurred_at TIMESTAMPTZ;
ALTER TABLE document_store.event_outbox ADD COLUMN publish_error VARCHAR(1000);
ALTER TABLE document_store.event_outbox ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;
