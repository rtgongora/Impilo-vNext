package zw.gov.mohcc.impilo.clinical.events;

import java.util.Map;

/**
 * Maps internal {@code event_type} values stored in {@code clinical.event_outbox} to Kafka topic names
 * (see {@code docs/contracts/kafka-clinical-guidance-events.md}).
 */
public final class ClinicalKafkaTopics {

    private static final Map<String, String> TYPE_TO_TOPIC = Map.ofEntries(
            Map.entry("KNOWLEDGE_VERSION_PUBLISHED", "impilo.clinical.knowledge.version.published"),
            Map.entry("RULE_PUBLISHED", "impilo.clinical.rule.published"),
            Map.entry("GUIDANCE_RECOMMENDATION_GENERATED", "impilo.clinical.guidance.recommendation.generated"),
            Map.entry("GUIDANCE_ALERT_FIRED", "impilo.clinical.guidance.alert.fired"),
            Map.entry("GUIDANCE_OVERRIDE_RECORDED", "impilo.clinical.guidance.override.recorded"),
            Map.entry("PATHWAY_SESSION_COMPLETED", "impilo.clinical.pathway.session.completed"),
            Map.entry("CITIZEN_NUDGE_GENERATED", "impilo.clinical.citizen.nudge.generated"),
            Map.entry("KNOWLEDGE_ITEM_APPROVED", "impilo.clinical.knowledge.version.published"),
            // brief.md §19. BUTANO archives these as FHIR DetectedIssue so a duplicate anticoagulant
            // or an unsafe combination is visible to the next facility, not only to the clinician who
            // happened to open the multimorbidity view. Routed in the same change as the emitter: an
            // event type absent from this map falls to the impilo.clinical.events catch-all, which
            // publishes successfully and reaches nobody.
            Map.entry("MULTIMORBIDITY_ISSUE_DETECTED", "impilo.clinical.multimorbidity.issue.detected")
    );

    private ClinicalKafkaTopics() {
    }

    public static String topicForEventType(String eventType) {
        if (eventType == null) {
            return "impilo.clinical.events";
        }
        return TYPE_TO_TOPIC.getOrDefault(eventType, "impilo.clinical.events");
    }
}
