package zw.gov.mohcc.impilo.varapi.api.dto;

public record ProviderSummary(
        String providerPublicId,
        String givenName,
        String familyName,
        String profession,
        String status,
        String primaryCouncilName
) {}
