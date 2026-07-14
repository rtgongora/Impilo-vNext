-- inpatient-service V025 — procedure consent bundle (Wave 2 elective theatre)
-- REFERENCE / index table only: one row per required consent type per episode,
-- pointing at the MVUMO consent request. MVUMO stays the consent SoR — this
-- table just lets startProcedure gate on "all required consents GRANTED".

CREATE TABLE inpatient.procedure_consent (
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id                  UUID NOT NULL
        REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    consent_type                VARCHAR(48) NOT NULL,   -- PROCEDURE | ANAESTHESIA | TRANSFUSION
    mvumo_consent_request_id    UUID,
    status                      VARCHAR(24) NOT NULL DEFAULT 'PENDING',
        -- PENDING | GRANTED | WITHDRAWN | REFUSED
    version                     INTEGER NOT NULL DEFAULT 1,
    required                    BOOLEAN NOT NULL DEFAULT TRUE,
    withdrawn                   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_procedure_consent_type CHECK (consent_type IN ('PROCEDURE','ANAESTHESIA','TRANSFUSION')),
    CONSTRAINT uq_procedure_consent UNIQUE (episode_id, consent_type)
);

CREATE INDEX idx_procedure_consent_episode
    ON inpatient.procedure_consent (episode_id, status);
