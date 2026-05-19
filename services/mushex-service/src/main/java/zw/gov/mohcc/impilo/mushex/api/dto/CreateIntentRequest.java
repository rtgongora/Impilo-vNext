package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /mushex/v1/payment-intents}.
 *
 * <p>The first seven fields form the original contract and remain required as before.
 * The last three fields are optional G-4 rail-selection inputs added per
 * {@code docs/design/g4-rail-selection-policy.md}. The {@link #CreateIntentRequest(String, String, BigDecimal, String, String, String, String)}
 * secondary constructor preserves source compatibility for existing callers that
 * construct the record positionally without rail-selection inputs (e.g. integration tests).
 */
public record CreateIntentRequest(
        @NotNull String sourceType,
        @NotBlank String sourceId,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        String currency,
        String facilityId,
        @NotBlank String idempotencyKey,
        String metadata,
        String preferredRailAdapter,
        Boolean allowFallback,
        Boolean directGatewayAllowed
) {
    /**
     * Backward-compatible constructor for callers that do not supply rail-selection
     * inputs. Equivalent to passing {@code null} for the three G-4 fields.
     */
    public CreateIntentRequest(String sourceType,
                               String sourceId,
                               BigDecimal amount,
                               String currency,
                               String facilityId,
                               String idempotencyKey,
                               String metadata) {
        this(sourceType, sourceId, amount, currency, facilityId, idempotencyKey, metadata,
                null, null, null);
    }
}
