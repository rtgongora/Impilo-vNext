package zw.gov.mohcc.impilo.oros.adapter.lims;

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
 * Adapter for communicating with external LIMS (Laboratory Information Management System).
 *
 * <p>Handles outbound order dispatch to the LIMS and inbound result/status processing.
 * The adapter resolves the LIMS endpoint from the capability configuration for the
 * order's tenant and facility. Communication uses REST/JSON with graceful degradation
 * on failure.</p>
 *
 * <h3>Outbound Operations</h3>
 * <ul>
 *   <li>{@link #sendOrder} -- POST order payload to LIMS endpoint</li>
 * </ul>
 *
 * <h3>Inbound Operations (called by event consumer)</h3>
 * <ul>
 *   <li>{@link #receiveStatusUpdate} -- process LIMS status change payload</li>
 *   <li>{@link #receiveResult} -- process LIMS result payload</li>
 * </ul>
 */
@Service
public class LimsAdapter {

    private static final Logger log = LoggerFactory.getLogger(LimsAdapter.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CapabilityRepository capabilityRepository;
    private final OrderItemRepository orderItemRepository;
    private final RoutingRepository routingRepository;

    /**
     * Constructs the LimsAdapter with all required dependencies.
     *
     * @param objectMapper         Jackson mapper for payload serialization
     * @param capabilityRepository repository for endpoint resolution
     * @param orderItemRepository  repository for order item lookups
     * @param routingRepository    repository for route status updates
     */
    public LimsAdapter(ObjectMapper objectMapper,
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
     * Send a lab order to the external LIMS.
     *
     * <p>Constructs a JSON payload containing the order details and items,
     * resolves the LIMS endpoint from the capability configuration, and
     * POSTs the payload. On success, sets the route's external reference
     * from the LIMS response. On failure, logs the error and re-throws
     * to let the dispatcher mark the route as FAILED.</p>
     *
     * @param order the order entity to send
     * @param route the routing entity for tracking dispatch status
     * @throws RuntimeException if the LIMS endpoint is not configured or the request fails
     */
    public void sendOrder(OrderEntity order, RoutingEntity route) {
        String endpoint = resolveEndpoint(order);
        if (endpoint == null) {
            throw new RuntimeException("No LIMS endpoint configured for facility "
                    + order.getFacilityId() + " and tenant " + order.getTenantId());
        }

        try {
            Map<String, Object> payload = buildOrderPayload(order);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Oros-Order-Id", order.getOrderId());
            headers.set("X-Tenant-Id", order.getTenantId().toString());

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint + "/api/v1/orders", request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // Extract external reference from LIMS response
                String externalRef = extractExternalRef(response.getBody());
                if (externalRef != null) {
                    route.setExternalRef(externalRef);
                    routingRepository.save(route);
                }
            }

            log.info("Order {} sent to LIMS at {}: status={}", order.getOrderId(),
                    endpoint, response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Failed to send order {} to LIMS at {}: {}",
                    order.getOrderId(), endpoint, e.getMessage());
            throw new RuntimeException("LIMS dispatch failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending order {} to LIMS: {}",
                    order.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("LIMS dispatch error: " + e.getMessage(), e);
        }
    }

    /**
     * Process an inbound LIMS status update payload.
     *
     * <p>Parses the status update from the LIMS and logs the result.
     * Actual processing is handled by the {@link zw.gov.mohcc.impilo.oros.events.OrosEventConsumer}.</p>
     *
     * @param payload the raw JSON payload from the LIMS
     */
    public void receiveStatusUpdate(String payload) {
        log.info("Received LIMS status update: {}", truncate(payload, 200));
    }

    /**
     * Process an inbound LIMS result payload.
     *
     * <p>Parses the result from the LIMS and logs the result.
     * Actual processing is handled by the {@link zw.gov.mohcc.impilo.oros.events.OrosEventConsumer}.</p>
     *
     * @param payload the raw JSON payload from the LIMS
     */
    public void receiveResult(String payload) {
        log.info("Received LIMS result: {}", truncate(payload, 200));
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Resolves the LIMS endpoint from capability configuration.
     */
    private String resolveEndpoint(OrderEntity order) {
        Optional<CapabilityEntity> cap = capabilityRepository
                .findByTenantIdAndFacilityIdAndOrderType(
                        order.getTenantId(), order.getFacilityId(), order.getOrderType());

        if (cap.isPresent() && cap.get().getExternalEndpoint() != null) {
            return cap.get().getExternalEndpoint();
        }

        // Fall back to tenant-level capability
        return capabilityRepository.findByTenantIdAndOrderType(order.getTenantId(), order.getOrderType())
                .stream()
                .filter(c -> c.getExternalEndpoint() != null)
                .map(CapabilityEntity::getExternalEndpoint)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds the order payload map for the LIMS.
     */
    private Map<String, Object> buildOrderPayload(OrderEntity order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("tenantId", order.getTenantId().toString());
        payload.put("facilityId", order.getFacilityId().toString());
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("orderType", order.getOrderType().name());
        payload.put("priority", order.getPriority().name());
        payload.put("placedBy", order.getPlacedBy());
        payload.put("ziboOrderCode", order.getZiboOrderCode());
        payload.put("clinicalNotes", order.getClinicalNotes());
        payload.put("items", orderItemRepository.findByOrderId(order.getOrderId()));
        return payload;
    }

    /**
     * Extracts the external reference from a LIMS response body.
     */
    private String extractExternalRef(String responseBody) {
        try {
            var node = objectMapper.readTree(responseBody);
            var refNode = node.get("externalRef");
            if (refNode == null) {
                refNode = node.get("accessionNumber");
            }
            if (refNode == null) {
                refNode = node.get("id");
            }
            return refNode != null ? refNode.asText() : null;
        } catch (Exception e) {
            log.debug("Could not extract external ref from LIMS response: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Truncates a string for log output.
     */
    private String truncate(String s, int maxLen) {
        if (s == null) return "null";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
