-- Step-up gate for high-sensitivity patient share scopes (MVP: shared code / env-configured).

ALTER TABLE vito.patient_share_session
    ADD COLUMN IF NOT EXISTS step_up_verified BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN vito.patient_share_session.step_up_verified IS
    'When false, session is in AWAITING_STEP_UP until verify-step-up succeeds; required before identity completion for sensitive scopes.';
