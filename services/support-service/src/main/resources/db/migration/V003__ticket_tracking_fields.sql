-- support-service V003: Add request_id and correlation_id tracking to tickets
ALTER TABLE sup_tickets ADD COLUMN request_id   VARCHAR(255);
ALTER TABLE sup_tickets ADD COLUMN correlation_id UUID;

CREATE INDEX idx_sup_tickets_correlation ON sup_tickets (correlation_id);
