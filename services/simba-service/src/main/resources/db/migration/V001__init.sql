CREATE SCHEMA IF NOT EXISTS simba;
CREATE TABLE simba.wellness_profiles (
    id              BIGSERIAL PRIMARY KEY,
    profile_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    display_name    VARCHAR(128),
    goals_json      JSONB DEFAULT '{}',
    preferences_json JSONB DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_profile_person UNIQUE (tenant_id, person_cpid)
);
CREATE TABLE simba.activities (
    id              BIGSERIAL PRIMARY KEY,
    activity_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    activity_type   VARCHAR(32) NOT NULL,
    title           VARCHAR(255),
    duration_minutes INT,
    calories        INT,
    distance_meters INT,
    steps           INT,
    source          VARCHAR(64),
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.vitals_log (
    id              BIGSERIAL PRIMARY KEY,
    entry_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    vital_type      VARCHAR(32) NOT NULL,
    value_numeric   DECIMAL(10,2),
    value_text      VARCHAR(128),
    unit            VARCHAR(16),
    source          VARCHAR(64),
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.mood_log (
    id              BIGSERIAL PRIMARY KEY,
    entry_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    mood_score      INT NOT NULL,
    mood_label      VARCHAR(32),
    notes           TEXT,
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.sleep_segments (
    id              BIGSERIAL PRIMARY KEY,
    segment_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ NOT NULL,
    stage           VARCHAR(16),
    duration_minutes INT,
    source          VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.exercise_sessions (
    id              BIGSERIAL PRIMARY KEY,
    session_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    exercise_type   VARCHAR(64) NOT NULL,
    start_time      TIMESTAMPTZ NOT NULL,
    end_time        TIMESTAMPTZ,
    calories        INT,
    distance_meters INT,
    laps            INT,
    repetitions     INT,
    source          VARCHAR(64),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.diet_entries (
    id              BIGSERIAL PRIMARY KEY,
    entry_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    meal_type       VARCHAR(16) NOT NULL,
    description     TEXT,
    calories        INT,
    protein_g       DECIMAL(8,2),
    carbs_g         DECIMAL(8,2),
    fat_g           DECIMAL(8,2),
    fiber_g         DECIMAL(8,2),
    water_ml        INT,
    recorded_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.goals (
    id              BIGSERIAL PRIMARY KEY,
    goal_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    goal_type       VARCHAR(32) NOT NULL,
    target_value    DECIMAL(10,2),
    current_value   DECIMAL(10,2) DEFAULT 0,
    unit            VARCHAR(16),
    period          VARCHAR(16) DEFAULT 'DAILY',
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    started_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    target_date     DATE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.clubs (
    id              BIGSERIAL PRIMARY KEY,
    club_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    name            VARCHAR(255) NOT NULL,
    description     TEXT,
    club_type       VARCHAR(32) NOT NULL,
    visibility      VARCHAR(16) DEFAULT 'PUBLIC',
    max_members     INT,
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.club_members (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    club_id         UUID NOT NULL REFERENCES simba.clubs(club_id),
    person_cpid     VARCHAR(128) NOT NULL,
    role            VARCHAR(16) DEFAULT 'MEMBER',
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_club_member UNIQUE (club_id, person_cpid)
);
CREATE TABLE simba.challenges (
    id              BIGSERIAL PRIMARY KEY,
    challenge_id    UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id       UUID NOT NULL,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    challenge_type  VARCHAR(32) NOT NULL,
    target_value    DECIMAL(10,2),
    unit            VARCHAR(16),
    start_date      DATE NOT NULL,
    end_date        DATE NOT NULL,
    status          VARCHAR(16) DEFAULT 'ACTIVE',
    created_by      VARCHAR(128),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE TABLE simba.challenge_participants (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    challenge_id    UUID NOT NULL REFERENCES simba.challenges(challenge_id),
    person_cpid     VARCHAR(128) NOT NULL,
    progress_value  DECIMAL(10,2) DEFAULT 0,
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_challenge_participant UNIQUE (challenge_id, person_cpid)
);
CREATE TABLE simba.connect_ingest_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    person_cpid     VARCHAR(128) NOT NULL,
    data_type       VARCHAR(64) NOT NULL,
    source_id       VARCHAR(255) NOT NULL,
    ingested_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_simba_ingest UNIQUE (tenant_id, person_cpid, data_type, source_id)
);
CREATE TABLE simba.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'simba-service',
    tenant_id       UUID NOT NULL,
    pod_id          VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id      VARCHAR(255) NOT NULL,
    subject_type    VARCHAR(64) NOT NULL,
    partition_key   VARCHAR(255),
    occurred_at     TIMESTAMPTZ NOT NULL,
    payload_json    JSONB NOT NULL,
    publish_error   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_simba_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_simba_outbox_unpublished ON simba.event_outbox (created_at) WHERE published_at IS NULL;
