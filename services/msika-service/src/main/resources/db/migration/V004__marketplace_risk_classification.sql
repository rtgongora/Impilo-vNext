-- =============================================================================
-- MSIKA V004 — Marketplace Risk Classification (Health OS §7, §8)
--
-- "Marketplace participation shall be governed according to the risk,
--  regulatory status, and sensitivity of the product or service involved."
--
-- "Friction must be proportionate to risk."
--
-- Risk levels:
--   UNRESTRICTED  — wellness goods, fitness accessories, dietary supplements
--   LOW_RISK      — basic medical supplies (bandages, thermometers)
--   MODERATE_RISK — medical devices, diagnostic kits
--   HIGH_RISK     — prescription medicines, controlled substances
--   REGULATED     — schedule drugs, blood products, implants
-- =============================================================================

ALTER TABLE msika.items
    ADD COLUMN IF NOT EXISTS risk_classification VARCHAR(32) NOT NULL DEFAULT 'LOW_RISK';

-- Derive initial risk from existing restrictions/controlled flags
UPDATE msika.items
SET risk_classification = CASE
    WHEN controlled = true THEN 'REGULATED'
    WHEN restrictions::text LIKE '%prescription_required%' THEN 'HIGH_RISK'
    WHEN restrictions::text LIKE '%provider_only_order%' THEN 'MODERATE_RISK'
    WHEN restrictions::text LIKE '%age_restricted%' THEN 'MODERATE_RISK'
    WHEN kind IN ('CHARGEABLE', 'SERVICE') THEN 'LOW_RISK'
    ELSE 'UNRESTRICTED'
END
WHERE risk_classification = 'LOW_RISK';

CREATE INDEX idx_items_risk ON msika.items (risk_classification);

-- Mapping table: risk → friction level for the PolicyEngine
CREATE TABLE IF NOT EXISTS msika.risk_friction_mapping (
    risk_classification VARCHAR(32) PRIMARY KEY,
    friction_level      VARCHAR(32) NOT NULL,
    requires_provider   BOOLEAN     NOT NULL DEFAULT FALSE,
    requires_facility   BOOLEAN     NOT NULL DEFAULT FALSE,
    max_qty_per_order   INTEGER,
    notes               TEXT
);

INSERT INTO msika.risk_friction_mapping (risk_classification, friction_level, requires_provider, requires_facility, max_qty_per_order, notes) VALUES
('UNRESTRICTED',  'MINIMAL',  false, false, NULL,  'Wellness goods, fitness accessories — low-friction checkout'),
('LOW_RISK',      'LOW',      false, false, NULL,  'Basic medical supplies — standard checkout with auth'),
('MODERATE_RISK', 'STANDARD', false, true,  100,   'Medical devices — facility context required'),
('HIGH_RISK',     'ELEVATED', true,  true,  30,    'Prescription medicines — Provider ID + facility required'),
('REGULATED',     'MAXIMUM',  true,  true,  10,    'Controlled substances — full assurance, step-up, audit')
ON CONFLICT (risk_classification) DO NOTHING;
