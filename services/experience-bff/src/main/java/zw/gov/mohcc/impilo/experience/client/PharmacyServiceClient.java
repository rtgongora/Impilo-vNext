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
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Pharmacy sovereign service.
 *
 * <p>Delegates dispense operations to the pharmacy-service, which manages
 * the canonical dispense lifecycle including accept, pick, partial dispense,
 * complete dispense, and backorder workflows.</p>
 */
@Component
public class PharmacyServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PharmacyServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PharmacyServiceClient(RestTemplate serviceRestTemplate,
                                 ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.pharmacyBaseUrl();
    }

    /**
     * Get dispense orders for a patient by CPID.
     */
    public JsonNode getPatientDispenseOrders(String cpid) {
        String url = baseUrl + "/v1/dispense-orders/patient/" + cpid;
        log.debug("Pharmacy: Getting dispense orders for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Complete a dispense order.
     */
    public JsonNode completeDispense(UUID dispenseOrderId) {
        String url = baseUrl + "/v1/dispense-orders/" + dispenseOrderId + "/complete";
        log.info("Pharmacy: Completing dispense order={}", dispenseOrderId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get the pharmacy worklist for a facility.
     */
    public JsonNode getWorklist(String facilityId, String status) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/worklists")
                .queryParam("facilityId", facilityId);
        if (status != null) builder.queryParam("status", status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get prescriptions for a patient by CPID with optional status filter.
     */
    public JsonNode getPatientPrescriptions(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/prescriptions/patient/" + cpid)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        log.debug("Pharmacy: Getting prescriptions for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single prescription by ID.
     */
    public JsonNode getPrescription(String prescriptionId) {
        String url = baseUrl + "/v1/prescriptions/" + prescriptionId;
        log.debug("Pharmacy: Getting prescription id={}", prescriptionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Request a prescription refill.
     */
    public JsonNode requestRefill(String prescriptionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/prescriptions/" + prescriptionId + "/refill";
        log.info("Pharmacy: Requesting refill for prescription={}", prescriptionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createPrescription(Map<String, Object> body) {
        String url = baseUrl + "/v1/prescriptions";
        log.info("Pharmacy: Creating prescription");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode cancelPrescription(UUID prescriptionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/prescriptions/" + prescriptionId + "/cancel";
        log.info("Pharmacy: Cancelling prescription={}", prescriptionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode dispensePrescription(UUID prescriptionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/prescriptions/" + prescriptionId + "/dispense";
        log.info("Pharmacy: Dispensing prescription={}", prescriptionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
