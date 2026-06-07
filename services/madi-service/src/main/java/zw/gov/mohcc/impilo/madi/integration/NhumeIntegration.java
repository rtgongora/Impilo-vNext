package zw.gov.mohcc.impilo.madi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NhumeIntegration {

    private static final Logger log = LoggerFactory.getLogger(NhumeIntegration.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public NhumeIntegration(RestTemplate restTemplate,
                            @Value("${madi.integration.nhume.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Creates a cold-chain NHUME delivery for an approved emergency blood redistribution.
     */
    public Optional<UUID> createBloodRedistributionDelivery(
            UUID redistributionId,
            UUID sourceFacilityId,
            UUID targetFacilityId,
            String bloodGroup,
            String componentType,
            int unitsAllocated,
            String requestedBy,
            String reason) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("delivery_type", "BLOOD_PRODUCT_TRANSFER");
            payload.put("priority", "EMERGENCY");
            payload.put("request_source", "madi-central-bank");
            payload.put("requesting_actor_id", requestedBy != null ? requestedBy : "madi-service");
            payload.put("requesting_actor_type", "SYSTEM");
            payload.put("requesting_facility_id", sourceFacilityId.toString());
            payload.put("origin", Map.of(
                    "kind", "FACILITY",
                    "ref", sourceFacilityId.toString(),
                    "label", "Source blood bank"));
            payload.put("destination", Map.of(
                    "kind", "FACILITY",
                    "ref", targetFacilityId.toString(),
                    "label", "Emergency receiving facility"));
            payload.put("recipient", Map.of(
                    "kind", "FACILITY",
                    "ref", targetFacilityId.toString(),
                    "name", "Receiving blood bank"));
            payload.put("items", List.of(Map.of(
                    "item_kind", "BLOOD_COMPONENT",
                    "item_ref", redistributionId.toString(),
                    "description", bloodGroup + " " + componentType,
                    "quantity", unitsAllocated,
                    "unit_of_measure", "UNIT",
                    "cold_chain", true,
                    "controlled", true)));
            payload.put("clinical_context_ref", redistributionId.toString());
            payload.put("programme_context_ref", "MADI_EMERGENCY_REDISTRIBUTION");
            payload.put("cold_chain_required", true);
            payload.put("controlled_item", true);
            payload.put("chain_of_custody_required", true);
            payload.put("biohazard", false);
            payload.put("notes", reason);
            payload.put("submit_immediately", true);
            payload.put("metadata", Map.of(
                    "madi_redistribution_id", redistributionId.toString(),
                    "blood_group", bloodGroup,
                    "component_type", componentType,
                    "units_allocated", unitsAllocated));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(
                    baseUrl + "/internal/v1/nhume/deliveries",
                    new HttpEntity<>(payload, headers),
                    Map.class);
            if (response == null) {
                return Optional.empty();
            }
            Object deliveryId = response.get("delivery_id");
            if (deliveryId == null) {
                return Optional.empty();
            }
            return Optional.of(UUID.fromString(deliveryId.toString()));
        } catch (Exception e) {
            log.warn("NHUME blood redistribution handoff failed for {}: {}", redistributionId, e.getMessage());
            return Optional.empty();
        }
    }

    public void notifyAdverseEvent(String reactionId, String reactionType, String severity) {
        try {
            HttpHeaders headers = buildTrustHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> payload = Map.of(
                    "reactionId", reactionId,
                    "reactionType", reactionType,
                    "severity", severity,
                    "source", "madi-service");
            restTemplate.postForEntity(baseUrl + "/internal/v1/reporting/adverse-events",
                    new HttpEntity<>(payload, headers), Void.class);
        } catch (RestClientException e) {
            log.warn("NHUME reporting unavailable for reaction {}: {}", reactionId, e.getMessage());
        }
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
        } catch (IllegalStateException ignored) {
            // graceful in dev
        }
        return headers;
    }
}
