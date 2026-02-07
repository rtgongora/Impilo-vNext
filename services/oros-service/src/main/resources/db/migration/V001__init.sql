-- =============================================
-- Service : oros-service (Orders & Results Orchestration)
-- Schema  : public (oros_ prefix convention)
-- Flyway  : V001__init.sql
-- Tables  : 12
-- =============================================

-- ─────────────────────────────────────────────
-- 1. oros_orders: canonical order with ULID PK
-- ─────────────────────────────────────────────
CREATE TABLE oros_orders (
    order_id        VARCHAR(26)     PRIMARY KEY,  -- ULID
    tenant_id       UUID            NOT NULL,
    facility_id     UUID            NOT NULL,
    patient_cpid    VARCHAR(64)     NOT NULL,
    order_type      VARCHAR(20)     NOT NULL,     -- LAB, IMAGING, PHARMACY, PROCEDURE, OTHER
    priority        VARCHAR(10)     NOT NULL DEFAULT 'ROUTINE',
    status          VARCHAR(30)     NOT NULL DEFAULT 'PLACED',
    placed_by       VARCHAR(128)    NOT NULL,
    placed_at       TIMESTAMPTZ     NOT NULL DEFAULT now(),
    workspace_id    UUID,
    encounter_ref   VARCHAR(128),   -- PCT encounter reference
    zibo_order_code VARCHAR(100),   -- ZIBO validated order code
    external_refs   JSONB,          -- external system references
    butano_refs     JSONB,          -- FHIR resource references (ServiceRequest, etc.)
    clinical_notes  TEXT,
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 2. oros_order_items: individual items within an order
-- ─────────────────────────────────────────────
CREATE TABLE oros_order_items (
    item_id         UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    code            VARCHAR(100)    NOT NULL,
    display_name    VARCHAR(500),
    quantity        INTEGER         NOT NULL DEFAULT 1,
    instructions    TEXT,
    specimen_type   VARCHAR(100),
    body_site       VARCHAR(100),
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 3. oros_worksteps: sub-state machine steps per order
-- ─────────────────────────────────────────────
CREATE TABLE oros_worksteps (
    workstep_id     UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    step_type       VARCHAR(30)     NOT NULL,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    assigned_to     VARCHAR(128),
    started_at      TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 4. oros_acknowledgements: dept/clinician/critical acks
-- ─────────────────────────────────────────────
CREATE TABLE oros_acknowledgements (
    ack_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    ack_type        VARCHAR(20)     NOT NULL,  -- DEPARTMENT, CLINICIAN, CRITICAL
    actor_id        VARCHAR(128)    NOT NULL,
    notes           TEXT,
    acked_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 5. oros_results: order results (lab, imaging, pharmacy, document)
-- ─────────────────────────────────────────────
CREATE TABLE oros_results (
    result_id       UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    kind            VARCHAR(20)     NOT NULL,  -- LAB, IMAGING, PHARMACY, DOCUMENT
    summary         JSONB           NOT NULL,  -- structured result data
    zibo_result_codes VARCHAR(500), -- validated result codes
    doc_ids         JSONB,          -- document references (Landela/BUTANO)
    is_critical     BOOLEAN         NOT NULL DEFAULT false,
    reported_by     VARCHAR(128)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 6. oros_routing: order routing decisions
-- ─────────────────────────────────────────────
CREATE TABLE oros_routing (
    route_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    route_target    VARCHAR(20)     NOT NULL,  -- INTERNAL, LIMS, PACS, PHARMACY_EXT, OTHER
    adapter_mode    VARCHAR(10)     NOT NULL DEFAULT 'INTERNAL',
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    last_error      TEXT,
    retry_count     INTEGER         NOT NULL DEFAULT 0,
    external_ref    VARCHAR(255),   -- external system reference/accession number
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 7. oros_reconcile_queue: hybrid mode reconciliation
-- ─────────────────────────────────────────────
CREATE TABLE oros_reconcile_queue (
    rec_id          UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    order_id        VARCHAR(26)     REFERENCES oros_orders(order_id),
    external_key    VARCHAR(255)    NOT NULL,
    external_system VARCHAR(50)     NOT NULL,
    patient_cpid    VARCHAR(64),
    confidence      NUMERIC(3,2)    DEFAULT 0.00,
    status          VARCHAR(20)     NOT NULL DEFAULT 'PENDING',
    ops_notes       TEXT,
    matched_at      TIMESTAMPTZ,
    resolved_by     VARCHAR(128),
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 8. oros_sla_timers: SLA tracking per order stage
-- ─────────────────────────────────────────────
CREATE TABLE oros_sla_timers (
    timer_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        VARCHAR(26)     NOT NULL REFERENCES oros_orders(order_id),
    stage           VARCHAR(30)     NOT NULL,
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    target_at       TIMESTAMPTZ,
    breached        BOOLEAN         NOT NULL DEFAULT false,
    breached_at     TIMESTAMPTZ
);

-- ─────────────────────────────────────────────
-- 9. oros_capabilities: tenant/facility capability configuration
-- ─────────────────────────────────────────────
CREATE TABLE oros_capabilities (
    capability_id   UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     UUID,
    order_type      VARCHAR(20)     NOT NULL,
    adapter_mode    VARCHAR(10)     NOT NULL DEFAULT 'INTERNAL',
    external_endpoint VARCHAR(500),
    config          JSONB,
    active          BOOLEAN         NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, facility_id, order_type)
);

-- ─────────────────────────────────────────────
-- 10. oros_catalog_items: orderable catalog per tenant/facility
-- ─────────────────────────────────────────────
CREATE TABLE oros_catalog_items (
    catalog_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    facility_id     UUID,
    order_type      VARCHAR(20)     NOT NULL,
    code            VARCHAR(100)    NOT NULL,
    display_name    VARCHAR(500)    NOT NULL,
    category        VARCHAR(100),
    zibo_canonical  VARCHAR(500),
    active          BOOLEAN         NOT NULL DEFAULT true,
    metadata        JSONB,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, code)
);

-- ─────────────────────────────────────────────
-- 11. oros_idempotency: track processed adapter events
-- ─────────────────────────────────────────────
CREATE TABLE oros_idempotency (
    idempotency_key VARCHAR(255)    PRIMARY KEY,
    source_system   VARCHAR(50)     NOT NULL,
    processed_at    TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- ─────────────────────────────────────────────
-- 12. oros_event_outbox: transactional outbox for Kafka
-- ─────────────────────────────────────────────
CREATE TABLE oros_event_outbox (
    id              BIGSERIAL       PRIMARY KEY,
    aggregate_type  VARCHAR(50)     NOT NULL,
    aggregate_id    VARCHAR(128)    NOT NULL,
    event_type      VARCHAR(100)    NOT NULL,
    payload         JSONB           NOT NULL,
    tenant_id       UUID            NOT NULL,
    published_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);


-- =============================================================
-- INDEXES
-- =============================================================

-- ── oros_orders ──────────────────────────────────────────────
CREATE INDEX idx_orders_tenant_status ON oros_orders (tenant_id, status);
CREATE INDEX idx_orders_tenant_facility_status ON oros_orders (tenant_id, facility_id, status);
CREATE INDEX idx_orders_tenant_type ON oros_orders (tenant_id, order_type);
CREATE INDEX idx_orders_patient_cpid ON oros_orders (patient_cpid);
CREATE INDEX idx_orders_encounter_ref ON oros_orders (encounter_ref) WHERE encounter_ref IS NOT NULL;
CREATE INDEX idx_orders_placed_at ON oros_orders (placed_at);
CREATE INDEX idx_orders_external_refs ON oros_orders USING gin (external_refs) WHERE external_refs IS NOT NULL;
CREATE INDEX idx_orders_butano_refs ON oros_orders USING gin (butano_refs) WHERE butano_refs IS NOT NULL;

-- ── oros_order_items ────────────────────────────────────────
CREATE INDEX idx_order_items_order_id ON oros_order_items (order_id);

-- ── oros_worksteps ──────────────────────────────────────────
CREATE INDEX idx_worksteps_order_id ON oros_worksteps (order_id);
CREATE INDEX idx_worksteps_order_status ON oros_worksteps (order_id, status);
CREATE INDEX idx_worksteps_assigned_to ON oros_worksteps (assigned_to) WHERE assigned_to IS NOT NULL;

-- ── oros_acknowledgements ───────────────────────────────────
CREATE INDEX idx_acks_order_id ON oros_acknowledgements (order_id);
CREATE INDEX idx_acks_order_type ON oros_acknowledgements (order_id, ack_type);

-- ── oros_results ────────────────────────────────────────────
CREATE INDEX idx_results_order_id ON oros_results (order_id);
CREATE INDEX idx_results_kind ON oros_results (order_id, kind);
CREATE INDEX idx_results_critical ON oros_results (is_critical) WHERE is_critical = true;

-- ── oros_routing ────────────────────────────────────────────
CREATE INDEX idx_routing_order_id ON oros_routing (order_id);
CREATE INDEX idx_routing_status ON oros_routing (status);
CREATE INDEX idx_routing_target_status ON oros_routing (route_target, status);

-- ── oros_reconcile_queue ────────────────────────────────────
CREATE INDEX idx_reconcile_tenant_status ON oros_reconcile_queue (tenant_id, status);
CREATE INDEX idx_reconcile_external_key ON oros_reconcile_queue (external_system, external_key);
CREATE INDEX idx_reconcile_patient_cpid ON oros_reconcile_queue (patient_cpid) WHERE patient_cpid IS NOT NULL;

-- ── oros_sla_timers ─────────────────────────────────────────
CREATE INDEX idx_sla_order_id ON oros_sla_timers (order_id);
CREATE INDEX idx_sla_breached ON oros_sla_timers (breached, target_at) WHERE breached = false;

-- ── oros_capabilities ───────────────────────────────────────
CREATE INDEX idx_capabilities_tenant ON oros_capabilities (tenant_id);
CREATE INDEX idx_capabilities_tenant_facility ON oros_capabilities (tenant_id, facility_id) WHERE facility_id IS NOT NULL;

-- ── oros_catalog_items ──────────────────────────────────────
CREATE INDEX idx_catalog_tenant_type ON oros_catalog_items (tenant_id, order_type);
CREATE INDEX idx_catalog_tenant_active ON oros_catalog_items (tenant_id, active) WHERE active = true;

-- ── oros_event_outbox (partial index for unpublished events) ─
CREATE INDEX idx_outbox_unpublished ON oros_event_outbox (created_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_aggregate ON oros_event_outbox (aggregate_type, aggregate_id);
