-- Governed role-template → required-Fundo-course mapping (PO-20260629-01 "plain build").
-- Vashandi owns which roles require which training; learning-service owns whether the
-- training is satisfied (FundoTrainingGateService); Tshepo/OPA owns the policy decision.
CREATE TABLE vashandi.vsh_training_requirement (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    role_template_id        VARCHAR(128) NOT NULL,
    course_code             VARCHAR(128) NOT NULL,
    enforcement_level       VARCHAR(16)  NOT NULL DEFAULT 'ADVISORY',
    active                  BOOLEAN      NOT NULL DEFAULT TRUE,
    note                    VARCHAR(512),
    created_by              VARCHAR(128),
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_vsh_training_req_level CHECK (enforcement_level IN ('ADVISORY', 'SOFT', 'HARD')),
    CONSTRAINT uq_vsh_training_req UNIQUE (tenant_id, role_template_id, course_code)
);

CREATE INDEX idx_vsh_training_req_role ON vashandi.vsh_training_requirement (tenant_id, role_template_id, active);
