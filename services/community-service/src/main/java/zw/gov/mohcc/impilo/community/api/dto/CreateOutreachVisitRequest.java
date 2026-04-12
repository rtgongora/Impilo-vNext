package zw.gov.mohcc.impilo.community.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CreateOutreachVisitRequest(
        @NotNull UUID tenantId,
        @NotNull UUID unitId,
        @NotBlank String patientCpid,
        @NotBlank String providerId,
        UUID journeyId,
        UUID encounterId,
        @NotBlank String visitType,
        BigDecimal locationLat,
        BigDecimal locationLon,
        OffsetDateTime scheduledAt
) {}
