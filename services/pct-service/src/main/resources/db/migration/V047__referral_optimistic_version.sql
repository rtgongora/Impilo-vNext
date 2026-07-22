-- ============================================================================
-- V047: Optimistic-concurrency guard on teleconsult referrals (TM-B6).
--
-- A referral is now a multi-writer aggregate (provider console, citizen lane,
-- lifecycle timers, escalation). Without a version, a concurrent edit silently
-- last-write-wins — e.g. a double-accept race, or a provider completing while a
-- timer expires the same case. A JPA @Version column turns that into a detected
-- stale write surfaced as 409 STALE_WRITE, so the experience layer can reload the
-- latest state and retry instead of losing an update.
--
-- Existing rows default to 0 (NOT NULL) so Hibernate's version check is well-defined
-- from the first update onward; no backfill needed.
-- ============================================================================

ALTER TABLE pct_referrals ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
