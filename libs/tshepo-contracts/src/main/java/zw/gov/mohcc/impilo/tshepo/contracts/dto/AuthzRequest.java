package zw.gov.mohcc.impilo.tshepo.contracts.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Authorization request sent to TSHEPO ext_authz (HTTP or gRPC).
 * Constructed from Envoy check request headers + derived path analysis.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AuthzRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotBlank String actorType,
        @NotBlank String purposeOfUse,
        @NotBlank String deviceFingerprint,
        @NotNull UUID correlationId,
        UUID facilityId,
        UUID workspaceId,
        String shiftId,
        @NotBlank String method,
        @NotBlank String path,
        String resourceType,
        String resourceId,
        String accessMode,
        Map<String, String> additionalHeaders
) {}
