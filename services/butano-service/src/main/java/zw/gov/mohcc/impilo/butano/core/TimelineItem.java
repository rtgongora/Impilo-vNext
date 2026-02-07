package zw.gov.mohcc.impilo.butano.core;

import java.time.Instant;

/**
 * A single entry in a patient's clinical timeline.
 * Represents one FHIR resource event with enough context for display.
 */
public record TimelineItem(
        String resourceType,
        String resourceId,
        Instant date,
        String summary,
        String encounterRef
) {}
