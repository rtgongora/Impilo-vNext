package zw.gov.mohcc.impilo.khuluma.core;

import java.util.UUID;

/**
 * The acting trust context for a Khuluma command — tenant, acting actor, pod and
 * correlation. Passed explicitly into core services so they stay unit-testable without
 * thread-local plumbing; controllers build it from the request via {@link ActorContextResolver}.
 */
public record ActorContext(
        UUID tenantId,
        String podId,
        String actorId,
        String actorType,
        String correlationId
) {
    public ActorContext {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
        if (actorType == null || actorType.isBlank()) {
            actorType = "PROVIDER";
        }
        if (podId == null || podId.isBlank()) {
            podId = "national-spine";
        }
    }
}
