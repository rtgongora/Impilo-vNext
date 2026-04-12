-- Add 10-dimension access control fields to policy_decision_log
-- Aligns with Health OS doctrine: provider context, organisational sub-unit,
-- ward, programme, subject, and identity assurance level.

ALTER TABLE tshepo_authz.policy_decision_log
    ADD COLUMN provider_id      VARCHAR(255),
    ADD COLUMN department_id    VARCHAR(255),
    ADD COLUMN ward_id          VARCHAR(255),
    ADD COLUMN programme_id     VARCHAR(255),
    ADD COLUMN subject_id       VARCHAR(255),
    ADD COLUMN assurance_level  VARCHAR(16);

CREATE INDEX idx_decision_log_provider
    ON tshepo_authz.policy_decision_log (tenant_id, provider_id, evaluated_at DESC)
    WHERE provider_id IS NOT NULL;

CREATE INDEX idx_decision_log_subject
    ON tshepo_authz.policy_decision_log (tenant_id, subject_id, evaluated_at DESC)
    WHERE subject_id IS NOT NULL;
