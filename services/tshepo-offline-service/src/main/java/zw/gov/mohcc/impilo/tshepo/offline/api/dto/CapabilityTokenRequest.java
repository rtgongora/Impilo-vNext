package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Request to issue a new offline capability token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CapabilityTokenRequest(
        @NotNull UUID tenantId,
        @NotBlank String actorId,
        @NotNull UUID facilityId,
        @NotBlank String deviceFingerprint,
        /** Requested capabilities. If null, defaults to allowed-offline-actions from config. */
        List<String> requestedCapabilities,
        /** Requested TTL in hours. If null, defaults to default-capability-ttl-hours. */
        Integer ttlHours
) {}
