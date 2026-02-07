package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Request to log an action performed while offline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OfflineActionRequest(
        @NotNull UUID tenantId,
        @NotNull UUID capabilityTokenId,
        @NotBlank String actorId,
        @NotBlank String actionType,
        String resourceType,
        String resourceRef,
        UUID oCpid,
        Map<String, Object> payload,
        @NotNull Instant performedAt
) {}
