package zw.gov.mohcc.impilo.companion.trust;

/**
 * Categorisation of who/what is originating a request. Carried in the
 * {@code X-Request-Source} header and recorded in every audit event.
 *
 * <p>The Health OS audit trail must distinguish between these origination
 * classes — see {@code HEALTH_OS_EXTENSIBILITY_DOCTRINE.md §3}.</p>
 */
public enum RequestSource {
    /** Human user clicked/typed something in a UI. */
    HUMAN,
    /** Internal system action with no human in the loop (e.g. reconciliation). */
    SYSTEM,
    /** Scheduled job (cron / Quartz / Spring @Scheduled). */
    SCHEDULED_JOB,
    /** Background worker / queue consumer (Kafka, Redis etc). */
    BACKGROUND_WORKER,
    /** Event consumer reacting to a domain event. */
    EVENT_CONSUMER,
    /** AI Skill / Nompilo automated action under governance. */
    AI_ASSISTED,
    /** External application acting via an approved Integration Contract. */
    EXTERNAL_APP;

    public static RequestSource parse(String raw) {
        if (raw == null || raw.isBlank()) return HUMAN;
        try {
            return RequestSource.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return SYSTEM;
        }
    }
}
