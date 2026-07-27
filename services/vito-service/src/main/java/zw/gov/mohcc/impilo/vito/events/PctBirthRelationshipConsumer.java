package zw.gov.mohcc.impilo.vito.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.vito.core.ClientIdentityOperationsService;

import java.util.UUID;

/**
 * Records the biological mother↔child birth dyad from pct's {@code pct.newborn.episode.opened} event.
 *
 * <p>This is the consumer OutboxPublisher's own routing comment anticipated ("VITO asserts the
 * mother-child relationship from it"). The event was previously stranded on the pct catch-all with no
 * route case; once W0 gave it a topic, this closes the loop. VITO owns the person↔person
 * relationship; pct owns the clinical birth record. The relationship is RECORDED from the event,
 * never inferred: no mother on the event, no dyad.
 *
 * <p>Best-effort and idempotent, the {@link NdilaLocationConsumer} discipline: cpid == health_id (the
 * identity collapse), so both cpids parse directly as health ids; a stillbirth carries no living
 * identity and is skipped; a missing party or a redelivered event records nothing. A parse or write
 * failure is logged and swallowed — a birth event must not wedge the listener, and the dyad can be
 * reconciled later, whereas a poison-pill loop would stall every subsequent birth.
 */
@Component
public class PctBirthRelationshipConsumer {

    private static final Logger log = LoggerFactory.getLogger(PctBirthRelationshipConsumer.class);

    private final ClientIdentityOperationsService identityOperations;
    private final ObjectMapper objectMapper;

    public PctBirthRelationshipConsumer(ClientIdentityOperationsService identityOperations,
                                        ObjectMapper objectMapper) {
        this.identityOperations = identityOperations;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = {"pct.newborn.episode.opened"}, groupId = "vito-pct-birth-dyad")
    public void onNewbornEpisode(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            // Tolerate an outer envelope {payload:{...}} or a flat payload.
            JsonNode p = node.has("payload") && node.get("payload").isObject() ? node.get("payload") : node;

            String outcome = text(p, "birthOutcome");
            if ("STILLBIRTH".equalsIgnoreCase(outcome)) {
                // A stillbirth mints no living identity (it is the mother's loss record, not a person);
                // there is no child health id to relate. A neonatal death WAS born alive and keeps its
                // identity, so the dyad still stands for it.
                return;
            }

            String tenantStr = text(p, "tenantId");
            String motherStr = text(p, "motherCpid");
            String childStr = text(p, "subjectCpid");
            if (isBlank(tenantStr) || isBlank(motherStr) || isBlank(childStr)) {
                // No mother on the record → no dyad to assert. Recorded, never inferred.
                return;
            }

            UUID tenantId = UUID.fromString(tenantStr);
            UUID motherHealthId = UUID.fromString(motherStr); // cpid == health_id
            UUID childHealthId = UUID.fromString(childStr);

            boolean written = identityOperations.recordBirthDyad(tenantId, motherHealthId, childHealthId);
            if (written) {
                log.info("Recorded birth dyad mother={} child={} tenant={}",
                        motherHealthId, childHealthId, tenantId);
            }
        } catch (IllegalArgumentException e) {
            // A malformed cpid/uuid on the event: log and move on, do not retry a poison pill.
            log.warn("newborn.episode.opened carried an unparseable identifier, dyad skipped: {}",
                    e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to record birth dyad from newborn.episode.opened: {}", e.getMessage());
        }
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
