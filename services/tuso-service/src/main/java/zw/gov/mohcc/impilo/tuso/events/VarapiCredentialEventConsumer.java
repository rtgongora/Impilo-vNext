package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tuso.core.PicNominationService;

import java.util.UUID;

/**
 * Consumes VARAPI credential-change signals (licence/certificate suspension,
 * revocation, expiry, renewal) and flags any ACTIVE practitioner-in-charge
 * assignments for audited human review (HPA-2017-V1: a credential lapse never
 * silently erases an assignment or closes a facility).
 *
 * <p>Topic: {@code varapi.credential} — the legacy route for VARAPI's
 * CREDENTIAL aggregate. Event: {@code varapi.provider.eligibility.changed}.</p>
 */
@Component
public class VarapiCredentialEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(VarapiCredentialEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final PicNominationService picNominationService;

    public VarapiCredentialEventConsumer(ObjectMapper objectMapper,
                                         PicNominationService picNominationService) {
        this.objectMapper = objectMapper;
        this.picNominationService = picNominationService;
    }

    @KafkaListener(topics = "varapi.credential", groupId = "tuso-service")
    @Transactional
    public void onCredentialEvent(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode payload = root.path("payload");
            if (payload.isMissingNode() || payload.isNull()) {
                payload = root;
            }
            if (payload.isTextual()) {
                payload = objectMapper.readTree(payload.asText());
            }
            String eventType = root.path("eventType").asText(root.path("event_type").asText(""));
            if (!eventType.isBlank() && !"varapi.provider.eligibility.changed".equals(eventType)) {
                return; // other credential events are not PIC-relevant yet
            }
            String providerPublicId = payload.path("providerPublicId").asText(null);
            if (providerPublicId == null || providerPublicId.isBlank()) {
                log.debug("Credential event without providerPublicId — skipping");
                return;
            }
            String changeAxis = payload.path("changeAxis").asText("CREDENTIAL");
            String newStatus = payload.path("newStatus").asText("UNKNOWN");
            String reason = payload.path("reason").asText("");
            UUID tenantId = null;
            String tenantText = root.path("tenantId").asText(payload.path("tenantId").asText(null));
            if (tenantText != null && !tenantText.isBlank()) {
                try {
                    tenantId = UUID.fromString(tenantText);
                } catch (IllegalArgumentException ignored) {
                    // legacy events may carry non-UUID tenants — review flag still applies
                }
            }
            int flagged = picNominationService.flagAssignmentsForReview(providerPublicId,
                    changeAxis + " -> " + newStatus + (reason.isBlank() ? "" : (": " + reason)), tenantId);
            if (flagged > 0) {
                log.info("Credential change for provider {} flagged {} PIC assignment(s) for review",
                        providerPublicId, flagged);
            }
        } catch (Exception e) {
            // Never crash the consumer/partition on one bad message.
            log.error("Failed to process varapi credential event: {}", e.getMessage(), e);
        }
    }
}
