-- Problem links (PCT) — the relationships a problem list needs to be more than a list.
--
-- The Adult Medicine brief asks the problem list to carry evidence, complications, comorbidity,
-- related medicines, related investigations and goals. Every one of those is a RELATIONSHIP, and
-- the tempting shape is a column per relationship on pct_problems: complication_of_id,
-- caused_by_id, evidenced_by_observation_id. That fails on the second example of each — a
-- complication with two causes, a diagnosis resting on three results — and every failure is
-- silent, because the second value simply has nowhere to go and is dropped.
--
-- So one link table, typed on both ends.
--
-- WHY THE TARGET IS UNTYPED (target_type + target_id rather than six nullable FKs). A problem links
-- to things owned by other services: an observation in pct_observations, a prescription in OROS, an
-- order, a goal on a care plan, a procedure executed by procedures-service. A foreign key can only
-- be declared to a table in this database, so six FKs would mean either restricting links to
-- PCT-local objects — which excludes medicines and investigations, i.e. most of the brief — or
-- declaring FKs that cannot exist. The type is a closed vocabulary and the id is validated by the
-- owning service on read; the alternative is a schema that cannot express the requirement.
--
-- PROBLEM-TO-PROBLEM LINKS ARE THE EXCEPTION and do get a real FK, because both ends are here.
-- That is where the clinically dangerous relationships live — a complication of, a cause of — and
-- a dangling reference there would silently orphan a complication from the disease that caused it.

CREATE TABLE pct_problem_links (
    link_id      UUID           PRIMARY KEY,
    tenant_id    UUID           NOT NULL,
    problem_id   UUID           NOT NULL REFERENCES pct_problems(problem_id) ON DELETE CASCADE,

    link_type    VARCHAR(32)    NOT NULL,
    target_type  VARCHAR(32)    NOT NULL,

    -- Set when target_type = 'PROBLEM'. A real FK, because both ends are in this database and a
    -- dangling complication is a complication detached from its cause.
    target_problem_id UUID      REFERENCES pct_problems(problem_id) ON DELETE CASCADE,
    -- Set for every other target type. Validated by the owning service, not by this schema.
    target_ref   VARCHAR(128),

    -- Why the link was made, in the recorder's words. A link with no rationale is an assertion.
    note         TEXT,
    recorded_by  VARCHAR(255)   NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),

    CONSTRAINT pct_problem_links_type_check CHECK (link_type IN (
        'COMPLICATION_OF',   -- this problem is a complication of the target
        'CAUSED_BY',         -- the target caused this problem
        'COMORBID_WITH',     -- managed alongside, neither causing the other
        'EVIDENCED_BY',      -- an observation or report supporting this problem
        'TREATED_BY',        -- a medicine or intervention addressing it
        'MONITORED_BY',      -- an investigation or measurement tracking it
        'GOAL_FOR'           -- a treatment goal set against it
    )),
    CONSTRAINT pct_problem_links_target_type_check CHECK (target_type IN (
        'PROBLEM',
        'OBSERVATION',
        'MEDICATION',
        'DIAGNOSTIC_ORDER',
        'PROCEDURE',
        'GOAL',
        'CARE_PLAN',
        'DOCUMENT'
    )),
    -- Exactly one target, and it must match the declared type. Without this a link could name a
    -- problem and an external reference at once, and readers would disagree about which it meant.
    CONSTRAINT pct_problem_links_target_check CHECK (
        (target_type = 'PROBLEM' AND target_problem_id IS NOT NULL AND target_ref IS NULL)
        OR (target_type <> 'PROBLEM' AND target_problem_id IS NULL AND target_ref IS NOT NULL)
    ),
    -- A problem cannot be a complication of, or caused by, itself.
    CONSTRAINT pct_problem_links_no_self_link CHECK (
        target_problem_id IS NULL OR target_problem_id <> problem_id
    )
);

-- The same relationship must not be recorded twice. Two "caused by" links between the same pair
-- would make a complication appear to have two identical causes and would double-count on any
-- multimorbidity view.
CREATE UNIQUE INDEX uq_pct_problem_links_problem_target
    ON pct_problem_links (tenant_id, problem_id, link_type, target_type,
                          COALESCE(target_problem_id::text, target_ref));

CREATE INDEX idx_pct_problem_links_problem ON pct_problem_links (tenant_id, problem_id);
-- Reverse lookups: "what does this observation support", "what is this medicine treating".
CREATE INDEX idx_pct_problem_links_target ON pct_problem_links (tenant_id, target_type, target_ref)
    WHERE target_ref IS NOT NULL;
CREATE INDEX idx_pct_problem_links_target_problem ON pct_problem_links (tenant_id, target_problem_id)
    WHERE target_problem_id IS NOT NULL;

COMMENT ON TABLE pct_problem_links IS
    'Relationships from a problem to other clinical objects — evidence, complications, comorbidity, '
    'medicines, investigations and goals. Problem-to-problem links carry a real foreign key; links '
    'to objects owned by other services carry a typed reference validated by the owner.';
COMMENT ON COLUMN pct_problem_links.target_ref IS
    'Identifier of an object owned by another service. Not a foreign key — the object is not in '
    'this database — so the owning service validates it on read.';
