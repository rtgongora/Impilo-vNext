package zw.gov.mohcc.impilo.varapi.api.dto;

public record ProviderSummary(
        String providerPublicId,
        String title,
        String givenName,
        String familyName,
        String profession,
        String cadre,
        String status,
        String practiceNumber
) {}
