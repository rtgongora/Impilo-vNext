-- V18: Allow queue entries without facility context.
-- Supports license-based work modes (independent practice, emergency response,
-- community outreach) where the provider operates without facility affiliation.

ALTER TABLE queue_entries ALTER COLUMN facility_id DROP NOT NULL;

COMMENT ON COLUMN queue_entries.facility_id IS 'Facility UUID, nullable for license-based work modes without facility context';
