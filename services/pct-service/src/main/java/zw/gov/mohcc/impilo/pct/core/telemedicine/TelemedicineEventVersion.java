package zw.gov.mohcc.impilo.pct.core.telemedicine;

/**
 * TM-B20 event versioning helper (pct-service side).
 *
 * <p>Stamps {@code .v1} onto the dotted lowercase {@code telemedicine.session.*} lifecycle events at
 * their outbox choke points so the contract can evolve without silently breaking consumers
 * (TM-G12 migration window). The events remain routed by the {@code startsWith("telemedicine.session.")}
 * prefix in {@code OutboxPublisher}, which a {@code .v1} suffix preserves.
 *
 * <p>Only dotted lowercase {@code telemedicine.*} names are versioned. Legacy UPPER_SNAKE events
 * ({@code TELECONSULT_COMPLETED}, {@code POOL_MATERIALIZED}, {@code QUEUE_SLA_BREACHED}) route by exact
 * match and are consumed cross-service by costing-engine / tuso; they are intentionally left unchanged.
 */
public final class TelemedicineEventVersion {

    private TelemedicineEventVersion() {
    }

    /** Append {@code .v1} to an unversioned dotted {@code telemedicine.*} event; otherwise return unchanged. */
    public static String withV1(String eventType) {
        if (eventType == null || !eventType.startsWith("telemedicine.")) {
            return eventType;
        }
        int dot = eventType.lastIndexOf('.');
        String tail = dot > 0 ? eventType.substring(dot + 1) : "";
        boolean versioned = tail.length() >= 2 && tail.charAt(0) == 'v'
                && tail.chars().skip(1).allMatch(Character::isDigit);
        return versioned ? eventType : eventType + ".v1";
    }
}
