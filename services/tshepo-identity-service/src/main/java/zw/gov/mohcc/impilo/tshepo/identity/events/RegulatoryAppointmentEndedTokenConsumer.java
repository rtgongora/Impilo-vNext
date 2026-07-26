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
 * Organisation-scoped work-token teardown (RB-1).
 *
 * <p><b>The gap this closes.</b> {@code RegulatoryAppointmentService.end()} has always published
 * an {@code ended} event to {@code impilo.org-registry.events}. Nothing consumed it — no
 * {@code @KafkaListener} in any service referenced the topic in code or configuration, and the
 * live broker had no consumer group on it. Ending or revoking a regulator's appointment therefore
 * revoked no session: the holder kept working until their token expired on its own.</p>
 *
 * <p>Companion to {@link AssignmentEndedTokenConsumer}, which does the same job for
 * facility-scoped clinical assignments. The org-scoped session (ROM-W2) carries no facility, so
 * that consumer's query can never reach these tokens — the two are disjoint by construction, and
 * revocation is scoped to the one organisation so a registrar appointed at two councils keeps the
 * session the event did not concern.</p>
 *
 * <p><b>Deployment invariant.</b> org-registry only publishes when
 * {@code impilo.orgregistry.kafka-events-enabled=true} ({@code ORG_REGISTRY_KAFKA_EVENTS_ENABLED}).
 * When it is false — the code default — {@code OrgRegistryOutboxNoKafkaDrainer} marks outbox rows
 * published and discards them, so appointment endings are silently swallowed and this consumer
 * never fires. That failure is indistinguishable from "nobody's appointment ended", so it is
 * asserted in the acceptance pack rather than left to be noticed.</p>
 */
@Component
public class RegulatoryAppointmentEndedTokenConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(RegulatoryAppointmentEndedTokenConsumer.class);

    private final ObjectMapper objectMapper;
    private final ScopedTokenRepository scopedTokenRepository;

    public RegulatoryAppointmentEndedTokenConsumer(ObjectMapper objectMapper,
                                                   ScopedTokenRepository scopedTokenRepository) {
        this.objectMapper = objectMapper;
        this.scopedTokenRepository = scopedTokenRepository;
    }

    @KafkaListener(
            topics = {"impilo.org-registry.events", "org-registry.events"},
            groupId = "tshepo-identity-regulatory-appointment-teardown")
    @Transactional
    public void onOrgRegistryEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(message);

            // OrgRegistryOutboxPublisher writes camelCase envelope fields (eventType /
            // aggregateType) — NOT the snake_case shape vashandi's publisher uses. Reading the
            // wrong key here would compile, run, and silently never match.
            String aggregateType = textOrEmpty(root, "aggregateType");
            String eventType = textOrEmpty(root, "eventType").toLowerCase(Locale.ROOT);
            if (!"REGULATORY_APPOINTMENT".equalsIgnoreCase(aggregateType)) {
                return;
            }

            JsonNode payload = root.path("payload");
            if (!payload.isObject()) {
                payload = root;
            }
            String status = textOrEmpty(payload, "status").toLowerCase(Locale.ROOT);
            boolean ended = eventType.contains("ended")
                    || status.equals("ended") || status.equals("revoked") || status.equals("suspended");
            if (!ended) {
                return;
            }

            String actorHealthId = textOrNull(payload, "personHealthId");
            String organisationId = textOrNull(payload, "organizationId");
            if (actorHealthId == null || organisationId == null) {
                // Never revoke on a guess: without both anchors we cannot tell whose session, at
                // which regulator, this concerns.
                log.info("regulatory appointment end without person/organisation anchor — teardown skipped: appointmentId={}",
                        textOrNull(payload, "appointmentId"));
                return;
            }

            int revoked = scopedTokenRepository.revokeWorkContextForActorAtOrganisation(
                    actorHealthId, organisationId, Instant.now());
            if (revoked > 0) {
                log.warn("regulatory appointment {} ({}): revoked {} WORK_CONTEXT token(s) for actor={} org={}",
                        status.isEmpty() ? "ended" : status,
                        textOrEmpty(payload, "endReason").isEmpty() ? "ENDED" : textOrEmpty(payload, "endReason"),
                        revoked, actorHealthId, organisationId);
            }
        } catch (Exception e) {
            log.error("Failed to process org-registry appointment event for token teardown: {}",
                    e.getMessage(), e);
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
