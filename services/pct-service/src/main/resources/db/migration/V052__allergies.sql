-- OF-B3: structured, coded allergy SoR (Vol II §8.1 layered safety validation).
-- Fills the estate gap where allergy checks degraded to WARNING ALLERGIES_UNVERIFIED
-- (no SoR existed). Coded (allergen_code/code_system) so safety matching is
-- code-aware, not substring-only. BFF strangler contract: GET/POST /v1/allergies,
-- POST /v1/allergies/{id}/deactivate.

CREATE TABLE pct.pct_allergies (
    allergy_id       UUID PRIMARY KEY,
    tenant_id        UUID NOT NULL,
    subject_cpid     VARCHAR(64) NOT NULL,
    allergen         VARCHAR(255) NOT NULL,          -- display name
    allergen_code    VARCHAR(64),                    -- coded reference (ATC/SNOMED) when known
    code_system      VARCHAR(128),
    allergen_type    VARCHAR(32) NOT NULL DEFAULT 'DRUG',   -- DRUG | FOOD | ENVIRONMENT | OTHER
    reaction         VARCHAR(255),
    severity         VARCHAR(32),                    -- MILD | MODERATE | SEVERE | LIFE_THREATENING
    clinical_status  VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE | INACTIVE | ENTERED_IN_ERROR
    onset_date       DATE,
    notes            TEXT,
    recorded_by      VARCHAR(128) NOT NULL,
    deactivated_at   TIMESTAMPTZ,
    deactivated_by   VARCHAR(128),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_allergies_subject ON pct.pct_allergies(tenant_id, subject_cpid);
CREATE INDEX idx_pct_allergies_subject_active ON pct.pct_allergies(tenant_id, subject_cpid)
    WHERE clinical_status = 'ACTIVE';
