-- Wave 5.6 — adult medicine journey position (brief.md §5).
-- Append-only position markers along the 17-step adult medicine pathway.

CREATE TABLE IF NOT EXISTS pct.adult_journey_positions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id UUID NOT NULL,
  subject_cpid VARCHAR(64) NOT NULL,
  journey_id VARCHAR(128),
  encounter_id VARCHAR(128),
  step_key VARCHAR(64) NOT NULL,
  step_index INT NOT NULL CHECK (step_index BETWEEN 1 AND 17),
  recorded_by VARCHAR(128),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  notes TEXT,

  CONSTRAINT adult_journey_pos_step_key_check CHECK (step_key IN (
    'PRESENTING', 'CLERKING', 'EXAMINATION', 'PROBLEMS', 'INVESTIGATIONS', 'DIAGNOSIS',
    'TREATMENT_PLAN', 'MEDICINES', 'PROCEDURES', 'ADMISSION', 'WARD_ROUND', 'CONSULTATION',
    'MDT', 'MONITORING', 'DISCHARGE_PLANNING', 'TRANSFER', 'FOLLOW_UP'
  )),
  CONSTRAINT adult_journey_pos_anchor_check CHECK (
    journey_id IS NOT NULL OR encounter_id IS NOT NULL
  )
);

CREATE INDEX IF NOT EXISTS idx_adult_journey_pos_subject
  ON pct.adult_journey_positions (tenant_id, subject_cpid, recorded_at DESC);

COMMENT ON TABLE pct.adult_journey_positions IS
  'Visit-scoped position along the adult medicine 17-step pathway (brief.md §5).';
