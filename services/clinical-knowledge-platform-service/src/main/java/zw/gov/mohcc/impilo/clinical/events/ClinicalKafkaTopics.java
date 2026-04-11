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
            Map.entry("KNOWLEDGE_ITEM_APPROVED", "impilo.clinical.knowledge.version.published")
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
