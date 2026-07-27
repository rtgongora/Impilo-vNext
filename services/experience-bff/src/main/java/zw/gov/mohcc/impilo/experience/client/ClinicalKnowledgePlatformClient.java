package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ClinicalPlatformProperties;

import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Clinical Knowledge Platform (EDLIZ-aligned rules, assistant, pathways, source PDF ingestion).
 */
@Component
public class ClinicalKnowledgePlatformClient {

    private static final Logger log = LoggerFactory.getLogger(ClinicalKnowledgePlatformClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ClinicalKnowledgePlatformClient(
            RestTemplate serviceRestTemplate,
            ClinicalPlatformProperties clinicalPlatform) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = clinicalPlatform.baseUrl();
    }

    public JsonNode assistantAsk(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/assistant/ask";
        log.debug("Clinical platform: assistant ask");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Deterministic clinical-rule evaluation over a supplied patient context (CDS alerts). */
    public JsonNode rulesEvaluate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/rules/evaluate";
        log.debug("Clinical platform: rules evaluate");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Governed HIV/TB programme decision support (W3). Advisory: interprets the supplied programme
     * facts (viral load, CD4, WHO stage, treatment month, sputum) against the versioned DAK content.
     */
    public JsonNode programmeGuidance(String programme, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/programme/" + programme + "/evaluate";
        log.debug("Clinical platform: programme guidance {}", programme);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Governed adult-medicine CDS (W4-W6): cvd-risk, deprescribing, procedure-indication, icope,
     * mhgap, antimicrobial-stewardship, palliative, oncology. Advisory; evaluates the supplied facts
     * against the versioned DAK content for the topic.
     */
    public JsonNode medicineCdsEvaluate(String topic, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/cds/" + topic + "/evaluate";
        log.debug("Clinical platform: medicine CDS {}", topic);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Context-aware interpretation of vitals/labs against patient-appropriate reference intervals. */
    public JsonNode interpretationEvaluate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/interpretation/evaluate";
        log.debug("Clinical platform: interpretation evaluate");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Grounded, fail-closed AI summary of the supplied deterministic CDS alerts (insight or null). */
    public JsonNode cdsSummary(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/cds/summary";
        log.debug("Clinical platform: cds summary");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Record a clinician override of a recommendation against its audit trace. */
    public JsonNode recordOverride(Map<String, Object> body, String actorId) {
        String url = baseUrl + "/internal/v1/clinical/audit/overrides";
        log.debug("Clinical platform: record override");
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        if (actorId != null && !actorId.isBlank()) {
            headers.set("x-actor-id", actorId);
        }
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, new org.springframework.http.HttpEntity<>(body, headers), JsonNode.class);
        return extractData(response);
    }

    public JsonNode getTrace(String traceId) {
        String url = baseUrl + "/internal/v1/clinical/assistant/traces/" + traceId;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode prescribingEvaluate(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/prescribing/evaluate";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listPathways() {
        String url = baseUrl + "/internal/v1/clinical/pathways";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode startPathwaySession(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/pathways/sessions";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    public JsonNode advancePathwaySession(UUID sessionId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/pathways/sessions/" + sessionId + "/advance";
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** PDF → source_sections for a {@code source_documents} row (operator tooling). */
    public JsonNode ingestPdfSections(UUID documentId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/source/documents/" + documentId + "/ingest-pdf";
        log.debug("Clinical platform: ingest PDF sections documentId={}", documentId);
        Object payload = body != null ? body : Map.of();
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, payload, JsonNode.class);
        return extractData(response);
    }

    public JsonNode sourceIngestionSummary(UUID documentId) {
        String url = baseUrl + "/internal/v1/clinical/source/documents/" + documentId + "/ingestion-summary";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode defaultEdlizSourceDocumentId() {
        String url = baseUrl + "/internal/v1/clinical/source/edliz-default-document-id";
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode listKnowledgeReviewItems(String status) {
        String url = baseUrl + "/internal/v1/clinical/curation/review-items?status=" + status;
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    public JsonNode decideKnowledgeReviewItem(UUID id, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/curation/review-items/" + id + "/decision";
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
