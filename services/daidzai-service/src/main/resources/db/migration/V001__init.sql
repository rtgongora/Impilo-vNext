-- =====================================================================================
-- Daidzai — Emergency, Disaster & Public-Health Response Command :: V001 initial schema
-- Schema: daidzai.  All domain tables are prefixed dai_*.
-- Owns: emergency_request (intake), emergency_incident (the orchestration aggregate),
-- mission status timeline, resource_request, owner-routed seam links (dispatch/maps/care/
-- facility/comms/after-action). Does NOT own: dispatch execution (nhume), routing (ndila),
-- clinical record (pct/butano), identity (vito), comms delivery (khuluma), after-action
-- review record (rito) — those are referenced by id only. No payment ever blocks an emergency.
-- =====================================================================================

CREATE SCHEMA IF NOT EXISTS daidzai;

-- -------------------------------------------------------------------------------------
-- Emergency request — the intake row. Captures who is asking, for whom, what, where.
-- A request is triaged into (or attached to) an incident. Requester may be a citizen,
-- caregiver, provider, facility or bystander; subject identity may be temporary/unknown.
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_emergency_request (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    request_reference       VARCHAR(48) NOT NULL,
    requester_type          VARCHAR(32) NOT NULL,   -- CITIZEN/CAREGIVER/PROVIDER/FACILITY/BYSTANDER
    requester_actor_id      VARCHAR(128),           -- actor id when authenticated (audit), null for anon
    -- Subject of the emergency (the person needing help). Vito owns identity; we store a ref + mode.
    subject_identity_mode   VARCHAR(24) NOT NULL,   -- KNOWN/TEMPORARY/UNKNOWN/ANONYMOUS
    subject_health_id       VARCHAR(128),           -- Vito Health ID when KNOWN; null otherwise
    subject_temp_ref        VARCHAR(128),           -- Vito temporary/unknown identity ref
    subject_label           VARCHAR(255),           -- free-text label for unknown subject (e.g. "male ~40")
    emergency_category      VARCHAR(48) NOT NULL,   -- MEDICAL/TRAUMA/OBSTETRIC/CARDIAC/MENTAL_HEALTH/...
    sensitive               BOOLEAN NOT NULL DEFAULT FALSE,  -- violence/SA/mental-health/child-protection
    severity                VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN', -- UNKNOWN/LOW/MODERATE/HIGH/CRITICAL
    description             TEXT,
    location_lat            DOUBLE PRECISION,
    location_lng            DOUBLE PRECISION,
    location_description    VARCHAR(512),
    attachments_ref         VARCHAR(255),           -- document-store ref (Daidzai never stores blobs)
    channel                 VARCHAR(32) NOT NULL DEFAULT 'WEB', -- WEB/MOBILE/USSD/IVR/PROVIDER/FACILITY
    status                  VARCHAR(24) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED/TRIAGED/LINKED/CANCELLED
    incident_id             UUID,                   -- set once an incident is created/linked
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dai_request_reference UNIQUE (tenant_id, request_reference)
);
CREATE INDEX idx_dai_request_tenant_status ON daidzai.dai_emergency_request (tenant_id, status);
CREATE INDEX idx_dai_request_incident ON daidzai.dai_emergency_request (incident_id);

-- -------------------------------------------------------------------------------------
-- Emergency incident — THE orchestration aggregate Daidzai owns. One incident can gather
-- many requests (e.g. a mass-casualty event). Carries triage classification, the dispatch
-- need, the clinical-handoff link, and (for DISASTER/MCI) the command-coordination state.
-- Lifecycle: NEW -> TRIAGED -> DISPATCH_REQUESTED -> RESPONDING -> ON_SCENE -> TRANSPORTING
--            -> HANDOVER -> RESOLVED -> CLOSED  (+ CANCELLED).
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_emergency_incident (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    incident_reference      VARCHAR(48) NOT NULL,
    incident_type           VARCHAR(24) NOT NULL DEFAULT 'INDIVIDUAL', -- INDIVIDUAL/DISASTER/MCI/OUTBREAK
    emergency_category      VARCHAR(48) NOT NULL,
    severity                VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN',
    triage_category         VARCHAR(16),            -- RED/ORANGE/YELLOW/GREEN/BLACK (rules-derived)
    sensitive               BOOLEAN NOT NULL DEFAULT FALSE,
    status                  VARCHAR(24) NOT NULL DEFAULT 'NEW',
    title                   VARCHAR(512),
    description             TEXT,
    -- Subject (for INDIVIDUAL incidents). Disaster/MCI incidents track affected area instead.
    subject_identity_mode   VARCHAR(24),
    subject_health_id       VARCHAR(128),
    subject_temp_ref        VARCHAR(128),
    -- Location / affected area
    location_lat            DOUBLE PRECISION,
    location_lng            DOUBLE PRECISION,
    location_description    VARCHAR(512),
    affected_area_ref       VARCHAR(255),           -- Indawo/Ndila area ref (DISASTER/MCI)
    facility_id             UUID,                   -- receiving/coordinating facility (Tuso)
    -- Owner-routed seam references (Daidzai holds the link, the owner holds the record)
    ndila_route_ref         VARCHAR(128),           -- Ndila routing/nearest-service result ref
    nhume_mission_ref       VARCHAR(128),           -- Nhume dispatch mission ref (execution)
    pct_encounter_ref       VARCHAR(128),           -- PCT emergency encounter ref (clinical handoff)
    rito_case_ref           VARCHAR(128),           -- Rito after-action case ref (closure)
    command_post            VARCHAR(255),           -- DISASTER/MCI incident-command post label
    incident_commander      VARCHAR(128),           -- actor id of declared commander
    opened_at               TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    closed_at               TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_dai_incident_reference UNIQUE (tenant_id, incident_reference)
);
CREATE INDEX idx_dai_incident_tenant_status ON daidzai.dai_emergency_incident (tenant_id, status);
CREATE INDEX idx_dai_incident_type ON daidzai.dai_emergency_incident (tenant_id, incident_type);

