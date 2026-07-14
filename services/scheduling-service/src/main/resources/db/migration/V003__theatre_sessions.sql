-- scheduling-service V003 — OR sessions / lists / resource reservations (Wave 2)
-- theatre_session = a booked OR list for a room on a date. or_list_item = one
-- case slot on that list, referencing a waitlist entry and (once published) the
-- inpatient procedure_episode via booking. resource_reservation is the
-- conflict-detection substrate: overlapping holds on a ROOM/PRACTITIONER/PATIENT/
-- etc. are how double-booking is detected against REAL data before publish.

CREATE TABLE scheduling.theatre_session (
    id                  UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    facility_id         VARCHAR(64)     NOT NULL,
    clinical_space_id   UUID,           -- reference to tuso.clinical_space (theatre)
    session_date        DATE            NOT NULL,
    start_time          TIME            NOT NULL DEFAULT '08:00',
    end_time            TIME            NOT NULL DEFAULT '17:00',
    slot                VARCHAR(16)     NOT NULL DEFAULT 'AM',  -- AM | PM | ALL_DAY
    turnover_minutes    INTEGER         NOT NULL DEFAULT 30,
    lead_surgeon_ref    VARCHAR(128),
    vashandi_team_id    UUID,           -- reference to vsh_theatre_team
    status              VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',  -- DRAFT | PUBLISHED | CANCELLED
    cancel_reason       TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_session_status CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED'))
);

CREATE INDEX idx_theatre_session_tenant_date
    ON scheduling.theatre_session (tenant_id, facility_id, session_date, status);

CREATE TABLE scheduling.or_list_item (
    id                      UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    session_id              UUID            NOT NULL
        REFERENCES scheduling.theatre_session(id) ON DELETE CASCADE,
    waitlist_entry_id       UUID,           -- reference to scheduling.surgical_waitlist_entry
    patient_id              VARCHAR(255)    NOT NULL,
    zibo                    VARCHAR(64),
    sequence_position       INTEGER         NOT NULL DEFAULT 1,
    estimated_duration_min  INTEGER         NOT NULL DEFAULT 60,
    surgeon_ref             VARCHAR(128),
    status                  VARCHAR(16)     NOT NULL DEFAULT 'PLANNED',
        -- PLANNED | PUBLISHED | CANCELLED | COMPLETED
    booking_ref             VARCHAR(64),    -- reference to booking-service booking id
    procedure_episode_ref   VARCHAR(64),    -- reference to inpatient.procedure_episode (the ONE case SoR)
    cancel_reason_code      VARCHAR(48),
    cancel_reason_text      TEXT,
    history                 JSONB           NOT NULL DEFAULT '[]'::jsonb,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_or_item_status CHECK (status IN ('PLANNED','PUBLISHED','CANCELLED','COMPLETED'))
);

CREATE INDEX idx_or_list_item_session
    ON scheduling.or_list_item (session_id, sequence_position);
CREATE INDEX idx_or_list_item_tenant_episode
    ON scheduling.or_list_item (tenant_id, procedure_episode_ref)
    WHERE procedure_episode_ref IS NOT NULL;

CREATE TABLE scheduling.resource_reservation (
    id                  UUID            PRIMARY KEY,
    tenant_id           UUID            NOT NULL,
    session_id          UUID            REFERENCES scheduling.theatre_session(id) ON DELETE CASCADE,
    or_list_item_id     UUID,
    resource_kind       VARCHAR(24)     NOT NULL,
        -- ROOM | PRACTITIONER | EQUIPMENT | INSTRUMENT_SET | RECOVERY_BED | ICU_BED | PATIENT
    resource_ref        VARCHAR(128)    NOT NULL,
    reservation_date    DATE            NOT NULL,
    start_time          TIME            NOT NULL,
    end_time            TIME            NOT NULL,
    status              VARCHAR(16)     NOT NULL DEFAULT 'HELD',  -- HELD | RELEASED
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT chk_reservation_kind CHECK (resource_kind IN
        ('ROOM','PRACTITIONER','EQUIPMENT','INSTRUMENT_SET','RECOVERY_BED','ICU_BED','PATIENT'))
);

-- Mirror the slot_reservations unique-constraint style: a HELD resource for a
-- kind+ref+date+start is unique (the DB is the double-book backstop).
CREATE UNIQUE INDEX uq_resource_reservation_slot
    ON scheduling.resource_reservation (tenant_id, resource_kind, resource_ref, reservation_date, start_time)
    WHERE status = 'HELD';
CREATE INDEX idx_resource_reservation_lookup
    ON scheduling.resource_reservation (tenant_id, resource_kind, resource_ref, reservation_date, status);
