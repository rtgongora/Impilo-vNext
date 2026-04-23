-- Workflow-gated visibility escalation (distinct from break-glass emergency access).

CREATE TABLE tshepo_authz.visibility_escalation_request (
    id                           BIGSERIAL PRIMARY KEY,
    tenant_id                    UUID            NOT NULL,
    actor_id                     VARCHAR(255)    NOT NULL,
    workflow_type                VARCHAR(64)     NOT NULL,
    justification                TEXT            NOT NULL,
    requested_visibility_ceiling VARCHAR(64)     NOT NULL,
    status                       VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    reviewed_by                  VARCHAR(255),
    review_notes                 TEXT,
    created_at                   TIMESTAMPTZ     NOT NULL DEFAULT now(),
    updated_at                   TIMESTAMPTZ
);

CREATE INDEX idx_vis_esc_req_tenant_actor
    ON tshepo_authz.visibility_escalation_request (tenant_id, actor_id, status);

CREATE TABLE tshepo_authz.visibility_escalation_grant (
    id                BIGSERIAL PRIMARY KEY,
    request_id        BIGINT          NOT NULL REFERENCES tshepo_authz.visibility_escalation_request (id),
    tenant_id         UUID            NOT NULL,
    actor_id          VARCHAR(255)    NOT NULL,
    grant_token       UUID            NOT NULL UNIQUE,
    workflow_type     VARCHAR(64)     NOT NULL,
    visibility_ceiling VARCHAR(64)  NOT NULL,
    status            VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    expires_at        TIMESTAMPTZ     NOT NULL,
    created_at        TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_vis_esc_grant_token
    ON tshepo_authz.visibility_escalation_grant (grant_token)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_vis_esc_grant_actor
    ON tshepo_authz.visibility_escalation_grant (tenant_id, actor_id, status);
