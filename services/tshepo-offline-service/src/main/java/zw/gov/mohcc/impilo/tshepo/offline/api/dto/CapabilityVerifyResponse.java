package zw.gov.mohcc.impilo.tshepo.offline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response from verifying an offline capability token.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CapabilityVerifyResponse(
        boolean valid,
        UUID tokenId,
        String actorId,
        UUID facilityId,
        List<String> capabilities,
        Instant expiresAt,
        String reason
) {
    public static CapabilityVerifyResponse valid(UUID tokenId, String actorId, UUID facilityId,
                                                  List<String> capabilities, Instant expiresAt) {
        return new CapabilityVerifyResponse(true, tokenId, actorId, facilityId, capabilities, expiresAt, null);
    }

    public static CapabilityVerifyResponse invalid(String reason) {
        return new CapabilityVerifyResponse(false, null, null, null, null, null, reason);
    }
}
