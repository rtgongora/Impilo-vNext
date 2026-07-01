-- Signatures / countersignatures and amendment audit for form responses (medico-legal).

CREATE TABLE pct_form_signature (
    signature_id            UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    response_id             UUID            NOT NULL REFERENCES pct_form_response(response_id) ON DELETE CASCADE,
    signature_kind          VARCHAR(24)     NOT NULL,   -- AUTHOR|COUNTERSIGN|SUPERVISOR
    signed_by               VARCHAR(255)    NOT NULL,
    signed_by_cadre         VARCHAR(128),
    signed_by_role          VARCHAR(128),
    step_up_ref             VARCHAR(128),
    attestation             TEXT,
    signed_at               TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_form_signature_response ON pct_form_signature (response_id);
CREATE UNIQUE INDEX uq_pct_form_signature_author
    ON pct_form_signature (response_id, signature_kind) WHERE signature_kind = 'AUTHOR';

CREATE TABLE pct_form_amendment (
    amendment_id            UUID            PRIMARY KEY,
    tenant_id               UUID            NOT NULL,
    response_id             UUID            NOT NULL REFERENCES pct_form_response(response_id) ON DELETE CASCADE,
    prior_answers           JSONB           NOT NULL,
    new_answers             JSONB           NOT NULL,
    changed_link_ids        JSONB           NOT NULL DEFAULT '[]',
    reason                  TEXT            NOT NULL,
    amended_by              VARCHAR(255)    NOT NULL,
    amended_at              TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_pct_form_amendment_response ON pct_form_amendment (response_id, amended_at DESC);

COMMENT ON TABLE pct_form_amendment IS
    'Append-only amendment log for submitted form responses (prior state retained, reason mandatory).';
