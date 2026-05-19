package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

/**
 * Canonical payment port. Adapters: MobileMoneyAdapter, BankAdapter,
 * CardPaymentAdapter, ClaimsSwitchAdapter, NativeMusheXAdapter.
 */
public interface PaymentIntegrationPort extends Adapter {

    OperationResult<PaymentIntentAck>  createIntent(PaymentRequest request, OperationContext ctx);
    OperationResult<PaymentStatus>     getStatus(String intentId, OperationContext ctx);
    OperationResult<Void>              refund(String intentId, double amount, String reason, OperationContext ctx);

    record PaymentRequest(String currency, double amount, String payerReference, String purpose, String idempotencyKey) {}
    record PaymentIntentAck(String intentId, String providerReference, String status, String checkoutUrl) {}
    record PaymentStatus(String intentId, String providerReference, String status, String failureReason) {}
}
