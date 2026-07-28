-- =====================================================================================
-- varapi :: V039 — the register entry becomes a governed thing (NCZ-W1C-a)
--
-- Verified against the live estate before writing this, and it contradicted the notes:
--
--   * `provider_council_registration_records.status` is VARCHAR DEFAULT 'REGISTERED' with NO CHECK.
--     Any string is a valid register status today. Provisional-to-Main migration has nothing to
--     hang on, which is why AD-7 called the entry FSM a prerequisite and not a later concern.
--   * There is NO effective dating on a register entry anywhere. `effective_from`/`effective_to`
--     exist only on `provider` and `provider_identifiers`. A register entry could not say when it
--     began, when it ended, or what replaced it — and the brief requires exactly that.
--   * The four NCZ registers already seeded — NCZ_GENERAL, NCZ_MENTAL_HEALTH, NCZ_MIDWIFE,
--     NCZ_SPECIALIST — are a QUALIFICATION taxonomy. They are not the brief's four (Student,
--     Provisional, Main, Nurse-Led Health Institutions). The COUNT matched, so an earlier
--     traceability row read EXISTING_REUSABLE; the identity did not. There is no Student Register.
--   * All 4,241 existing entries carry `register_id` NULL, so even where the linkage exists it is
--     unused.
--
-- What this migration does NOT do: reclassify another council's seeded registers. MDPCZ_PROVISIONAL
-- shows the original seed mixed both axes, so inferring which of the others are standings and which
-- are qualifications would be guessing at eight councils' statutory models. They are left
-- UNCLASSIFIED and the question is raised as a council decision (NCZ-DEC-016).
-- =====================================================================================

-- ── Which axis a register measures ───────────────────────────────────────────────────────────
-- STANDING: where a person stands with the council (Student, Provisional, Main).
-- QUALIFICATION: what they are qualified in (General, Midwife, Mental Health).
-- A nurse on the Main Register holding several qualifications is the brief's "multiple nursing
-- qualifications" case, and it only expresses cleanly once the two axes are distinguishable.
ALTER TABLE varapi.professional_registers
    ADD COLUMN IF NOT EXISTS register_axis VARCHAR(24),
    ADD COLUMN IF NOT EXISTS statutory_title VARCHAR(160),
    ADD COLUMN IF NOT EXISTS statutory_title_decision_ref VARCHAR(64);

ALTER TABLE varapi.professional_registers
    DROP CONSTRAINT IF EXISTS chk_register_axis;
ALTER TABLE varapi.professional_registers
    ADD CONSTRAINT chk_register_axis CHECK (
        register_axis IS NULL OR register_axis IN ('STANDING', 'QUALIFICATION'));

COMMENT ON COLUMN varapi.professional_registers.register_axis IS
    'STANDING (where a person stands with the council) or QUALIFICATION (what they are qualified '
    'in). NULL means not yet classified: the original seed mixed both axes and reclassifying a '
    'council''s statutory model by inference would be guessing — see NCZ-DEC-016.';

COMMENT ON COLUMN varapi.professional_registers.statutory_title IS
    'The register''s exact title in law. NULL where the council has not confirmed it. A title '
    'appears on certificates and cannot be quietly corrected once issued, so it is never guessed.';

-- The brief's four NCZ registers. They are additive: the existing NCZ_* rows are not touched.
INSERT INTO varapi.professional_registers
    (tenant_id, council_id, register_code, name, register_axis, statutory_title,
     statutory_title_decision_ref)
SELECT c.tenant_id, c.id, v.code, v.name, 'STANDING', NULL, 'NCZ-DEC-013'
  FROM varapi.councils c
  CROSS JOIN (VALUES
      ('NCZ_STUDENT',              'Student Register'),
      ('NCZ_PROVISIONAL',          'Provisional Register'),
      ('NCZ_MAIN',                 'Main Register'),
      ('NCZ_NURSE_LED_INSTITUTION','Register of Nurse-Led Health Institutions')
  ) AS v(code, name)
 WHERE c.council_code = 'NCZ'
   AND NOT EXISTS (SELECT 1 FROM varapi.professional_registers r
                    WHERE r.council_id = c.id AND r.register_code = v.code);

-- ── The entry lifecycle ──────────────────────────────────────────────────────────────────────
-- A closed vocabulary, including the two values already in use so 4,241 live rows stay legal.
-- The STATES are platform mechanics; the CRITERIA for entering them are the council's, and stay in
-- the configuration registry (migration criteria are unset pending NCZ-DEC-006).
ALTER TABLE varapi.provider_council_registration_records
    ADD COLUMN IF NOT EXISTS effective_from DATE,
    ADD COLUMN IF NOT EXISTS effective_to DATE,
    ADD COLUMN IF NOT EXISTS admitted_via VARCHAR(128),
    ADD COLUMN IF NOT EXISTS decided_by VARCHAR(128),
    ADD COLUMN IF NOT EXISTS decision_ref VARCHAR(128),
    ADD COLUMN IF NOT EXISTS superseded_by_entry_id BIGINT
        REFERENCES varapi.provider_council_registration_records(id),
    ADD COLUMN IF NOT EXISTS status_changed_at TIMESTAMPTZ;

