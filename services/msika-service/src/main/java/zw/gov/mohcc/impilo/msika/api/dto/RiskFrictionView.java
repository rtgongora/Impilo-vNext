package zw.gov.mohcc.impilo.msika.api.dto;

/**
 * Governed risk-class → friction mapping row (V004). Consumed by msika-flow
 * for order-time friction (max_qty_per_order) and by publish gates here.
 */
public record RiskFrictionView(
        String riskClassification,
        String frictionLevel,
        boolean requiresProvider,
        boolean requiresFacility,
        Integer maxQtyPerOrder,
        /** V009: recognised licensed provider may self-satisfy prescriber standing for own purchase. */
        boolean professionalSelfAuthorizable,
        String notes
) {}
