package zw.gov.mohcc.impilo.experience.telemedicine;

/**
 * TM-B20 event versioning helper (experience-bff side).
 *
 * <p>The telemedicine lifecycle/communication events are migrating from bare dotted names
 * ({@code telemedicine.session.completed}) to versioned names ({@code telemedicine.session.completed.v1})
 * so the contract can evolve without silently breaking consumers (TM-G12 migration window).
 *
 * <p>Migration rule (consumer-tolerant first): consumers {@link #strip(String) strip} any trailing
 * {@code .vN} before matching, so they accept BOTH the legacy bare name (events still in flight /
 * emitted by an un-upgraded producer) and the new {@code .v1} name. Emitters {@link #withV1(String)}
 * stamp {@code .v1} at their single outbound choke point. Only dotted lowercase {@code telemedicine.*}
 * names are versioned — legacy UPPER_SNAKE events (e.g. {@code TELECONSULT_COMPLETED}) route by exact
 * match cross-service and are out of scope for this suffix scheme.
 */
public final class TelemedicineEventVersion {

    private TelemedicineEventVersion() {
    }

    /** Append {@code .v1} to an unversioned dotted {@code telemedicine.*} event; otherwise return unchanged. */
    public static String withV1(String eventType) {
        if (eventType == null || !eventType.startsWith("telemedicine.")) {
            return eventType;
        }
        if (isVersioned(eventType)) {
            return eventType;
        }
        return eventType + ".v1";
    }

    /** Remove a trailing {@code .vN} version suffix so exact-match consumers can compare on the bare name. */
    public static String strip(String eventType) {
        if (eventType == null) {
            return null;
        }
        int dot = eventType.lastIndexOf('.');
        if (dot <= 0 || dot == eventType.length() - 1) {
            return eventType;
        }
        String tail = eventType.substring(dot + 1);
        if (tail.length() >= 2 && tail.charAt(0) == 'v' && tail.chars().skip(1).allMatch(Character::isDigit)) {
            return eventType.substring(0, dot);
        }
        return eventType;
    }

    private static boolean isVersioned(String eventType) {
        return !eventType.equals(strip(eventType));
    }
}
