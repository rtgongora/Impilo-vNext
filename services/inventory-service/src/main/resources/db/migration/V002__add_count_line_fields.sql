-- =============================================
-- Service : inventory-service
-- Database: inventory
-- Flyway  : V002__add_count_line_fields.sql
--
-- Adds bin_id and counted_at columns to
-- inv_count_lines for bin-level counting and
-- count timestamp tracking.
-- =============================================

ALTER TABLE inv_count_lines ADD COLUMN bin_id UUID;
ALTER TABLE inv_count_lines ADD COLUMN counted_at TIMESTAMPTZ;
