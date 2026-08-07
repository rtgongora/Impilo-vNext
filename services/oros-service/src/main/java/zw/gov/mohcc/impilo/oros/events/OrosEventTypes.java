package zw.gov.mohcc.impilo.oros.events;

import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

/**
 * Maps oros's stored event types onto the canonical v1.1 form.
 *
 * <p>The outbox stores legacy types such as {@code ORDER_PLACED}, {@code RESULT_CRITICAL} and
 * {@code ORDER_ACKNOWLEDGED_BY_LAB}. The v1.1 envelope requires
 * {@code impilo.{service}.{entity}.{action}.v{N}}, and CompanionOutboxPublisher puts
 * {@code row.eventType()} straight into the envelope — so passing the stored value through
 * unchanged emits an envelope that fails the event-type contract.</p>
 *
 * <p>Nothing is invented. The entity is the aggregate type; the action is what remains of the
 * stored event type once the aggregate prefix is removed, so {@code ORDER_ACKNOWLEDGED_BY_LAB}
 * keeps its full meaning as {@code acknowledged_by_lab} rather than being truncated to the
 * last segment.</p>
 */
public final class OrosEventTypes {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("oros");

    private OrosEventTypes() {
    }

    public static String canonical(String aggregateType, String legacyEventType) {
        return REGISTRY.eventType(entity(aggregateType), action(aggregateType, legacyEventType));
    }

    private static String entity(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "event";
        }
        return aggregateType.toLowerCase();
    }

    private static String action(String aggregateType, String legacyEventType) {
        if (legacyEventType == null || legacyEventType.isBlank()) {
            return "changed";
        }
        String action = legacyEventType.toLowerCase().replace('.', '_');
        if (aggregateType != null && !aggregateType.isBlank()) {
            String prefix = aggregateType.toLowerCase() + "_";
            if (action.startsWith(prefix) && action.length() > prefix.length()) {
                action = action.substring(prefix.length());
            }
        }
        return action;
    }
}
