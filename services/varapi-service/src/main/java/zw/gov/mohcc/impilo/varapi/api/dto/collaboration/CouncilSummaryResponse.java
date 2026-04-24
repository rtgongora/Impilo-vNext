package zw.gov.mohcc.impilo.varapi.api.dto.collaboration;

/**
 * Minimal council projection for external collaboration UIs (council picker).
 */
public record CouncilSummaryResponse(
        long id,
        String councilCode,
        String name,
        String councilType,
        String registrationNumberPattern) {}
