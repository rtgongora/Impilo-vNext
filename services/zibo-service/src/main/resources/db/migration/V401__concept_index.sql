-- ============================================================
-- ZIBO — terminology lane, block V400–V449 (docs/registry/zibo-terminology-leases.md)
-- V401: the concept index — a derived, searchable projection of what artifacts contain
--
-- Concepts live only inside zibo_artifacts.content_json today, and every read walks that JSONB blob
-- in Java: ValidationEngine.lookupCodeInContent parses the whole document and linearly scans
-- concept[] on EVERY validation, and MedicineRegistryService does the same to list 20 medicines.
-- At the 44-artifact, 254-concept scale of the current seed that is merely wasteful. At LOINC's
-- ~109,000 concepts it is not implementable at all, and there is no free-text search of any kind —
-- which is exactly why the terminology page in the UI says search is "intentionally not shown".
--
-- ── THE PROJECTION IS NEVER THE ORIGIN ──────────────────────────────────────────────────────
--
-- content_json remains the artifact of record. This table is derived from it, rebuilt on publish,
-- and can be dropped and reconstructed without losing anything (mandatory rule 9: a projection or
-- cache never silently becomes the authoritative origin of a fact). Nothing writes a concept here
-- that did not come from an artifact.
--
-- ── AUTHORITY SCOPE, NOT TENANT DUPLICATION ─────────────────────────────────────────────────
--
-- The ZIBO spec proposed UNIQUE (tenant_id, system, version, code). That contradicts its own §4.4:
-- national terminology must resolve from either plane WITHOUT being duplicated into each. V007
-- already inserts the ATC CodeSystem twice, once per tenant, precisely because lookups could not
-- cross the boundary — that is the workaround being retired, not a pattern to enshrine.
--
-- So the key is (authority_scope, tenant_id, system, version, code), with tenant_id NULL for
-- NATIONAL rows. A national concept exists once and is visible to every plane; a tenant may still
-- publish its own artifact at the same canonical URL and its concepts win, exactly as
-- ArtifactResolutionService already prefers a local artifact over the national one.
-- ============================================================

CREATE TABLE IF NOT EXISTS zibo_concept (
    concept_id      UUID            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- NATIONAL concepts carry a NULL tenant_id and are readable from every plane.
    authority_scope VARCHAR(20)     NOT NULL,
    tenant_id       UUID,

    -- The artifact this was projected from. ON DELETE CASCADE because a concept has no meaning
    -- once its artifact is gone — this is a projection, not a record.
    artifact_id     UUID            NOT NULL REFERENCES zibo_artifacts(artifact_id) ON DELETE CASCADE,

    system          VARCHAR(500)    NOT NULL,
    version         VARCHAR(50)     NOT NULL,
    code            VARCHAR(255)    NOT NULL,
    display         TEXT,
    definition      TEXT,

    -- FHIR CodeSystem.concept has no `active` element; inactivity is expressed as a property named
    -- `status` or `inactive`. Projected here as a first-class column because $expand(activeOnly)
    -- needs it in the WHERE clause, not in a JSONB scan.
    active          BOOLEAN         NOT NULL DEFAULT true,

    -- Synonyms and language variants. The estate needs Shona and Ndebele designations to be
    -- searchable, not decorative, so they are indexed alongside display below.
    designations    JSONB           NOT NULL DEFAULT '[]'::jsonb,
    properties      JSONB           NOT NULL DEFAULT '{}'::jsonb,

    -- Hierarchy, for a future $subsumes / is-a filter. Nullable: most seeded systems are flat.
    parent_code     VARCHAR(255),

    search_vector   TSVECTOR,

    created_at      TIMESTAMPTZ     NOT NULL DEFAULT now(),

    CONSTRAINT zibo_concept_scope_tenant_ck CHECK (
        (authority_scope = 'NATIONAL' AND tenant_id IS NULL)
        OR (authority_scope = 'TENANT' AND tenant_id IS NOT NULL)
    )
);

-- One concept per (scope, tenant, system, version, code). A partial unique index is required
-- rather than a table constraint because NULL tenant_id would otherwise never collide.
CREATE UNIQUE INDEX IF NOT EXISTS idx_zibo_concept_national_key
    ON zibo_concept (system, version, code)
    WHERE authority_scope = 'NATIONAL';

CREATE UNIQUE INDEX IF NOT EXISTS idx_zibo_concept_tenant_key
    ON zibo_concept (tenant_id, system, version, code)
    WHERE authority_scope = 'TENANT';

-- $lookup and $validate-code: a point lookup, which is the p95 < 50 ms path.
CREATE INDEX IF NOT EXISTS idx_zibo_concept_lookup
    ON zibo_concept (system, version, code);

-- $expand: every concept of a system/version, optionally active only.
CREATE INDEX IF NOT EXISTS idx_zibo_concept_expand
    ON zibo_concept (system, version, active);

-- Rebuild-on-publish deletes by artifact before re-inserting.
CREATE INDEX IF NOT EXISTS idx_zibo_concept_artifact
    ON zibo_concept (artifact_id);

-- Free-text search over display + designations. GIN over tsvector is the same approach already
-- used in-house by search-service, msika (V002__fts_indexes.sql) and tuso — no new infrastructure,
-- and sufficient for a type-ahead picker at ICD-11 scale.
CREATE INDEX IF NOT EXISTS idx_zibo_concept_search
    ON zibo_concept USING GIN (search_vector);

COMMENT ON TABLE zibo_concept IS
    'Derived, searchable projection of concepts held in zibo_artifacts.content_json. Rebuilt on '
    'publish; safe to drop and reconstruct. content_json remains the artifact of record — this '
    'table must never become the origin of a concept.';

COMMENT ON COLUMN zibo_concept.authority_scope IS
    'NATIONAL (tenant_id NULL, readable from every plane) or TENANT (a plane-local override). '
    'Replaces duplicating national content per tenant, which is what V007 had to do.';

COMMENT ON COLUMN zibo_concept.search_vector IS
    'to_tsvector over display and designation values. Maintained by the projection service on '
    'insert, not by a trigger, so the language configuration lives with the code that knows the '
    'artifact''s language.';
