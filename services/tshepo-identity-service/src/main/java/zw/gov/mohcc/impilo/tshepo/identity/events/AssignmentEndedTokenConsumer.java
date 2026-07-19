package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.repository.ScopedTokenRepository;

import java.time.Instant;
import java.util.Locale;

/**
 * Facility-scoped work-token teardown (W5, D-P7): when Vashandi reports an
 * assignment ending (expiry sweep, transfer, revocation), the person's ACTIVE
 * WORK_CONTEXT tokens bound to THAT facility are revoked — a lapsed locum
 * window closes the live ward session without touching the provider's
 * sessions at other facilities. Companion to
 * {@link ProviderRevocationTokenConsumer} (which handles the all-facility
 * licence-level revocations). Events without a person anchor or facility are
 * skipped — never revoke on a guess.
 */
@Component
public class AssignmentEndedTokenConsumer {

    private static final Logger log = LoggerFactory.getLogger(AssignmentEndedTokenConsumer.class);

    private final ObjectMapper objectMapper;
    private final ScopedTokenRepository scopedTokenRepository;

    public AssignmentEndedTokenConsumer(ObjectMapper objectMapper,
                                        ScopedTokenRepository scopedTokenRepository) {
        this.objectMapper = objectMapper;
        this.scopedTokenRepository = scopedTokenRepository;
    }

    @KafkaListener(
            topics = {"impilo.vashandi.assignment", "vashandi.assignment"},
            groupId = "tshepo-identity-assignment-teardown")
    @Transactional
    public void onAssignmentEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(message);
            String eventType = textOrEmpty(root, "event_type");
            JsonNode payload = root.path("payload");
            if (!payload.isObject()) {
                payload = root;
            }

            String eventLower = eventType.toLowerCase(Locale.ROOT);
            String status = textOrEmpty(payload, "status").toLowerCase(Locale.ROOT);
            boolean ended = eventLower.contains(".assignment.ended")
                    || eventLower.contains(".assignment.revoked")
                    || status.equals("ended") || status.equals("revoked") || status.equals("expired");
            if (!ended) {
                return;
            }

            String actorHealthId = textOrNull(payload, "impiloHealthId");
            String facilityId = textOrNull(payload, "facilityId");
            if (actorHealthId == null || facilityId == null) {
                log.info("assignment end without person anchor/facility — token teardown skipped: assignmentId={}",
                        textOrNull(payload, "assignmentId"));
                return;
            }

            int revoked = scopedTokenRepository.revokeWorkContextForActorAtFacility(
                    actorHealthId, facilityId, Instant.now());
            if (revoked > 0) {
                log.warn("assignment ended: revoked {} WORK_CONTEXT token(s) for actor={} facility={}",
                        revoked, actorHealthId, facilityId);
            }
        } catch (Exception e) {
            log.error("Failed to process vashandi assignment event for token teardown: {}", e.getMessage(), e);
        }
    }

    private static String textOrEmpty(JsonNode node, String field) {
        String t = textOrNull(node, field);
        return t == null ? "" : t;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String v = node.get(field).asText();
        return v.isBlank() ? null : v;
    }
}
