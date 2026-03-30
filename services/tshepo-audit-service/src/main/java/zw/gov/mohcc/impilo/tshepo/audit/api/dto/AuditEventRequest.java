package zw.gov.mohcc.impilo.tshepo.audit.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Map;
import java.util.UUID;

/**
 * Inbound audit event request from other TSHEPO services or via Kafka.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuditEventRequest(
        @NotNull(message = "tenantId is required")
        UUID tenantId,

        @NotBlank(message = "eventType is required")
        @Size(max = 64)
        String eventType,

        @NotBlank(message = "actorId is required")
        @Size(max = 255)
        String actorId,

        @NotBlank(message = "actorType is required")
        @Size(max = 32)
        String actorType,

        @Size(max = 255)
        String subjectRef,

        @Size(max = 64)
        String resourceType,

        @Size(max = 255)
        String resourceId,

        @NotBlank(message = "action is required")
        @Size(max = 64)
        String action,

        @NotBlank(message = "outcome is required")
        @Size(max = 16)
        String outcome,

        @Size(max = 32)
        String purposeOfUse,

        UUID facilityId,

        @NotNull(message = "correlationId is required")
        UUID correlationId,

        @Size(max = 32)
        String policyVersion,

        Map<String, Object> detail
) {
    /**
     * Default constructor for Jackson deserialization with no-arg support.
     */
    public AuditEventRequest {
        // Record canonical constructor — no additional logic needed
    }
}
