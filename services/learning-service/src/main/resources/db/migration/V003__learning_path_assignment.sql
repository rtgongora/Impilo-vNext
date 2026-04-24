-- Subject ↔ learning path assignments (orchestration / council workflows)

CREATE TABLE lrn_subject_path_assignment (
    id                      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID            NOT NULL,
    subject_type            VARCHAR(32)     NOT NULL,
    subject_id              VARCHAR(255)    NOT NULL,
    path_id                 UUID            NOT NULL REFERENCES lrn_learning_path(id) ON DELETE CASCADE,
    assigned_by_actor_id    VARCHAR(255),
    correlation_id          VARCHAR(128),
    metadata_json           TEXT,
    created_at              TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CONSTRAINT uq_lrn_spa UNIQUE (tenant_id, subject_type, subject_id, path_id)
);

CREATE INDEX idx_lrn_spa_subject ON lrn_subject_path_assignment (tenant_id, subject_type, subject_id);
