package zw.gov.mohcc.impilo.oros.adapter.pacs;

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
 * Adapter for communicating with external PACS (Picture Archiving and Communication System).
 *
 * <p>Handles outbound imaging order dispatch to the PACS and inbound study result
 * processing. The adapter resolves the PACS endpoint from the capability
 * configuration and communicates via REST/JSON with graceful degradation.</p>
 *
 * <h3>Outbound Operations</h3>
 * <ul>
 *   <li>{@link #sendImagingOrder} -- POST imaging order to PACS endpoint</li>
 * </ul>
 *
 * <h3>Inbound Operations (called by event consumer)</h3>
 * <ul>
 *   <li>{@link #receiveStudy} -- process PACS study available payload</li>
 * </ul>
 */
@Service
public class PacsAdapter {

    private static final Logger log = LoggerFactory.getLogger(PacsAdapter.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final CapabilityRepository capabilityRepository;
    private final OrderItemRepository orderItemRepository;
    private final RoutingRepository routingRepository;

    /**
     * Constructs the PacsAdapter with all required dependencies.
     *
     * @param objectMapper         Jackson mapper for payload serialization
     * @param capabilityRepository repository for endpoint resolution
     * @param orderItemRepository  repository for order item lookups
     * @param routingRepository    repository for route status updates
     */
    public PacsAdapter(ObjectMapper objectMapper,
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
     * Send an imaging order to the external PACS.
     *
     * <p>Constructs a JSON payload containing the order details with imaging-specific
     * fields (body site, modality from items), resolves the PACS endpoint from
     * capability configuration, and POSTs the payload. On success, sets the route's
     * external reference from the PACS response (study UID). On failure, logs the
     * error and re-throws for the dispatcher to handle.</p>
     *
     * @param order the imaging order entity to send
     * @param route the routing entity for tracking dispatch status
     * @throws RuntimeException if the PACS endpoint is not configured or the request fails
     */
    public void sendImagingOrder(OrderEntity order, RoutingEntity route) {
        String endpoint = resolveEndpoint(order);
        if (endpoint == null) {
            throw new RuntimeException("No PACS endpoint configured for facility "
                    + order.getFacilityId() + " and tenant " + order.getTenantId());
        }

        try {
            Map<String, Object> payload = buildImagingPayload(order);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Oros-Order-Id", order.getOrderId());
            headers.set("X-Tenant-Id", order.getTenantId().toString());

            HttpEntity<String> request = new HttpEntity<>(
                    objectMapper.writeValueAsString(payload), headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    endpoint + "/api/v1/imaging-orders", request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String studyUid = extractStudyUid(response.getBody());
                if (studyUid != null) {
                    route.setExternalRef(studyUid);
                    routingRepository.save(route);
                }
            }

            log.info("Imaging order {} sent to PACS at {}: status={}",
                    order.getOrderId(), endpoint, response.getStatusCode());

        } catch (RestClientException e) {
            log.error("Failed to send imaging order {} to PACS at {}: {}",
                    order.getOrderId(), endpoint, e.getMessage());
            throw new RuntimeException("PACS dispatch failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error sending imaging order {} to PACS: {}",
                    order.getOrderId(), e.getMessage(), e);
            throw new RuntimeException("PACS dispatch error: " + e.getMessage(), e);
        }
    }

    /**
     * Process an inbound PACS study available payload.
     *
     * <p>Parses the study notification from the PACS and logs the result.
     * Actual processing is handled by the {@link zw.gov.mohcc.impilo.oros.events.OrosEventConsumer}.</p>
     *
     * @param payload the raw JSON payload from the PACS
     */
    public void receiveStudy(String payload) {
        log.info("Received PACS study notification: {}",
                payload != null && payload.length() > 200 ? payload.substring(0, 200) + "..." : payload);
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    /**
     * Resolves the PACS endpoint from capability configuration.
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
     * Builds the imaging order payload for the PACS.
     */
    private Map<String, Object> buildImagingPayload(OrderEntity order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getOrderId());
        payload.put("tenantId", order.getTenantId().toString());
        payload.put("facilityId", order.getFacilityId().toString());
        payload.put("patientCpid", order.getPatientCpid());
        payload.put("priority", order.getPriority().name());
        payload.put("placedBy", order.getPlacedBy());
        payload.put("ziboOrderCode", order.getZiboOrderCode());
        payload.put("clinicalNotes", order.getClinicalNotes());
        payload.put("items", orderItemRepository.findByOrderId(order.getOrderId()));
        return payload;
    }

    /**
     * Extracts the study UID from a PACS response body.
     */
    private String extractStudyUid(String responseBody) {
        try {
            var node = objectMapper.readTree(responseBody);
            var uidNode = node.get("studyInstanceUid");
            if (uidNode == null) {
                uidNode = node.get("accessionNumber");
            }
            if (uidNode == null) {
                uidNode = node.get("id");
            }
            return uidNode != null ? uidNode.asText() : null;
        } catch (Exception e) {
            log.debug("Could not extract study UID from PACS response: {}", e.getMessage());
            return null;
        }
    }
}
