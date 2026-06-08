CREATE SCHEMA IF NOT EXISTS madi;

-- Donor domain
CREATE TABLE madi.donor_profiles (
    id                  BIGSERIAL PRIMARY KEY,
    donor_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    person_cpid         VARCHAR(128) NOT NULL,
    blood_group         VARCHAR(8),
    rh_factor           VARCHAR(8),
    status              VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    registered_by       VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_donor_person UNIQUE (tenant_id, person_cpid)
);

CREATE TABLE madi.donor_eligibility_screenings (
    id                  BIGSERIAL PRIMARY KEY,
    screening_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    drive_id            UUID,
    result              VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'COMPLETED',
    screening_notes     TEXT,
    screened_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    screened_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.donor_deferrals (
    id                  BIGSERIAL PRIMARY KEY,
    deferral_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    reason_code         VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    deferred_until      DATE,
    notes               TEXT,
    deferred_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.donor_communication_preferences (
    id                  BIGSERIAL PRIMARY KEY,
    preference_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    channel             VARCHAR(32) NOT NULL,
    enabled             BOOLEAN NOT NULL DEFAULT TRUE,
    locale              VARCHAR(16) DEFAULT 'en-ZW',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    updated_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_donor_pref UNIQUE (tenant_id, donor_id, channel)
);

CREATE TABLE madi.donor_feedback (
    id                  BIGSERIAL PRIMARY KEY,
    feedback_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    drive_id            UUID,
    rating              INT,
    comments            TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'SUBMITTED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.donor_notifications (
    id                  BIGSERIAL PRIMARY KEY,
    notification_id     UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    channel             VARCHAR(32) NOT NULL,
    subject             VARCHAR(255),
    body                TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    sent_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Donation drives
CREATE TABLE madi.donation_drives (
    id                  BIGSERIAL PRIMARY KEY,
    drive_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    drive_type          VARCHAR(32) NOT NULL DEFAULT 'MOBILE',
    facility_id         UUID,
    location_ref        VARCHAR(255),
    ndila_site_ref      VARCHAR(255),
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    start_at            TIMESTAMPTZ NOT NULL,
    end_at              TIMESTAMPTZ NOT NULL,
    capacity            INT,
    published_by        VARCHAR(128),
    created_by          VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.donation_drive_slots (
    id                  BIGSERIAL PRIMARY KEY,
    slot_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    drive_id            UUID NOT NULL REFERENCES madi.donation_drives(drive_id),
    slot_start          TIMESTAMPTZ NOT NULL,
    slot_end            TIMESTAMPTZ NOT NULL,
    capacity            INT NOT NULL DEFAULT 1,
    booked_count        INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.donation_drive_attendances (
    id                  BIGSERIAL PRIMARY KEY,
    attendance_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    drive_id            UUID NOT NULL REFERENCES madi.donation_drives(drive_id),
    slot_id             UUID REFERENCES madi.donation_drive_slots(slot_id),
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'REGISTERED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    registered_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    checked_in_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_drive_donor UNIQUE (tenant_id, drive_id, donor_id)
);

CREATE TABLE madi.donation_events (
    id                  BIGSERIAL PRIMARY KEY,
    event_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    drive_id            UUID REFERENCES madi.donation_drives(drive_id),
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    event_type          VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_collections (
    id                  BIGSERIAL PRIMARY KEY,
    collection_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    donor_id            UUID NOT NULL REFERENCES madi.donor_profiles(donor_id),
    drive_id            UUID REFERENCES madi.donation_drives(drive_id),
    bag_number          VARCHAR(64) NOT NULL,
    volume_ml           INT,
    status              VARCHAR(32) NOT NULL DEFAULT 'COLLECTED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    collected_by        VARCHAR(128),
    collected_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_bag_number UNIQUE (tenant_id, bag_number)
);

-- Blood units and processing
CREATE TABLE madi.blood_products (
    id                  BIGSERIAL PRIMARY KEY,
    product_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    product_code        VARCHAR(64) NOT NULL,
    product_name        VARCHAR(255) NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    blood_group         VARCHAR(8),
    shelf_life_days     INT,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_product_code UNIQUE (tenant_id, product_code)
);

CREATE TABLE madi.blood_units (
    id                  BIGSERIAL PRIMARY KEY,
    unit_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    collection_id       UUID NOT NULL REFERENCES madi.blood_collections(collection_id),
    bag_number          VARCHAR(64) NOT NULL,
    blood_group         VARCHAR(8) NOT NULL,
    rh_factor           VARCHAR(8),
    component_type      VARCHAR(32) NOT NULL DEFAULT 'WHOLE_BLOOD',
    status              VARCHAR(32) NOT NULL DEFAULT 'COLLECTED',
    expiry_at           TIMESTAMPTZ,
    facility_id         UUID,
    blood_bank_id       UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_components (
    id                  BIGSERIAL PRIMARY KEY,
    component_id        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    product_id          UUID REFERENCES madi.blood_products(product_id),
    component_type      VARCHAR(32) NOT NULL,
    volume_ml           INT,
    status              VARCHAR(32) NOT NULL DEFAULT 'PREPARED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    prepared_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_processing_batches (
    id                  BIGSERIAL PRIMARY KEY,
    batch_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    batch_status        VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    test_results_json   JSONB DEFAULT '{}',
    quarantine_reason   TEXT,
    released_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Blood bank inventory
CREATE TABLE madi.blood_banks (
    id                  BIGSERIAL PRIMARY KEY,
    blood_bank_id       UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    facility_id         UUID NOT NULL,
    bank_name           VARCHAR(255) NOT NULL,
    bank_type           VARCHAR(32) NOT NULL DEFAULT 'HOSPITAL',
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_storage_locations (
    id                  BIGSERIAL PRIMARY KEY,
    location_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    location_code       VARCHAR(64) NOT NULL,
    location_name       VARCHAR(255) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_fridges (
    id                  BIGSERIAL PRIMARY KEY,
    fridge_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    location_id         UUID REFERENCES madi.blood_storage_locations(location_id),
    fridge_code         VARCHAR(64) NOT NULL,
    temperature_c       DECIMAL(5,2),
    status              VARCHAR(32) NOT NULL DEFAULT 'OPERATIONAL',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_inventory_balances (
    id                  BIGSERIAL PRIMARY KEY,
    balance_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    inventory_item_code VARCHAR(64) NOT NULL,
    blood_group         VARCHAR(8) NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    quantity_on_hand    INT NOT NULL DEFAULT 0,
    quantity_reserved   INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_madi_inventory_balance UNIQUE (tenant_id, blood_bank_id, inventory_item_code, blood_group, component_type)
);

CREATE TABLE madi.blood_stock_movements (
    id                  BIGSERIAL PRIMARY KEY,
    movement_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    unit_id             UUID REFERENCES madi.blood_units(unit_id),
    inventory_item_code VARCHAR(64) NOT NULL,
    movement_type       VARCHAR(32) NOT NULL,
    quantity            INT NOT NULL DEFAULT 1,
    reference_type      VARCHAR(64),
    reference_id        VARCHAR(128),
    status              VARCHAR(32) NOT NULL DEFAULT 'POSTED',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    moved_by            VARCHAR(128),
    moved_at            TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_stock_reconciliations (
    id                  BIGSERIAL PRIMARY KEY,
    reconciliation_id   UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    variance_count      INT NOT NULL DEFAULT 0,
    notes               TEXT,
    reconciled_by       VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    reconciled_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Orders and transfusion workflow
CREATE TABLE madi.blood_orders (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    oros_order_ref      VARCHAR(128),
    patient_cpid        VARCHAR(128) NOT NULL,
    blood_group         VARCHAR(8) NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    units_requested     INT NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    priority            VARCHAR(16) DEFAULT 'ROUTINE',
    facility_id         UUID,
    ordering_provider   VARCHAR(128),
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_order_items (
    id                  BIGSERIAL PRIMARY KEY,
    item_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES madi.blood_orders(order_id),
    component_type      VARCHAR(32) NOT NULL,
    blood_group         VARCHAR(8) NOT NULL,
    quantity            INT NOT NULL DEFAULT 1,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_samples (
    id                  BIGSERIAL PRIMARY KEY,
    sample_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES madi.blood_orders(order_id),
    sample_number       VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'COLLECTED',
    collected_by        VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    collected_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.crossmatch_requests (
    id                  BIGSERIAL PRIMARY KEY,
    request_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES madi.blood_orders(order_id),
    sample_id           UUID NOT NULL REFERENCES madi.blood_samples(sample_id),
    unit_id             UUID REFERENCES madi.blood_units(unit_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    requested_by        VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    requested_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.crossmatch_results (
    id                  BIGSERIAL PRIMARY KEY,
    result_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    request_id          UUID NOT NULL REFERENCES madi.crossmatch_requests(request_id),
    result_status       VARCHAR(32) NOT NULL,
    result_notes        TEXT,
    tested_by           VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    tested_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_reservations (
    id                  BIGSERIAL PRIMARY KEY,
    reservation_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES madi.blood_orders(order_id),
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reserved_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    reserved_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_issues (
    id                  BIGSERIAL PRIMARY KEY,
    issue_id            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID NOT NULL REFERENCES madi.blood_orders(order_id),
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    reservation_id      UUID REFERENCES madi.blood_reservations(reservation_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'ISSUED',
    issued_by           VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_dispatches (
    id                  BIGSERIAL PRIMARY KEY,
    dispatch_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    issue_id            UUID NOT NULL REFERENCES madi.blood_issues(issue_id),
    destination_facility_id UUID,
    status              VARCHAR(32) NOT NULL DEFAULT 'DISPATCHED',
    dispatched_by       VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    dispatched_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_receipts (
    id                  BIGSERIAL PRIMARY KEY,
    receipt_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    dispatch_id         UUID NOT NULL REFERENCES madi.blood_dispatches(dispatch_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    received_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transfusion
CREATE TABLE madi.transfusion_episodes (
    id                  BIGSERIAL PRIMARY KEY,
    episode_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    order_id            UUID REFERENCES madi.blood_orders(order_id),
    issue_id            UUID REFERENCES madi.blood_issues(issue_id),
    patient_cpid        VARCHAR(128) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'PLANNED',
    facility_id         UUID,
    ward_id             UUID,
    started_by          VARCHAR(128),
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    started_at          TIMESTAMPTZ,
    completed_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.transfusion_observations (
    id                  BIGSERIAL PRIMARY KEY,
    observation_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    episode_id          UUID NOT NULL REFERENCES madi.transfusion_episodes(episode_id),
    observation_type    VARCHAR(64) NOT NULL,
    value_numeric       DECIMAL(10,2),
    value_text          VARCHAR(255),
    unit                VARCHAR(16),
    observed_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    observed_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.transfusion_outcomes (
    id                  BIGSERIAL PRIMARY KEY,
    outcome_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    episode_id          UUID NOT NULL REFERENCES madi.transfusion_episodes(episode_id),
    outcome_status      VARCHAR(32) NOT NULL,
    outcome_notes       TEXT,
    recorded_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Haemovigilance
CREATE TABLE madi.adverse_transfusion_reactions (
    id                  BIGSERIAL PRIMARY KEY,
    reaction_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    episode_id          UUID NOT NULL REFERENCES madi.transfusion_episodes(episode_id),
    reaction_type       VARCHAR(64) NOT NULL,
    severity            VARCHAR(16) NOT NULL DEFAULT 'MODERATE',
    status              VARCHAR(32) NOT NULL DEFAULT 'REPORTED',
    description         TEXT,
    reported_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    reported_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.haemovigilance_cases (
    id                  BIGSERIAL PRIMARY KEY,
    case_id             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    reaction_id         UUID NOT NULL REFERENCES madi.adverse_transfusion_reactions(reaction_id),
    status              VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    investigation_notes TEXT,
    assigned_to         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    opened_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Returns, wastage, discards
CREATE TABLE madi.blood_returns (
    id                  BIGSERIAL PRIMARY KEY,
    return_id           UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    reason_code         VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'RETURNED',
    returned_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    returned_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_wastage (
    id                  BIGSERIAL PRIMARY KEY,
    wastage_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    reason_code         VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'RECORDED',
    recorded_by         VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_discards (
    id                  BIGSERIAL PRIMARY KEY,
    discard_id          UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    unit_id             UUID NOT NULL REFERENCES madi.blood_units(unit_id),
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    reason_code         VARCHAR(64) NOT NULL,
    status              VARCHAR(32) NOT NULL DEFAULT 'DISCARDED',
    discarded_by        VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    discarded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Forecasting and audit
CREATE TABLE madi.blood_forecasts (
    id                  BIGSERIAL PRIMARY KEY,
    forecast_id         UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    blood_bank_id       UUID NOT NULL REFERENCES madi.blood_banks(blood_bank_id),
    blood_group         VARCHAR(8) NOT NULL,
    component_type      VARCHAR(32) NOT NULL,
    forecast_period     VARCHAR(16) NOT NULL DEFAULT 'WEEKLY',
    predicted_demand    INT NOT NULL DEFAULT 0,
    predicted_supply    INT NOT NULL DEFAULT 0,
    status              VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    forecast_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE madi.blood_audit_events (
    id                  BIGSERIAL PRIMARY KEY,
    audit_event_id      UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    tenant_id           UUID NOT NULL,
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        VARCHAR(128) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    actor_id            VARCHAR(128),
    facility_id         UUID,
    jurisdiction        VARCHAR(64) NOT NULL DEFAULT 'ZW',
    payload_json        JSONB DEFAULT '{}',
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Transactional outbox (v1.1)
CREATE TABLE madi.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'madi-service',
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
    CONSTRAINT uq_madi_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_madi_outbox_unpublished ON madi.event_outbox (created_at) WHERE published_at IS NULL;
