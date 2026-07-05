-- rtc-gateway-service V005 — linked child sessions (LIVE_EVENT backstage rooms)
-- A backstage room is a separate rtc session provisioned with the same
-- owning_ref (the live event) and parent_session_id pointing at the main
-- room's session. Nullable: ordinary sessions have no parent.
ALTER TABLE rtc.rtc_sessions
    ADD COLUMN parent_session_id VARCHAR(255);

CREATE INDEX idx_rtc_sessions_parent
    ON rtc.rtc_sessions (parent_session_id)
    WHERE parent_session_id IS NOT NULL;
