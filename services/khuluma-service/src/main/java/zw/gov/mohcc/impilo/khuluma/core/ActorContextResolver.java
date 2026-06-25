package zw.gov.mohcc.impilo.khuluma.core;

import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.UUID;

/**
 * Builds an {@link ActorContext} for the current request by combining the v1.1 companion
 * context (tenant/pod/correlation, populated by the companion header filter) with the trust
 * context (actor identity, populated by {@code TrustContextFilter}). Rejects requests that
 * carry no resolvable tenant or actor — Khuluma never acts without a governed identity.
 */
@Component
public class ActorContextResolver {

    public ActorContext resolve() {
        TrustContext trust = TrustContextHolder.get();
        RequestContext request = RequestContextHolder.get();

        UUID tenantId = resolveTenant(trust, request);
        if (tenantId == null) {
            throw new IllegalStateException("Missing X-Tenant-ID — request carries no tenant");
        }
        String actorId = trust != null ? trust.actorId() : null;
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalStateException("Missing X-Actor-ID — request carries no acting identity");
        }
        String actorType = trust != null ? trust.actorType() : null;
        String podId = request != null ? request.podId() : null;
        String correlationId = request != null ? request.correlationId()
                : (trust != null && trust.correlationId() != null ? trust.correlationId().toString() : null);

        return new ActorContext(tenantId, podId, actorId, actorType, correlationId);
    }

    private static UUID resolveTenant(TrustContext trust, RequestContext request) {
        if (trust != null && trust.tenantId() != null) {
            return trust.tenantId();
        }
        if (request != null && request.tenantId() != null && !request.tenantId().isBlank()) {
            try {
                return UUID.fromString(request.tenantId());
            } catch (IllegalArgumentException ignored) {
                return UUID.nameUUIDFromBytes(request.tenantId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        return null;
    }
}
