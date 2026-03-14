package zw.gov.mohcc.impilo.sharedkernel.events;

import java.util.Objects;

/**
 * Canonical event topic naming and event type construction for Impilo v1.1 events.
 *
 * <h3>Naming conventions</h3>
 * <ul>
 *   <li>Event type: {@code impilo.{service}.{entity}.{action}.v{version}}</li>
 *   <li>V1.1 topic: {@code impilo.{service}.{domain}}</li>
 *   <li>Snapshot event type: {@code impilo.{service}.snapshot.{entity}.v{version}}</li>
 *   <li>Snapshot topic: {@code impilo.{service}.snapshots}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * EventTopicRegistry registry = new EventTopicRegistry("vito");
 * String eventType = registry.eventType("client", "created");      // "impilo.vito.client.created.v1"
 * String topic     = registry.v11Topic("identity");                 // "impilo.vito.identity"
 * String snapType  = registry.snapshotEventType("client");          // "impilo.vito.snapshot.client.v1"
 * String snapTopic = registry.snapshotTopic();                      // "impilo.vito.snapshots"
 * }</pre>
 */
public final class EventTopicRegistry {

    private static final String PREFIX = "impilo";
    private static final int DEFAULT_VERSION = 1;

    private final String serviceId;

    /**
     * @param serviceId canonical service identifier (e.g. "vito", "tuso", "msika")
     */
    public EventTopicRegistry(String serviceId) {
        this.serviceId = Objects.requireNonNull(serviceId, "serviceId is required")
                .toLowerCase().trim();
    }

    /** The canonical service identifier. */
    public String serviceId() {
        return serviceId;
    }

    // ── Event type construction ──

    /**
     * Build a v1.1 event type: {@code impilo.{service}.{entity}.{action}.v{version}}
     */
    public String eventType(String entity, String action, int version) {
        return PREFIX + "." + serviceId + "." + normalize(entity) + "." + normalize(action)
                + ".v" + version;
    }

    /**
     * Build a v1.1 event type with default version 1.
     */
    public String eventType(String entity, String action) {
        return eventType(entity, action, DEFAULT_VERSION);
    }

    /**
     * Build a snapshot event type: {@code impilo.{service}.snapshot.{entity}.v{version}}
     */
    public String snapshotEventType(String entity, int version) {
        return PREFIX + "." + serviceId + ".snapshot." + normalize(entity) + ".v" + version;
    }

    /**
     * Build a snapshot event type with default version 1.
     */
    public String snapshotEventType(String entity) {
        return snapshotEventType(entity, DEFAULT_VERSION);
    }

    // ── Topic construction ──

    /**
     * Build a v1.1 Kafka topic: {@code impilo.{service}.{domain}}
     */
    public String v11Topic(String domain) {
        return PREFIX + "." + serviceId + "." + normalize(domain);
    }

    /**
     * Build the snapshot Kafka topic: {@code impilo.{service}.snapshots}
     */
    public String snapshotTopic() {
        return PREFIX + "." + serviceId + ".snapshots";
    }

    /**
     * Validate that an event type follows the convention.
     * @return true if the event type matches {@code impilo.{service}.*.*.v\d+}
     */
    public boolean isValidEventType(String eventType) {
        if (eventType == null) return false;
        String prefix = PREFIX + "." + serviceId + ".";
        if (!eventType.startsWith(prefix)) return false;
        String suffix = eventType.substring(prefix.length());
        // Must have at least entity.action.vN
        return suffix.matches("[a-z][a-z0-9._]*\\.[a-z][a-z0-9_]*\\.v\\d+");
    }

    /**
     * Extract the entity name from a valid event type.
     * For {@code impilo.vito.client.created.v1} returns {@code "client"}.
     * For snapshot types {@code impilo.vito.snapshot.client.v1} returns {@code "client"}.
     */
    public String extractEntity(String eventType) {
        if (eventType == null) return null;
        String prefix = PREFIX + "." + serviceId + ".";
        if (!eventType.startsWith(prefix)) return null;
        String suffix = eventType.substring(prefix.length());
        if (suffix.startsWith("snapshot.")) {
            suffix = suffix.substring("snapshot.".length());
        }
        int dot = suffix.indexOf('.');
        return dot > 0 ? suffix.substring(0, dot) : null;
    }

    /**
     * Extract the action from a valid event type.
     * For {@code impilo.vito.client.created.v1} returns {@code "created"}.
     */
    public String extractAction(String eventType) {
        if (eventType == null) return null;
        // Remove version suffix first
        int lastDot = eventType.lastIndexOf('.');
        if (lastDot <= 0) return null;
        String withoutVersion = eventType.substring(0, lastDot);
        int prevDot = withoutVersion.lastIndexOf('.');
        return prevDot >= 0 ? withoutVersion.substring(prevDot + 1) : null;
    }

    private static String normalize(String s) {
        return s.toLowerCase().replace("-", "_").replace(" ", "_").trim();
    }
}
