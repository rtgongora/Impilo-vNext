package zw.gov.mohcc.impilo.tshepo.identity.events;

import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

/**
 * Maps tshepo-identity's stored event types onto the canonical v1.1 form.
 *
 * <p>The outbox stores legacy types such as {@code MAPPING_CREATED},
 * {@code WORK_CONTEXT_TOKEN_ISSUED} and {@code OCPID_CREATED}. The v1.1 envelope requires
 * {@code impilo.{service}.{entity}.{action}.v{N}}, and CompanionOutboxPublisher puts
 * {@code row.eventType()} straight into the envelope — so passing the stored value through
 * unchanged emits an envelope that fails the event-type contract.</p>
 *
 * <p>Nothing is invented. The entity is the aggregate type; the action is what remains of the
 * stored event type once the aggregate prefix is removed, so {@code WORK_CONTEXT_TOKEN_ISSUED}
 * keeps its meaning instead of collapsing to its last segment.</p>
 */
public final class IdentityEventTypes {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("identity");

    private IdentityEventTypes() {
    }

    public static String canonical(String aggregateType, String legacyEventType) {
        return REGISTRY.eventType(entity(aggregateType), action(aggregateType, legacyEventType));
    }

    private static String entity(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "event";
        }
        return switch (aggregateType) {
            case "ProvisionalCpid" -> "provisional_cpid";
            case "IdMapping" -> "id_mapping";
            case "ScopedToken" -> "scoped_token";
            case "MosipLink" -> "mosip_link";
            default -> aggregateType.toLowerCase();
        };
    }

    private static String action(String aggregateType, String legacyEventType) {
        if (legacyEventType == null || legacyEventType.isBlank()) {
            return "changed";
        }
        String action = legacyEventType.toLowerCase().replace('.', '_');
        String entity = entity(aggregateType);
        if (action.startsWith(entity + "_") && action.length() > entity.length() + 1) {
            action = action.substring(entity.length() + 1);
        }
        return action;
    }
}
