package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;
import java.util.Map;

public record ReconciliationCaseResponse(
        Long id,
        Long councilId,
        String sourceSystem,
        String sourceRef,
        String caseType,
        String status,
        Map<String, Object> payload,
        String notes,
        Instant createdAt
) {}
