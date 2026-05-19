package zw.gov.mohcc.impilo.mushex.service;

/**
 * Thrown by {@link PaymentIntentService#getIntent(String)} when the requested intent
 * does not exist. Mapped to HTTP 404 with stable error code {@code INTENT_NOT_FOUND}
 * by {@link zw.gov.mohcc.impilo.mushex.api.PaymentIntentController}.
 *
 * <p>Extends {@link IllegalArgumentException} so that existing callers that catch
 * {@code IllegalArgumentException} keep working unchanged (backward-compatible).
 */
public class IntentNotFoundException extends IllegalArgumentException {

    public static final String CODE = "INTENT_NOT_FOUND";

    private final String intentId;

    public IntentNotFoundException(String intentId) {
        super("Payment intent not found: " + intentId);
        this.intentId = intentId;
    }

    public String getIntentId() {
        return intentId;
    }
}
