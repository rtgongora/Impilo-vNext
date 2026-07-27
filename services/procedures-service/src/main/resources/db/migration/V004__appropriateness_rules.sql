-- =====================================================================================
-- Procedures :: V004 — appropriateness and duplication inputs (pipeline §5)
--
-- §5's twelve detections need three things the catalogue did not yet declare: how long a
-- procedure counts as recently done, what it conflicts with, and what must precede it.
--
-- These are catalogue CONTENT, so they live on the definition — not in an engine, and not
-- in a rules table of their own. A duplication window that changes because a clinical
-- committee decided so is a content release; a duplication window compiled into an engine
-- is a deployment. That difference is the whole reason the catalogue exists.
--
-- ENGINE-NOT-STORE still holds: this migration adds INPUTS to a judgement. There is
-- deliberately no table of detections, because a detection is a verdict and the caller
-- persists verdicts. If a future migration here adds `procedure_detection`, the rule has
-- been broken.
-- =====================================================================================

ALTER TABLE procedures.procedure_definition
    -- How long after a completed instance a fresh request counts as a possible duplicate.
    -- NULL means "no window declared" — which the engine reports as UNKNOWN rather than
    -- treating as zero. An undeclared window is not a window of nothing.
    ADD COLUMN IF NOT EXISTS duplicate_lookback_days INTEGER,
    -- Definition codes this procedure conflicts with — cannot sensibly be done together,
    -- or one invalidates the other.
    ADD COLUMN IF NOT EXISTS conflicts_with          JSONB NOT NULL DEFAULT '[]'::jsonb,
    -- Definition codes that must have been completed first.
    ADD COLUMN IF NOT EXISTS prerequisite_codes      JSONB NOT NULL DEFAULT '[]'::jsonb;

COMMENT ON COLUMN procedures.procedure_definition.duplicate_lookback_days IS
    'Days within which a completed instance makes a new request a possible duplicate. NULL = not declared, reported as UNKNOWN, never assumed zero.';

CREATE INDEX IF NOT EXISTS idx_procedure_definition_conflicts
    ON procedures.procedure_definition USING gin (conflicts_with);

-- Seeded windows for the procedures where repeating too soon is a real and recognisable
-- harm or waste. Everything else stays NULL and is honestly reported as undeclared rather
-- than silently defaulted — a default here would manufacture duplicate warnings, and a
-- warning nobody believes is worse than no warning.
UPDATE procedures.procedure_definition SET duplicate_lookback_days = 90
    WHERE definition_code IN ('PROC-OGD','PROC-COLONOSCOPY','PROC-FLEXIBLE-SIGMOIDOSCOPY','PROC-BRONCHOSCOPY','PROC-CYSTOSCOPY');
UPDATE procedures.procedure_definition SET duplicate_lookback_days = 30
    WHERE definition_code IN ('PROC-US-GUIDED-BIOPSY','PROC-BREAST-CORE-BIOPSY','PROC-BONE-MARROW-BIOPSY','PROC-CT-GUIDED-DRAINAGE');
UPDATE procedures.procedure_definition SET duplicate_lookback_days = 7
    WHERE definition_code IN ('PROC-LUMBAR-PUNCTURE','PROC-ASCITIC-TAP','PROC-PLEURAL-ASPIRATION');
-- A dialysis session is prescribed to recur; a same-day repeat is still worth a question.
UPDATE procedures.procedure_definition SET duplicate_lookback_days = 1
    WHERE definition_code IN ('PROC-HAEMODIALYSIS','PROC-PERITONEAL-DIALYSIS');

-- Conflicts and prerequisites, kept to relationships that are clinically defensible rather
-- than exhaustive. An invented conflict produces a false block, which teaches clinicians to
-- override the engine — the failure mode that makes every later true detection worthless.
UPDATE procedures.procedure_definition
   SET conflicts_with = '["PROC-FLEXIBLE-SIGMOIDOSCOPY"]'::jsonb
 WHERE definition_code = 'PROC-COLONOSCOPY';
UPDATE procedures.procedure_definition
   SET conflicts_with = '["PROC-COLONOSCOPY"]'::jsonb
 WHERE definition_code = 'PROC-FLEXIBLE-SIGMOIDOSCOPY';
UPDATE procedures.procedure_definition
   SET prerequisite_codes = '["PROC-BREAST-CORE-BIOPSY"]'::jsonb
 WHERE definition_code = 'PROC-MASTECTOMY';
UPDATE procedures.procedure_definition
   SET prerequisite_codes = '["PROC-AV-FISTULA"]'::jsonb
 WHERE definition_code = 'PROC-HAEMODIALYSIS';
