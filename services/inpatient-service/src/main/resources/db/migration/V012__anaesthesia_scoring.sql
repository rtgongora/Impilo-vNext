-- Structured anaesthetic scoring: Mallampati/Cormack on preop assessment + flexible score registry.

ALTER TABLE inpatient.procedure_preop_assessment
    ADD COLUMN IF NOT EXISTS mallampati_class VARCHAR(8),
    ADD COLUMN IF NOT EXISTS cormack_lehane_grade VARCHAR(8);

CREATE TABLE IF NOT EXISTS inpatient.procedure_anaesthesia_score (
    score_id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    episode_id          UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    score_type          VARCHAR(32) NOT NULL,
    phase               VARCHAR(16) NOT NULL DEFAULT 'PREOP',
    components_json     TEXT,
    total_score         INTEGER,
    interpretation      VARCHAR(512),
    risk_band           VARCHAR(32),
    recorded_by         VARCHAR(128),
    recorded_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_procedure_anaesthesia_score_episode
    ON inpatient.procedure_anaesthesia_score (episode_id, phase, score_type);
