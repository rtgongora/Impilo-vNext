package zw.gov.mohcc.impilo.pharmacy.api.dto;

/**
 * Request DTO for placing a dispense order on backorder.
 *
 * @param notes operational notes explaining the backorder reason
 */
public record BackorderRequest(
        String notes
) {}
