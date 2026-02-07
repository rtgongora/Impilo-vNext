package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        Long facilityId,
        String facilityName,
        String alertType,
        String severity,
        String title,
        String description,
        String metricType,
        BigDecimal metricValue,
        BigDecimal threshold,
        String status,
        String acknowledgedBy,
        Instant acknowledgedAt,
        Instant resolvedAt,
        Instant createdAt
) {}
