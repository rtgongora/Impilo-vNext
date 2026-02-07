-- =============================================================================
-- Ubomi — Civil Registration & Vital Statistics Interface: Base Schema
-- National CRVS integration gateway for birth/death notification,
-- vital event verification, and civil registry interoperability.
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS ubomi;

-- ---------------------------------------------------------------------------
-- Birth Notification: facility-originated birth events
-- ---------------------------------------------------------------------------
-- Flow: Facility submits → Ubomi validates → Civil Registrar approves
--       → BIRTH_REGISTERED event → VITO issues newborn Impilo ID
-- ---------------------------------------------------------------------------
CREATE TABLE ubomi.birth_notification (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    notification_number     VARCHAR(100) NOT NULL,
    facility_id             UUID         NOT NULL,       -- TUSO facility reference
    mother_cpid             VARCHAR(100),                -- VITO CPID of mother (if known)
    father_cpid             VARCHAR(100),                -- VITO CPID of father (if known)
    child_given_names       VARCHAR(500),
    child_surname           VARCHAR(500),
    date_of_birth           DATE         NOT NULL,
    time_of_birth           TIME,
    place_of_birth_type     VARCHAR(50)  NOT NULL,       -- FACILITY, HOME, TRANSIT
    sex                     VARCHAR(10)  NOT NULL,       -- MALE, FEMALE, INDETERMINATE
    birth_weight_grams      INTEGER,
    gestational_age_weeks   INTEGER,
    birth_order             INTEGER      DEFAULT 1,
    multiple_birth          BOOLEAN      NOT NULL DEFAULT false,
    attendant_cpid          VARCHAR(100),                -- VARAPI provider who attended
    attendant_role          VARCHAR(50),                 -- MIDWIFE, DOCTOR, OTHER
    notified_by_actor       VARCHAR(255) NOT NULL,       -- actor who submitted
    civil_reg_number        VARCHAR(100),                -- assigned after registration
    status                  VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED',
                                                         -- SUBMITTED, VALIDATED, REGISTERED,
                                                         -- REJECTED, CANCELLED
    rejection_reason        TEXT,
    registered_at           TIMESTAMPTZ,
    metadata                JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_birth_notification UNIQUE (tenant_id, notification_number)
);

CREATE INDEX idx_birth_mother    ON ubomi.birth_notification (tenant_id, mother_cpid) WHERE mother_cpid IS NOT NULL;
CREATE INDEX idx_birth_facility  ON ubomi.birth_notification (tenant_id, facility_id, status);
CREATE INDEX idx_birth_date      ON ubomi.birth_notification (tenant_id, date_of_birth);
CREATE INDEX idx_birth_civil_reg ON ubomi.birth_notification (civil_reg_number) WHERE civil_reg_number IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Death Notification: facility-originated death events
-- ---------------------------------------------------------------------------
-- Flow: Facility submits → Practitioner certifies cause → Civil Registrar
--       approves → DEATH_REGISTERED event → VITO marks CPID as DECEASED
-- ---------------------------------------------------------------------------
CREATE TABLE ubomi.death_notification (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    notification_number     VARCHAR(100) NOT NULL,
    facility_id             UUID         NOT NULL,       -- TUSO facility reference
    deceased_cpid           VARCHAR(100) NOT NULL,       -- VITO CPID of deceased
    date_of_death           DATE         NOT NULL,
    time_of_death           TIME,
    place_of_death_type     VARCHAR(50)  NOT NULL,       -- FACILITY, HOME, DOA, TRANSIT
    manner_of_death         VARCHAR(50),                 -- NATURAL, ACCIDENT, SUICIDE, HOMICIDE, UNDETERMINED, PENDING
    immediate_cause         VARCHAR(500),                -- ICD-11 coded cause
    underlying_cause        VARCHAR(500),                -- ICD-11 coded underlying cause
    contributing_conditions TEXT,
    certifying_practitioner VARCHAR(100),                -- VARAPI provider CPID
    certifier_role          VARCHAR(50),                 -- DOCTOR, MEDICAL_EXAMINER
    certified_at            TIMESTAMPTZ,
    notified_by_actor       VARCHAR(255) NOT NULL,
    civil_reg_number        VARCHAR(100),                -- death certificate number
    status                  VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED',
                                                         -- SUBMITTED, CERTIFIED, REGISTERED,
                                                         -- REJECTED, CANCELLED
    rejection_reason        TEXT,
    registered_at           TIMESTAMPTZ,
    metadata                JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),

    CONSTRAINT uq_death_notification UNIQUE (tenant_id, notification_number)
);

CREATE INDEX idx_death_deceased  ON ubomi.death_notification (tenant_id, deceased_cpid);
CREATE INDEX idx_death_facility  ON ubomi.death_notification (tenant_id, facility_id, status);
CREATE INDEX idx_death_date      ON ubomi.death_notification (tenant_id, date_of_death);
CREATE INDEX idx_death_civil_reg ON ubomi.death_notification (civil_reg_number) WHERE civil_reg_number IS NOT NULL;

-- ---------------------------------------------------------------------------
-- Verification Log: tracks all vital event verification requests
-- ---------------------------------------------------------------------------
CREATE TABLE ubomi.verification_log (
    id                      BIGSERIAL PRIMARY KEY,
    tenant_id               UUID         NOT NULL,
    registration_number     VARCHAR(100) NOT NULL,
    event_type              VARCHAR(20)  NOT NULL,       -- BIRTH, DEATH
    requested_by_actor      VARCHAR(255) NOT NULL,
    access_mode             VARCHAR(20)  NOT NULL,       -- INTERNAL, EXTERNAL
    verification_result     VARCHAR(30)  NOT NULL,       -- VERIFIED, NOT_FOUND, MISMATCH, ERROR
    response_summary        JSONB,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_verification_reg ON ubomi.verification_log (tenant_id, registration_number);

-- ---------------------------------------------------------------------------
-- Event outbox (reliable Kafka publishing)
-- ---------------------------------------------------------------------------
CREATE TABLE ubomi.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(100) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    payload         JSONB        NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_ubomi_outbox_unpublished ON ubomi.event_outbox (created_at)
    WHERE published_at IS NULL;
