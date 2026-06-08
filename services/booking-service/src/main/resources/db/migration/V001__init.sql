-- =============================================
-- Service : booking-service
-- Schema  : booking
-- Flyway  : V001__init.sql
-- =============================================

CREATE SCHEMA IF NOT EXISTS booking;

-- Booking transaction container
CREATE TABLE booking.booking (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL,
    booking_number              VARCHAR(32)     NOT NULL,
    client_id                   VARCHAR(255)    NOT NULL,
    requested_by_actor_id       VARCHAR(255)    NOT NULL,
    requested_by_actor_type     VARCHAR(32)     NOT NULL,
    booking_type                VARCHAR(50)     NOT NULL,
    service_id                  VARCHAR(255),
    service_name                VARCHAR(255),
    facility_id                 BIGINT,
    provider_id                 VARCHAR(255),
    provider_name               VARCHAR(255),
    target_type                 VARCHAR(32),
    target_ref                  VARCHAR(255),
    preferred_start_at          TIMESTAMPTZ,
    preferred_end_at            TIMESTAMPTZ,
    preferred_channel           VARCHAR(32),
    location                    TEXT,
    reason                      TEXT,
    clinical_priority           VARCHAR(32)     NOT NULL DEFAULT 'ROUTINE',
    triage_status               VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    eligibility_status          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    consent_status              VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    agreement_status            VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    authorisation_status        VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    payment_status              VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    approval_status             VARCHAR(32)     NOT NULL DEFAULT 'NOT_REQUIRED',
    booking_status              VARCHAR(32)     NOT NULL DEFAULT 'REQUESTED',
    linked_appointment_id       UUID,
    specialty                   VARCHAR(128),
    resource_id                 UUID,
    tuso_booking_id             UUID,
    notes                       TEXT,
    created_by                  VARCHAR(255)    NOT NULL,
    updated_by                  VARCHAR(255),
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    UNIQUE (tenant_id, booking_number)
);

CREATE INDEX idx_booking_tenant_client ON booking.booking (tenant_id, client_id, created_at DESC);
CREATE INDEX idx_booking_tenant_status ON booking.booking (tenant_id, booking_status, created_at DESC);
CREATE INDEX idx_booking_facility ON booking.booking (tenant_id, facility_id, booking_status);
CREATE INDEX idx_booking_provider ON booking.booking (tenant_id, provider_id, booking_status);

-- Scheduled appointment (operational event)
CREATE TABLE booking.appointment (
    id                          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                   UUID            NOT NULL,
    booking_id                  UUID            REFERENCES booking.booking(id),
    appointment_number          VARCHAR(32)     NOT NULL,
    client_id                   VARCHAR(255)    NOT NULL,
    patient_cpid                VARCHAR(255)    NOT NULL,
    patient_id                  VARCHAR(255),
    provider_id                 VARCHAR(255),
    provider_name               VARCHAR(255),
    facility_id                 BIGINT,
    facility_name               VARCHAR(255),
    service_id                  VARCHAR(255),
    appointment_type            VARCHAR(50)     NOT NULL,
    start_time                  TIMESTAMPTZ     NOT NULL,
    end_time                    TIMESTAMPTZ,
    timezone                    VARCHAR(64)     DEFAULT 'Africa/Harare',
    channel                     VARCHAR(32),
    location                    TEXT,
    virtual_meeting_details     JSONB,
    status                      VARCHAR(30)     NOT NULL DEFAULT 'SCHEDULED',
    check_in_status             VARCHAR(32)     NOT NULL DEFAULT 'NOT_CHECKED_IN',
    encounter_id                UUID,
    queue_token_id              UUID,
    resource_id                 UUID,
    reason                      TEXT,
    notes                       TEXT,
    -- legacy TUSO compat fields
    scheduled_at                TIMESTAMPTZ,
    end_at                      TIMESTAMPTZ,
    created_by                  VARCHAR(255)    NOT NULL,
    cancelled_by                VARCHAR(255),
    cancelled_at                TIMESTAMPTZ,
    cancel_reason               TEXT,
    created_at                  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ,
    UNIQUE (tenant_id, appointment_number)
);

CREATE INDEX idx_appt_tenant_cpid ON booking.appointment (tenant_id, patient_cpid, start_time DESC);
CREATE INDEX idx_appt_facility_time ON booking.appointment (facility_id, start_time)
    WHERE status NOT IN ('CANCELLED');
CREATE INDEX idx_appt_booking ON booking.appointment (booking_id);
CREATE INDEX idx_appt_provider ON booking.appointment (tenant_id, provider_id, start_time DESC);

-- Links to sovereign records (orders, referrals, payments, etc.)
CREATE TABLE booking.booking_link (
    id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    booking_id      UUID            NOT NULL REFERENCES booking.booking(id) ON DELETE CASCADE,
    link_type       VARCHAR(32)     NOT NULL,
    link_id         VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (booking_id, link_type, link_id)
);

CREATE INDEX idx_booking_link_booking ON booking.booking_link (booking_id);

-- Mvumo consent/agreement/authorisation orchestration
CREATE TABLE booking.booking_mvumo (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID            NOT NULL,
    booking_id          UUID            NOT NULL REFERENCES booking.booking(id) ON DELETE CASCADE,
    mvumo_record_id     VARCHAR(255)    NOT NULL,
    mvumo_type          VARCHAR(32)     NOT NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ,
    UNIQUE (booking_id, mvumo_type)
);

CREATE INDEX idx_booking_mvumo_booking ON booking.booking_mvumo (booking_id);

-- Booking state transition audit trail
CREATE TABLE booking.booking_audit (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    booking_id      UUID            NOT NULL REFERENCES booking.booking(id) ON DELETE CASCADE,
    action          VARCHAR(64)     NOT NULL,
    actor_id        VARCHAR(255)    NOT NULL,
    from_status     VARCHAR(32),
    to_status       VARCHAR(32),
    detail          JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_booking_audit_booking ON booking.booking_audit (booking_id, created_at DESC);

-- Standard transactional event outbox
CREATE TABLE booking.event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(255)    NOT NULL,
    aggregate_id    VARCHAR(255)    NOT NULL,
    event_type      VARCHAR(255)    NOT NULL,
    payload         JSONB           NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished
    ON booking.event_outbox (created_at)
    WHERE published_at IS NULL;
