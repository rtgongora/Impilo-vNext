package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for the Inpatient sovereign service (admissions, transfers, discharges).
 *
 * <p>Trust headers are forwarded by the RestTemplate interceptor in
 * {@link zw.gov.mohcc.impilo.experience.config.ServiceClientConfig}.</p>
 */
@Component
public class InpatientServiceClient {

    private static final Logger log = LoggerFactory.getLogger(InpatientServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public InpatientServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints,
                                  ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.inpatientBaseUrl();
        this.objectMapper = objectMapper;
    }

    public JsonNode createAdmission(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions";
        log.info("INPATIENT: createAdmission operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getAdmission(String id) {
        String url = baseUrl + "/internal/v1/admissions/" + id;
        log.info("INPATIENT: getAdmission operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getActiveAdmissions(String subjectCpid, String facilityId) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/admissions/active")
                .queryParam("subject_cpid", subjectCpid)
                .queryParam("facility_id", facilityId)
                .encode()
                .toUriString();
        log.info("INPATIENT: getActiveAdmissions operation [subjectCpid={}, facilityId={}]",
                subjectCpid != null ? subjectCpid.substring(0, Math.min(8, subjectCpid.length())) + "..." : null,
                facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    /**
     * Resolved current inpatient location for a patient (ward + bed with labels), or {@code null}
     * when the patient is not currently admitted (upstream returns 204). Backs the patient-location
     * badge (G053). {@code facilityId} is optional.
     */
    public JsonNode getCurrentLocation(String subjectCpid, String facilityId) {
        UriComponentsBuilder b = UriComponentsBuilder
                .fromHttpUrl(baseUrl + "/internal/v1/admissions/current-location")
                .queryParam("subject_cpid", subjectCpid);
        Optional.ofNullable(facilityId)
                .filter(s -> !s.isBlank())
                .ifPresent(f -> b.queryParam("facility_id", f));
        String url = b.encode().toUriString();
        log.info("INPATIENT: getCurrentLocation operation [subjectCpid={}, facilityId={}]",
                subjectCpid != null ? subjectCpid.substring(0, Math.min(8, subjectCpid.length())) + "..." : null,
                facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode dischargeAdmission(String id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions/" + id + "/discharge";
        log.info("INPATIENT: dischargeAdmission operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, request == null || request.isEmpty() ? null : request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode transferPatient(String id, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/admissions/" + id + "/transfer";
        log.info("INPATIENT: transferPatient operation [id={}]", id);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    /**
     * Lists admissions. When {@code patientCpid} is set, filters using {@code subjectCpid} query
     * (forward-compatible; the sovereign admission list API currently supports
     * {@code facilityId} and {@code status} only).
     */
    public JsonNode listAdmissions(String patientCpid) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/admissions");
        Optional.ofNullable(patientCpid)
                .filter(s -> !s.isBlank())
                .ifPresent(cpid -> b.queryParam("subjectCpid", cpid));
        String url = b.encode().toUriString();
        log.info("INPATIENT: listAdmissions operation [patientCpid={}]",
                patientCpid != null ? patientCpid.substring(0, Math.min(8, patientCpid.length())) + "..." : null);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listWardRounds(String admissionId) {
        String url = baseUrl + "/internal/v1/admissions/" + admissionId + "/ward-rounds";
        log.info("INPATIENT: listWardRounds operation [admissionId={}]", admissionId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startWardRound(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ward-rounds";
        log.info("INPATIENT: startWardRound operation");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addWardRoundEntry(String roundId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ward-rounds/" + roundId + "/entries";
        log.info("INPATIENT: addWardRoundEntry operation [roundId={}]", roundId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listWardRoundsByWard(String wardId) {
        String url = baseUrl + "/internal/v1/ward-rounds?wardId=" + wardId;
        log.info("INPATIENT: listWardRoundsByWard operation [wardId={}]", wardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Bed/Ward Management (strangler migration) ─────────────

    public JsonNode listWards(String facilityId) {
        String url = baseUrl + "/internal/v1/beds/wards?facility_id=" + facilityId;
        log.info("INPATIENT: listWards operation [facilityId={}]", facilityId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listBeds(String facilityId, String wardId, String status) {
        StringBuilder url = new StringBuilder(baseUrl + "/internal/v1/beds?facility_id=" + facilityId);
        if (wardId != null) url.append("&ward_id=").append(wardId);
        if (status != null) url.append("&status=").append(status);
        log.info("INPATIENT: listBeds operation [facilityId={}, wardId={}]", facilityId, wardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateBedStatus(String bedId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/status";
        log.info("INPATIENT: updateBedStatus operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode assignPatientToBed(String bedId, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/assign";
        log.info("INPATIENT: assignPatientToBed operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, request, JsonNode.class);
        return extractData(response);
    }

    public JsonNode dischargeBed(String bedId) {
        String url = baseUrl + "/internal/v1/beds/" + bedId + "/discharge";
        log.info("INPATIENT: dischargeBed operation [bedId={}]", bedId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Clinical depth (sovereign inpatient-service) ────────────────

    public JsonNode listCarePlans(String patientId) {
        String url = baseUrl + "/internal/v1/care-plans?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode createCarePlan(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/care-plans";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode addCarePlanGoal(String planId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/care-plans/" + planId + "/goals";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode updateCarePlanGoal(String planId, String goalId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/care-plans/" + planId + "/goals/" + goalId + "/update";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode addCarePlanIntervention(String planId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/care-plans/" + planId + "/interventions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode performCarePlanIntervention(String planId, String interventionId) {
        String url = baseUrl + "/internal/v1/care-plans/" + planId + "/interventions/" + interventionId + "/perform";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return response.getBody();
    }

    public JsonNode getFluidBalance(String patientId, String date) {
        StringBuilder url = new StringBuilder(baseUrl + "/internal/v1/fluid-balance?patientId=" + patientId);
        if (date != null) url.append("&date=").append(date);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordFluidBalance(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/fluid-balance";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordObservation(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/observations";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getObservations(String patientId) {
        String url = baseUrl + "/internal/v1/observations?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getWardChartEntries(String chartType, String patientId) {
        String url = baseUrl + "/internal/v1/ward-charts/" + chartType + "/entries?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordWardChartEntry(String chartType, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/ward-charts/" + chartType + "/entries";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getWardChartActivity(String patientId) {
        String url = baseUrl + "/internal/v1/ward-charts/activity?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listMar(String patientId) {
        String url = baseUrl + "/internal/v1/mar?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode syncMarSchedule(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mar/sync";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode administerMedication(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/mar/administer";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode recordEws(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/ews";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getEws(String patientId) {
        String url = baseUrl + "/internal/v1/ews?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listEmergencyActivations() {
        String url = baseUrl + "/internal/v1/emergency/activations";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode activateEmergency(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/activate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode endEmergency(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/end";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode logEmergencyAction(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/action";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listEmergencyActions(String activationId, String actionType) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/actions";
        if (actionType != null && !actionType.isBlank()) {
            url += "?actionType=" + actionType;
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordResuscitation(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/resuscitation";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getResuscitation(String activationId) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/resuscitation";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordApgar(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/apgar";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode getApgar(String patientId) {
        String url = baseUrl + "/internal/v1/apgar?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listHandovers(String facilityId, String status) {
        String url = baseUrl + "/internal/v1/handover?facilityId=" + facilityId
                + (status != null && !status.isBlank() ? "&status=" + status : "");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode submitHandover(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/handover";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode acceptTakeover(String handoverId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/handover/" + handoverId + "/takeover";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode createWardAlert(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/ward-alerts";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listWardAlerts(String wardId, String status) {
        String url = baseUrl + "/internal/v1/ward-alerts?wardId=" + wardId + "&status=" + status;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode acknowledgeWardAlert(String alertId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/ward-alerts/" + alertId + "/acknowledge";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode requestTransfer(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/transfers";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode acceptTransfer(String transferId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/transfers/" + transferId + "/accept";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listTransfers(String patientId) {
        StringBuilder url = new StringBuilder(baseUrl + "/internal/v1/transfers");
        if (patientId != null && !patientId.isBlank()) {
            url.append("?patientId=").append(patientId);
        }
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url.toString(), JsonNode.class);
        return response.getBody();
    }

    public JsonNode initDischargeClearances(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/discharge-clearances/init";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getDischargeClearances(String encounterId) {
        String url = baseUrl + "/internal/v1/discharge-clearances?encounterId=" + encounterId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode clearDischargeClearance(String clearanceId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/discharge-clearances/" + clearanceId + "/clear";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode waiveDischargeClearance(String clearanceId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/discharge-clearances/" + clearanceId + "/waive";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startResuscitationPhase(String activationId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/phases";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode endResuscitationPhase(String activationId, String phaseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/phases/" + phaseId + "/end";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listResuscitationPhases(String activationId) {
        String url = baseUrl + "/internal/v1/emergency/" + activationId + "/phases";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode createProcedureEpisode(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listProcedureEpisodes(String patientId) {
        String url = baseUrl + "/internal/v1/procedures/episodes?patientId=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listProcedureHistory(String patientId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/history?patient_id=" + patientId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getProcedureEpisode(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode submitProcedurePreop(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/preop";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode completeProcedureChecklist(String episodeId, String phase, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/checklist/" + phase;
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode startProcedureEpisode(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/start";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordProcedureIntraop(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/intraop/events";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode enterProcedurePacu(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/pacu";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode completeProcedurePostop(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/postop";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode completeProcedureEpisode(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/complete";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode bindProcedureConsentEvidence(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/consent-evidence";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getProcedureConsentStatus(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/consent";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode linkProcedureDocument(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/documents";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listProcedureDocuments(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/documents";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordProcedureConsumable(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/consumables";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listProcedureConsumables(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/consumables";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getAnaesthesiaScoreSuggestions(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/anaesthesia/score-suggestions";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listAnaesthesiaScores(String episodeId) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/anaesthesia/scores";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordAnaesthesiaScore(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/anaesthesia/scores";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordIntraopVitals(String episodeId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/procedures/episodes/" + episodeId + "/intraop/vitals";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }


    // ── Deterioration escalations (WS#6) ─────────────────────────────

    public JsonNode listEscalations(String patientId, String wardId) {
        org.springframework.web.util.UriComponentsBuilder b =
                org.springframework.web.util.UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/escalations");
        if (patientId != null && !patientId.isBlank()) b.queryParam("patientId", patientId);
        if (wardId != null && !wardId.isBlank()) b.queryParam("wardId", wardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(b.encode().toUriString(), JsonNode.class);
        return response.getBody();
    }

    public JsonNode acknowledgeEscalation(String escalationId) {
        String url = baseUrl + "/internal/v1/escalations/" + escalationId + "/acknowledge";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return response.getBody();
    }

    public JsonNode respondEscalation(String escalationId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/escalations/" + escalationId + "/respond";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    // ── Discharge summary (WS#6) ─────────────────────────────────────

    public JsonNode saveDischargeSummary(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/discharge-summary";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getDischargeSummary(String encounterId) {
        String url = baseUrl + "/internal/v1/discharge-summary?encounterId=" + encounterId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode finaliseDischargeSummary(String encounterId) {
        String url = baseUrl + "/internal/v1/discharge-summary/" + encounterId + "/finalise";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, Map.of(), JsonNode.class);
        return response.getBody();
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }

    // ── Theatre & perioperative depth (WS#6 theatre seam) ──────────────────────────
    public JsonNode theatreQueue() {
        String url = baseUrl + "/internal/v1/theatre/queue";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode intakeTheatreCase(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode setTheatreTriage(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/triage";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode evaluateTheatreReadiness(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/readiness";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listTheatreReadiness(String caseId) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/readiness";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode bookTheatreCase(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/book";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode startTheatreCase(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/start";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode draftTheatreNote(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/note";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode signTheatreNote(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/note/sign";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode getTheatreNote(String caseId) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/note";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode recordTheatrePacuDisposition(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/pacu/disposition";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode cancelTheatreCase(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/cancel";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode reportTheatreSafetyEvent(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/safety-events";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }

    public JsonNode listTheatreSafetyEvents(String caseId) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/safety-events";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return response.getBody();
    }

    public JsonNode routeTheatreDeath(String caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/theatre/cases/" + caseId + "/death";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return response.getBody();
    }
}
