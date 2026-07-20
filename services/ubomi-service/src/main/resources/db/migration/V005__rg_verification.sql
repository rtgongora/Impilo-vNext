-- W1c — Registrar-General (RG / CRVS) outbound verification ledger.
--
-- One row per outbound RG interaction (National-ID / passport / birth-reg
-- verification, or a birth/death registration confirmation). This is the state
-- store behind the fail-open-for-care posture and the reconcile loop:
--   RG_PENDING     — attempt parked (RG unavailable / not yet driven). Never
--                    blocks care; the native proofing ladder stays available.
--   RG_VERIFIED    — RG returned a match; civil_identity_token captured.
--   RG_NO_MATCH    — RG returned an authoritative non-match.
--   RG_CONFIRMED   — RG confirmed a birth/death registration.
--   RG_DISPUTED    — identity contested at the RG.
--   RG_UNAVAILABLE — RG_PENDING expired past ubomi.rg.pending-ttl-hours
--                    (honest terminal status; never a fabricated verification).
--
-- Response-minimisation is enforced by what this table CAN hold: an opaque
-- civil_identity_token + status + a one-way reference_hash of the supplied
-- identifier. The full RG record (National ID, names, address, ...) is NEVER
-- persisted here and never leaves the RegistrarGeneralClient boundary.

CREATE TABLE ubomi.rg_verification (
    id                   BIGSERIAL PRIMARY KEY,
    tenant_id            UUID          NOT NULL,
    -- Internal person anchor (Health ID) the assurance attaches to — NOT civil PII.
    subject_ref          VARCHAR(128)  NOT NULL,
    verification_type    VARCHAR(40)   NOT NULL,   -- NATIONAL_ID | PASSPORT | BIRTH_REGISTRATION | DEATH_REGISTRATION
    -- SHA-256 hex of the supplied identifier; the raw identifier is never stored.
    reference_hash       VARCHAR(64),
    status               VARCHAR(24)   NOT NULL DEFAULT 'RG_PENDING',
    -- Opaque token returned by the RG on a match; the only durable RG artefact.
    civil_identity_token VARCHAR(512),
    -- Optional link back to the source event_outbox row (birth/death package push).
    source_outbox_id     BIGINT,
    -- Stable idempotency/business key; tolerates at-least-once redelivery.
    business_key         VARCHAR(256)  NOT NULL,
    attempt_count        INTEGER       NOT NULL DEFAULT 0,
    last_error           TEXT,
    last_attempt_at      TIMESTAMPTZ,
    verified_at          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_ubomi_rg_verification_business_key UNIQUE (tenant_id, business_key)
);

-- Reconcile-loop driver: find parked attempts oldest-first.
CREATE INDEX idx_ubomi_rg_verification_pending
    ON ubomi.rg_verification (created_at)
    WHERE status = 'RG_PENDING';

CREATE INDEX idx_ubomi_rg_verification_subject
    ON ubomi.rg_verification (tenant_id, subject_ref);
