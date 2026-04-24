package zw.gov.mohcc.impilo.varapi.api.dto;

import java.util.UUID;

public record ProviderSummary(
        String providerPublicId,
        UUID impiloHealthId,
        String title,
        String givenName,
        String familyName,
        String profession,
        String cadre,
        String status,
        String practiceNumber
) {}
