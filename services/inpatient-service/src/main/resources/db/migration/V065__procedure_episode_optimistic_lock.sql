-- ============================================================================
-- V065 — procedure_episode optimistic-lock version column (Wave 6 §18).
--
-- Concurrent edit of the SAME theatre episode by two clinicians is an explicit §18
-- hazard. The episode FSM mutations are read-modify-write; without a version guard a
-- stale write would silently clobber a concurrent update (lost update). This adds a
-- JPA @Version column so a stale write fails fast with an optimistic-lock conflict,
-- surfaced to the client as a clean HTTP 409 STALE_WRITE (see GlobalExceptionHandler)
-- rather than silent data loss.
--
-- NOT NULL DEFAULT 0 backfills every existing row to version 0; Hibernate increments it
-- on each update. Additive and backward-compatible: readers ignore the column, and any
-- non-JPA writer simply never trips the check.
--
-- Migration sequencing (inpatient): trauma reserved V035..V064; this theatre §18 column
-- is the first free number after that reservation → V065. (Flyway tolerates the V036..V064
-- gap; versions are applied in order.)
-- ============================================================================

ALTER TABLE inpatient.procedure_episode
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
