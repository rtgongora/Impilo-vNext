-- =====================================================================================
-- Daidzai V015 — EMS clinical-dispatch bounded context (daidzai.ems).
--
-- Architecture decision #2: EMS clinical dispatch (dispatching a crew to a PATIENT, capturing
-- prehospital vitals) is owned by DAIDZAI, NOT by nhume/dispatch (those are the courier/logistics
-- lane — see doctrine amendment). This context holds:
--   * dai_ems_mission        — the crew/vehicle clinical mission + its state machine
--   * dai_ems_unit           — the responding unit (ambulance asset id + crew, referenced by value)
--   * dai_ems_epcr           — the prehospital electronic patient care record (captured in W3)
--   * dai_ems_epcr_event     — the ePCR time-series (vitals / GCS / interventions) (W3)
-- The mission binds to the DAIDZAI incident and the canonical trauma_episode_id. Crew resolves from
-- VASHANDI and routing/ETA from NDILA by reference (values only — no foreign schema here); the
-- ambulance is an asset-registry Object ID carried by value.
--
-- State machine (dai_ems_mission.state):
--   CREATED -> DISPATCHED -> ACKNOWLEDGED -> ACCEPTED -> EN_ROUTE_SCENE -> ON_SCENE ->
--   PATIENT_CONTACT -> DEPARTED_SCENE -> EN_ROUTE_FACILITY -> ARRIVED_FACILITY -> HANDOVER -> CLEARED
--   (+ STANDDOWN / CANCELLED as early exits).
-- Reserved EMS sub-block: daidzai V015–V029.
-- =====================================================================================

CREATE TABLE daidzai.dai_ems_unit (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    unit_reference         VARCHAR(48) NOT NULL,
    call_sign              VARCHAR(64),
    -- Ambulance = asset-registry Object ID, carried by value (no cross-schema FK).
    ambulance_asset_id     VARCHAR(128),
    -- Crew resolved from VASHANDI, carried by value (list of {providerId,role}); no vashandi FK.
    crew_json              JSONB NOT NULL DEFAULT '[]',
    status                 VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',  -- AVAILABLE / ASSIGNED / OUT_OF_SERVICE
    home_facility_id       UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dai_ems_unit_tenant ON daidzai.dai_ems_unit (tenant_id);

CREATE TABLE daidzai.dai_ems_mission (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    mission_reference      VARCHAR(48) NOT NULL,
    incident_id            UUID NOT NULL REFERENCES daidzai.dai_emergency_incident (id),
    trauma_episode_id      UUID,
    unit_id                UUID REFERENCES daidzai.dai_ems_unit (id),
    state                  VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    dispatch_priority      VARCHAR(16),
    destination_facility_id UUID,        -- NDILA-resolved catchment facility (by reference)
    destination_eta_seconds INTEGER,
    pct_encounter_ref      VARCHAR(128), -- set at HANDOVER (PCT owns the encounter)
    standdown_reason       VARCHAR(255),
    dispatched_at          TIMESTAMPTZ,
    on_scene_at            TIMESTAMPTZ,
    departed_scene_at      TIMESTAMPTZ,
    arrived_facility_at    TIMESTAMPTZ,
    handover_at            TIMESTAMPTZ,
    cleared_at             TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Idempotent dispatch: one clinical mission per incident (mass-casualty multi-unit is W9).
    CONSTRAINT uq_dai_ems_mission_incident UNIQUE (tenant_id, incident_id)
);

CREATE INDEX idx_dai_ems_mission_tenant   ON daidzai.dai_ems_mission (tenant_id);
CREATE INDEX idx_dai_ems_mission_episode  ON daidzai.dai_ems_mission (trauma_episode_id);
CREATE INDEX idx_dai_ems_mission_state    ON daidzai.dai_ems_mission (tenant_id, state);

-- Prehospital electronic patient care record (populated in W3 from the responder mobile app).
CREATE TABLE daidzai.dai_ems_epcr (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    mission_id             UUID NOT NULL REFERENCES daidzai.dai_ems_mission (id) ON DELETE CASCADE,
    trauma_episode_id      UUID,
    patient_health_id      VARCHAR(128),   -- VITO CPID (provisional for unknown patients)
    primary_survey_json    JSONB,
    narrative              TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_dai_ems_epcr_mission UNIQUE (tenant_id, mission_id)
);

CREATE INDEX idx_dai_ems_epcr_episode ON daidzai.dai_ems_epcr (trauma_episode_id);

-- ePCR time-series: timed vitals / GCS / interventions captured en route.
CREATE TABLE daidzai.dai_ems_epcr_event (
    id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id              UUID NOT NULL,
    epcr_id                UUID NOT NULL REFERENCES daidzai.dai_ems_epcr (id) ON DELETE CASCADE,
    mission_id             UUID NOT NULL,
    event_type             VARCHAR(32) NOT NULL,   -- VITALS / GCS / INTERVENTION / MEDICATION / NOTE
    channel                VARCHAR(48),
    payload_json           JSONB,
    recorded_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_dai_ems_epcr_event_epcr ON daidzai.dai_ems_epcr_event (epcr_id, recorded_at);
