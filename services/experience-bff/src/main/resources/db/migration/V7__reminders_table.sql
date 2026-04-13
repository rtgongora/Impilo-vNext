CREATE TABLE reminders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id VARCHAR(255) NOT NULL,
  patient_id UUID NOT NULL REFERENCES patients(id),
  title VARCHAR(500) NOT NULL,
  reminder_type VARCHAR(50) NOT NULL DEFAULT 'GENERAL',
  description TEXT,
  scheduled_at TIMESTAMPTZ NOT NULL,
  recurrence VARCHAR(50) NOT NULL DEFAULT 'ONCE',
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  source_type VARCHAR(100),
  source_id UUID,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_reminders_patient ON reminders (tenant_id, patient_id);
