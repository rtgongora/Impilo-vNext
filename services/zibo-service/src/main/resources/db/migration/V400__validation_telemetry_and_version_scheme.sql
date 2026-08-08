-- ============================================================
-- ZIBO — terminology lane, block V400–V449 (docs/registry/zibo-terminology-leases.md)
-- V400: make the coding gap measurable, and stop deciding version precedence by clock
--
-- Two independent defects, both measured in impilo-full-preview on 2026-08-08.
--
-- 1. zibo_validation_logs holds ZERO rows, and could not answer the question anyway.
--
--    The table has no `code` column. It records canonical_url and a free-text `details` blob, so
--    "which code failed" is only recoverable by string-parsing prose. There is also no denominator:
--    ValidationEngine.logValidation is called on exactly two paths (not-found, code-invalid), so
--    even a full table would count failures against nothing.
--
--    That is why no one in this estate can state how coded it actually is. Every claim about the
--    coding gap -- including the ones in the ZIBO spec -- rests on counting artifacts rather than
--    counting what clinicians record. This migration gives the log the columns needed to answer
--    "what percentage of clinical codings resolve against governed terminology", per service, per
--    resource type, per facility.
--
--    NO PHI. The coding and its context are recorded; the resource body is not, and element_path
--    is a FHIR path (`Observation.code`), never a value.
--
-- 2. Version precedence is decided by clock, not by the publisher's version string.
--
--    ValidationEngine:107 and MedicineRegistryService:107 both select `max(comparing(publishedAt))`,
--    and ArtifactRepository.findAllVersions orders `createdAt DESC`. Publishing a 1.0.1 correction
--    after 1.1.0 therefore silently starts serving the older content -- and for external
--    terminologies whose versions are dates (LOINC 2.82, ATC 2026.01, ICD-11 2026-01) the clock and
--    the version agree only by luck.
--
--    version_scheme declares how a publisher's string is ordered; version_sort_key is the derived,
--    comparable value. The publisher's `version` string is NEVER rewritten -- LOINC's "2.82" stays
--    "2.82". Coercing external versions into SemVer would destroy the identifier the publisher and
--    every downstream record use.
-- ============================================================

-- ─────────────────────────────────────────────────────────────────────
-- 1. Validation telemetry
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE zibo_validation_logs
    ADD COLUMN IF NOT EXISTS system          VARCHAR(500),
    ADD COLUMN IF NOT EXISTS code            VARCHAR(255),
    ADD COLUMN IF NOT EXISTS resource_type   VARCHAR(100),
    ADD COLUMN IF NOT EXISTS element_path    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS calling_service VARCHAR(100),
    ADD COLUMN IF NOT EXISTS policy_mode     VARCHAR(10),
    ADD COLUMN IF NOT EXISTS result          VARCHAR(30),
    ADD COLUMN IF NOT EXISTS correlation_id  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS request_id      VARCHAR(255);

COMMENT ON COLUMN zibo_validation_logs.system IS
    'The coding system URI as the caller supplied it — including one that resolves to no artifact, '
    'which is the single most useful thing this table records.';
COMMENT ON COLUMN zibo_validation_logs.code IS
    'The code as supplied. A clinical code, not a patient identifier — no PHI belongs in this table.';
COMMENT ON COLUMN zibo_validation_logs.element_path IS
    'FHIR element path (e.g. Observation.code). The path, never the value.';
COMMENT ON COLUMN zibo_validation_logs.result IS
    'RESOLVED | UNKNOWN_SYSTEM | UNKNOWN_CODE | INVALID_MEMBERSHIP | UNRESOLVED_MAPPING. '
    'RESOLVED rows are the denominator: without them a full table still cannot produce a percentage.';
COMMENT ON COLUMN zibo_validation_logs.calling_service IS
    'Which service asked. service_name has always been the hardcoded literal "zibo-service", so it '
    'cannot distinguish oros from butano from the BFF.';

-- The coverage query is "group by result, filtered by time, sliced by service/resource/facility".
CREATE INDEX IF NOT EXISTS idx_zibo_validation_logs_coverage
    ON zibo_validation_logs (tenant_id, created_at DESC, result);
