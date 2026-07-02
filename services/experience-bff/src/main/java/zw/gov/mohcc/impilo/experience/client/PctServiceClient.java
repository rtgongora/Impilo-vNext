package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
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

        log.info("PCT: Starting journey for patient={}... at facility={}",
                patientCpid.substring(0, Math.min(8, patientCpid.length())), facilityId);
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
     * List queues for a facility (with embedded stats from PCT).
     */
    public JsonNode listQueues(UUID facilityId, UUID workspaceId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/queues")
                .queryParam("facilityId", facilityId);
        if (workspaceId != null) {
            b.queryParam("workspaceId", workspaceId);
        }
        String url = b.toUriString();
        log.debug("PCT: Listing queues for facility={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Queue materialisation-status summary for a facility (materialised vs seed/demo, counts, last sync). */
    public JsonNode getQueueMaterializationStatus(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/queues/materialization-status")
                .queryParam("facilityId", facilityId).toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** Reconcile a facility's queues from TUSO (idempotent, failure-safe in PCT). */
    public JsonNode reconcileQueues(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/queues/reconcile")
                .queryParam("facilityId", facilityId).toUriString();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * List items in a queue, optionally filtered by status.
     */
    public JsonNode listQueueItems(UUID queueId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/queues/" + queueId + "/items");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        String url = b.toUriString();
        log.debug("PCT: Listing queue items queue={} status={}", queueId, status);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Update lifecycle status for a queue item.
     */
    public JsonNode updateQueueItemStatus(UUID itemId, String status) {
        String url = baseUrl + "/v1/queue-items/" + itemId + "/status";
        Map<String, Object> body = Map.of("status", status);
        log.info("PCT: Queue item {} -> {}", itemId, status);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Transfer a queue item to another queue.
     */
    public JsonNode transferQueueItem(UUID itemId, UUID targetQueueId) {
        String url = baseUrl + "/v1/queue-items/" + itemId + "/transfer";
        Map<String, Object> body = Map.of("targetQueueId", targetQueueId.toString());
        log.info("PCT: Transferring queue item {} to queue {}", itemId, targetQueueId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Start a clinical encounter within a journey.
     *
     * @param journeyId     the PCT journey ID
     * @param encounterType encounter type (CONSULTATION, PROCEDURE, TRIAGE, LAB)
     * @return the encounter response with encounterRef
     */
    public JsonNode startEncounter(String journeyId,
                                   String encounterType,
                                   String encounterContext,
                                   String entryPoint,
                                   String modality,
                                   String virtualMode,
                                   String careSetting,
                                   String priority,
                                   String triageCategory,
                                   String pathwayRef,
                                   String protocolRef) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/encounter/start";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("encounterType", encounterType);
        if (encounterContext != null) body.put("encounterContext", encounterContext);
        if (entryPoint != null) body.put("entryPoint", entryPoint);
        if (modality != null) body.put("modality", modality);
        if (virtualMode != null) body.put("virtualMode", virtualMode);
        if (careSetting != null) body.put("careSetting", careSetting);
        if (priority != null) body.put("priority", priority);
        if (triageCategory != null) body.put("triageCategory", triageCategory);
        if (pathwayRef != null) body.put("pathwayRef", pathwayRef);
        if (protocolRef != null) body.put("protocolRef", protocolRef);

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
     * Update pathway/protocol linkage for an encounter.
     */
    public JsonNode updateEncounterPathwayProtocol(Long encounterId, String pathwayRef, String protocolRef) {
        String url = baseUrl + "/v1/encounters/" + encounterId + "/pathway-protocol";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("pathwayRef", pathwayRef);
        body.put("protocolRef", protocolRef);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    /**
     * Load a single encounter by numeric PCT id.
     */
    public JsonNode getEncounter(long encounterId) {
        String url = baseUrl + "/v1/encounters/" + encounterId;
        log.debug("PCT: Getting encounter id={}", encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createEncounterImagingLink(long encounterId, Map<String, Object> body) {
        String url = baseUrl + "/v1/encounters/" + encounterId + "/imaging-links";
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode listEncounterImagingLinks(long encounterId) {
        String url = baseUrl + "/v1/encounters/" + encounterId + "/imaging-links";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
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
        log.debug("PCT: Getting timeline for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
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

    public JsonNode listJourneys(String patientId, int page, int size) {
        String url = baseUrl + "/v1/journeys?patient_id=" + patientId + "&page=" + page + "&size=" + size;
        log.debug("PCT: listing journeys for patient={}", patientId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listConditions(String patientId, int page, int size) {
        String url = baseUrl + "/v1/conditions?patient_id=" + patientId + "&page=" + page + "&size=" + size;
        log.debug("PCT: listing conditions for patient={}", patientId);
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
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dischargeType", dischargeType != null ? dischargeType : "CLINICAL");
        return startDischarge(journeyId, body);
    }

    public JsonNode startDischarge(String journeyId, Map<String, Object> body) {
        String url = baseUrl + "/v1/journeys/" + journeyId + "/discharge/start";
        log.info("PCT: Starting discharge for journey={}", journeyId);
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

    /**
     * Get the health summary for a patient (IPS-like aggregation from PCT perspective).
     */
    public JsonNode getPatientHealthSummary(String cpid) {
        String url = baseUrl + "/v1/patient/" + cpid + "/summary";
        log.debug("PCT: Getting health summary for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get clinical records/documents for a patient.
     */
    public JsonNode getPatientRecords(String cpid, String documentType, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/patient/" + cpid + "/records")
                .queryParam("page", page)
                .queryParam("size", size);
        if (documentType != null) builder.queryParam("documentType", documentType);
        log.debug("PCT: Getting records for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single clinical record by ID.
     */
    public JsonNode getPatientRecord(String recordId) {
        String url = baseUrl + "/v1/records/" + recordId;
        log.debug("PCT: Getting record id={}", recordId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a clinical record (document metadata) in PCT.
     */
    public JsonNode createPatientRecord(Map<String, Object> body) {
        String url = baseUrl + "/v1/records";
        log.info("PCT: Creating clinical record for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get telehealth sessions for a patient.
     */
    public JsonNode getPatientTelehealthSessions(String cpid, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/patient/" + cpid + "/telehealth")
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        log.debug("PCT: Getting telehealth sessions for patient={}...",
                cpid.substring(0, Math.min(8, cpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single telehealth session by ID.
     */
    public JsonNode getTelehealthSession(String sessionId) {
        String url = baseUrl + "/v1/telehealth/" + sessionId;
        log.debug("PCT: Getting telehealth session id={}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Request a new telehealth session.
     */
    public JsonNode requestTelehealthSession(Map<String, Object> request) {
        String url = baseUrl + "/v1/telehealth";
        log.info("PCT: Requesting telehealth session");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Join a telehealth session.
     */
    public JsonNode joinTelehealthSession(String sessionId) {
        String url = baseUrl + "/v1/telehealth/" + sessionId + "/join";
        log.info("PCT: Joining telehealth session id={}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * End a telehealth session.
     */
    public JsonNode endTelehealthSession(String sessionId, Map<String, Object> request) {
        String url = baseUrl + "/v1/telehealth/" + sessionId + "/end";
        log.info("PCT: Ending telehealth session id={}", sessionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * List referrals for a patient.
     */
    public JsonNode listPatientReferrals(String patientId, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/referrals")
                .queryParam("patientId", patientId)
                .queryParam("page", page)
                .queryParam("size", size);
        log.info("PCT: Listing referrals for patient={}", patientId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a single referral by ID.
     */
    public JsonNode getReferral(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId;
        log.info("PCT: Getting referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Create a referral.
     */
    public JsonNode createReferral(Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals";
        log.info("PCT: Creating referral");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Complete a referral.
     */
    public JsonNode completeReferral(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/complete";
        log.info("PCT: Completing referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Accept a referral.
     */
    public JsonNode acceptReferral(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/accept";
        log.info("PCT: Accepting referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Respond to a referral.
     */
    public JsonNode respondReferral(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/respond";
        log.info("PCT: Responding to referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * List incoming referrals for a facility.
     */
    public JsonNode listIncomingReferrals(String facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/referrals/incoming")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null) builder.queryParam("status", status);
        log.info("PCT: Listing incoming referrals for facility={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * List telehealth sessions for a facility/workspace operational hub.
     */
    public JsonNode listTelehealthSessions(String facilityId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/telehealth")
                .queryParam("facilityId", facilityId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        log.info("PCT: Listing telehealth sessions for facility={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getTelemedicineOps(String facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ops/telemedicine")
                .queryParam("facilityId", facilityId)
                .toUriString();
        log.info("PCT: Telemedicine ops snapshot for facility={}", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateReferralStage(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/stage";
        log.info("PCT: Updating referral stage for id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.PUT, new HttpEntity<>(request), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateReferralConsent(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/consent";
        log.info("PCT: Updating referral consent for id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode submitReferral(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/submit";
        log.info("PCT: Submitting referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Allergies (strangler migration) ────────────────────────

    public JsonNode listAllergies(String patientCpid) {
        String url = baseUrl + "/v1/allergies?patient_id=" + patientCpid;
        log.debug("PCT: Listing allergies for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createAllergy(Map<String, Object> body) {
        String url = baseUrl + "/v1/allergies";
        log.info("PCT: Creating allergy for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode deactivateAllergy(String allergyId) {
        String url = baseUrl + "/v1/allergies/" + allergyId + "/deactivate";
        log.info("PCT: Deactivating allergy={}", allergyId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Conditions (strangler migration) ────────────────────────

    public JsonNode createCondition(Map<String, Object> body) {
        String url = baseUrl + "/v1/conditions";
        log.info("PCT: Creating condition for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode resolveCondition(String conditionId) {
        String url = baseUrl + "/v1/conditions/" + conditionId + "/resolve";
        log.info("PCT: Resolving condition={}", conditionId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Vitals (strangler migration) ────────────────────────────

    public JsonNode deleteVital(String vitalId) {
        String url = baseUrl + "/v1/vitals/" + vitalId;
        log.info("PCT: Deleting vital={}", vitalId);
        restTemplate.exchange(url, HttpMethod.DELETE, null, JsonNode.class);
        return new ObjectMapper().createObjectNode();
    }

    public JsonNode listVitals(String patientCpid, int page, int size) {
        String url = baseUrl + "/v1/vitals?patient_id=" + patientCpid + "&page=" + page + "&size=" + size;
        log.debug("PCT: Listing vitals for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createVitals(Map<String, Object> body) {
        String url = baseUrl + "/v1/vitals";
        log.info("PCT: Recording vitals for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Tasks ───────────────────────────────────────────────────

    public JsonNode getMyTasks() {
        String url = baseUrl + "/v1/tasks/my";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listWorkspaceTasks(UUID workspaceId) {
        String url = baseUrl + "/v1/tasks/workspace/" + workspaceId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listJourneyTasks(String journeyId) {
        String url = baseUrl + "/v1/tasks/journey/" + journeyId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode completeTask(UUID taskId) {
        String url = baseUrl + "/v1/tasks/" + taskId + "/complete";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createTask(Map<String, Object> body) {
        String url = baseUrl + "/v1/tasks";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getTask(UUID taskId) {
        String url = baseUrl + "/v1/tasks/" + taskId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateTask(UUID taskId, Map<String, Object> body) {
        String url = baseUrl + "/v1/tasks/" + taskId;
        ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.PATCH, new org.springframework.http.HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    public JsonNode escalateTask(UUID taskId, Map<String, Object> body) {
        String url = baseUrl + "/v1/tasks/" + taskId + "/escalate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Immunizations (strangler migration) ─────────────────────

    public JsonNode listImmunizations(String patientCpid, int page, int size) {
        String url = baseUrl + "/v1/immunizations?patient_id=" + patientCpid + "&page=" + page + "&size=" + size;
        log.debug("PCT: Listing immunizations for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createImmunization(Map<String, Object> body) {
        String url = baseUrl + "/v1/immunizations";
        log.info("PCT: Recording immunization for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Notes (strangler migration) ────────────────────

    public JsonNode listClinicalNotes(String patientCpid, int page, int size) {
        String url = baseUrl + "/v1/clinical-notes?patient_id=" + patientCpid + "&page=" + page + "&size=" + size;
        log.debug("PCT: Listing clinical notes for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getClinicalNote(String noteId) {
        String url = baseUrl + "/v1/clinical-notes/" + noteId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createClinicalNote(Map<String, Object> body) {
        return createClinicalNote(body, null);
    }

    /**
     * Creates a clinical note, optionally adding patient-share provenance headers for downstream PCT storage.
     * Trust headers are still forwarded from the inbound request by the RestTemplate interceptor.
     */
    public JsonNode createClinicalNote(Map<String, Object> body, Map<String, String> patientShareProvenanceHeaders) {
        String url = baseUrl + "/v1/clinical-notes";
        log.info("PCT: Creating clinical note for patient={}", body.get("patient_id"));
        if (patientShareProvenanceHeaders == null || patientShareProvenanceHeaders.isEmpty()) {
            ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
            return extractData(response);
        }
        HttpHeaders headers = new HttpHeaders();
        patientShareProvenanceHeaders.forEach((k, v) -> {
            if (k != null && v != null && !v.isBlank()) {
                headers.set(k, v);
            }
        });
        ResponseEntity<JsonNode> response = restTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
        return extractData(response);
    }

    public JsonNode signClinicalNote(String noteId) {
        String url = baseUrl + "/v1/clinical-notes/" + noteId + "/sign";
        log.info("PCT: Signing clinical note={}", noteId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Growth (strangler migration) ────────────────────────────

    public JsonNode listGrowthMeasurements(String patientCpid) {
        String url = baseUrl + "/v1/growth?patient_id=" + patientCpid;
        log.debug("PCT: Listing growth measurements for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordGrowthMeasurement(Map<String, Object> body) {
        String url = baseUrl + "/v1/growth";
        log.info("PCT: Recording growth measurement for patient={}", body.get("patient_id"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Depth — Discharge Clearances (strangler migration) ──

    public JsonNode initDischargeClearances(Map<String, Object> body) {
        String url = baseUrl + "/v1/discharge-clearances/init";
        log.info("PCT: Init discharge clearances for encounter={}", body.get("encounterId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getDischargeClearances(String encounterId) {
        String url = baseUrl + "/v1/discharge-clearances?encounterId=" + encounterId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode clearDischargeClearance(String clearanceId, Map<String, Object> body) {
        String url = baseUrl + "/v1/discharge-clearances/" + clearanceId + "/clear";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode waiveDischargeClearance(String clearanceId, Map<String, Object> body) {
        String url = baseUrl + "/v1/discharge-clearances/" + clearanceId + "/waive";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Depth — Resuscitation (strangler migration) ────

    public JsonNode startResuscitationPhase(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/phases";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode endResuscitationPhase(String activationId, String phaseId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/phases/" + phaseId + "/end";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getResuscitationPhases(String activationId) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/phases";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordCPRCycle(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/cpr-cycles";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getCPRCycles(String activationId) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/cpr-cycles";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordResuscitationMedication(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/medications";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getResuscitationMedications(String activationId) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/medications";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Depth — Care Plans (strangler migration) ───────

    public JsonNode addCarePlanGoal(String planId, Map<String, Object> body) {
        String url = baseUrl + "/v1/care-plans/" + planId + "/goals";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateCarePlanGoal(String planId, String goalId, Map<String, Object> body) {
        String url = baseUrl + "/v1/care-plans/" + planId + "/goals/" + goalId + "/update";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addCarePlanIntervention(String planId, Map<String, Object> body) {
        String url = baseUrl + "/v1/care-plans/" + planId + "/interventions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode performCarePlanIntervention(String planId, String interventionId) {
        String url = baseUrl + "/v1/care-plans/" + planId + "/interventions/" + interventionId + "/perform";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Depth — NEWS2 (strangler migration) ────────────

    public JsonNode recordNEWS2Components(Map<String, Object> body) {
        String url = baseUrl + "/v1/ews/news2";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Structured History (strangler migration) ────────────────

    public JsonNode getSocialHistory(String patientCpid) {
        String url = baseUrl + "/v1/ehr/social-history?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFamilyHistory(String patientCpid) {
        String url = baseUrl + "/v1/ehr/family-history?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFunctionalAssessments(String patientCpid) {
        String url = baseUrl + "/v1/ehr/functional-assessments?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getProcedures(String patientCpid) {
        String url = baseUrl + "/v1/ehr/procedures?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAdvanceDirectives(String patientCpid) {
        String url = baseUrl + "/v1/ehr/advance-directives?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Care/Emergency/Inpatient (strangler migration) ──────────

    public JsonNode listCarePlans(String patientId) {
        String url = baseUrl + "/v1/care-plans?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createCarePlan(Map<String, Object> body) {
        String url = baseUrl + "/v1/care-plans";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFluidBalance(String patientId, String date) {
        String url = baseUrl + "/v1/fluid-balance?patientId=" + patientId + (date != null ? "&date=" + date : "");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordFluidBalance(Map<String, Object> body) {
        String url = baseUrl + "/v1/fluid-balance";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listEmergencyActivations() {
        String url = baseUrl + "/v1/emergency/activations";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode activateEmergency(Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/activate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode logEmergencyAction(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/action";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode endEmergency(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/end";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordResuscitation(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/" + activationId + "/resuscitation";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordApgar(Map<String, Object> body) {
        String url = baseUrl + "/v1/apgar";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getApgar(String patientId) {
        String url = baseUrl + "/v1/apgar?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordEWS(Map<String, Object> body) {
        String url = baseUrl + "/v1/ews";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEWS(String patientId) {
        String url = baseUrl + "/v1/ews?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordObservation(Map<String, Object> body) {
        String url = baseUrl + "/v1/observations";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getObservations(String patientId) {
        String url = baseUrl + "/v1/observations?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Maternity (strangler migration) ─────────────────────────

    public JsonNode createFetalMonitoringSession(Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/ctg/sessions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getActiveFetalMonitoringSession(String patientId, String encounterId) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/maternity/ctg/sessions/active?patientId=" + patientId);
        if (encounterId != null) url.append("&encounterId=").append(encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFetalMonitoringSession(String sessionId) {
        String url = baseUrl + "/v1/maternity/ctg/sessions/" + sessionId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addFetalMonitoringChunk(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/ctg/sessions/" + sessionId + "/chunks";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getFetalMonitoringChunks(String sessionId, String channel, String from, String to) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/maternity/ctg/sessions/" + sessionId + "/chunks?_=1");
        if (channel != null) url.append("&channel=").append(channel);
        if (from != null) url.append("&from=").append(from);
        if (to != null) url.append("&to=").append(to);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode addFetalMonitoringAnnotation(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/ctg/sessions/" + sessionId + "/annotations";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listLabourMonitoring(String patientId, String encounterId) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/labour-monitoring?patientId=" + patientId);
        if (encounterId != null) url.append("&encounterId=").append(encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordLabourMonitoring(Map<String, Object> body) {
        String url = baseUrl + "/v1/labour-monitoring";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createPartographSession(Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/partograph/sessions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getActivePartographSession(String patientId, String encounterId) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/maternity/partograph/sessions/active?patientId=" + patientId);
        if (encounterId != null) url.append("&encounterId=").append(encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getPartographSession(String sessionId) {
        String url = baseUrl + "/v1/maternity/partograph/sessions/" + sessionId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addPartographPoint(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/partograph/sessions/" + sessionId + "/points";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode closePartographSession(String sessionId, Map<String, Object> body) {
        String url = baseUrl + "/v1/maternity/partograph/sessions/" + sessionId + "/close";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getMaternitySummary(String patientId, String encounterId) {
        StringBuilder url = new StringBuilder(baseUrl + "/v1/maternity/summary?patientId=" + patientId);
        if (encounterId != null) url.append("&encounterId=").append(encounterId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    // ── Inpatient-relevant operations via PCT ───────────────────

    public JsonNode createAdmission(Map<String, Object> body) {
        String url = baseUrl + "/v1/admissions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listAdmissions(String patientId) {
        String url = baseUrl + "/v1/admissions" + (patientId != null ? "?patientId=" + patientId : "");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startWardRound(Map<String, Object> body) {
        String url = baseUrl + "/v1/ward-rounds";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addWardRoundEntry(String roundId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ward-rounds/" + roundId + "/entries";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listWardRounds(String wardId) {
        String url = baseUrl + "/v1/ward-rounds?wardId=" + wardId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode requestTransfer(Map<String, Object> body) {
        String url = baseUrl + "/v1/transfers";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode acceptTransfer(String transferId, Map<String, Object> body) {
        String url = baseUrl + "/v1/transfers/" + transferId + "/accept";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listTransfers(String patientId) {
        String url = baseUrl + "/v1/transfers" + (patientId != null ? "?patientId=" + patientId : "");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── ED / Casualty operations (PCT V012) ─────────────────────────

    public JsonNode openEdVisit(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/visits";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listEdVisits(UUID facilityId, String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ed/visits");
        if (facilityId != null) b.queryParam("facilityId", facilityId);
        if (status != null) b.queryParam("status", status);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

    public JsonNode getEdVisit(String visitId) {
        String url = baseUrl + "/v1/ed/visits/" + visitId;
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode edVisitPost(String visitId, String action, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/visits/" + visitId + action;
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    public JsonNode edProtocolSuggestions(String visitId) {
        String url = baseUrl + "/v1/ed/visits/" + visitId + "/protocol-suggestions";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode edTriageDiscriminators() {
        String url = baseUrl + "/v1/ed/triage/discriminators";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode edTriageScore(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/triage/score";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode openEmergencyCase(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/emergency-cases";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode markEdPageDelivered(String pageId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/pages/" + pageId + "/delivered";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    /**
     * Resolve a Cadre Engine decision (shared read-model C9). The Encounter Cockpit renders its adaptive spine
     * strictly from {@code cockpitSpine}; disabled actions are never live buttons. PCT owns the decision and
     * audits every resolution; the BFF only composes.
     *
     * @param request the C9 request (role/cadre/scope/visitType/acuity/context/accessState + optional
     *                journeyId/encounterId correlations)
     * @return the CadreDecision JSON (permittedWorkflows, cockpitSpine, escalation, auditRef)
     */
    public JsonNode resolveCadreDecision(Map<String, Object> request) {
        String url = baseUrl + "/v1/cadre/decision";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    // ── WS#8 Death & Post-Death Pathway (PCT owns the DeathCase) ──

    public JsonNode confirmDeath(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/confirm", body, JsonNode.class));
    }

    public JsonNode confirmBroughtInDead(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/brought-in-dead", body, JsonNode.class));
    }

    public JsonNode reportCommunityDeath(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/community-report", body, JsonNode.class));
    }

    public JsonNode recordDeathVerbalAutopsy(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/verbal-autopsy", body, JsonNode.class));
    }

    public JsonNode listDeathVerbalAutopsies(String caseId) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/" + caseId + "/verbal-autopsy", JsonNode.class));
    }

    public JsonNode recordDeathFieldBodyManagement(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/field-body-management", body, JsonNode.class));
    }

    public JsonNode listDeathFieldBodyManagement(String caseId) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/" + caseId + "/field-body-management", JsonNode.class));
    }

    public JsonNode listDeathCases() {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/cases", JsonNode.class));
    }

    public JsonNode getDeathCase(String caseId) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/" + caseId, JsonNode.class));
    }

    public JsonNode getDeathAudit(String caseId) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/" + caseId + "/audit", JsonNode.class));
    }

    public JsonNode screenDeathPublicHealth(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/public-health-screen", body, JsonNode.class));
    }

    public JsonNode certifyDeathCause(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/certify", body, JsonNode.class));
    }

    public JsonNode updateDeathCoronerStatus(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/coroner-status", body, JsonNode.class));
    }

    public JsonNode stageDeathCrvsPackage(String caseId) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/crvs-package", Map.of(), JsonNode.class));
    }

    public JsonNode getDeathBodyCustody(String caseId) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/death/" + caseId + "/body", JsonNode.class));
    }

    public JsonNode receiveDeathBody(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/body/receive", body, JsonNode.class));
    }

    public JsonNode setDeathPostmortemHold(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/body/postmortem-hold", body, JsonNode.class));
    }

    public JsonNode releaseDeathBody(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/body/release", body, JsonNode.class));
    }

    public JsonNode attachDeathDocument(String caseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/death/" + caseId + "/documents", body, JsonNode.class));
    }

    // ── Encounter Structured Forms (PCT owns resolver + responses) ──

    public JsonNode resolveEncounterForms(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/forms/resolve", body, JsonNode.class));
    }

    public JsonNode createFormResponse(Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/forms/responses", body, JsonNode.class));
    }

    public JsonNode getFormResponse(String responseId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/forms/responses/" + responseId, JsonNode.class));
    }

    public JsonNode updateFormAnswers(String responseId, Map<String, Object> body) {
        return extractData(restTemplate.exchange(
                baseUrl + "/v1/forms/responses/" + responseId + "/answers",
                HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class));
    }

    public JsonNode submitFormResponse(String responseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/forms/responses/" + responseId + "/submit", body, JsonNode.class));
    }

    public JsonNode countersignFormResponse(String responseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/forms/responses/" + responseId + "/countersign", body, JsonNode.class));
    }

    public JsonNode amendFormResponse(String responseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/forms/responses/" + responseId + "/amend", body, JsonNode.class));
    }

    public JsonNode voidFormResponse(String responseId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/forms/responses/" + responseId + "/void", body, JsonNode.class));
    }

    public JsonNode listEncounterFormResponses(String encounterId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/encounters/" + encounterId + "/form-responses", JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
