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
 * HTTP client for rito-quality-safety-service — quality, safety &amp; client voice.
 *
 * <p>Thin pass-through: the shared {@code serviceRestTemplate} forwards the inbound v1.1
 * trust headers (tenant/pod/request/correlation/actor + Authorization + Idempotency-Key)
 * so the downstream companion filter sees the same trust context as the BFF caller.
 */
@Component
public class RitoServiceClient {

    private static final Logger log = LoggerFactory.getLogger(RitoServiceClient.class);
    private static final String API = "/internal/v1/rito";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RitoServiceClient(RestTemplate serviceRestTemplate, ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.ritoBaseUrl();
    }

    // ── Cases ────────────────────────────────────────────────────────
    public JsonNode listCases(Map<String, String> filters) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/cases");
        if (filters != null) {
            filters.forEach((k, v) -> {
                if (v != null && !v.isBlank()) {
                    b.queryParam(k, v);
                }
            });
        }
        return get(b.toUriString(), "listCases");
    }

    public JsonNode getCase(String id) {
        return get(baseUrl + API + "/cases/" + id, "getCase");
    }

    public JsonNode createCase(Map<String, Object> body) {
        return post(baseUrl + API + "/cases", body, "createCase");
    }

    public JsonNode caseSubResource(String id, String sub) {
        return get(baseUrl + API + "/cases/" + id + "/" + sub, "case:" + sub);
    }

    public JsonNode caseAction(String id, String action, Map<String, Object> body) {
        return post(baseUrl + API + "/cases/" + id + "/" + action, body, "caseAction:" + action);
    }

    // ── Safety incident detail ───────────────────────────────────────
    public JsonNode recordSafetyIncident(String caseId, Map<String, Object> body) {
        return post(baseUrl + API + "/cases/" + caseId + "/safety-incident", body, "recordSafetyIncident");
    }

    public JsonNode getSafetyIncident(String caseId) {
        return get(baseUrl + API + "/cases/" + caseId + "/safety-incident", "getSafetyIncident");
    }

    public JsonNode sentinelIncidents() {
        return get(baseUrl + API + "/safety-incidents/sentinel", "sentinelIncidents");
    }

    // ── Quality signals ──────────────────────────────────────────────
    public JsonNode listSignals(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/quality-signals");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return get(b.toUriString(), "listSignals");
    }

    public JsonNode signalAction(String id, String action, Map<String, Object> body) {
        return post(baseUrl + API + "/quality-signals/" + id + "/" + action, body, "signalAction:" + action);
    }

    // ── Audits ───────────────────────────────────────────────────────
    public JsonNode listAudits(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/audits");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return get(b.toUriString(), "listAudits");
    }

    public JsonNode getAudit(String id) {
        return get(baseUrl + API + "/audits/" + id, "getAudit");
    }

    public JsonNode createAudit(Map<String, Object> body) {
        return post(baseUrl + API + "/audits", body, "createAudit");
    }

    public JsonNode auditAction(String id, String action, Map<String, Object> body) {
        return post(baseUrl + API + "/audits/" + id + "/" + action, body, "auditAction:" + action);
    }

    public JsonNode auditFindings(String id) {
        return get(baseUrl + API + "/audits/" + id + "/findings", "auditFindings");
    }

    public JsonNode listAuditTools() {
        return get(baseUrl + API + "/audit-tools", "listAuditTools");
    }

    public JsonNode createAuditTool(Map<String, Object> body) {
        return post(baseUrl + API + "/audit-tools", body, "createAuditTool");
    }

    // ── Improvement: CAPA + QI ───────────────────────────────────────
    public JsonNode listCorrectiveActions(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/corrective-actions");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return get(b.toUriString(), "listCorrectiveActions");
    }

    public JsonNode createCorrectiveAction(Map<String, Object> body) {
        return post(baseUrl + API + "/corrective-actions", body, "createCorrectiveAction");
    }

    public JsonNode correctiveActionAction(String id, String action, Map<String, Object> body) {
        return post(baseUrl + API + "/corrective-actions/" + id + "/" + action, body, "ca:" + action);
    }

    public JsonNode listQiPlans(String status) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/qi-plans");
        if (status != null && !status.isBlank()) {
            b.queryParam("status", status);
        }
        return get(b.toUriString(), "listQiPlans");
    }

    public JsonNode createQiPlan(Map<String, Object> body) {
        return post(baseUrl + API + "/qi-plans", body, "createQiPlan");
    }

    // ── Surveys ──────────────────────────────────────────────────────
    public JsonNode listSurveys() {
        return get(baseUrl + API + "/surveys", "listSurveys");
    }

    public JsonNode createSurvey(Map<String, Object> body) {
        return post(baseUrl + API + "/surveys", body, "createSurvey");
    }

    public JsonNode submitSurveyResponse(String surveyId, Map<String, Object> body) {
        return post(baseUrl + API + "/surveys/" + surveyId + "/responses", body, "submitSurveyResponse");
    }

    public JsonNode surveyAggregate(String surveyId) {
        return get(baseUrl + API + "/surveys/" + surveyId + "/aggregate", "surveyAggregate");
    }

    // ── Dashboards ───────────────────────────────────────────────────
    public JsonNode dashboard(String kind, String facilityId) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(baseUrl + API + "/dashboards/" + kind);
        if (facilityId != null && !facilityId.isBlank()) {
            b.queryParam("facility_id", facilityId);
        }
        return get(b.toUriString(), "dashboard:" + kind);
    }

    // ── Config ───────────────────────────────────────────────────────
    public JsonNode listSlaPolicies() {
        return get(baseUrl + API + "/config/sla-policies", "listSlaPolicies");
    }

    public JsonNode createSlaPolicy(Map<String, Object> body) {
        return post(baseUrl + API + "/config/sla-policies", body, "createSlaPolicy");
    }

    public JsonNode listEscalationRules() {
        return get(baseUrl + API + "/config/escalation-rules", "listEscalationRules");
    }

    // ── helpers ──────────────────────────────────────────────────────
    private JsonNode get(String url, String op) {
        log.debug("RITO {}: GET {}", op, url);
        ResponseEntity<JsonNode> r = restTemplate.getForEntity(url, JsonNode.class);
        return r.getBody();
    }

    private JsonNode post(String url, Map<String, Object> body, String op) {
        log.debug("RITO {}: POST {}", op, url);
        ResponseEntity<JsonNode> r = restTemplate.postForEntity(url, body, JsonNode.class);
        return r.getBody();
    }

    public JsonNode put(String url, Map<String, Object> body, String op) {
        log.debug("RITO {}: PUT {}", op, url);
        ResponseEntity<JsonNode> r = restTemplate.exchange(
                baseUrl + API + url, HttpMethod.PUT, new HttpEntity<>(body), JsonNode.class);
        return r.getBody();
    }
}
