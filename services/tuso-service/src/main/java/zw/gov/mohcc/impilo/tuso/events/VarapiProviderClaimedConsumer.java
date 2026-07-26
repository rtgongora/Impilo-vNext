package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.core.PicNominationService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.PicNominationEntity;

import java.util.List;
import java.util.UUID;

/**
 * Unparks PIC nominations when the nominated practitioner claims their profile (HAR W3).
 *
 * <p>The HPA seed left 6,180 nominations at {@code ELIGIBILITY_CHECKED}: VARAPI assessed each
 * preloaded, unclaimed provider as ineligible, which was the right answer at the time — a person
 * who has never used the platform cannot accept a statutory appointment. But nothing ever asked
 * again, so a practitioner who claims their profile would still find their facility's nomination
 * frozen on a verdict about a person who no longer exists in that state.</p>
 *
 * <p>This consumer only triggers a fresh eligibility assessment. It cannot create an assignment,
 * and the furthest a nomination can move is to {@code PRACTITIONER_ACCEPTANCE_PENDING} — the
 * practitioner still accepts and the regulator still reviews. Authority is never inferred from a
 * claim.</p>
 */
@Component
public class VarapiProviderClaimedConsumer {

    private static final Logger log = LoggerFactory.getLogger(VarapiProviderClaimedConsumer.class);

    private final ObjectMapper objectMapper;
    private final PicNominationService picNominationService;

    public VarapiProviderClaimedConsumer(ObjectMapper objectMapper,
                                         PicNominationService picNominationService) {
        this.objectMapper = objectMapper;
        this.picNominationService = picNominationService;
    }

    @KafkaListener(topics = "varapi.provider.claimed", groupId = "tuso-service")
    public void onProviderClaimed(String message) {
        try {
            JsonNode node = unwrap(objectMapper.readTree(message));
            String providerPublicId = text(node, "providerPublicId");
            if (providerPublicId == null) {
                return;
            }
            // The tenant comes from the nominations themselves, never from the event: this consumer
            // must not be able to act in a tenant it was merely told about.
            List<PicNominationEntity> parked = picNominationService
                    .listForPractitioner(providerPublicId).stream()
                    .filter(n -> "ELIGIBILITY_CHECKED".equals(n.getState()))
                    .toList();
            if (parked.isEmpty()) {
                return;
            }
            for (UUID tenantId : parked.stream().map(PicNominationEntity::getTenantId).distinct().toList()) {
                TrustContext ctx = systemContext(tenantId);
                TrustContextHolder.set(ctx);
                try {
                    int advanced = picNominationService.reassessAfterClaim(ctx, providerPublicId);
                    log.info("PIC unpark after claim: provider={} tenant={} advanced={}",
                            providerPublicId, tenantId, advanced);
                } finally {
                    TrustContextHolder.clear();
                }
            }
        } catch (Exception e) {
            log.error("Failed to process varapi.provider.claimed event: {}", e.getMessage(), e);
        }
    }

    private static TrustContext systemContext(UUID tenantId) {
        return new TrustContext(tenantId, "SYSTEM:VARAPI-CLAIM-KAFKA", "SERVICE", "REGULATORY_OPERATIONS",
                "kafka-consumer", null, null, null, null, AccessMode.INTERNAL);
    }

    private static JsonNode unwrap(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) return root.get("payload");
        if (root.has("data") && root.get("data").isObject()) return root.get("data");
        return root;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