COMMENT ON COLUMN varapi.provider_council_registration_records.admitted_via IS
    'The application or import that produced this entry. An entry with no provenance is an entry '
    'nobody can account for.';
COMMENT ON COLUMN varapi.provider_council_registration_records.superseded_by_entry_id IS
    'Set when this entry was MIGRATED to another register — the Provisional row points at the Main '
    'row that replaced it, so the history reads forwards instead of being overwritten.';

ALTER TABLE varapi.provider_council_registration_records
    DROP CONSTRAINT IF EXISTS chk_register_entry_status;
ALTER TABLE varapi.provider_council_registration_records
    ADD CONSTRAINT chk_register_entry_status CHECK (status IN (
        -- In use today: the column default, and the HPA backfill's honest holding state.
        'REGISTERED',
        'LISTED_PENDING_COUNCIL_VERIFICATION',
        -- The governed lifecycle.
        'ADMITTED',    -- on the register, current
        'SUSPENDED',   -- on the register, not currently entitled to practise under it
        'MIGRATED',    -- moved to another register; superseded_by_entry_id says which
        'LAPSED',      -- ended by the passage of time rather than by a decision
        'REMOVED'      -- ended by a decision
    ));

ALTER TABLE varapi.provider_council_registration_records
    DROP CONSTRAINT IF EXISTS chk_register_entry_dates;
ALTER TABLE varapi.provider_council_registration_records
    ADD CONSTRAINT chk_register_entry_dates CHECK (
        effective_to IS NULL OR effective_from IS NULL OR effective_to >= effective_from);

-- A migration must say where to. "MIGRATED" with nothing to point at loses the person's continuity,
-- which is the one thing a register is for.
ALTER TABLE varapi.provider_council_registration_records
    DROP CONSTRAINT IF EXISTS chk_register_entry_migration_target;
ALTER TABLE varapi.provider_council_registration_records
    ADD CONSTRAINT chk_register_entry_migration_target CHECK (
        status <> 'MIGRATED' OR superseded_by_entry_id IS NOT NULL);

-- ── The transitions ──────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION varapi.assert_register_entry_transition()
RETURNS TRIGGER AS $$
DECLARE
    legal BOOLEAN;
BEGIN
    IF NEW.status IS NOT DISTINCT FROM OLD.status THEN
        RETURN NEW;
    END IF;

    legal := CASE OLD.status
        -- The legacy states are entry points into the governed lifecycle, not part of it.
        WHEN 'REGISTERED'                          THEN NEW.status IN
            ('ADMITTED','SUSPENDED','MIGRATED','LAPSED','REMOVED')
        WHEN 'LISTED_PENDING_COUNCIL_VERIFICATION' THEN NEW.status IN
            ('ADMITTED','REMOVED')
        WHEN 'ADMITTED'  THEN NEW.status IN ('SUSPENDED','MIGRATED','LAPSED','REMOVED')
        WHEN 'SUSPENDED' THEN NEW.status IN ('ADMITTED','REMOVED','LAPSED')
        WHEN 'LAPSED'    THEN NEW.status IN ('ADMITTED','REMOVED')
        -- Terminal. A person who was removed, or who moved on, is not quietly reinstated by an
        -- UPDATE: that is a new decision and belongs in a new entry with its own provenance.
        WHEN 'MIGRATED'  THEN FALSE
        WHEN 'REMOVED'   THEN FALSE
        ELSE FALSE
    END;

    IF NOT legal THEN
        RAISE EXCEPTION
            'Register entry % cannot move from % to %. A terminal entry is superseded by a new '
            'entry carrying its own decision and provenance, never reopened in place.',
            OLD.id, OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;

    NEW.status_changed_at := NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_register_entry_transition
    ON varapi.provider_council_registration_records;
CREATE TRIGGER trg_register_entry_transition
    BEFORE UPDATE ON varapi.provider_council_registration_records
    FOR EACH ROW EXECUTE FUNCTION varapi.assert_register_entry_transition();

-- One current entry per (provider, register). A person is on a register once, or not at all.
CREATE UNIQUE INDEX IF NOT EXISTS uq_register_entry_current
    ON varapi.provider_council_registration_records (tenant_id, provider_id, register_id)
    WHERE status IN ('ADMITTED', 'SUSPENDED') AND register_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_register_entry_register_status
    ON varapi.provider_council_registration_records (register_id, status)
    WHERE register_id IS NOT NULL;
