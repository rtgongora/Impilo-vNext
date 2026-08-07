-- vito-service V056 — vito.event_outbox.pod_id must carry a pod.
--
-- V017 added pod_id as NULL-able and nothing ever populated it: measured 2026-08-07,
-- all 43 rows in the preview vito database and the one unpublished row in oogate_vito
-- hold pod_id IS NULL. Until now that was invisible, because OutboxEventBuilder
-- substituted the literal "unknown" at publish time — so every vito v1.1 event carried
-- a fabricated pod that FederationAuthority.isNational() rejects.
--
-- The builder now refuses to publish without federation context rather than inventing it,
-- which turns that silent fabrication into a stuck outbox row. This backfills the column
-- so the refusal has nothing to catch.
--
-- 'national' is deliberate: FederationAuthority.NATIONAL_POD_ID is "national", so the
-- 'national-spine' literal used across ~58 migration files in this estate is precisely
-- the value isNational() does not recognise.

UPDATE vito.event_outbox SET pod_id = 'national' WHERE pod_id IS NULL OR pod_id = '';

ALTER TABLE vito.event_outbox ALTER COLUMN pod_id SET DEFAULT 'national';
ALTER TABLE vito.event_outbox ALTER COLUMN pod_id SET NOT NULL;
