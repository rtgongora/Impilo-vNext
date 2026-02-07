package zw.gov.mohcc.impilo.pharmacy.api.dto;

/**
 * Request DTO for resolving a stock reconciliation entry.
 *
 * @param notes operational notes explaining the resolution
 */
public record ResolveRequest(
        String notes
) {}
