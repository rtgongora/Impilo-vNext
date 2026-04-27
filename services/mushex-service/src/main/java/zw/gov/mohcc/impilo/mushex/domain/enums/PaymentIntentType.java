package zw.gov.mohcc.impilo.mushex.domain.enums;

/**
 * Classifies the payment rail obligation carried by a payment intent (pre-service through deferred / claims).
 */
public enum PaymentIntentType {
    PRE_SERVICE_PAYMENT,
    DEPOSIT,
    AUTHORISATION_HOLD,
    POINT_OF_CARE_PAYMENT,
    FINAL_PAYMENT,
    PARTIAL_PAYMENT,
    CO_PAYMENT,
    CLAIM_SUBMISSION,
    WALLET_DEBIT,
    REMITTANCE,
    REFUND,
    REVERSAL,
    DEFERRED_PAYMENT_PROMISE
}
