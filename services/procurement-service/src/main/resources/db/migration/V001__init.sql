CREATE SCHEMA IF NOT EXISTS proc;

CREATE TABLE proc.suppliers (
    supplier_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    name                VARCHAR(255) NOT NULL,
    registration_number VARCHAR(64),
    tax_number          VARCHAR(64),
    contact_person      VARCHAR(128),
    phone               VARCHAR(32),
    email               VARCHAR(128),
    address             TEXT,
    category            VARCHAR(32) NOT NULL,
    payment_terms       VARCHAR(32),
    bank_account        VARCHAR(128),
    bank_name           VARCHAR(64),
    status              VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    rating              DECIMAL(3,2),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_proc_sup_cat CHECK (category IN (
        'PHARMACEUTICAL', 'MEDICAL_EQUIPMENT', 'CONSUMABLES', 'SERVICES', 'IT', 'MAINTENANCE')),
    CONSTRAINT chk_proc_sup_status CHECK (status IN ('ACTIVE', 'BLACKLISTED', 'SUSPENDED'))
);

CREATE INDEX idx_proc_suppliers_tenant ON proc.suppliers (tenant_id);

CREATE TABLE proc.requisitions (
    requisition_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    facility_id         UUID,
    department_id       VARCHAR(128),
    requested_by        VARCHAR(128),
    urgency             VARCHAR(16),
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    approved_by         VARCHAR(128),
    approved_at         TIMESTAMPTZ,
    total_estimated     DECIMAL(14,2) DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_proc_req_status CHECK (status IN (
        'DRAFT', 'SUBMITTED', 'APPROVED', 'ORDERED', 'RECEIVED', 'CANCELLED'))
);

CREATE TABLE proc.requisition_lines (
    line_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    requisition_id      UUID NOT NULL REFERENCES proc.requisitions(requisition_id) ON DELETE CASCADE,
    item_code           VARCHAR(64),
    description         VARCHAR(512),
    quantity            DECIMAL(10,2),
    unit                VARCHAR(16),
    estimated_unit_price DECIMAL(14,2)
);

CREATE TABLE proc.purchase_orders (
    po_id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    po_number           VARCHAR(32) NOT NULL,
    supplier_id         UUID NOT NULL REFERENCES proc.suppliers(supplier_id),
    requisition_id      UUID REFERENCES proc.requisitions(requisition_id),
    facility_id         UUID,
    delivery_address    TEXT,
    payment_terms       VARCHAR(32),
    total_amount        DECIMAL(14,2),
    currency            VARCHAR(3) DEFAULT 'ZWL',
    status              VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by          VARCHAR(128),
    approved_by         VARCHAR(128),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_proc_po_number UNIQUE (tenant_id, po_number),
    CONSTRAINT chk_proc_po_status CHECK (status IN (
        'DRAFT', 'SENT', 'ACKNOWLEDGED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED'))
);

CREATE TABLE proc.po_lines (
    line_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    po_id               UUID NOT NULL REFERENCES proc.purchase_orders(po_id) ON DELETE CASCADE,
    item_code           VARCHAR(64),
    description         VARCHAR(512),
    quantity            DECIMAL(10,2),
    unit                VARCHAR(16),
    unit_price          DECIMAL(14,2),
    total_price         DECIMAL(14,2),
    received_quantity   DECIMAL(10,2) DEFAULT 0
);

CREATE TABLE proc.goods_received (
    grn_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    grn_number          VARCHAR(32) NOT NULL,
    po_id               UUID NOT NULL REFERENCES proc.purchase_orders(po_id),
    supplier_id         UUID NOT NULL REFERENCES proc.suppliers(supplier_id),
    received_by         VARCHAR(128),
    received_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    inspection_status   VARCHAR(16) DEFAULT 'PENDING',
    notes               TEXT,
    CONSTRAINT uq_proc_grn_number UNIQUE (tenant_id, grn_number),
    CONSTRAINT chk_proc_grn_insp CHECK (inspection_status IN ('PENDING', 'PASSED', 'FAILED', 'PARTIAL'))
);

CREATE TABLE proc.grn_lines (
    line_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grn_id              UUID NOT NULL REFERENCES proc.goods_received(grn_id) ON DELETE CASCADE,
    po_line_id          UUID NOT NULL REFERENCES proc.po_lines(line_id),
    quantity_received   DECIMAL(10,2),
    quantity_rejected   DECIMAL(10,2),
    rejection_reason    TEXT,
    batch_number        VARCHAR(64),
    expiry_date         DATE
);

CREATE TABLE proc.supplier_invoices (
    invoice_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    invoice_number      VARCHAR(64) NOT NULL,
    supplier_id         UUID NOT NULL REFERENCES proc.suppliers(supplier_id),
    po_id               UUID REFERENCES proc.purchase_orders(po_id),
    grn_id              UUID REFERENCES proc.goods_received(grn_id),
    amount              DECIMAL(14,2),
    tax_amount          DECIMAL(14,2),
    total_amount        DECIMAL(14,2),
    due_date            DATE,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    three_way_match     BOOLEAN NOT NULL DEFAULT FALSE,
    matched_at          TIMESTAMPTZ,
    paid_at             TIMESTAMPTZ,
    CONSTRAINT chk_proc_inv_status CHECK (status IN (
        'PENDING', 'MATCHED', 'APPROVED', 'PAID', 'DISPUTED'))
);

CREATE TABLE proc.rfqs (
    rfq_id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    deadline            DATE,
    status              VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_proc_rfq_status CHECK (status IN ('OPEN', 'CLOSED', 'AWARDED', 'CANCELLED'))
);

CREATE TABLE proc.rfq_responses (
    response_id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rfq_id              UUID NOT NULL REFERENCES proc.rfqs(rfq_id) ON DELETE CASCADE,
    supplier_id         UUID NOT NULL REFERENCES proc.suppliers(supplier_id),
    total_price         DECIMAL(14,2),
    delivery_days       INT,
    terms               TEXT,
    submitted_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    awarded             BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE proc.event_outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID NOT NULL DEFAULT gen_random_uuid(),
    aggregate_type  VARCHAR(64) NOT NULL,
    aggregate_id    VARCHAR(255) NOT NULL,
    event_type      VARCHAR(128) NOT NULL,
    schema_version  INT NOT NULL DEFAULT 1,
    correlation_id  UUID,
    causation_id    UUID,
    idempotency_key VARCHAR(255) NOT NULL,
    producer        VARCHAR(64) NOT NULL DEFAULT 'procurement-service',
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
    CONSTRAINT uq_proc_outbox_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX idx_proc_outbox_unpublished ON proc.event_outbox (created_at) WHERE published_at IS NULL;
