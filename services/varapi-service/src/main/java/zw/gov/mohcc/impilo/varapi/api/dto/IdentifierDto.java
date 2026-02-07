package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.LocalDate;

public record IdentifierDto(
        String identifierSystem,
        String identifierValue,
        String status,
        LocalDate issuedDate,
        LocalDate expiryDate
) {}
