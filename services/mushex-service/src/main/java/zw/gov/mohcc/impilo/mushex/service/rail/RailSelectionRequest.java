package zw.gov.mohcc.impilo.mushex.service.rail;

import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Inputs to {@link RailSelectionPolicy#select(RailSelectionRequest)}. All fields are
 * deliberately nullable / tri-state so the policy can distinguish "caller did not specify"
 * from "caller explicitly chose false".
 *
 * <p>See {@code docs/design/g4-rail-selection-policy.md} §5.3.
 */
public record RailSelectionRequest(
        String preferredRailAdapter,
        Boolean allowFallback,
        Boolean directGatewayAllowed,
        SourceType sourceType,
        BigDecimal amount,
        String currency,
        UUID facilityId,
        boolean impiloSimulation
) {
    /**
     * Convenience factory for callers that have not yet collected the optional
     * caller-supplied fields (e.g. the legacy 7-arg path on
     * {@code PaymentIntentService.createIntent}).
     */
    public static RailSelectionRequest empty(SourceType sourceType,
                                             BigDecimal amount,
                                             String currency,
                                             UUID facilityId,
                                             boolean impiloSimulation) {
        return new RailSelectionRequest(null, null, null, sourceType, amount, currency,
                facilityId, impiloSimulation);
    }
}
