package zw.gov.mohcc.impilo.mushex.api.dto;

/**
 * Request body for {@code POST /mushex/v1/payment-intents/{intentId}/attempts}.
 *
 * <p>Both fields are optional. When {@code idempotencyKey} is non-null the
 * attempt creation is idempotent against {@code (intentId, idempotencyKey)}.
 * The {@code reason} field is informational only and is logged with the attempt
 * (not persisted as a column — recorded in the enforcement-metadata audit trail).
 *
 * <p>Callers may alternatively pass {@code X-Idempotency-Key} as a request header;
 * the body field, when present and non-blank, takes precedence.
 */
public record CreateAttemptRequest(
        String idempotencyKey,
        String reason
) {
    public CreateAttemptRequest() {
        this(null, null);
    }
}
