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
        return startJourney(patientCpid, facilityId, referralSource, referralId, null, null, null);
    }

    /**
     * Start a journey, optionally carrying a live biometric probe. When
     * {@code biometricProbeBase64} and {@code biometricSubjectRef} are supplied,
     * pct-service verifies the arrival probe through the shared ABIS seam:
     * MATCH → journey starts; NO_MATCH → pct-service rejects with a 4xx (the
     * caller surfaces it); UNAVAILABLE → the check-in proceeds without a binding.
     */
    public JsonNode startJourney(String patientCpid, UUID facilityId,
                                 String referralSource, String referralId,
                                 String biometricSubjectRef, String biometricModality,
                                 String biometricProbeBase64) {
        String url = baseUrl + "/v1/journeys/start";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("patientCpid", patientCpid);
        if (facilityId != null) body.put("facilityId", facilityId.toString());
        if (referralSource != null) body.put("referralSource", referralSource);
        if (referralId != null) body.put("referralId", referralId);
        if (biometricSubjectRef != null && !biometricSubjectRef.isBlank())
            body.put("biometricSubjectRef", biometricSubjectRef);
        if (biometricModality != null && !biometricModality.isBlank())
            body.put("biometricModality", biometricModality);
        if (biometricProbeBase64 != null && !biometricProbeBase64.isBlank())
            body.put("biometricProbeBase64", biometricProbeBase64);

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
     * Escalate a queue item (urgency bump, mandatory reason, optional target queue).
     */
    public JsonNode escalateQueueItem(UUID itemId, String reason, UUID targetQueueId, Integer priority) {
        String url = baseUrl + "/v1/queue-items/" + itemId + "/escalate";
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("reason", reason);
        if (targetQueueId != null) {
            body.put("targetQueueId", targetQueueId.toString());
        }
        if (priority != null) {
            body.put("priority", priority);
        }
        log.info("PCT: Escalating queue item {} (target queue {})", itemId, targetQueueId);
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

    /**
     * A patient's telehealth sessions (read-only) — used by the Khuluma "upcoming sessions"
     * composite. Consumes the stable per-CPID reader; does not touch teleconsult orchestration.
     */
    public JsonNode listPatientTelehealth(String patientCpid) {
        String url = baseUrl + "/v1/patient/" + patientCpid + "/telehealth";
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

    /**
     * The patient's problem list.
     *
     * <p>PCT serves this at {@code /v1/problems} keyed by {@code subject_cpid}. This client
     * previously called {@code /v1/conditions?patient_id=}, an endpoint pct-service has never
     * served, so every read 404'd and every clinical surface above showed an empty problem list.
     * "Condition" survives as the BFF's outward-facing noun because the UI is built on it;
     * "problem" is the doctrine word and the name of the record. One clinical truth, and the
     * translation happens here rather than by adding a second endpoint to the system of record.</p>
     *
     * <p>Deliberately unpaged: a problem list is read whole or it misleads. Truncating it at a
     * page boundary hides the diagnosis that did not fit.</p>
     */
    public JsonNode listProblems(String subjectCpid) {
        String url = baseUrl + "/v1/problems?subject_cpid=" + subjectCpid;
        log.debug("PCT: listing problems for subject={}", subjectCpid);
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
     * Clinical records/documents for a patient, newest first. Unpaged: pct serves the whole
     * index and ignores paging params, so the experience layer pages the composed rows itself
     * rather than pretending a page/size handshake happened.
     */
    public JsonNode getPatientRecords(String cpid, String documentType) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/patient/" + cpid + "/records");
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
        return getPatientRecord(recordId, null);
    }

    /**
     * Get a single clinical record by ID, bound to a subject. When {@code subjectCpid} is
     * supplied pct answers 404 on a mismatch without disclosing that the id exists — the
     * citizen-facing path passes the caller's own CPID here so a person can only ever fetch
     * their own record.
     */
    public JsonNode getPatientRecord(String recordId, String subjectCpid) {
        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/records/" + recordId);
        if (subjectCpid != null && !subjectCpid.isBlank()) {
            builder.queryParam("subject_cpid", subjectCpid);
        }
        log.debug("PCT: Getting record id={}", recordId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
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
    /**
     * Records that a teleconsultation has actually begun. Idempotent in PCT — a reconnect or a
     * second participant joining must not move the start time forward.
     */
    public JsonNode markTeleconsultSessionStarted(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/session-started";
        log.info("PCT: marking teleconsult session started referral={}", referralId);
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(url, java.util.Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Encounters started by this tenant since midnight (or {@code since}). */
    public JsonNode countEncounters(String since) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/encounters/count");
        if (since != null && !since.isBlank()) {
            b.queryParam("since", since);
        }
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
    }

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
     * Soft accept-gate duty resolver (optional wiring — avoids constructor
     * churn for the many existing test constructions). When present, every
     * accept of a POOL-routed referral records the pool's on-duty state
     * (true|false|UNKNOWN) in the acceptance audit. Advisory only: any
     * failure here degrades to UNKNOWN/absent and NEVER blocks the accept.
     */
    private zw.gov.mohcc.impilo.experience.telemedicine.VirtualPoolDutyService virtualPoolDutyService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setVirtualPoolDutyService(
            zw.gov.mohcc.impilo.experience.telemedicine.VirtualPoolDutyService virtualPoolDutyService) {
        this.virtualPoolDutyService = virtualPoolDutyService;
    }

    /**
     * Accept a referral. POOL-routed referrals get the soft duty audit
     * ({@code on_duty}) attached before delegation to PCT.
     */
    public JsonNode acceptReferral(String referralId, Map<String, Object> request) {
        Map<String, Object> enriched = enrichAcceptWithDutyAudit(referralId, request);
        String url = baseUrl + "/v1/referrals/" + referralId + "/accept";
        log.info("PCT: Accepting referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, enriched, JsonNode.class);
        return extractData(response);
    }

    /** TM-B1 guard-permitted next actions for the referral's current state. */
    public JsonNode allowedActions(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/allowed-actions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** TM-B1 reason-bound lifecycle transition (cancel|reopen|escalate|transfer|error-mark). */
    public JsonNode lifecycleAction(String referralId, String action, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/" + action;
        log.info("PCT: lifecycle {} on referral {}", action, referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url,
                request == null ? Map.of() : request, JsonNode.class);
        return extractData(response);
    }

    /** TM-B15: MDT / specialist-board spine (pct-owned clinical record). */
    public JsonNode mdtCreateSession(Map<String, Object> request) {
        ResponseEntity<JsonNode> response =
                restTemplate.postForEntity(baseUrl + "/v1/mdt/sessions", request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode mdtGetBoard(String mdtSessionId, String viewer) {
        String url = baseUrl + "/v1/mdt/sessions/" + mdtSessionId
                + (viewer == null || viewer.isBlank() ? "" : "?viewer=" + viewer);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode mdtAddCase(String mdtSessionId, Map<String, Object> request) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/mdt/sessions/" + mdtSessionId + "/cases", request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode mdtRecordOutcome(String caseItemId, Map<String, Object> request) {
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                baseUrl + "/v1/mdt/cases/" + caseItemId + "/outcome", request, JsonNode.class);
        return extractData(response);
    }

    /** TM-B5 B5-3: provider-only side-channel notes (never patient-visible). */
    public JsonNode getProviderNotes(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/provider-notes";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** TM-B7 tasks linked to a teleconsult referral. */
    public JsonNode listReferralTasks(String referralId) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/tasks";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** TM-B7 create a follow-up / execution task scoped to a teleconsult referral. */
    public JsonNode addReferralTask(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/tasks";
        log.info("PCT: creating task on referral {}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url,
                request == null ? Map.of() : request, JsonNode.class);
        return extractData(response);
    }

    private Map<String, Object> enrichAcceptWithDutyAudit(String referralId, Map<String, Object> request) {
        Map<String, Object> body = request == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request);
        if (virtualPoolDutyService == null || body.containsKey("on_duty") || body.containsKey("onDuty")) {
            return body;
        }
        try {
            JsonNode referral = getReferral(referralId);
            if (referral != null && "POOL".equalsIgnoreCase(referral.path("routingKind").asText(""))) {
                String poolId = referral.path("routingPoolId").asText(null);
                if (poolId != null && !poolId.isBlank()) {
                    body.put("on_duty", virtualPoolDutyService.onDutyFlag(poolId));
                }
            }
        } catch (Exception e) {
            // Never block an accept on duty resolution — PCT records UNKNOWN when absent.
            log.warn("Duty audit enrichment skipped for referral {}: {}", referralId, e.getMessage());
        }
        return body;
    }

    /** Per-queue stats for a virtual pool (depth, oldest wait, SLA breaches, materialisation state). */
    public JsonNode getVirtualPoolStats(String poolId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/internal/queues/virtual-pool-stats")
                .queryParam("poolId", poolId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** On-demand reconcile of the tenant's virtual-pool queues from TUSO. */
    public JsonNode reconcileVirtualPools() {
        String url = baseUrl + "/v1/internal/queues/reconcile-virtual-pools";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
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

    /** TM-B6 structured response (v1 spine + v2 additive sections: coded diagnosis, prescriptions,
     *  orders, patient instructions, onward referral). */
    public JsonNode respondReferralStructured(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/respond-structured";
        log.info("PCT: Structured response to referral id={}", referralId);
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
     * List teleconsult referrals routed to a specialty pool (virtual-hospital queue),
     * oldest first. {@code status} is optional (PCT defaults to SUBMITTED) and accepts a
     * comma-separated status-in list.
     */
    public JsonNode listPoolReferrals(String poolId, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/referrals/pool/" + poolId)
                .queryParam("page", page)
                .queryParam("size", size);
        if (status != null && !status.isBlank()) builder.queryParam("status", status);
        log.info("PCT: Listing pool referrals for pool={}", poolId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(builder.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /**
     * Route a referral to a team/pool/on-call roster (Stage 3 routing).
     */
    public JsonNode routeReferral(String referralId, Map<String, Object> request) {
        String url = baseUrl + "/v1/referrals/" + referralId + "/route";
        log.info("PCT: Routing referral id={}", referralId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
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

    // ── Problem list (the UI calls these "conditions") ──────────

    /**
     * Adds a problem. The body must already be in PCT's vocabulary — see
     * {@code ConditionsController#toPctProblem}, which owns the translation.
     */
    public JsonNode createProblem(Map<String, Object> body) {
        String url = baseUrl + "/v1/problems";
        log.info("PCT: Creating problem for subject={}", body.get("subject_cpid"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode resolveProblem(String problemId) {
        String url = baseUrl + "/v1/problems/" + problemId + "/resolve";
        log.info("PCT: Resolving problem={}", problemId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Vitals → discrete observations ──────────────────────────
    //
    // The vitals surface reads and writes pct's observation spine (pct_observations). This client
    // previously called /v1/vitals, an endpoint pct-service has never served, so every clinical
    // vitals read 404'd and rendered as "no vitals recorded" — the same defect, and the same
    // repair, as /v1/conditions → /v1/problems. "Vitals" survives as the BFF's outward-facing
    // noun because the UI is built on it; the clinical truth is the coded observation, and the
    // translation lives in VitalsObservationBridge rather than in a second endpoint on the
    // system of record.

    /** All observations for a subject, optionally narrowed to one encounter. Unpaged from pct. */
    public JsonNode listObservationsForSubject(String patientCpid, String encounterId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/observations")
                .queryParam("patient_id", patientCpid);
        if (encounterId != null && !encounterId.isBlank()) {
            b.queryParam("encounter_id", encounterId);
        }
        log.debug("PCT: Listing observations for patient={}...",
                patientCpid.substring(0, Math.min(8, patientCpid.length())));
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.toUriString(), JsonNode.class);
        return extractData(response);
    }

    /** A set of observations taken at one contact — applied in one transaction by pct. */
    public JsonNode recordObservationsBatch(List<Map<String, Object>> observations) {
        String url = baseUrl + "/v1/observations/batch";
        log.info("PCT: Recording observation batch of {}", observations.size());
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, Map.of("observations", observations), JsonNode.class);
        return extractData(response);
    }

    /** Withdraw an observation (ENTERED_IN_ERROR). Idempotent on pct's side. */
    public JsonNode voidObservation(String observationId, String reason) {
        String url = baseUrl + "/v1/observations/" + observationId + "/void";
        log.info("PCT: Voiding observation={}", observationId);
        Map<String, Object> body = reason == null ? Map.of() : Map.of("reason", reason);
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

    // ── IMAM — acute malnutrition treatment episodes ────────────

    public JsonNode listImamEpisodes(String patientCpid) {
        String url = baseUrl + "/v1/imam/episodes?patient_id=" + patientCpid;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getImamEpisode(String episodeId) {
        String url = baseUrl + "/v1/imam/episodes/" + episodeId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode enrolImamEpisode(Map<String, Object> body) {
        String url = baseUrl + "/v1/imam/episodes";
        log.info("PCT: Enrolling IMAM episode for patient={} classification={}",
                body.get("patient_id"), body.get("source_classification"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordImamVisit(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/imam/episodes/" + episodeId + "/visits";
        log.info("PCT: Recording IMAM review for episode={} attended={}", episodeId, body.get("attended"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode closeImamEpisode(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/imam/episodes/" + episodeId + "/outcome";
        log.info("PCT: Closing IMAM episode={} outcome={}", episodeId, body.get("outcome"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode imamTracingQueue(String facilityId) {
        String url = baseUrl + "/v1/imam/tracing-queue"
                + (facilityId == null || facilityId.isBlank() ? "" : "?facility_id=" + facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical Depth — Discharge Clearances (strangler migration) ──

    // Resuscitation lives in inpatient-service, not PCT. The eight methods that used to sit
    // here called pct-service at /v1/emergency/{activationId}/... — a path pct has never served,
    // for a record it has never owned. They had zero callers; the working resuscitation lane is
    // InpatientServiceClient against /internal/v1/emergency/**, which is what the shell actually
    // uses. Deleted rather than repointed: repointing would create a second client path to one
    // truth, and the emergency pack is about to serve the emergency EPISODE at /v1/emergency on
    // pct, so dead resuscitation methods squatting on that exact namespace would collide
    // semantically with it. Found by DownstreamRouteContractTest.

    // ── Emergency episode + acceptance handshake (emergency pack, W19b) ─────────────────────
    //
    // pct.EmergencyEpisodeController at /v1/emergency/**. Never proxied before this wave —
    // DownstreamRouteContractTest's own comment above named this exact gap. Care-first mint (open),
    // the FSM (transition/arrive/location), and the acceptance handshake (handover request/accept/
    // decline/expire) — the invariant that responsibility transfers ONLY on the accepting party's
    // own write, never a request or a timeout.

    public JsonNode openEmergencyEpisode(Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEmergencyEpisode(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** The facility board: every open episode. */
    public JsonNode emergencyEpisodeBoard(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/emergency/episodes")
                .queryParam("facilityId", facilityId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode arriveEmergencyEpisode(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/arrive";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode transitionEmergencyEpisode(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/transition";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode confirmEmergencyEpisodeLocation(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/location";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** THE ACCEPTANCE HANDSHAKE — requesting is not accepting; see pct's own EmergencyHandoverEntity doc. */
    public JsonNode requestEmergencyHandover(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/handover";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyHandoverHistory(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/handovers";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** The ONE write that closes the episode as CLOSED_HANDED_OVER — carries the accepting party's own record id. */
    public JsonNode acceptEmergencyHandover(UUID handoverId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/handovers/" + handoverId + "/accept";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode declineEmergencyHandover(UUID handoverId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/handovers/" + handoverId + "/decline";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode expireEmergencyHandover(UUID handoverId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/handovers/" + handoverId + "/expire";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency disposition + observation stay (W9b) ───────────────────────────────────────

    public JsonNode recordEmergencyDisposition(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/disposition";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEmergencyDisposition(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/disposition";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency alerts (W5/W5b) ─────────────────────────────────────────────────────────────

    public JsonNode emergencyAlertBoard(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/emergency/alerts")
                .queryParam("facilityId", facilityId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyEpisodeAlerts(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/alerts";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode acknowledgeEmergencyAlert(UUID alertId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/alerts/" + alertId + "/acknowledge";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode respondEmergencyAlert(UUID alertId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/alerts/" + alertId + "/respond";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** MCI bulk-mint (W11): mint one emergency_episode per not-yet-minted casualty on an incident. */
    public JsonNode mciBulkMint(UUID incidentId, UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/emergency/mci/" + incidentId + "/bulk-mint")
                .queryParam("facilityId", facilityId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /** Episode-by-state + alert-by-severity counts for one facility (W10 command view). */
    public JsonNode emergencyCommandSummary(UUID facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/emergency/command-summary")
                .queryParam("facilityId", facilityId)
                .toUriString();
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Closing an alert is a separate authority from acknowledging or responding to it: ack says a human
     * saw it, respond says a human acted, close says the condition that raised it is gone. Only close
     * clears the partial unique index that suppresses a duplicate open alert, so without this method the
     * suppression never lifts and the same hazard can never re-raise.
     */
    public JsonNode closeEmergencyAlert(UUID alertId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/alerts/" + alertId + "/close";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency order sets (W7b) ────────────────────────────────────────────────────────────
    //
    // pct.EmergencyOrderSetController has been live since W7b and was reachable from nothing at all —
    // no BFF client method, no BFF route. A declined order-set item carries a mandatory reason, which
    // is the whole clinical point of the table; unreachable, that reason could never be captured.

    public JsonNode invokeEmergencyOrderSet(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/order-sets";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyOrderSets(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/order-sets";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEmergencyOrderSet(UUID instanceId) {
        String url = baseUrl + "/v1/emergency/order-sets/" + instanceId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode orderEmergencyOrderSetItem(UUID itemId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/order-sets/items/" + itemId + "/order";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Declining is a first-class clinical act and pct requires a reason for it. */
    public JsonNode declineEmergencyOrderSetItem(UUID itemId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/order-sets/items/" + itemId + "/decline";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency medication administration (W8a) ──────────────────────────────────────────────
    //
    // pct.EmergencyMedicationAdminController — the table that exists specifically to kill the old
    // medications_json blob. Also unreachable until this wave.

    public JsonNode recordEmergencyMedication(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/medications";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyMedications(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/medications";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency observation / short stay (W9b) ───────────────────────────────────────────────
    //
    // Not an admission and not a discharge — the genuinely unowned middle state. The disposition
    // proxy landed in W9b's BFF pass; the observation stay it depends on did not.

    public JsonNode startEmergencyObservationStay(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/observation-stays";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyObservationStays(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/observation-stays";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode endEmergencyObservationStay(UUID stayId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/observation-stays/" + stayId + "/end";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Emergency identity link (W12) ──────────────────────────────────────────────────────────
    //
    // Append-only, link-never-overwrite: both identities are retained forever. Unreachable from the
    // experience layer, the unknown patient recorded at the ED door could never be resolved by the
    // clinician who recognises them.

    public JsonNode linkEmergencyIdentity(UUID episodeId, Map<String, Object> body) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/identity-link";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode emergencyIdentityLinks(UUID episodeId) {
        String url = baseUrl + "/v1/emergency/episodes/" + episodeId + "/identity-link";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── ED intake + diagnostics acts (pre-existing pct routes, never proxied) ──────────────────

    public JsonNode upsertEdPreArrival(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/pre-arrival";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode edArrivalFromEms(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/arrival";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode orderEdDiagnostic(UUID visitId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/visits/" + visitId + "/diagnostics";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode reconcileEdCriticalResult(Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/diagnostics/reconcile-critical";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Acting on a critical result is what pct's close CHECK requires before the order may be closed. */
    public JsonNode actOnEdDiagnostic(UUID linkId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/diagnostics/" + linkId + "/act";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode closeEdDiagnostic(UUID linkId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/diagnostics/" + linkId + "/close";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
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

    // ── ED trauma-team roster / acknowledgement / escalation (WU2) ───────────────
    // Pure passthrough to pct-service EdVisitController; trust context is forwarded by
    // the RestTemplate interceptor. No composition — the sovereign roster is authoritative.

    public JsonNode edTraumaTeam(String traumaId) {
        String url = baseUrl + "/v1/ed/trauma/" + traumaId + "/team";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode edTraumaTeamAck(String traumaId, String memberId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/trauma/" + traumaId + "/team/" + memberId + "/ack";
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    public JsonNode edTraumaTeamEscalate(String traumaId, Map<String, Object> body) {
        String url = baseUrl + "/v1/ed/trauma/" + traumaId + "/team/escalate";
        return extractData(restTemplate.postForEntity(url, body != null ? body : Map.of(), JsonNode.class));
    }

    // ── Blood-readiness gate + ED pre-arrival board (WU4) — passthrough to pct-service ──
    // Read-through views: blood readiness reflects MADI truth (never fabricated); the
    // pre-arrival board lists inbound EMS patients with prehospital snapshots.

    public JsonNode edBloodReadiness(String orderId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ed/blood-readiness")
                .queryParam("orderId", orderId).toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode edPreArrival(UUID facilityId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/v1/ed/pre-arrival");
        if (facilityId != null) b.queryParam("facilityId", facilityId);
        return extractData(restTemplate.getForEntity(b.toUriString(), JsonNode.class));
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

    // ── Sorting desk (R7/G12) — pre-triage visit-type sort between ARRIVED and TRIAGE ──

    public JsonNode getSortingVisitTypes(String context) {
        String url = baseUrl + "/v1/sorting-desk/visit-types"
                + (context != null && !context.isBlank() ? "?context=" + context : "");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode sortJourney(String journeyId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/sorting-desk/journeys/" + journeyId + "/sort", body, JsonNode.class));
    }

    public JsonNode getLatestSort(String journeyId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/sorting-desk/journeys/" + journeyId, JsonNode.class));
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

    public JsonNode listEncounterFormExtractions(String encounterId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/encounters/" + encounterId + "/form-extractions", JsonNode.class));
    }

    // ── HIV/TB programme enrolment (W3) ─────────────────────────
    //
    // pct-service serves these at /v1/programme-enrolments. HIV enrolments are confidential
    // (derived from the programme); the response carries a `confidential` flag the surfaces read.

    public JsonNode listProgrammeEnrolments(String subjectCpid, boolean openOnly) {
        String url = baseUrl + "/v1/programme-enrolments?subject_cpid=" + subjectCpid
                + "&open_only=" + openOnly;
        log.debug("PCT: listing programme enrolments for subject={}", subjectCpid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    // ── Structured examinations (brief.md §7) ───────────────────
    //
    // pct serves these at /v1/examinations. The read carries a `coverage` block naming what was NOT
    // examined; the BFF must forward it intact, because a findings list on its own shows only what
    // was looked at and absence has no row.

    public JsonNode listExaminations(String subjectCpid) {
        String url = baseUrl + "/v1/examinations?subject_cpid="
                + java.net.URLEncoder.encode(subjectCpid, java.nio.charset.StandardCharsets.UTF_8);
        log.debug("PCT: listing examinations for subject={}", subjectCpid);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode getExamination(String examinationId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/examinations/" + examinationId, JsonNode.class));
    }

    public JsonNode examinationVocabulary() {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/examinations/vocabulary", JsonNode.class));
    }

    public JsonNode recordExamination(Object body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/examinations", body, JsonNode.class));
    }

    // ── Consultation and MDT (brief.md §14) ─────────────────────
    //
    // Every consultation response carries owning_service and a sentence saying what it means. The
    // BFF must forward both: the failure this record prevents is a reader assuming the answering
    // team took the patient, and stripping the note to tidy the payload reintroduces it.

    public JsonNode listConsultations(String subjectCpid) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/consultations?subject_cpid="
                + java.net.URLEncoder.encode(subjectCpid, java.nio.charset.StandardCharsets.UTF_8),
                JsonNode.class));
    }

    public JsonNode consultationWorklist(String service) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/consultations/worklist?service="
                + java.net.URLEncoder.encode(service, java.nio.charset.StandardCharsets.UTF_8),
                JsonNode.class));
    }

    public JsonNode requestConsultation(Object body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/consultations", body, JsonNode.class));
    }

    public JsonNode answerConsultation(String consultationId, Object body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/consultations/" + consultationId + "/answer", body, JsonNode.class));
    }

    public JsonNode declineConsultation(String consultationId, Object body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/consultations/" + consultationId + "/decline", body, JsonNode.class));
    }

    public JsonNode listMdtDecisions(String subjectCpid) {
        return extractData(restTemplate.getForEntity(baseUrl + "/v1/consultations/mdt?subject_cpid="
                + java.net.URLEncoder.encode(subjectCpid, java.nio.charset.StandardCharsets.UTF_8),
                JsonNode.class));
    }

    public JsonNode recordMdtDecision(Object body) {
        return extractData(restTemplate.postForEntity(baseUrl + "/v1/consultations/mdt", body, JsonNode.class));
    }

    public JsonNode getProgrammeEnrolment(String enrolmentId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/programme-enrolments/" + enrolmentId, JsonNode.class));
    }

    /**
     * The chronic-disease register for one programme at one facility — a recall worklist.
     *
     * <p>pct refuses this with 403 for programmes on the confidential lane, because a patient-level
     * listing names everyone on it as having the condition and the classification that would restrict
     * it is not enforcing. That refusal must reach the caller as a refusal: turning it into an empty
     * list here would tell a clinician nobody is on the register.</p>
     */
    public JsonNode programmeRegister(String programme, String facilityId, java.util.List<String> statuses) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/v1/programme-enrolments/register?programme=")
                .append(java.net.URLEncoder.encode(programme, java.nio.charset.StandardCharsets.UTF_8));
        if (facilityId != null && !facilityId.isBlank()) {
            url.append("&facility_id=")
               .append(java.net.URLEncoder.encode(facilityId, java.nio.charset.StandardCharsets.UTF_8));
        }
        if (statuses != null) {
            for (String status : statuses) {
                url.append("&status=")
                   .append(java.net.URLEncoder.encode(status, java.nio.charset.StandardCharsets.UTF_8));
            }
        }
        log.debug("PCT: register programme={} facility={}", programme, facilityId);
        return extractData(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    public JsonNode enrolProgramme(Map<String, Object> body) {
        log.info("PCT: enrolling programme={} for subject={}", body.get("programme"), body.get("subject_cpid"));
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/programme-enrolments", body, JsonNode.class));
    }

    public JsonNode updateProgrammeStatus(String enrolmentId, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/programme-enrolments/" + enrolmentId + "/status", body, JsonNode.class));
    }

    public JsonNode exitProgramme(String enrolmentId, Map<String, Object> body) {
        log.info("PCT: exiting programme enrolment={} reason={}", enrolmentId, body.get("exit_reason"));
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/programme-enrolments/" + enrolmentId + "/exit", body, JsonNode.class));
    }

    public JsonNode listProgrammeRegimens(String enrolmentId) {
        return extractData(restTemplate.getForEntity(
                baseUrl + "/v1/programme-enrolments/" + enrolmentId + "/regimens", JsonNode.class));
    }

    public JsonNode startProgrammeRegimen(String enrolmentId, Map<String, Object> body) {
        log.info("PCT: starting regimen on enrolment={} code={}", enrolmentId, body.get("regimen_code"));
        return extractData(restTemplate.postForEntity(
                baseUrl + "/v1/programme-enrolments/" + enrolmentId + "/regimens", body, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
