package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;

public record CertificateResponse(
        Long id,
        String licenseType,
        String licenseNumber,
        String status,
        java.time.LocalDate validFrom,
        java.time.LocalDate validTo,
        String councilName,
        Instant issuedAt
) {}
