package zw.gov.mohcc.impilo.pct.events;

import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

/**
 * Maps PCT's stored event types onto the canonical v1.1 form.
 *
 * <p>PCT is the only converted service whose outbox holds two flavours of legacy event type:
 * UPPER_SNAKE ({@code JOURNEY_STATE_CHANGED}, {@code ENCOUNTER_STARTED}) and already-dotted
 * ({@code pct.observation.recorded}, {@code telemedicine.session.started}). Both are what
 * {@link OutboxPublisher#routeTopic(String)} switches on, so neither can be replaced in
 * place — the v1.1 envelope needs {@code impilo.{service}.{entity}.{action}.v{N}} and
 * CompanionOutboxPublisher puts {@code row.eventType()} straight into it.</p>
 *
 * <p>Nothing is invented. A dotted type already names its own entity and action, so it keeps
 * them. An UPPER_SNAKE type takes its entity from the aggregate and its action from what is
 * left once the aggregate prefix is removed, so {@code QUEUE_ITEM_TRANSFERRED} stays
 * {@code transferred} rather than collapsing to a last segment.</p>
 */
public final class PctEventTypes {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("pct");
    private static final String SERVICE_PREFIX = "pct.";

    private PctEventTypes() {
    }

    public static String canonical(String aggregateType, String legacyEventType) {
        if (legacyEventType == null || legacyEventType.isBlank()) {
            return REGISTRY.eventType(entity(aggregateType), "changed");
        }
        if (legacyEventType.indexOf('.') >= 0) {
            String dotted = fromDotted(legacyEventType);
            if (dotted != null) {
                return dotted;
            }
        }
        return REGISTRY.eventType(entity(aggregateType), snakeAction(aggregateType, legacyEventType));
    }

    /**
     * {@code pct.observation.recorded} → {@code impilo.pct.observation.recorded.v1};
     * {@code telemedicine.session.started} → {@code impilo.pct.telemedicine.session.started.v1}.
     * Returns null when the remainder cannot supply both an entity and an action, so the
     * caller falls back to the aggregate rather than emitting a malformed type.
     */
    private static String fromDotted(String legacyEventType) {
        String remainder = legacyEventType.toLowerCase();
        if (remainder.startsWith(SERVICE_PREFIX)) {
            remainder = remainder.substring(SERVICE_PREFIX.length());
        }
        int lastDot = remainder.lastIndexOf('.');
        if (lastDot <= 0 || lastDot == remainder.length() - 1) {
            return null;
        }
        String entity = remainder.substring(0, lastDot);
        String action = remainder.substring(lastDot + 1);
        return REGISTRY.eventType(entity, action);
    }

    private static String entity(String aggregateType) {
        if (aggregateType == null || aggregateType.isBlank()) {
            return "event";
        }
        return aggregateType.toLowerCase();
    }

    private static String snakeAction(String aggregateType, String legacyEventType) {
        String action = legacyEventType.toLowerCase();
        if (aggregateType != null && !aggregateType.isBlank()) {
            String prefix = aggregateType.toLowerCase() + "_";
            if (action.startsWith(prefix) && action.length() > prefix.length()) {
                action = action.substring(prefix.length());
            }
        }
        return action;
    }
}
