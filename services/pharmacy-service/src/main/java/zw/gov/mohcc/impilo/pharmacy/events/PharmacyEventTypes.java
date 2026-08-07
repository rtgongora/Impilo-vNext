package zw.gov.mohcc.impilo.pharmacy.events;

import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

/**
 * Maps pharmacy's stored event types onto the canonical v1.1 form.
 *
 * <p>The outbox stores legacy types such as {@code DISPENSE_COMPLETED} and
 * {@code PICKUP_EXPIRED_RETURN}. The v1.1 envelope requires
 * {@code impilo.{service}.{entity}.{action}.v{N}}, and CompanionOutboxPublisher puts
 * {@code row.eventType()} straight into the envelope — so passing the stored value through
 * unchanged emits an envelope that fails the event-type contract.</p>
 *
 * <p>Nothing is invented. The entity is the aggregate type; the action is what remains of the
 * stored event type once the aggregate prefix is removed, so {@code PICKUP_EXPIRED_RETURN}
 * keeps its full meaning rather than being truncated to its last segment.</p>
 */
public final class PharmacyEventTypes {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("pharmacy");

    private PharmacyEventTypes() {
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
