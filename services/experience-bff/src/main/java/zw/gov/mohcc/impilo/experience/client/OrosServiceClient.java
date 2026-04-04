package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
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

        log.info("OROS: Placing order type={} for patient={}, encounter={}",
                orderType, patientCpid, encounterRef);
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
        StringBuilder url = new StringBuilder(baseUrl + "/v1/worklist?facilityId=" + facilityId);
        if (status != null) url.append("&status=").append(status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