-- FK from request -> incident (added after both tables exist)
ALTER TABLE daidzai.dai_emergency_request
    ADD CONSTRAINT fk_dai_request_incident FOREIGN KEY (incident_id)
    REFERENCES daidzai.dai_emergency_incident (id) ON DELETE SET NULL;

-- -------------------------------------------------------------------------------------
-- Mission status timeline — append-only mission/dispatch status events on an incident.
-- This is Daidzai's *tracking* of the Nhume-executed mission; it is NOT the dispatch
-- record itself. Each row is an honest, audited state observation.
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_mission_event (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    incident_id         UUID NOT NULL REFERENCES daidzai.dai_emergency_incident (id) ON DELETE CASCADE,
    status              VARCHAR(24) NOT NULL,   -- DISPATCH_REQUESTED/ASSIGNED/RESPONDING/ON_SCENE/TRANSPORTING/HANDOVER/RESOLVED
    nhume_mission_ref   VARCHAR(128),
    note                VARCHAR(1024),
    actor_id            VARCHAR(128),
    actor_type          VARCHAR(48),
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dai_mission_event_incident ON daidzai.dai_mission_event (incident_id, occurred_at);

-- -------------------------------------------------------------------------------------
-- Resource request — the *need* Daidzai raises against an owner (ambulance/blood/kit/staff/
-- bed). Daidzai records the need + tracks fulfilment status; the OWNER executes (Nhume fleet,
-- Madi blood, Dura stock, Vashandi roster, Tuso bed). resource_owner names the system-of-record.
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_resource_request (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    incident_id         UUID NOT NULL REFERENCES daidzai.dai_emergency_incident (id) ON DELETE CASCADE,
    resource_type       VARCHAR(32) NOT NULL,   -- AMBULANCE/BLOOD/KIT_PPE/STAFF/BED/EQUIPMENT/OTHER
    resource_owner      VARCHAR(32) NOT NULL,   -- NHUME/MADI/DURA/VASHANDI/TUSO/ASSET_REGISTRY
    quantity            INT NOT NULL DEFAULT 1,
    detail              VARCHAR(512),
    owner_request_ref   VARCHAR(128),           -- ref returned by the owner system (when wired)
    status              VARCHAR(24) NOT NULL DEFAULT 'REQUESTED', -- REQUESTED/ACKNOWLEDGED/FULFILLED/UNAVAILABLE/CANCELLED
    requested_by        VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dai_resource_incident ON daidzai.dai_resource_request (incident_id);

-- -------------------------------------------------------------------------------------
-- Affected site — for DISASTER/MCI incidents: the sites/areas in scope (Indawo/Ndila refs).
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_affected_site (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    incident_id         UUID NOT NULL REFERENCES daidzai.dai_emergency_incident (id) ON DELETE CASCADE,
    site_label          VARCHAR(255) NOT NULL,
    indawo_site_ref     VARCHAR(128),
    ndila_area_ref      VARCHAR(128),
    location_lat        DOUBLE PRECISION,
    location_lng        DOUBLE PRECISION,
    estimated_affected  INT,
    status              VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',  -- ACTIVE/CONTAINED/CLEARED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_dai_affected_site_incident ON daidzai.dai_affected_site (incident_id);

-- -------------------------------------------------------------------------------------
-- Transactional outbox (canonical Impilo contract). Relayed to Kafka by DaidzaiOutboxPublisher.
-- -------------------------------------------------------------------------------------
CREATE TABLE daidzai.dai_outbox (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id            UUID NOT NULL,
    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        VARCHAR(255) NOT NULL,
    event_type          VARCHAR(128) NOT NULL,
    schema_version      INT NOT NULL DEFAULT 1,
    correlation_id      UUID,
    causation_id        UUID,
    idempotency_key     VARCHAR(255) NOT NULL,
    producer            VARCHAR(64) NOT NULL DEFAULT 'daidzai-service',
    tenant_id           UUID NOT NULL,
    pod_id              VARCHAR(64) NOT NULL DEFAULT 'national-spine',
    subject_id          VARCHAR(255) NOT NULL,
    subject_type        VARCHAR(64) NOT NULL,
    partition_key       VARCHAR(255),
    occurred_at         TIMESTAMPTZ NOT NULL,
    payload_json        JSONB NOT NULL,
    publish_error       TEXT,
    retry_count         INT NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at        TIMESTAMPTZ
);
CREATE INDEX idx_dai_outbox_unpublished ON daidzai.dai_outbox (created_at) WHERE published_at IS NULL;
