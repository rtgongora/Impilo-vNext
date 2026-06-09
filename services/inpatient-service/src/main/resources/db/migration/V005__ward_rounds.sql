-- Ward rounds — sovereign inpatient nursing / medical rounds documentation.

CREATE TABLE inpatient.ward_round (
    round_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID            NOT NULL,
    admission_ref   UUID            NOT NULL,
    ward_id         UUID,
    round_type      VARCHAR(50)     NOT NULL DEFAULT 'MORNING',
    led_by          VARCHAR(128),
    status          VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS',
    started_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    notes           TEXT,
    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ward_round_admission ON inpatient.ward_round (tenant_id, admission_ref);
CREATE INDEX idx_ward_round_ward ON inpatient.ward_round (tenant_id, ward_id);

CREATE TABLE inpatient.ward_round_entry (
    entry_id        UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    round_id        UUID            NOT NULL REFERENCES inpatient.ward_round(round_id) ON DELETE CASCADE,
    assessment      TEXT,
    plan            TEXT,
    new_orders      TEXT,
    escalation      VARCHAR(50),
    reviewed_by     VARCHAR(128),
    reviewed_at     TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_ward_round_entry_round ON inpatient.ward_round_entry (round_id);
