-- mushex_claims.status is VARCHAR (Java enum persisted as string) — no PostgreSQL enum to alter.
-- Store coverage-service / eligibility correlation for audit and reconciliation.

ALTER TABLE mushex_claims
    ADD COLUMN coverage_eligibility_ref VARCHAR(255);

ALTER TABLE mushex_payment_intents
    ADD COLUMN credential_verification_ref VARCHAR(255);
