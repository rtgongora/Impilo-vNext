-- V027: Scheduled-session ↔ Impilo Live linkage (LEARNING_LIVE W3).
--
-- Promotes the Impilo Live linkage out of the free-text metadata_json blob into
-- first-class columns so attendance webhooks and the experience shell can join
-- a scheduled learning session to its live event without JSON parsing:
--
--   * session_mode  — delivery mode of the session (LIVE / RECORDED / HYBRID /
--                     IN_PERSON). Existing rows default to IN_PERSON; rows with a
--                     recoverable impiloLiveEventId are backfilled to LIVE.
--   * live_event_id — the Impilo Live event id returned by live-service when the
--                     Fundo webinar was scheduled (metadata_json.impiloLiveEventId).
--   * join_path     — the experience-shell join path (metadata_json.impiloLiveJoinPath).
--
-- metadata_json keeps carrying the same keys for backward compatibility
-- (dual-write; reads prefer the columns).
--
-- Also adds lrn_session_attendance.watch_minutes so webhook-accurate live watch
-- minutes from impilo.live.attendance.updated.v1 land on the Fundo attendance
-- row (the learning attendance system of record) instead of being lost.

ALTER TABLE lrn_scheduled_learning_session
    ADD COLUMN IF NOT EXISTS session_mode VARCHAR(32) NOT NULL DEFAULT 'IN_PERSON'
        CONSTRAINT ck_lrn_sls_session_mode
        CHECK (session_mode IN ('LIVE', 'RECORDED', 'HYBRID', 'IN_PERSON'));

ALTER TABLE lrn_scheduled_learning_session
    ADD COLUMN IF NOT EXISTS live_event_id UUID NULL;

ALTER TABLE lrn_scheduled_learning_session
    ADD COLUMN IF NOT EXISTS join_path VARCHAR(512) NULL;

ALTER TABLE lrn_session_attendance
    ADD COLUMN IF NOT EXISTS watch_minutes INTEGER NULL;

-- Defensive backfill from metadata_json (a TEXT column that carries JSON written
-- by LearningV11NativeService.createScheduledSession). Rows whose metadata_json
-- is not valid JSON — or whose impiloLiveEventId is not a UUID — are skipped
-- row-by-row instead of aborting the migration.
DO $$
DECLARE
    r RECORD;
    v_event UUID;
    v_join VARCHAR(512);
BEGIN
    FOR r IN
        SELECT id, metadata_json
        FROM lrn_scheduled_learning_session
        WHERE live_event_id IS NULL
          AND metadata_json IS NOT NULL
          AND metadata_json <> ''
          AND metadata_json LIKE '%impiloLiveEventId%'
    LOOP
        BEGIN
            v_event := (r.metadata_json::jsonb ->> 'impiloLiveEventId')::uuid;
            v_join := left(r.metadata_json::jsonb ->> 'impiloLiveJoinPath', 512);
            IF v_event IS NOT NULL THEN
                UPDATE lrn_scheduled_learning_session
                SET live_event_id = v_event,
                    join_path = COALESCE(join_path, v_join),
                    session_mode = 'LIVE'
                WHERE id = r.id;
            END IF;
        EXCEPTION WHEN OTHERS THEN
            -- Malformed JSON / non-UUID id: leave the row as-is (IN_PERSON, no linkage).
            RAISE NOTICE 'V027 backfill skipped session % (unparseable metadata_json)', r.id;
        END;
    END LOOP;
END $$;

CREATE INDEX IF NOT EXISTS idx_lrn_sls_live_event
    ON lrn_scheduled_learning_session (live_event_id)
    WHERE live_event_id IS NOT NULL;
