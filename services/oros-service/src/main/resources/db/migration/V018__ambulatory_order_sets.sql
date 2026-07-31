-- Wave 3 Phase F — ambulatory order-set SoR (OROS-owned).
-- Definitions + items + optional instance. Batch place loops placeOrder; each item is a real order.
-- Distinct from PCT ED emergency_order_set (V206).

CREATE TABLE oros_order_set_definitions (
    set_id           UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    set_code         VARCHAR(64)   NOT NULL,
    set_name         VARCHAR(255)  NOT NULL,
    indication       VARCHAR(255),
    description      TEXT,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_oros_order_set_code UNIQUE (tenant_id, set_code)
);

CREATE TABLE oros_order_set_items (
    item_id          UUID          PRIMARY KEY,
    set_id           UUID          NOT NULL REFERENCES oros_order_set_definitions(set_id),
    tenant_id        UUID          NOT NULL,
    sort_order       INTEGER       NOT NULL DEFAULT 0,
    order_type       VARCHAR(32)   NOT NULL,
    zibo_code        VARCHAR(64)   NOT NULL,
    display_name     VARCHAR(255)  NOT NULL,
    priority         VARCHAR(16)   NOT NULL DEFAULT 'ROUTINE',
    quantity         INTEGER       NOT NULL DEFAULT 1,
    instructions     TEXT,
    removable        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_oros_order_set_items_set ON oros_order_set_items (set_id, sort_order);

CREATE TABLE oros_order_set_instances (
    instance_id      UUID          PRIMARY KEY,
    tenant_id        UUID          NOT NULL,
    set_id           UUID          NOT NULL REFERENCES oros_order_set_definitions(set_id),
    patient_cpid     VARCHAR(128)  NOT NULL,
    encounter_ref    VARCHAR(128),
    facility_id      UUID          NOT NULL,
    placed_by        VARCHAR(255)  NOT NULL,
    placed_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    order_ids_json   TEXT          NOT NULL,
    notes            TEXT
);
CREATE INDEX idx_oros_order_set_instances_patient
    ON oros_order_set_instances (tenant_id, patient_cpid, placed_at DESC);

-- Seed a small indication-based catalogue (fulfilimorbidity / HTN clinic style).
INSERT INTO oros_order_set_definitions (set_id, tenant_id, set_code, set_name, indication, description)
VALUES
  ('a1000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
   'HTN_CLINIC_BASELINE', 'Hypertension clinic baseline', 'HYPERTENSION',
   'Indication-based ambulatory labs for HTN review'),
  ('a1000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001',
   'DM_CLINIC_BASELINE', 'Diabetes clinic baseline', 'DIABETES',
   'Indication-based ambulatory labs for diabetes review');

INSERT INTO oros_order_set_items (item_id, set_id, tenant_id, sort_order, order_type, zibo_code, display_name, priority, quantity)
VALUES
  ('b1000000-0000-4000-8000-000000000001', 'a1000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
   1, 'LAB', 'UE', 'Urea & Electrolytes', 'ROUTINE', 1),
  ('b1000000-0000-4000-8000-000000000002', 'a1000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
   2, 'LAB', 'CREAT', 'Creatinine', 'ROUTINE', 1),
  ('b1000000-0000-4000-8000-000000000003', 'a1000000-0000-4000-8000-000000000001', '00000000-0000-4000-8000-000000000001',
   3, 'LAB', 'LIPID', 'Lipid profile', 'ROUTINE', 1),
  ('b1000000-0000-4000-8000-000000000011', 'a1000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001',
   1, 'LAB', 'HBA1C', 'HbA1c', 'ROUTINE', 1),
  ('b1000000-0000-4000-8000-000000000012', 'a1000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001',
   2, 'LAB', 'FBC', 'Full Blood Count', 'ROUTINE', 1),
  ('b1000000-0000-4000-8000-000000000013', 'a1000000-0000-4000-8000-000000000002', '00000000-0000-4000-8000-000000000001',
   3, 'LAB', 'UE', 'Urea & Electrolytes', 'ROUTINE', 1);
