-- Environmental and nuisance complaints registry

CREATE TABLE surv.environmental_complaints (
    id              BIGSERIAL       PRIMARY KEY,
    tenant_id       UUID            NOT NULL,
    category        VARCHAR(100)    NOT NULL,
    title           VARCHAR(500)    NOT NULL,
    description     TEXT,
    status          VARCHAR(30)     NOT NULL DEFAULT 'RECEIVED',
    province        VARCHAR(100),
    district        VARCHAR(100),
    site_id         UUID,
    facility_id     UUID,
    reporter_contact VARCHAR(255),
    jurisdiction_pack VARCHAR(50),
    created_by      VARCHAR(255)    NOT NULL,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_env_complaints_tenant ON surv.environmental_complaints (tenant_id);
CREATE INDEX idx_env_complaints_status ON surv.environmental_complaints (tenant_id, status);
