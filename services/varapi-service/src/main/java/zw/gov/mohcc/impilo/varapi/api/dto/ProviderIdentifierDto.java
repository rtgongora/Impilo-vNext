package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record ProviderIdentifierDto(
        Long id,
        String identifierSystem,
        String identifierValue,
        Long issuingCouncilId,
        String status,
        LocalDate issuedDate,
        LocalDate expiryDate
) {}
