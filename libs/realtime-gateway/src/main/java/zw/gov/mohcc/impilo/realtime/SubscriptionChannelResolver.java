package zw.gov.mohcc.impilo.realtime;

import java.util.Set;
import java.util.UUID;

/**
 * Computes which realtime channels a connecting actor may subscribe to. Implementations are
 * domain-owned (khuluma conversations/calls, PCT emergency episodes) because membership checks
 * are JPA-coupled to the owning service's tables. The gateway core only depends on this interface.
 */
public interface SubscriptionChannelResolver {

    /**
     * Default + explicitly-requested channels the actor may listen to.
     *
     * @param tenantId  trust tenant
     * @param actorId   connecting actor
     * @param requested optional CSV of extra channel names; each is admitted only after an access check
     */
    Set<String> resolveFor(UUID tenantId, String actorId, String requested);
}
