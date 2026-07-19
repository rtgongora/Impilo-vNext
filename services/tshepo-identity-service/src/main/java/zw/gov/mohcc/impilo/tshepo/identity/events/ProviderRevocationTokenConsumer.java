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

/**
 * Scoped-token teardown on provider privilege revocation (D-P3, the PJ15
 * follow-up): when VARAPI reports a provider lifecycle transition out of an
 * active practising state (lapsed / suspended / retired / revoked), every
 * ACTIVE scoped token held by that person is revoked so a live work session
 * cannot outlive the revocation.
 *
 * <p>Companion to the tshepo-authz {@code PrivilegeRevocationConsumer} (which
 * handles next-call PDP denial via cache eviction); this consumer handles the
 * already-issued-token side. Detection logic mirrors that consumer. Events are
 * keyed by the claimed person anchor ({@code impiloHealthId} in the payload,
 * emitted by the varapi licence sweep since W1); events without a person
 * anchor are logged and skipped — the PDP path still denies next-call, and we
 * must never revoke an unrelated actor on a guess.</p>
 */
@Component
public class ProviderRevocationTokenConsumer {

    private static final Logger log = LoggerFactory.getLogger(ProviderRevocationTokenConsumer.class);

    private final ObjectMapper objectMapper;
    private final ScopedTokenRepository scopedTokenRepository;

    public ProviderRevocationTokenConsumer(ObjectMapper objectMapper,
                                           ScopedTokenRepository scopedTokenRepository) {
        this.objectMapper = objectMapper;
        this.scopedTokenRepository = scopedTokenRepository;
    }

    @KafkaListener(
            topics = {"varapi.provider", "varapi.credential", "impilo.varapi.provider"},
            groupId = "tshepo-identity-token-teardown")
    @Transactional
    public void onVarapiEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(message);

            String eventType = textOrEmpty(root, "event_type");
            JsonNode payload = root.path("payload");
            boolean v11Envelope = root.hasNonNull("event_type") && payload.isObject();
            if (!v11Envelope) {
                payload = root;
                eventType = "";
            }

            if (!isRevocation(eventType.toLowerCase(java.util.Locale.ROOT),
                    textOrNull(payload, "newStatus"),
                    textOrNull(payload, "status"),
                    textOrNull(payload, "newLifecycle"))) {
                return;
            }

            String actorHealthId = firstNonBlank(
                    textOrNull(payload, "impiloHealthId"),
                    textOrNull(payload, "impilo_health_id"));
            if (actorHealthId == null) {
                log.info("provider revocation without person anchor — token teardown skipped "
                        + "(PDP next-call denial still applies): providerPublicId={}",
                        textOrNull(payload, "providerPublicId"));
                return;
            }

            int revoked = scopedTokenRepository.revokeAllForActor(actorHealthId, Instant.now());
            if (revoked > 0) {
                log.warn("provider privilege revocation: revoked {} active scoped token(s) for actor={} providerPublicId={}",
                        revoked, actorHealthId, textOrNull(payload, "providerPublicId"));
            }
        } catch (Exception e) {
            log.error("Failed to process VARAPI event for token teardown: {}", e.getMessage(), e);
        }
    }

    /** Mirrors tshepo-authz PrivilegeRevocationConsumer.isRevocation (PJ15 semantics). */
    static boolean isRevocation(String eventLower, String newStatus, String status, String newLifecycle) {
        if (eventLower.contains("revoked") || eventLower.contains("suspended")
                || eventLower.contains("deactivated") || eventLower.contains("lapsed")
                || eventLower.contains("retired") || eventLower.contains("expired")) {
            return true;
        }
        if (newLifecycle != null && !isActiveLifecycle(newLifecycle)) {
            return true;
        }
        if (eventLower.contains("status_changed")) {
            if (newStatus != null) {
                return !isActive(newStatus);
            }
            return status != null && !isActive(status);
        }
        if (newStatus != null && !isActive(newStatus)) {
            return true;
        }
        return status != null && !isActive(status);
    }

    private static boolean isActiveLifecycle(String lifecycle) {
        String l = lifecycle.trim().toUpperCase(java.util.Locale.ROOT);
        return l.equals("LICENCED_ACTIVE") || l.equals("LICENSED_ACTIVE")
                || l.equals("LICENCE_DUE_FOR_RENEWAL") || l.equals("LICENSE_DUE_FOR_RENEWAL");
    }

    private static boolean isActive(String s) {
        return "ACTIVE".equalsIgnoreCase(s.trim());
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

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
