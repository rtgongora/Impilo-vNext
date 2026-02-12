package zw.gov.mohcc.impilo.companion.context;

import java.security.Principal;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable request context extracted from v1.1 mandatory headers.
 *
 * Populated by {@link zw.gov.mohcc.impilo.companion.filter.V11HeaderFilter},
 * accessible via {@link RequestContextHolder}.
 */
public record RequestContext(
        String tenantId,
        String podId,
        String requestId,
        String correlationId,
        String authToken,
        Principal principal,
        Long clientTimeoutMs
) {

    /**
     * Create a context from raw header values.
     * Generates request/correlation IDs if absent.
     */
    public static RequestContext of(String tenantId, String podId,
                                    String requestId, String correlationId,
                                    String authToken, Principal principal,
                                    Long clientTimeoutMs) {
        return new RequestContext(
                tenantId,
                podId,
                requestId != null && !requestId.isBlank() ? requestId : UUID.randomUUID().toString(),
                correlationId != null && !correlationId.isBlank() ? correlationId : UUID.randomUUID().toString(),
                authToken,
                principal,
                clientTimeoutMs
        );
    }

    /** True if the pod is the national spine. */
    public boolean isNationalPod() {
        return "national".equalsIgnoreCase(podId);
    }

    /** Optional timeout from X-Client-Timeout-MS. */
    public Optional<Long> timeout() {
        return Optional.ofNullable(clientTimeoutMs);
    }
}
