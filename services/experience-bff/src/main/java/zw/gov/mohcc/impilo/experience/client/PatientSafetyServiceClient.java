package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for patient-safety-service (pharmacovigilance SoR). Thin proxy over the internal v1
 * routes; v1.1 trust headers and the Idempotency-Key are forwarded by the shared RestTemplate
 * interceptor, so mutations satisfy the downstream contract.
 */
@Component
public class PatientSafetyServiceClient {

    private static final Logger log = LoggerFactory.getLogger(PatientSafetyServiceClient.class);
    private static final String BASE = "/internal/v1/patient-safety";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PatientSafetyServiceClient(RestTemplate serviceRestTemplate,
                                      ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.patientSafetyBaseUrl();
    }

    // ---- reports ------------------------------------------------------------

    public JsonNode listReports(String status, String reporterActorId, String facilityId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + BASE + "/reports");
        if (status != null) b.queryParam("status", status);
        if (reporterActorId != null) b.queryParam("reporter_actor_id", reporterActorId);
        if (facilityId != null) b.queryParam("facility_id", facilityId);
        return get(b.toUriString());
    }

    public JsonNode getReport(String id) {
        return get(baseUrl + BASE + "/reports/" + id);
    }

    public JsonNode createReport(Map<String, Object> body) {
        return post(baseUrl + BASE + "/reports", body);
    }

    public JsonNode updateReport(String id, Map<String, Object> body) {
        return patch(baseUrl + BASE + "/reports/" + id, body);
    }

    public JsonNode addProduct(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/reports/" + id + "/products", body);
    }

    public JsonNode addEvent(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/reports/" + id + "/events", body);
    }

    public JsonNode addAttachment(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/reports/" + id + "/attachments", body);
    }

    public JsonNode submitReport(String id) {
        return post(baseUrl + BASE + "/reports/" + id + "/submit", null);
    }

    // ---- cases --------------------------------------------------------------

    public JsonNode listCases(String status, String priority) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + BASE + "/cases");
        if (status != null) b.queryParam("status", status);
        if (priority != null) b.queryParam("priority", priority);
        return get(b.toUriString());
    }

    public JsonNode getCase(String id) {
        return get(baseUrl + BASE + "/cases/" + id);
    }

    public JsonNode triage(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/triage", body);
    }

    public JsonNode reviewAction(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/review-actions", body);
    }

    public JsonNode requestFollowUp(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/follow-up", body);
    }

    public JsonNode followUpResponse(String id, String followUpId, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/follow-up/" + followUpId + "/response", body);
    }

    public JsonNode vigiflowManualEntry(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/vigiflow-manual-entry", body);
    }

    public JsonNode markExportReady(String id) {
        return post(baseUrl + BASE + "/cases/" + id + "/mark-export-ready", null);
    }

    public JsonNode closeCase(String id, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + id + "/close", body);
    }

    // ---- MCAZ workbench -----------------------------------------------------

    public JsonNode mcazWorkbench() {
        return get(baseUrl + BASE + "/mcaz/workbench");
    }

    public JsonNode incomingQueue() {
        return get(baseUrl + BASE + "/mcaz/incoming-queue");
    }

    public JsonNode priorityQueue() {
        return get(baseUrl + BASE + "/mcaz/priority-queue");
    }

    // ---- investigations -----------------------------------------------------

    public JsonNode openInvestigation(String caseId, Map<String, Object> body) {
        return post(baseUrl + BASE + "/cases/" + caseId + "/investigation", body);
    }

    public JsonNode getInvestigation(String caseId) {
        return get(baseUrl + BASE + "/cases/" + caseId + "/investigation");
    }

    public JsonNode updateInvestigation(String investigationId, Map<String, Object> body) {
        return patch(baseUrl + BASE + "/investigations/" + investigationId, body);
    }

    // ---- config -------------------------------------------------------------

    public JsonNode vigiMobileLinks() {
        return get(baseUrl + BASE + "/config/vigimobile-links");
    }

    public JsonNode adapters() {
        return get(baseUrl + BASE + "/config/adapters");
    }

    // ---- transport helpers --------------------------------------------------

    private JsonNode get(String url) {
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode post(String url, Map<String, Object> body) {
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    private JsonNode patch(String url, Map<String, Object> body) {
        ResponseEntity<JsonNode> response =
                restTemplate.exchange(url, HttpMethod.PATCH, new HttpEntity<>(body), JsonNode.class);
        return extractData(response);
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        JsonNode body = response.getBody();
        if (body != null && body.has("data")) {
            return body.get("data");
        }
        return body;
    }
}
