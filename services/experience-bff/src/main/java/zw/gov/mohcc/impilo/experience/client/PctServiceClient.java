package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Patient Care Tracker (PCT) sovereign service.
 *
 * <p>Delegates clinical workflow commands to PCT, which manages the canonical
 * patient journey lifecycle including queue management, triage, encounter
 * start/complete, admission, discharge, and transfer workflows.</p>
 *
 * <p>Trust headers are automatically forwarded by the RestTemplate's
 * header-forwarding interceptor configured in {@link ServiceClientConfig}.</p>
 */
@Component
public class PctServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PctServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public PctServiceClient(RestTemplate serviceRestTemplate,
                            ServiceClientConfig.ServiceEndpoints endpoints,
                            ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.pctBaseUrl();
        this.objectMapper = objectMapper;
    }

    /**
     * Start a patient journey at a facility.
     *
     * @param patientCpid    the patient's Common Patient Identifier
     * @param facilityId     the facility UUID
     * @param referralSource referral source (OPD, EMERGENCY, REFERRAL)
     * @param referralId     optional referral document ID
     * @return the PCT journey response as a JSON tree
     */
    public JsonNode startJourney(String patientCpid, UUID facilityId,
                                 String referralSource, String referralId) {
        String url = baseUrl + "/v1/journeys/start";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("patientCpid", patientCpid);
        if (facilityId != null) body.put("facilityId", facilityId.toString());
        if (referralSource != null) body.put("referralSource", referralSource);
        if (referralId != null) body.put("referralId", referralId);

        log.info("PCT: Starting journey for patient={} at facility={}", patientCpid, facilityId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Record triage for a journey.
     *
     * @param journeyId the PCT journey ID
     * @param acuity    triage acuity level
     * @param vitals    vitals map
     * @param notes     triage notes
     * @return the triage record response
     */
    public JsonNode recordTriage(String journeyId, String acuity,
                                 Map<String, Object> vitals, String notes) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/triage";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("acuity", acuity);
        if (vitals != null) body.put("vitals", vitals);
        if (notes != null) body.put("notes", notes);

        log.info("PCT: Recording triage for journey={}, acuity={}", journeyId, acuity);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Enqueue a journey into a PCT queue.
     *
     * @param queueId   the target queue UUID
     * @param journeyId the journey to enqueue
     * @param priority  priority level
     * @return the queue item response
     */
    public JsonNode enqueue(UUID queueId, String journeyId, int priority) {
        String url = baseUrl + "/v1/queues/" + queueId + "/enqueue";
        Map<String, Object> body = Map.of(
                "journeyId", journeyId,
                "priority", priority
        );

        log.info("PCT: Enqueueing journey={} into queue={}", journeyId, queueId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Call the next patient from a PCT queue.
     *
     * @param queueId the queue to call from
     * @return the called queue item, or null if empty
     */
    public JsonNode callNext(UUID queueId) {
        String url = baseUrl + "/v1/queues/" + queueId + "/call-next";
        log.info("PCT: Calling next patient from queue={}", queueId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Start a clinical encounter within a journey.
     *
     * @param journeyId     the PCT journey ID
     * @param encounterType encounter type (CONSULTATION, PROCEDURE, TRIAGE, LAB)
     * @return the encounter response with encounterRef
     */
    public JsonNode startEncounter(String journeyId, String encounterType) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/encounter/start";
        Map<String, Object> body = Map.of("encounterType", encounterType);

        log.info("PCT: Starting encounter for journey={}, type={}", journeyId, encounterType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Complete a clinical encounter.
     *
     * @param encounterId the PCT encounter ID
     * @return the completed encounter response
     */
    public JsonNode completeEncounter(Long encounterId) {
        String url = baseUrl + "/v1/encounters/" + encounterId + "/complete";

        log.info("PCT: Completing encounter={}", encounterId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get the operational timeline for a patient.
     *
     * @param cpid patient's CPID
     * @return timeline with journeys and encounters
     */
    public JsonNode getPatientTimeline(String cpid) {
        String url = baseUrl + "/v1/patient/" + cpid + "/timeline";
        log.debug("PCT: Getting timeline for patient={}", cpid);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a journey by its ID.
     *
     * @param journeyId the journey ID
     * @return the journey entity
     */
    public JsonNode getJourney(String journeyId) {
        String url = baseUrl + "/v1/journeys/" + journeyId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Start the discharge workflow for a journey.
     *
     * @param journeyId     the journey ID
     * @param dischargeType type of discharge (CLINICAL, AMA, TRANSFER)
     * @return the discharge case response
     */
    public JsonNode startDischarge(String journeyId, String dischargeType) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/discharge/start";
        Map<String, Object> body = Map.of("dischargeType", dischargeType);

        log.info("PCT: Starting discharge for journey={}, type={}", journeyId, dischargeType);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Route a journey to a target queue.
     *
     * @param journeyId     the journey ID
     * @param targetQueueId the target queue UUID
     * @return routing acknowledgement
     */
    public JsonNode routeJourney(String journeyId, UUID targetQueueId) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/route";
        Map<String, Object> body = Map.of("targetQueueId", targetQueueId.toString());

        log.info("PCT: Routing journey={} to queue={}", journeyId, targetQueueId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
