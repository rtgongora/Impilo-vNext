package zw.gov.mohcc.impilo.tshepo.consent.events;

import zw.gov.mohcc.impilo.sharedkernel.events.EventTopicRegistry;

/**
 * Maps consent's stored event types onto the canonical v1.1 form.
 *
 * <p>The outbox stores legacy types such as {@code CONSENT_GRANTED} and
 * {@code SHARE_LINK_REVOKED}. The v1.1 envelope requires
 * {@code impilo.{service}.{entity}.{action}.v{N}}, and CompanionOutboxPublisher puts
 * {@code row.eventType()} straight into the envelope — so passing the stored value through
 * unchanged emits an envelope that fails the event-type contract.</p>
 *
 * <p>Nothing is invented: the entity comes from the aggregate type and the action from the
 * trailing segment already present in the stored value.</p>
 */
public final class ConsentEventTypes {

    private static final EventTopicRegistry REGISTRY = new EventTopicRegistry("consent");

    private ConsentEventTypes() {
    }

    public static String canonical(String aggregateType, String legacyEventType) {
        return REGISTRY.eventType(entity(aggregateType), action(legacyEventType));
    }

    private static String entity(String aggregateType) {
        if (aggregateType == null) {
            return "consent";
        }
        return switch (aggregateType) {
            case "ConsentDirective" -> "consent_directive";
            case "ShareLink" -> "share_link";
            default -> aggregateType.toLowerCase();
        };
    }

    /** {@code CONSENT_GRANTED} → granted; {@code consent.revoked} → revoked. */
    private static String action(String legacyEventType) {
        if (legacyEventType == null || legacyEventType.isBlank()) {
            return "changed";
        }
        int lastSeparator = Math.max(legacyEventType.lastIndexOf('.'), legacyEventType.lastIndexOf('_'));
        String tail = lastSeparator >= 0 && lastSeparator < legacyEventType.length() - 1
                ? legacyEventType.substring(lastSeparator + 1)
                : legacyEventType;
        return tail.toLowerCase();
    }
}
