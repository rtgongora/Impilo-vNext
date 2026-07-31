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

    /**
     * The multimorbidity view (brief.md §9) — eleven panels and seven detections over the whole
     * patient at once.
     *
     * <p>The body is passed through as built. A key omitted by the caller means that source could not
     * be obtained and the engine answers the detections depending on it as unanswered; a key present
     * with an empty array means the source was read and is empty. This client must not normalise
     * between the two.</p>
     */
    public JsonNode multimorbidityAssess(Object body) {
        String url = baseUrl + "/internal/v1/clinical/multimorbidity/assess";
        log.debug("Clinical platform: multimorbidity assess");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * The self-measured blood-pressure series verdict.
     *
     * <p>An assessment, not an intake: telemonitoring owns {@code tm_readings} and remains the single
     * SHR writer of monitoring-band Observations, so this call hands CKP a series it already holds and
     * gets back a clinical reading of it. The body is passed through as built — in particular a null
     * {@code dangerSignPresent} must stay null, because a danger sign that was never asked about is not
     * one that was denied, and this client must not normalise between the two.
     */
    public JsonNode smbpAssess(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/smbp/assess";
        log.debug("Clinical platform: SMBP series verdict");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Classifies one woman against the WHO maternal near-miss criteria.
     *
     * <p>The body is forwarded as built, and an omitted observation must stay omitted. Filling absent
     * fields with false here would turn "we never measured her creatinine" into "her creatinine was
     * normal", and a near-miss recorded as a normal birth improves the near-miss-to-death ratio while
     * removing the one case with the most to learn from.
     */
    public JsonNode nearMissClassify(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/near-miss/classify";
        log.debug("Clinical platform: maternal near-miss classify");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** The near-miss-to-death ratio and mortality index over a reporting period's cases. */
    public JsonNode nearMissIndicators(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/near-miss/indicators";
        log.debug("Clinical platform: maternal near-miss indicators");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** PPH first-response bundle checklist verdict. Stateless — caller persists the episode. */
    public JsonNode emergencyBundleAssessPph(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/emergency-bundles/pph/assess";
        log.debug("Clinical platform: PPH emergency bundle assess");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** Eclampsia first-response bundle checklist verdict. Stateless — caller persists the episode. */
    public JsonNode emergencyBundleAssessEclampsia(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/emergency-bundles/eclampsia/assess";
        log.debug("Clinical platform: eclampsia emergency bundle assess");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** The respectful-maternity-care measure set: prompts, scale and which items are reverse-scored. */
    public JsonNode respectfulCareInstrument() {
        String url = baseUrl + "/internal/v1/clinical/maternal/respectful-care/instrument";
        log.debug("Clinical platform: respectful maternity care instrument");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Normalises respectful-care answers into Rito-shaped domain scores.
     *
     * <p>Called rather than computed locally because which items are reverse-scored is ratifiable
     * content, not arithmetic. A copy of that list here would mean a content revision needed a BFF
     * release, and two callers could disagree about the polarity of the same stored number.
     */
    public JsonNode respectfulCareNormalise(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/maternal/respectful-care/normalise";
        log.debug("Clinical platform: respectful maternity care normalise");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /** The bedside calculators this platform serves, with the descriptors a form renders from. */
    public JsonNode listCalculators() {
        String url = baseUrl + "/internal/v1/clinical/calculators";
        log.debug("Clinical platform: list calculators");
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /** One calculator's description, including the fields a form must render and its caveats. */
    public JsonNode describeCalculator(String calculatorId) {
        String url = baseUrl + "/internal/v1/clinical/calculators/" + calculatorId;
        log.debug("Clinical platform: describe calculator {}", calculatorId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Runs a calculator over submitted values.
     *
     * <p>The body is passed through as built, and the response is passed back as returned. A
     * refusal — the calculator declining to produce a number, with a named reason and an
     * explanation — is a successful call, and this client must not convert one into an error or an
     * empty result. Both would present a stated clinical limitation as a system fault.</p>
     */
    public JsonNode evaluateCalculator(String calculatorId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/clinical/calculators/" + calculatorId + "/evaluate";
        log.debug("Clinical platform: evaluate calculator {}", calculatorId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, body != null ? body : Map.of(), JsonNode.class);
        return extractData(response);
    }

    /** Renal dosing awareness — advisory bands from {@code RenalDosingAdvisor}, not dose arithmetic. */
    public JsonNode renalDosingAdvise(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/medicine/renal-dosing/advise";
        log.debug("Clinical platform: renal dosing advise");
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                url, body != null ? body : Map.of(), JsonNode.class);
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
