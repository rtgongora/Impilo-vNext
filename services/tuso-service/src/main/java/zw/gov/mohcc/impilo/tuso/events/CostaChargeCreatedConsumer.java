package zw.gov.mohcc.impilo.tuso.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tuso.core.FacilityFeeService;

/**
 * Links the COSTA charge id back onto the HPA application. COSTA mints a
 * REGULATORY_APPLICATION_FEE charge from TUSO's fee_due and publishes
 * {@code costa.charge.created}; this consumer records the charge id as the
 * application's fee_reference (the money-truth handle), ignoring every other
 * charge source on the shared topic.
 */
@Component
public class CostaChargeCreatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(CostaChargeCreatedConsumer.class);

    private final ObjectMapper objectMapper;
    private final FacilityFeeService facilityFeeService;

    public CostaChargeCreatedConsumer(ObjectMapper objectMapper, FacilityFeeService facilityFeeService) {
        this.objectMapper = objectMapper;
        this.facilityFeeService = facilityFeeService;
    }

    @KafkaListener(topics = "costa.charge.created", groupId = "tuso-service")
    public void onChargeCreated(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = root.has("payload") ? maybeParse(root.get("payload")) : root;
            String sourceType = text(node, "sourceType");
            if (!"REGULATORY_APPLICATION_FEE".equals(sourceType)) {
                return; // not an HPA fee charge
            }
            facilityFeeService.linkChargeReference(text(node, "sourceRef"), text(node, "chargeId"));
        } catch (Exception e) {
            log.error("Failed to process costa.charge.created: {}", e.getMessage(), e);
        }
    }

    private JsonNode maybeParse(JsonNode payload) throws Exception {
        if (payload.isTextual()) return objectMapper.readTree(payload.asText());
        return payload;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) return null;
        String v = node.get(field).asText();
        return v != null && !v.isBlank() ? v : null;
    }
}
