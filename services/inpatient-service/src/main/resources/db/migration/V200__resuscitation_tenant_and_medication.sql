-- Resus hardening (emergency pack W6a) — the trauma lane's exclusive resuscitation_* class,
-- extended in place. Nothing here forks a second resuscitation model.
--
-- 1. TENANT ID ON resuscitation_record AND resuscitation_phase. Both tables have had activation_id
--    as their only anchor since V007 — resuscitation_event (V036) already carries tenant_id, so
--    these two are simply behind a property their sibling table already has, not a design choice
--    being overturned. Today the only protection is application-layer: every entry point in
--    InpatientClinicalService funnels through requireActivation(activationId), which does verify
--    tenant ownership of the activation before touching these rows. That is a real control, so this
--    is a defense-in-depth close, not an active leak — but a control that lives entirely in one
--    method's discipline is one refactor away from a cross-tenant read with no database backstop at
--    all, which is exactly the shape of hazard this table class cannot afford.
--
--    Brownfield-safe two-step: add nullable, backfill from the FK parent (which is NOT NULL on
--    emergency_activation and has been since V007 — the backfill is therefore total, verified below
--    against a pre-seeded legacy-style row), THEN set NOT NULL. NOT subject_cpid: that anchor is
--    deliberately kept on emergency_activation ONLY (see InpatientResuscitationRepointHook's own
--    doc comment) so there is exactly one place VITO's identity repoint has to touch. Duplicating
--    subject_cpid here would create a second copy the existing repoint hook does not know to update
--    — a drift bug, not a fix.

ALTER TABLE inpatient.resuscitation_record ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE inpatient.resuscitation_phase ADD COLUMN IF NOT EXISTS tenant_id UUID;

UPDATE inpatient.resuscitation_record r
   SET tenant_id = a.tenant_id
  FROM inpatient.emergency_activation a
 WHERE r.activation_id = a.activation_id
   AND r.tenant_id IS NULL;

UPDATE inpatient.resuscitation_phase p
   SET tenant_id = a.tenant_id
  FROM inpatient.emergency_activation a
 WHERE p.activation_id = a.activation_id
   AND p.tenant_id IS NULL;

ALTER TABLE inpatient.resuscitation_record ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE inpatient.resuscitation_phase ALTER COLUMN tenant_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_resuscitation_record_tenant ON inpatient.resuscitation_record (tenant_id);
CREATE INDEX IF NOT EXISTS idx_resuscitation_phase_tenant ON inpatient.resuscitation_phase (tenant_id);

COMMENT ON COLUMN inpatient.resuscitation_record.tenant_id IS
    'Denormalised from emergency_activation.tenant_id (immutable — never repointed, unlike identity, '
    'so no repoint hook is needed for this column). Gives this table its own tenant boundary instead '
    'of relying solely on application code always joining through activation_id.';

-- ---------------------------------------------------------------------------
-- 2. resuscitation_medication — kills the free-text medications_json blob on resuscitation_record
--    going forward. That column is left in place (a live-writer TEXT field with unknown existing
--    content; dropping it is a separate, deliberate decision for whoever owns the migration off it,
--    not a side effect of adding its replacement) but new medication administrations during a code
--    are structured rows from this migration on.
--
--    Genuinely new table, so unlike the two ALTERs above there is no brownfield data to protect —
--    every constraint here can be a hard NOT NULL / CHECK from creation, including the idempotent-
--    retry protection a live-writer table should have from day one rather than as a later retrofit.
-- ---------------------------------------------------------------------------
CREATE TABLE inpatient.resuscitation_medication (
    med_id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID        NOT NULL,
    activation_id      UUID        NOT NULL REFERENCES inpatient.emergency_activation (activation_id) ON DELETE CASCADE,
    trauma_episode_id  UUID,
    resus_id           UUID,

    drug_name          VARCHAR(128) NOT NULL,
    drug_code          VARCHAR(64),
    dose_value         NUMERIC(10,3) NOT NULL,
    dose_unit          VARCHAR(32) NOT NULL,
    route              VARCHAR(32) NOT NULL,

    administered_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    administered_by    VARCHAR(128) NOT NULL,
    witnessed_by       VARCHAR(128),

    -- Idempotent retry: any device mints its own id and may retry blind during a code. A double-POST
    -- of the SAME adrenaline dose must not become two rows. NULL is allowed for the rare caller that
    -- cannot generate one; the constraint only bites when a value IS supplied, matching the
    -- resuscitation_event design this mirrors.
    client_event_id    VARCHAR(64),

    -- Correct, never delete (the ed_trauma_survey V039 discipline). A wrong drug logged during a code
    -- is corrected by a new row referencing the old one, never by rewriting or deleting history.
    superseded_by      UUID REFERENCES inpatient.resuscitation_medication (med_id),
    correction_reason  TEXT,

    notes              TEXT,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_rm_dose_positive CHECK (dose_value > 0),
    CONSTRAINT chk_rm_correction_pair CHECK (
        superseded_by IS NULL OR correction_reason IS NOT NULL)
);

