ALTER TABLE mushex_payment_intents
    ADD COLUMN intent_type VARCHAR(50);

COMMENT ON COLUMN mushex_payment_intents.intent_type IS
    'PaymentIntentType: classifies pre-service, deposit, authorisation, PoC, final, partial, claim, wallet, remittance, refund, reversal, deferred promise.';
