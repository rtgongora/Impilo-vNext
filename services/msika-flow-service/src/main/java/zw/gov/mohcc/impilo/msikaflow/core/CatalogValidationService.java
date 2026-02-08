package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msikaflow.domain.ActorType;
import zw.gov.mohcc.impilo.msikaflow.integration.MsikaCoreClient;

import java.util.*;

@Service
public class CatalogValidationService {

    private static final Logger log = LoggerFactory.getLogger(CatalogValidationService.class);

    private final MsikaCoreClient msikaCoreClient;
    private final ObjectMapper objectMapper;

    public CatalogValidationService(MsikaCoreClient msikaCoreClient, ObjectMapper objectMapper) {
        this.msikaCoreClient = msikaCoreClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Validates a list of cart items against MSIKA Core catalog restrictions.
     * Returns a ValidationResult with per-item results.
     */
    public ValidationResult validateCart(List<CartItem> items, String channel, ActorType actorType, String patientCpid) {
        List<ItemValidation> results = new ArrayList<>();
        boolean allValid = true;

        for (CartItem item : items) {
            ItemValidation validation = validateItem(item, channel, actorType, patientCpid);
            results.add(validation);
            if (!validation.valid()) {
                allValid = false;
            }
        }

        return new ValidationResult(allValid, results);
    }

    private ItemValidation validateItem(CartItem item, String channel, ActorType actorType, String patientCpid) {
        List<String> errors = new ArrayList<>();

        try {
            JsonNode catalogItem = msikaCoreClient.lookupItem(item.msikaCoreCode());
            if (catalogItem == null || catalogItem.isMissingNode()) {
                return new ItemValidation(item.msikaCoreCode(), false, List.of("Item not found in MSIKA Core catalog"), null);
            }

            JsonNode restrictions = catalogItem.path("restrictions");

            // Check prescription_required
            if (restrictions.path("prescription_required").asBoolean(false)) {
                if (actorType == ActorType.PATIENT && !"facility".equals(channel)) {
                    errors.add("Prescription required — must be ordered through facility or with valid Rx token");
                }
            }

            // Check controlled_item
            if (restrictions.path("controlled_item").asBoolean(false)) {
                if (actorType == ActorType.PATIENT) {
                    errors.add("Controlled item — requires provider authorization and step-up verification");
                }
            }

            // Check facility_only
            if (restrictions.path("facility_only").asBoolean(false)) {
                if (!"facility".equals(channel)) {
                    errors.add("This item can only be ordered at a facility");
                }
            }

            // Check provider_only_order
            if (restrictions.path("provider_only_order").asBoolean(false)) {
                if (actorType != ActorType.PROVIDER && actorType != ActorType.OPS) {
                    errors.add("This item can only be ordered by a provider");
                }
            }

            // Check channel restrictions
            JsonNode allowedChannels = restrictions.path("allowed_channels");
            if (allowedChannels.isArray() && allowedChannels.size() > 0) {
                boolean channelAllowed = false;
                for (JsonNode ch : allowedChannels) {
                    if (ch.asText().equalsIgnoreCase(channel)) {
                        channelAllowed = true;
                        break;
                    }
                }
                if (!channelAllowed) {
                    errors.add("Item not available on channel: " + channel);
                }
            }

            String restrictionsJson = null;
            try {
                restrictionsJson = objectMapper.writeValueAsString(restrictions);
            } catch (Exception e) {
                log.warn("Failed to serialize restrictions for {}", item.msikaCoreCode());
            }

            return new ItemValidation(item.msikaCoreCode(), errors.isEmpty(), errors, restrictionsJson);

        } catch (Exception e) {
            log.error("Error validating item {}: {}", item.msikaCoreCode(), e.getMessage());
            return new ItemValidation(item.msikaCoreCode(), false, List.of("Catalog validation error: " + e.getMessage()), null);
        }
    }

    public record CartItem(String msikaCoreCode, int qty) {}
    public record ItemValidation(String msikaCoreCode, boolean valid, List<String> errors, String restrictionsSnapshot) {}
    public record ValidationResult(boolean valid, List<ItemValidation> items) {}
}