CREATE UNIQUE INDEX uq_resuscitation_medication_client_event
    ON inpatient.resuscitation_medication (tenant_id, activation_id, client_event_id)
    WHERE client_event_id IS NOT NULL;

CREATE INDEX idx_resuscitation_medication_activation
    ON inpatient.resuscitation_medication (activation_id, administered_at);
CREATE INDEX idx_resuscitation_medication_episode
    ON inpatient.resuscitation_medication (trauma_episode_id, administered_at)
    WHERE trauma_episode_id IS NOT NULL;

COMMENT ON TABLE inpatient.resuscitation_medication IS
    'Structured medication administrations during a resuscitation, replacing the free-text '
    'medications_json blob on resuscitation_record for all NEW entries. Corrections append a new row '
    'via superseded_by rather than rewriting history.';
COMMENT ON COLUMN inpatient.resuscitation_medication.witnessed_by IS
    'Optional second-person confirmation. Not enforced as mandatory here — a full two-person-witness '
    'authorisation workflow is emergency-medicines-wave scope (pct emergency_medication_admin), not '
    'this in-resus local write; this column exists so the fact can be captured when it happens.';

-- ---------------------------------------------------------------------------
-- 3. emergency_activation: origin (where in the facility this code was called from) and
--    emergency_episode_id (the cross-reference back to pct.emergency_episode for in-facility
--    deterioration: inpatient raises the activation, PCT consumes the event and mints on the
--    admission's EXISTING journey rather than opening a second one). Both additive and nullable —
--    every existing activation predates both concepts and stays valid with neither set.
--
--    No foreign key on emergency_episode_id: emergency_episode lives in pct-service's own database,
--    a different service entirely, so this is a plain correlating UUID — the same pattern
--    trauma_episode_id already uses on resuscitation_record/_event for daidzai's episode.
-- ---------------------------------------------------------------------------
ALTER TABLE inpatient.emergency_activation
    ADD COLUMN IF NOT EXISTS origin VARCHAR(24),
    ADD COLUMN IF NOT EXISTS emergency_episode_id UUID;

ALTER TABLE inpatient.emergency_activation
    ADD CONSTRAINT chk_ea_origin CHECK (origin IS NULL OR origin IN (
        'WARD', 'ED', 'CLINIC', 'THEATRE_RECOVERY', 'DIALYSIS', 'IMAGING', 'PUBLIC_AREA'));

CREATE INDEX IF NOT EXISTS idx_emergency_activation_episode
    ON inpatient.emergency_activation (emergency_episode_id)
    WHERE emergency_episode_id IS NOT NULL;

-- protocol_type has been fully caller-supplied free text since V007, with only a Java-side default
-- ("CODE_BLUE") and no constraint at all — any string a client sends becomes the protocol of a
-- resuscitation record. Unlike the two ALTERs in section 1, this column has been genuinely
-- unconstrained on a live table, so its existing values cannot be assumed to fit any vocabulary this
-- migration declares. NOT VALID is the brownfield-safe answer used elsewhere in this estate for
-- exactly this shape of problem (surgery's OROS cancellation-reason CHECK, same reasoning): every
-- NEW write is validated against the vocabulary from this migration forward, but historical rows are
-- never scanned and cannot fail the migration or block a boot. It is deliberately left NOT VALIDATEd
-- rather than force-validated later — validating would require auditing every historical value first,
-- which is a data-quality pass, not a schema change.
ALTER TABLE inpatient.emergency_activation
    ADD CONSTRAINT chk_ea_protocol_type CHECK (protocol_type IN (
        'CODE_BLUE', 'RAPID_RESPONSE', 'TRAUMA_ACTIVATION', 'MASSIVE_HAEMORRHAGE',
        'STROKE_ALERT', 'SEPSIS_ALERT', 'OBSTETRIC_EMERGENCY', 'NEONATAL_RESUSCITATION',
        'OTHER')) NOT VALID;

COMMENT ON COLUMN inpatient.emergency_activation.origin IS
    'Where in the facility this activation was called from — distinct from '
    'pct.emergency_episode.entry_route, which is how the person reached the facility at all. Additive '
    'and nullable; every pre-existing activation predates this concept.';
COMMENT ON COLUMN inpatient.emergency_activation.emergency_episode_id IS
    'Cross-reference to pct.emergency_episode (a different service/database — no FK, a plain '
    'correlating id, same pattern as trauma_episode_id). Lets an in-facility deterioration mint on '
    'the admission''s EXISTING journey rather than opening a second one.';
COMMENT ON CONSTRAINT chk_ea_protocol_type ON inpatient.emergency_activation IS
    'NOT VALID deliberately: protocol_type has been unconstrained free text since V007 and existing '
    'values are not audited. This constrains every future write without scanning or risking historical '
    'rows. See the section-3 header comment for the full reasoning.';
