-- A5: reference validation status + quarantine for the free-text
-- department_id/programme_id fields on vsh_workforce_assignment now that
-- canonical registries exist (tuso.facility_department V051,
-- wgv_programme V014).
--
-- This does NOT retroactively hard-validate the historical column (a full
-- inventory-and-migrate sweep across all 11 programme-referencing and 12
-- department-referencing services is tracked as separate follow-on work —
-- see the Work Context Resolution programme plan, Phase A5). What lands
-- here is the real, bounded, high-value piece: Vashandi is the system of
-- record for work-context-issuing assignments, so every NEW assignment is
-- soft-validated against the canonical registries at precheck time
-- (ReferenceValidationService), and the outcome is recorded — never
-- silently guessed, never blocking (an assignment with an unvalidated
-- department still works; it just doesn't unlock DEPARTMENT_MANAGEMENT
-- mode for that department — see WorkMode design, Phase B).

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD COLUMN IF NOT EXISTS department_ref_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE',
    ADD COLUMN IF NOT EXISTS programme_ref_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE';
    -- NOT_APPLICABLE (no department_id/programme_id set) | UNVERIFIED (set,
    -- not yet checked) | VALIDATED (confirmed against the canonical
    -- registry) | UNVALIDATED (checked, not found — quarantined) |
    -- UNVERIFIABLE (the registry service was unreachable at check time)

ALTER TABLE vashandi.vsh_workforce_assignment
    ADD CONSTRAINT chk_vsh_assignment_department_ref_status
        CHECK (department_ref_status IN ('NOT_APPLICABLE','UNVERIFIED','VALIDATED','UNVALIDATED','UNVERIFIABLE')),
    ADD CONSTRAINT chk_vsh_assignment_programme_ref_status
        CHECK (programme_ref_status IN ('NOT_APPLICABLE','UNVERIFIED','VALIDATED','UNVALIDATED','UNVERIFIABLE'));

CREATE INDEX IF NOT EXISTS idx_vsh_assignment_dept_ref_status
    ON vashandi.vsh_workforce_assignment (department_ref_status) WHERE department_ref_status = 'UNVALIDATED';
CREATE INDEX IF NOT EXISTS idx_vsh_assignment_prog_ref_status
    ON vashandi.vsh_workforce_assignment (programme_ref_status) WHERE programme_ref_status = 'UNVALIDATED';

-- Quarantine: a free-text department_id/programme_id value that was
-- checked against the canonical registry and NOT found. Exposed as
-- remediation work (an admin surface lists these), never silently
-- discarded or guessed into a canonical row.
CREATE TABLE vashandi.vsh_reference_quarantine (
    id                      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id               UUID NOT NULL,
    workforce_assignment_id UUID NOT NULL REFERENCES vashandi.vsh_workforce_assignment(id),
    reference_field         VARCHAR(32) NOT NULL,
        -- DEPARTMENT_ID | PROGRAMME_ID
    source_value            VARCHAR(255) NOT NULL,
    reason                  VARCHAR(255) NOT NULL DEFAULT 'NOT_FOUND_IN_CANONICAL_REGISTRY',
    resolved                BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at             TIMESTAMPTZ,
    resolved_by             VARCHAR(255),
    resolution_note         TEXT,
    quarantined_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_vsh_ref_quarantine_assignment ON vashandi.vsh_reference_quarantine (workforce_assignment_id);
CREATE INDEX IF NOT EXISTS idx_vsh_ref_quarantine_unresolved
    ON vashandi.vsh_reference_quarantine (tenant_id) WHERE NOT resolved;

COMMENT ON TABLE vashandi.vsh_reference_quarantine IS
    'Free-text department_id/programme_id values that did not resolve against '
    'their canonical registry (A5). Remediation surface, not a silent-discard bin.';
