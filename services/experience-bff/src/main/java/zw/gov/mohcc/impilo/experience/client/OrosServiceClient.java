package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * HTTP client for the Orders &amp; Results Orchestration Service (OROS).
 *
 * <p>Delegates clinical order commands to OROS, which manages the canonical
 * order lifecycle including placement, routing, workstep generation, SLA
 * tracking, result capture, and acknowledgement workflows.</p>
 */
@Component
public class OrosServiceClient {

    private static final Logger log = LoggerFactory.getLogger(OrosServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public OrosServiceClient(RestTemplate serviceRestTemplate,
                             ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.orosBaseUrl();
    }

    /**
     * Place a clinical order through OROS.
     *
     * @param orderType     LAB, IMAGING, PHARMACY, PROCEDURE
     * @param priority      ROUTINE, URGENT, STAT
     * @param patientCpid   the patient's CPID
     * @param encounterRef  the encounter reference (UUID string)
     * @param clinicalNotes free-text clinical notes
     * @param items         list of order item maps with code, displayName, quantity, etc.
     * @return the order summary from OROS
     */
    public JsonNode placeOrder(String orderType, String priority, String patientCpid,
                               String encounterRef, String clinicalNotes,
                               List<Map<String, Object>> items) {
        String url = baseUrl + "/v1/orders";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("orderType", orderType);
        if (priority != null) body.put("priority", priority);
        body.put("patientCpid", patientCpid);
        if (encounterRef != null) body.put("encounterRef", encounterRef);
        if (clinicalNotes != null) body.put("clinicalNotes", clinicalNotes);
        if (items != null && !items.isEmpty()) body.put("items", items);

        log.info("OROS: Placing order type={} for patient={}..., encounter={}",
                orderType, patientCpid.substring(0, Math.min(8, patientCpid.length())), encounterRef);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single order by its ID.
     *
     * @param orderId the OROS order ID
     * @return the order summary
     */
    public JsonNode getOrder(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get all orders for a patient by CPID.
     *
     * @param cpid patient's CPID
     * @return list of order summaries
     */
    public JsonNode getPatientOrders(String cpid) {
        String url = baseUrl + "/v1/orders/patient/" + cpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Cancel an order.
     *
     * @param orderId the order ID
     * @param reason  cancellation reason
     * @return the cancelled order
     */
    public JsonNode cancelOrder(String orderId, String reason) {
        String url = baseUrl + "/v1/orders/" + orderId + "/cancel";
        Map<String, Object> body = Map.of("reason", reason);

        log.info("OROS: Cancelling order={}, reason={}", orderId, reason);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get worklist for a facility (in-progress orders).
     *
     * @param facilityId the facility UUID
     * @param status     optional status filter
     * @return list of worklist items
     */
    public JsonNode getWorklist(String facilityId, String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/worklist")
                .queryParam("facilityId", facilityId);
        if (status != null) builder.queryParam("status", status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Acknowledge an order result in OROS.
     *
     * @param orderId the OROS order ID
     * @param ackType DEPARTMENT, CLINICIAN, or CRITICAL
     * @param notes   optional acknowledgement notes
     * @return the acknowledgement response
     */
    public JsonNode acknowledgeOrder(String orderId, String ackType, String notes) {
        String url = baseUrl + "/v1/orders/" + orderId + "/ack";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ackType", ackType);
        if (notes != null) body.put("notes", notes);

        log.info("OROS: Acknowledging order={}, type={}", orderId, ackType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get results for an OROS order.
     *
     * @param orderId the OROS order ID
     * @return list of result summaries
     */
    public JsonNode getOrderResults(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId + "/results";
        log.debug("OROS: Getting results for order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Post a result for an OROS order.
     *
     * @param orderId      the OROS order ID
     * @param kind         result kind (LAB, IMAGING, PHARMACY, DOCUMENT)
     * @param summary      result summary object (will be serialized to JSON)
     * @param isCritical   whether this is a critical result
     * @return the result summary from OROS
     */
    public JsonNode postResult(String orderId, String kind, Object summary, boolean isCritical) {
        String url = baseUrl + "/v1/orders/" + orderId + "/results";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("kind", kind);
        body.put("summary", summary);
        body.put("isCritical", isCritical);

        log.info("OROS: Posting result for order={}, kind={}, critical={}", orderId, kind, isCritical);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get resulted orders for a patient (lab results).
     */
    public JsonNode getPatientResults(String cpid, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/orders/patient/" + cpid + "/results")
                .queryParam("page", page)
                .queryParam("size", size);
        log.debug("OROS: Getting results for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single result by order ID.
     */
    public JsonNode getResult(String orderId) {
        String url = baseUrl + "/v1/orders/" + orderId;
        log.debug("OROS: Getting result for order={}", orderId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
