package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record ProviderIdentifierDto(
        Long id,
        String identifierSystem,
        String identifierType,
        String identifierValue,
        Long issuingCouncilId,
        String status,
        String verificationState,
        boolean primary,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        LocalDate issuedDate,
        LocalDate expiryDate
) {}
