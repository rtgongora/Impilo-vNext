package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record CertificateResponse(
        Long id,
        String licenseType,
        String licenseNumber,
        String status,
        LocalDate validFrom,
        LocalDate validTo,
        String councilName,
        Instant issuedAt
) {}
