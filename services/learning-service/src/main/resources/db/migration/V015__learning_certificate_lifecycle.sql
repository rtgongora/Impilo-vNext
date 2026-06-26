-- V015: Certificate renewal/refresher/expiry lifecycle support.
--
-- Adds a dedupe marker so the scheduled lifecycle sweep emits a single
-- pre-expiry "refresher due" signal per certificate. Expiry itself is
-- deduped naturally via the status transition ISSUED -> EXPIRED.

ALTER TABLE lrn_certificate
    ADD COLUMN IF NOT EXISTS refresher_notified_at TIMESTAMPTZ NULL;

-- Supports the scheduled sweep that scans active certificates by validity window.
CREATE INDEX IF NOT EXISTS idx_lrn_certificate_validity_sweep
    ON lrn_certificate (status, valid_until)
    WHERE valid_until IS NOT NULL;
