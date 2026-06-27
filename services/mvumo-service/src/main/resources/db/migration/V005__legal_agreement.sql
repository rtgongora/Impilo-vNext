-- Mvumo — Legal/platform agreement acceptance (Privacy Policy, Terms of Use).
-- DISTINCT from clinical consent: the subject is an actor (Health ID), NOT a Patient/ ref,
-- and acceptance is NEVER materialised as a FHIR Consent directive in tshepo-consent.
-- It reuses Mvumo's lifecycle + outbox so legal acceptance has a real, audited home.
SET search_path TO public;

CREATE TABLE mvumo.legal_agreement (
    id                 UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id          UUID         NOT NULL,
    subject_actor_id   VARCHAR(255) NOT NULL,                 -- Health ID / actor (not a Patient/ ref)
    document_type      VARCHAR(32)  NOT NULL,                 -- PRIVACY_POLICY | TERMS_OF_USE
    version            VARCHAR(64)  NOT NULL,                 -- policy document version accepted
    state              VARCHAR(32)  NOT NULL,                 -- GRANTED | WITHDRAWN | SUPERSEDED
    channel            VARCHAR(32)  NOT NULL DEFAULT 'WEB',   -- WEB/APP/SMS/USSD/KIOSK/VOICE/PAPER/OPERATOR
    assurance          VARCHAR(40),                           -- ConsentAssuranceLevel name (optional)
    actor_ref          VARCHAR(255),                          -- who recorded it (self/operator/guardian)
    actor_relationship VARCHAR(64),                           -- self | operator | guardian | ...
    device_metadata    JSONB,
    proof_ref          VARCHAR(255),
    accepted_at        TIMESTAMPTZ,
    withdrawn_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Lookups for "current acceptance for this subject + document type" and history.
CREATE INDEX idx_mvumo_legal_agreement_subject
    ON mvumo.legal_agreement (tenant_id, subject_actor_id, document_type);
