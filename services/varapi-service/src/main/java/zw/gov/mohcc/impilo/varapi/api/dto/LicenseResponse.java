package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.time.LocalDate;

public record LicenseResponse(
        Long id,
        String providerPublicId,
        Long councilId,
        String councilName,
        String licenseType,
        String licenseNumber,
        String status,
        LocalDate validFrom,
        LocalDate validTo,
        String conditions,
        String issuedBy,
        Instant issuedAt,
        Instant suspendedAt,
        String suspensionReason,
        Instant revokedAt,
        String revocationReason,
        Integer version,
        Instant createdAt,
        Instant updatedAt
) {}
