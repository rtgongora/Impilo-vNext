-- Wave 1 clinical-safety — Multi-channel anaesthesia chart (the intra-op anaesthetic record).
-- A time-series of chart entries across channels (AGENT | FLUID | BLOOD | MEDICATION | MONITORING |
-- VITAL | EVENT). Intra-op vitals already recorded through recordIntraopVitals ALSO append a VITAL
-- entry here (no re-entry). The BLOOD channel is PROJECTED from procedure_blood_link
-- (madi_transfusion_episode_id) so administered blood shows on the chart without duplicate entry.
-- getAnaesthesiaChart returns the ordered multi-channel series (by recorded_at).

CREATE TABLE IF NOT EXISTS inpatient.procedure_anaesthesia_chart_entry (
    entry_id                     UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id                    UUID NOT NULL,
    episode_id                   UUID NOT NULL REFERENCES inpatient.procedure_episode(episode_id) ON DELETE CASCADE,
    channel                      VARCHAR(16) NOT NULL,   -- AGENT|FLUID|BLOOD|MEDICATION|MONITORING|VITAL|EVENT
    seq                          INT NOT NULL DEFAULT 1,
    technique                    VARCHAR(24),            -- GA|REGIONAL_SPINAL|LOCAL_SEDATION|CONVERSION|COMPLICATION
    label                        VARCHAR(256),
    value_numeric                NUMERIC(12,3),
    value_text                   VARCHAR(512),
    unit                         VARCHAR(32),
    detail_json                  TEXT,
    madi_transfusion_episode_id  VARCHAR(128),           -- set for BLOOD-channel entries projected from V020
    recorded_at                  TIMESTAMPTZ NOT NULL DEFAULT now(),
    recorded_by                  VARCHAR(128),
    idempotency_key              VARCHAR(128),
    created_at                   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_procedure_anaesthesia_chart_entry UNIQUE (tenant_id, episode_id, channel, seq)
);

-- Ordered multi-channel time series read path.
CREATE INDEX IF NOT EXISTS idx_procedure_anaesthesia_chart_time
    ON inpatient.procedure_anaesthesia_chart_entry (episode_id, recorded_at, channel);
