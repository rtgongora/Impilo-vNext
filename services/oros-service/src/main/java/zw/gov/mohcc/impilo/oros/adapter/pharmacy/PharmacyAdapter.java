package zw.gov.mohcc.impilo.oros.adapter.pharmacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.oros.persistence.entity.CapabilityEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderEntity;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;
import zw.gov.mohcc.impilo.oros.persistence.repository.CapabilityRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.OrderItemRepository;
import zw.gov.mohcc.impilo.oros.persistence.repository.RoutingRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapter for communicating with external pharmacy systems.
 *
 * <p>Handles outbound pharmacy order dispatch and inbound dispense completion
 * processing. The adapter resolves the pharmacy endpoint from the capability
 * configuration and communicates via REST/JSON with graceful degradation.</p>
 *
 * <h3>Outbound Operations</h3>
 * <ul>
 *   <li>{@link #sendPharmacyOrder} -- POST pharmacy order to external pharmacy endpoint</li>
 * </ul>
 *
 * <h3>Inbound Operations (called by event consumer)</h3>
 * <ul>
 *   <li>{@link #receiveDispenseComplete} -- process pharmacy dispense completion payload</li>
 * </ul>
 */
@Service
public class PharmacyAdapter {

    private static final Logger log = LoggerFactory.getLogger(PharmacyAdapter.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CapabilityRepository capabilityRepository;
    private final OrderItemRepository orderItemRepository;
    private final RoutingRepository routingRepository;

    /**
     * Constructs the PharmacyAdapter with all required dependencies.
     *
     * @param objectMapper         Jackson mapper for payload serialization
     * @param capabilityRepository repository for endpoint resolution
     * @param orderItemRepository  repository for order item lookups
     * @param routingRepository    repository for route status updates
     */
    public PharmacyAdapter(ObjectMapper objectMapper,
                           CapabilityRepository capabilityRepository,
                           OrderItemRepository orderItemRepository,
                           RoutingRepository routingRepository) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
        this.capabilityRepository = capabilityRepository;
        this.orderItemRepository = orderItemRepository;
        this.routingRepository = routingRepository;
    }

    /**
     * Send a pharmacy order to the external pharmacy system.
     *
     * <p>Constructs a JSON payload containing the order details with medication-specific
     * fields (drug codes, quantities, instructions from items), resolves the pharmacy
     * endpoint from capability configuration, and POSTs the payload. On success, sets
     * the route's external reference from the pharmacy response (prescription ID).
     * On failure, logs the error and re-throws for the dispatcher to handle.</p>
     *
     * @param order the pharmacy order entity to send
     * @param route the routing entity for tracking dispatch status
     * @throws RuntimeException if the pharmacy endpoint is not configured or the request fails
     */
    public void sendPharmacyOrder(OrderEntity order, RoutingEntity route) {
        String endpoint = resolveEndpoint(order);
        if (endpoint == null) {
            throw new RuntimeException("No pharmacy endpoint configured for facility "
                    + order.getFacilityId() + " and tenant " + order.getTenantId());
        }

        try {
            Map<String, Object> payload = buildPharmacyPayload(order);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Oros-Order-Id", order.getOrderId());
            headers.set("X-Tenant-Id", order.getTenantId().toString());

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint + "/api/v1/prescriptions", request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String prescriptionRef = extractPrescriptionRef(response.getBody());
                if (prescriptionRef != null) {
                    route.setExternalRef(prescriptionRef);
                    routingRepository.save(route);
                }
            }

            log.info("Pharmacy order {} sent to {} : status={}",
                    order.getOrderId(), endpoint, response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Failed to send pharmacy order {} to {}: {}",
                    order.getOrderId(), endpoint, e.getMessage());
            throw new RuntimeException("Pharmacy dispatch failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending pharmacy order {} to external system: {}",
                    order.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("Pharmacy dispatch error: " + e.getMessage(), e);
        }
    }

    /**
     * Process an inbound pharmacy dispense completion payload.
     *
     * <p>Parses the dispense completion notification from the pharmacy and logs it.
     * Actual processing is handled by the {@link zw.gov.mohcc.impilo.oros.events.OrosEventConsumer}.</p>
     *
     * @param payload the raw JSON payload from the pharmacy system
     */
    public void receiveDispenseComplete(String payload) {
        log.info("Received pharmacy dispense completion: {}",
                payload != null && payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Resolves the pharmacy endpoint from capability configuration.
     */
    private String resolveEndpoint(OrderEntity order) {
        Optional<CapabilityEntity> cap = capabilityRepository
                .findByTenantIdAndFacilityIdAndOrderType(
                        order.getTenantId(), order.getFacilityId(), order.getOrderType());

        if (cap.isPresent() && cap.get().getExternalEndpoint() != null) {
            return cap.get().getExternalEndpoint();
        }

        return capabilityRepository.findByTenantIdAndOrderType(order.getTenantId(), order.getOrderType())
                .stream()
                .filter(c -> c.getExternalEndpoint() != null)
                .map(CapabilityEntity::getExternalEndpoint)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds the pharmacy order payload for the external system.
     */
    private Map<String, Object> buildPharmacyPayload(OrderEntity order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("tenantId", order.getTenantId().toString());
        payload.put("facilityId", order.getFacilityId().toString());
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("priority", order.getPriority().name());
        payload.put("placedBy", order.getPlacedBy());
        payload.put("encounterRef", order.getEncounterRef());
        payload.put("clinicalNotes", order.getClinicalNotes());
        payload.put("items", orderItemRepository.findByOrderId(order.getOrderId()));
        return payload;
    }

    /**
     * Extracts the prescription reference from a pharmacy response body.
     */
    private String extractPrescriptionRef(String responseBody) {
        try {
            var node = objectMapper.readTree(responseBody);
            var refNode = node.get("prescriptionId");
            if (refNode == null) {
                refNode = node.get("dispenseRef");
            }
            if (refNode == null) {
                refNode = node.get("id");
            }
            return refNode != null ? refNode.asText() : null;
        } catch (Exception e) {
            log.debug("Could not extract prescription ref from pharmacy response: {}", e.getMessage());
            return null;
        }
    }
}
