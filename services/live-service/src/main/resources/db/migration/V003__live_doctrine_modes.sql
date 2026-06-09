-- =============================================
-- Service : live-service (Impilo Live)
-- Schema  : live
-- Flyway  : V003__live_doctrine_modes.sql
-- Purpose : Promote canonical Impilo Live doctrine to first-class columns.
--           Adds governed `mode`, `owning_service`, `owning_entity_id` and the
--           governance policy fields that drive LiveGovernanceGuard (doctrine §6, §8).
--           Additive only; legacy event_type / context_type / organiser_* are preserved
--           and backfilled into the canonical fields.
-- =============================================

ALTER TABLE live.live_events
    ADD COLUMN IF NOT EXISTS mode                          VARCHAR(32),
    ADD COLUMN IF NOT EXISTS owning_service                VARCHAR(32),
    ADD COLUMN IF NOT EXISTS owning_entity_id              VARCHAR(255),
    -- Governance policies (doctrine §8): clinical stricter than public.
    ADD COLUMN IF NOT EXISTS recording_policy              VARCHAR(32)  NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN IF NOT EXISTS replay_policy                 VARCHAR(32)  NOT NULL DEFAULT 'DISABLED',
    ADD COLUMN IF NOT EXISTS moderation_policy             VARCHAR(32)  NOT NULL DEFAULT 'NONE',
    ADD COLUMN IF NOT EXISTS consent_policy                VARCHAR(32)  NOT NULL DEFAULT 'NOT_REQUIRED',
    ADD COLUMN IF NOT EXISTS speaker_verification_required BOOLEAN      NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS emergency_flag                BOOLEAN      NOT NULL DEFAULT false,
    -- Clinical context (only populated for CLINICAL_SESSION; never free-floating).
    ADD COLUMN IF NOT EXISTS clinical_context_ref          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS client_id                     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS provider_id                   VARCHAR(255),
    ADD COLUMN IF NOT EXISTS privacy_level                 VARCHAR(32);

-- ---------------------------------------------------------------------------
-- Backfill canonical mode from legacy free-form fields (best-effort, doctrine map).
-- ---------------------------------------------------------------------------
UPDATE live.live_events SET mode = CASE
    WHEN mode IS NOT NULL THEN mode
    WHEN upper(coalesce(event_type,'')) LIKE '%CLINICAL%'
      OR upper(coalesce(event_type,'')) LIKE '%CONSULT%'
      OR upper(coalesce(event_type,'')) LIKE '%TELEMED%'
      OR upper(coalesce(event_type,'')) LIKE '%WARD%'
      OR upper(coalesce(event_type,'')) LIKE '%REFERRAL%'
      OR upper(coalesce(organiser_type,'')) = 'TELEMEDICINE' THEN 'CLINICAL_SESSION'
    WHEN upper(coalesce(event_type,'')) LIKE '%EMERGENCY%'
      OR upper(coalesce(event_type,'')) LIKE '%OUTBREAK%'
      OR upper(coalesce(event_type,'')) LIKE '%BRIEFING%' THEN 'EMERGENCY_BRIEFING'
    WHEN upper(coalesce(event_type,'')) LIKE '%HYBRID%' THEN 'HYBRID_EVENT'
    WHEN upper(coalesce(event_type,'')) LIKE '%CPD%'
      OR upper(coalesce(event_type,'')) LIKE '%WEBINAR%'
      OR upper(coalesce(event_type,'')) LIKE '%TRAINING%'
      OR upper(coalesce(event_type,'')) LIKE '%COURSE%'
      OR upper(coalesce(organiser_type,'')) = 'FUNDO'
      OR linked_fundo_course_id IS NOT NULL THEN 'WEBINAR_CPD'
    WHEN upper(coalesce(event_type,'')) LIKE '%BROADCAST%'
      OR upper(coalesce(event_type,'')) LIKE '%CAMPAIGN%'
      OR upper(coalesce(event_type,'')) LIKE '%LAUNCH%'
      OR upper(coalesce(event_type,'')) LIKE '%EDUCATION%'
      OR upper(coalesce(organiser_type,'')) = 'PUBLIC_HEALTH'
      OR context_type = 'PUBLIC'
      OR context_type = 'CITIZEN' THEN 'PUBLIC_BROADCAST'
    ELSE 'PROFESSIONAL_MEETING'
END
WHERE mode IS NULL;

UPDATE live.live_events SET owning_service = CASE
    WHEN owning_service IS NOT NULL THEN owning_service
    WHEN mode = 'CLINICAL_SESSION' THEN 'TELEMEDICINE'
    WHEN mode = 'WEBINAR_CPD' THEN 'FUNDO'
    WHEN mode = 'PUBLIC_BROADCAST' THEN 'PUBLIC_HEALTH'
    WHEN mode = 'EMERGENCY_BRIEFING' THEN 'PUBLIC_HEALTH'
    WHEN upper(coalesce(organiser_type,'')) = 'TELEMEDICINE' THEN 'TELEMEDICINE'
    WHEN upper(coalesce(organiser_type,'')) = 'FUNDO' THEN 'FUNDO'
    WHEN upper(coalesce(organiser_type,'')) = 'PUBLIC_HEALTH' THEN 'PUBLIC_HEALTH'
    ELSE 'STANDALONE_IMPILO_LIVE'
END
WHERE owning_service IS NULL;

-- Preserve existing recording/replay booleans into the new policy fields.
UPDATE live.live_events SET recording_policy = 'ALLOWED' WHERE recording_allowed = true AND recording_policy = 'DISABLED';
UPDATE live.live_events SET replay_policy = 'ALLOWED' WHERE replay_allowed = true AND replay_policy = 'DISABLED';
UPDATE live.live_events SET consent_policy = 'REQUIRED' WHERE consent_required = true AND consent_policy = 'NOT_REQUIRED';
UPDATE live.live_events SET owning_entity_id = linked_service_id WHERE owning_entity_id IS NULL AND linked_service_id IS NOT NULL;
UPDATE live.live_events SET owning_entity_id = linked_campaign_id WHERE owning_entity_id IS NULL AND linked_campaign_id IS NOT NULL;
UPDATE live.live_events SET owning_entity_id = linked_fundo_course_id::text WHERE owning_entity_id IS NULL AND linked_fundo_course_id IS NOT NULL;
UPDATE live.live_events SET moderation_policy = 'MODERATED', speaker_verification_required = true
    WHERE mode IN ('PUBLIC_BROADCAST', 'EMERGENCY_BRIEFING');

ALTER TABLE live.live_events ALTER COLUMN mode SET DEFAULT 'PROFESSIONAL_MEETING';
ALTER TABLE live.live_events ALTER COLUMN owning_service SET DEFAULT 'STANDALONE_IMPILO_LIVE';

CREATE INDEX IF NOT EXISTS idx_live_events_mode ON live.live_events (tenant_id, mode, status, start_time);
CREATE INDEX IF NOT EXISTS idx_live_events_owning ON live.live_events (tenant_id, owning_service, owning_entity_id);
CREATE INDEX IF NOT EXISTS idx_live_events_clinical ON live.live_events (clinical_context_ref) WHERE clinical_context_ref IS NOT NULL;
