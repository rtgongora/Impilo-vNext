package zw.gov.mohcc.impilo.live.domain;

/**
 * The vNext service that owns the end-to-end workflow a {@code LiveEvent} belongs to.
 *
 * <p>Doctrine: Impilo Live owns the live interaction capability only. The owning service remains
 * responsible for the workflow, documentation and source-of-truth records:
 * <ul>
 *   <li>{@link #TELEMEDICINE} owns clinical care (encounter, orders, prescriptions, referrals).</li>
 *   <li>{@link #FUNDO} owns learning (registration, course linkage, CPD points, certificates).</li>
 *   <li>{@link #PUBLIC_HEALTH} owns campaigns and health messaging.</li>
 *   <li>{@link #CITIZEN_ENGAGEMENT} owns citizen-facing public communication.</li>
 *   <li>{@link #ENTERPRISE} owns internal/operational coordination.</li>
 *   <li>{@link #STANDALONE_IMPILO_LIVE} is the only owner permitted to operate without an external
 *       owning entity, and never for clinical sessions.</li>
 * </ul>
 */
public enum OwningService {

    TELEMEDICINE,
    FUNDO,
    PUBLIC_HEALTH,
    CITIZEN_ENGAGEMENT,
    ENTERPRISE,
    STANDALONE_IMPILO_LIVE;

    public static OwningService fromString(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OwningService.valueOf(value.trim().toUpperCase().replace('-', '_').replace(' ', '_'));
    }

    /** Whether an owning entity id (appointment, course, campaign, etc.) is mandatory for this owner. */
    public boolean requiresOwningEntity() {
        return this != STANDALONE_IMPILO_LIVE;
    }

    public String legacyOrganiserType() {
        return switch (this) {
            case TELEMEDICINE -> "TELEMEDICINE";
            case FUNDO -> "FUNDO";
            case PUBLIC_HEALTH -> "PUBLIC_HEALTH";
            case CITIZEN_ENGAGEMENT -> "CITIZEN_ENGAGEMENT";
            case ENTERPRISE -> "ENTERPRISE";
            case STANDALONE_IMPILO_LIVE -> "IMPILO_LIVE";
        };
    }
}
