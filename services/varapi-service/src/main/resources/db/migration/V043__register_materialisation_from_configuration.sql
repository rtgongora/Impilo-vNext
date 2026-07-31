-- =====================================================================================
-- varapi :: V043 — a register becomes configuration rather than code (NCZ task #97)
--
-- V039 created NCZ's four standing registers with literal INSERT statements. That was the
-- right call at the time — nothing could read a configuration pack yet — and it is now the
-- only place where adding a council requires a code change. The NCZ pack already ships the
-- four REGISTER definitions as configuration and nothing consumes them, so "a council is a
-- configuration pack" is true of everything except the registers themselves.
--
-- This migration adds what the materialiser needs to close that loop honestly:
--
--   * PROVENANCE, so a register can say whether it came from a migration or from an
--     activated pack, and which exact definition version put it there. Without this the
--     PCZ fixture's inverted P1 could only assert that rows EXIST — and rows existing is
--     precisely what a second hardcoded migration would also produce. Provenance is what
--     makes the assertion discriminate between the fix and the leak repeated.
--
--   * RETIREMENT, so a register dropped from a pack stops being offered without being
--     destroyed. A register with entries on it cannot vanish because somebody edited a
--     pack — the same reasoning that keeps issued_number rows forever. Retirement is a
--     state; deletion is refused outright below.
--
-- What this migration deliberately does NOT do: seed any register. Registers arrive by
-- reconcile from an ACTIVE release, and a migration that seeded one would be the very leak
-- being closed.
-- =====================================================================================

-- ── Provenance ───────────────────────────────────────────────────────────────────────────────
ALTER TABLE varapi.professional_registers
    ADD COLUMN IF NOT EXISTS source                   VARCHAR(24),
    ADD COLUMN IF NOT EXISTS config_definition_key    VARCHAR(128),
    ADD COLUMN IF NOT EXISTS config_semantic_version  VARCHAR(32),
    ADD COLUMN IF NOT EXISTS config_content_hash      VARCHAR(128),
    ADD COLUMN IF NOT EXISTS materialised_at          TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS retired_at               TIMESTAMPTZ;

-- Everything standing today arrived from a migration seed (V030 and V039). Saying so is the
-- point: it is the record of what the leak looked like before it was closed.
UPDATE varapi.professional_registers SET source = 'MIGRATION_SEED' WHERE source IS NULL;

ALTER TABLE varapi.professional_registers
    ALTER COLUMN source SET DEFAULT 'MIGRATION_SEED',
    ALTER COLUMN source SET NOT NULL;

ALTER TABLE varapi.professional_registers DROP CONSTRAINT IF EXISTS chk_register_source;
ALTER TABLE varapi.professional_registers
    ADD CONSTRAINT chk_register_source CHECK (source IN ('MIGRATION_SEED', 'CONFIG_PACK'));

COMMENT ON COLUMN varapi.professional_registers.source IS
    'MIGRATION_SEED (created by literal SQL, the pre-materialiser world) or CONFIG_PACK '
    '(reconciled from an activated regulatory configuration release). A council added as a '
    'configuration pack produces only CONFIG_PACK rows; a MIGRATION_SEED row appearing for a '
    'new council means somebody hardcoded registers again.';

COMMENT ON COLUMN varapi.professional_registers.config_content_hash IS
    'The content hash of the REGISTER definition version that materialised this row. It names '
    'the exact approved definition, so a register can be traced to the release that authorised '
    'it rather than to whatever the pack happens to say today.';

-- ── Status is a closed vocabulary, and retirement is dated ───────────────────────────────────
-- status has been an unconstrained VARCHAR since V029: any string was a legal register status.
ALTER TABLE varapi.professional_registers DROP CONSTRAINT IF EXISTS chk_register_status;
ALTER TABLE varapi.professional_registers
    ADD CONSTRAINT chk_register_status CHECK (status IN ('ACTIVE', 'RETIRED'));

ALTER TABLE varapi.professional_registers DROP CONSTRAINT IF EXISTS chk_register_retired_dated;
ALTER TABLE varapi.professional_registers
    ADD CONSTRAINT chk_register_retired_dated CHECK (
        (status = 'RETIRED' AND retired_at IS NOT NULL)
     OR (status <> 'RETIRED' AND retired_at IS NULL));

COMMENT ON COLUMN varapi.professional_registers.retired_at IS
    'When the council stopped offering this register. A retired register keeps its entries and '
    'its history; it simply admits nobody new. Retirement is reversible by reinstatement, which '
    'is why it is a state and not a deletion.';

-- ── A register is never deleted ──────────────────────────────────────────────────────────────
-- The materialiser reconciles a pack against the registers a council already has, and a pack
-- can lose a definition — by edit, by mistake, or by a release that simply omits it. If that
-- removed the row, every entry admitted to that register would lose the thing it was admitted
-- TO. Refusing the delete outright means the materialiser CANNOT express that mistake, rather
-- than being trusted not to make it.
CREATE OR REPLACE FUNCTION varapi.refuse_professional_register_delete()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'Register %/% cannot be deleted. A register carries the entries admitted to it, so it is '
        'retired (status = RETIRED), never removed — including when a configuration pack stops '
        'declaring it.',
        OLD.council_id, OLD.register_code
        USING ERRCODE = 'check_violation';
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_refuse_professional_register_delete ON varapi.professional_registers;
CREATE TRIGGER trg_refuse_professional_register_delete
    BEFORE DELETE ON varapi.professional_registers
    FOR EACH ROW EXECUTE FUNCTION varapi.refuse_professional_register_delete();

-- Reconcile looks a council's registers up by code on every run; this is that lookup.
CREATE INDEX IF NOT EXISTS idx_professional_registers_reconcile
    ON varapi.professional_registers(tenant_id, council_id, register_code);
