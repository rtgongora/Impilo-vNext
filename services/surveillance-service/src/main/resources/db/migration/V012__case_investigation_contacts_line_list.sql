-- Case investigation updates, contact tracing, and the line list.
--
-- WHY THIS EXISTS
-- The surveillance case panel has always offered three controls: "Save Update", "Link Contacts" and
-- "Generate Line List". None of them did anything — no handler on the button, and nowhere for the
-- data to go. The panel collects a case classification, a patient outcome and a lab result, and not
-- one of those fields existed on cases or investigations. There was no contact entity at all.
--
-- That is the most consequential shape of a dead control: a public-health officer in an outbreak
-- clicks "Link Contacts", sees no error, and reasonably concludes tracing has started. Nobody is
-- being traced.
--
-- WHAT IS MODELLED, AND WHY THIS SHAPE
--
-- Updates are a HISTORY, not a column. A case classification moves suspected → probable → confirmed
-- as evidence arrives, and outbreak work needs to know when it moved and on what basis — a
-- retrospective asks "when did we know", which a single overwritten column cannot answer. The
-- current values are also denormalised onto the case, because a line list reads current state for
-- hundreds of cases at once and should not fold a history per row.
--
-- Contacts are people, and this table deliberately does NOT require them to be registered patients.
-- Contact tracing happens before identity is established — a name and a phone number at a household
-- visit is the normal case, and demanding a CPID would make the feature unusable exactly when it
-- matters. `contact_cpid` is nullable and links the moment identity is known.

CREATE TABLE IF NOT EXISTS surv.case_updates (
    id              BIGSERIAL   PRIMARY KEY,
    tenant_id       UUID        NOT NULL,
    case_id         BIGINT      NOT NULL REFERENCES surv.cases(id) ON DELETE CASCADE,
    classification  VARCHAR(32),
    outcome         VARCHAR(32),
    lab_result      VARCHAR(32),
    notes           TEXT,
    recorded_by     VARCHAR(128) NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_case_updates_case
    ON surv.case_updates (tenant_id, case_id, recorded_at DESC);

-- Current state, for line lists. Written from the latest update.
ALTER TABLE surv.cases
    ADD COLUMN IF NOT EXISTS classification VARCHAR(32),
    ADD COLUMN IF NOT EXISTS outcome        VARCHAR(32),
    ADD COLUMN IF NOT EXISTS lab_result     VARCHAR(32);

COMMENT ON COLUMN surv.cases.classification IS
    'Current case classification (SUSPECTED/PROBABLE/CONFIRMED/DISCARDED). History in surv.case_updates.';

CREATE TABLE IF NOT EXISTS surv.case_contacts (
    id               BIGSERIAL   PRIMARY KEY,
    tenant_id        UUID        NOT NULL,
    case_id          BIGINT      NOT NULL REFERENCES surv.cases(id) ON DELETE CASCADE,
    -- Nullable on purpose: a contact is traced before they are identified. See header.
    contact_cpid     VARCHAR(64),
    contact_name     VARCHAR(256) NOT NULL,
    contact_phone    VARCHAR(64),
    relationship     VARCHAR(64),
    exposure_date    DATE,
    -- Tracing state, not clinical state. A contact who develops symptoms becomes a CASE; this
    -- column never carries a diagnosis.
    trace_status     VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    last_contacted_at TIMESTAMPTZ,
    notes            TEXT,
    created_by       VARCHAR(128) NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_case_contacts_case
    ON surv.case_contacts (tenant_id, case_id);
-- Outstanding tracing work across an outbreak — the query a response team runs every morning.
CREATE INDEX IF NOT EXISTS idx_case_contacts_pending
    ON surv.case_contacts (tenant_id, trace_status)
    WHERE trace_status <> 'COMPLETED';