CREATE INDEX IF NOT EXISTS idx_zibo_validation_logs_unknown
    ON zibo_validation_logs (system, code)
    WHERE result IN ('UNKNOWN_SYSTEM', 'UNKNOWN_CODE');
CREATE INDEX IF NOT EXISTS idx_zibo_validation_logs_caller
    ON zibo_validation_logs (calling_service, created_at DESC);

-- ─────────────────────────────────────────────────────────────────────
-- 2. Version scheme
-- ─────────────────────────────────────────────────────────────────────

ALTER TABLE zibo_artifacts
    ADD COLUMN IF NOT EXISTS version_scheme   VARCHAR(20),
    ADD COLUMN IF NOT EXISTS release_date     DATE,
    ADD COLUMN IF NOT EXISTS version_sort_key VARCHAR(64);

COMMENT ON COLUMN zibo_artifacts.version_scheme IS
    'SEMVER | DATE | INTEGER | PUBLISHER_DEFINED. Declares how `version` is ordered. The publisher''s '
    'version string itself is never rewritten.';
COMMENT ON COLUMN zibo_artifacts.version_sort_key IS
    'Derived, lexicographically comparable form of `version` under version_scheme. Zero-padded so a '
    'plain string ORDER BY is correct: SemVer 1.10.0 must sort above 1.9.0, which a naive string '
    'compare gets backwards.';

-- Backfill the 44 existing rows. Every current version is either SemVer (1.0.0, 1.1.0) or a
-- publisher date (2026.01, 2025.1, v10-2025). Anything unrecognised is PUBLISHER_DEFINED and sorts
-- by its raw string — honest about not knowing rather than guessing an order.
-- DATE is tested BEFORE the numeric schemes and the order is load-bearing: `2025.1` and `2.82` are
-- both "digits dot digits", and only the four-digit leading year separates a release date from
-- LOINC's two-part version. Testing numerically first would classify every ATC and EDLIZ release as
-- a version number and sort 2026.01 below 2025.1.
UPDATE zibo_artifacts SET version_scheme =
    CASE
        WHEN version ~ '^[0-9]{4}[.-][0-9]{1,2}$'        THEN 'DATE'
        -- Two- or three-part numeric. Two-part covers LOINC ("2.82"), whose minor is a sequential
        -- integer: as raw strings "2.9" sorts above "2.82", which is backwards.
        WHEN version ~ '^[0-9]+\.[0-9]+(\.[0-9]+)?'      THEN 'SEMVER'
        WHEN version ~ '^[0-9]+$'                        THEN 'INTEGER'
        ELSE 'PUBLISHER_DEFINED'
    END
WHERE version_scheme IS NULL;

UPDATE zibo_artifacts SET version_sort_key =
    CASE version_scheme
        WHEN 'SEMVER' THEN
            lpad(split_part(version, '.', 1), 6, '0') || '.' ||
            lpad(split_part(version, '.', 2), 6, '0') || '.' ||
            lpad(regexp_replace(split_part(version, '.', 3), '[^0-9].*$', ''), 6, '0')
        WHEN 'DATE' THEN
            lpad(split_part(regexp_replace(version, '[.-]', '|', 'g'), '|', 1), 6, '0') || '.' ||
            lpad(split_part(regexp_replace(version, '[.-]', '|', 'g'), '|', 2), 6, '0') || '.' ||
            lpad('0', 6, '0')
        WHEN 'INTEGER' THEN
            lpad(version, 6, '0') || '.' || lpad('0', 6, '0') || '.' || lpad('0', 6, '0')
        ELSE version
    END
WHERE version_sort_key IS NULL;

ALTER TABLE zibo_artifacts
    ALTER COLUMN version_scheme SET DEFAULT 'PUBLISHER_DEFINED';

-- Resolution order for "the current version of this canonical URL": highest version_sort_key among
-- rows whose effective window contains now. effective_start/effective_end were added by V003 and
-- have never been read by anything.
CREATE INDEX IF NOT EXISTS idx_zibo_artifacts_resolution
    ON zibo_artifacts (tenant_id, canonical_url, status, version_sort_key DESC);
