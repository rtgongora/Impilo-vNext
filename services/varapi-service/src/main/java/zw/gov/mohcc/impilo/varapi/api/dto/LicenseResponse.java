package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record LicenseResponse(
        Long id,
        String providerPublicId,
        Long councilId,
        String licenseType,
        String licenseNumber,
        String status,
        LocalDate validFrom,
        LocalDate validTo,
        String conditions,
        String issuedBy,
        Instant issuedAt,
        Instant createdAt
) {}
