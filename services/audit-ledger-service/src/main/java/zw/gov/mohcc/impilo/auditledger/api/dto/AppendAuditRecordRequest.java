package zw.gov.mohcc.impilo.auditledger.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

public record AppendAuditRecordRequest(
        @NotNull UUID correlationId,
        @NotBlank String actorId,
        @NotBlank String actorType,
        @NotBlank String action,
        @NotBlank String resourceType,
        String resourceId,
        String outcome,
        Map<String, Object> detail) {}
