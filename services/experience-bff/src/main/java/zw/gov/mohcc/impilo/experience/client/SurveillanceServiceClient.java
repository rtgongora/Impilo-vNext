package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP client for the Surveillance sovereign service (cases, signals, ingest, counters).
 */
@Component
public class SurveillanceServiceClient {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public SurveillanceServiceClient(RestTemplate serviceRestTemplate,
                                     ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.surveillanceBaseUrl();
    }

    public JsonNode listCases(String status, int page, int size) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/internal/v1/cases?page=").append(page).append("&size=").append(size);
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }
        log.info("Surveillance: list cases [status={}, page={}, size={}]", status, page, size);
        return extractData(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    /** Records an investigation update on a case (classification / outcome / lab result / note). */
    public JsonNode recordCaseUpdate(long caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/cases/" + caseId + "/updates";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    /** Links a traced contact to a case. */
    public JsonNode linkCaseContact(long caseId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/cases/" + caseId + "/contacts";
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listCaseContacts(long caseId) {
        String url = baseUrl + "/internal/v1/cases/" + caseId + "/contacts";
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    /** The outbreak line list — one row per case, with contact counts. */
    public JsonNode lineList(String caseType) {
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/internal/v1/cases/line-list")
                .queryParam("caseType", caseType == null ? "" : caseType)
                .toUriString();
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode listSignals() {
        String url = baseUrl + "/internal/v1/signals";
        log.info("Surveillance: list signals");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createSignal(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/signals";
        log.info("Surveillance: create signal [name={}, eventType={}]",
                request.get("name"), request.get("eventType"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode ingest(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/ingest";
        log.info("Surveillance: ingest [eventType={}]", request.get("eventType"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode getCounters(LocalDate from, LocalDate to) {
        StringBuilder url = new StringBuilder(baseUrl).append("/internal/v1/surveillance/counters");
        char sep = '?';
        if (from != null) {
            url.append(sep).append("from=").append(from);
            sep = '&';
        }
        if (to != null) {
            url.append(sep).append("to=").append(to);
        }
        log.info("Surveillance: get counters [from={}, to={}]", from, to);
        return extractData(restTemplate.getForEntity(url.toString(), JsonNode.class));
    }

    public JsonNode incrementCounter(Map<String, String> body) {
        String url = baseUrl + "/internal/v1/surveillance/counters";
        log.info("Surveillance: increment counter [facility_id={}]", body.get("facility_id"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode getAlerts(boolean unacknowledgedOnly) {
        String url = baseUrl + "/internal/v1/surveillance/alerts?unacknowledgedOnly=" + unacknowledgedOnly;
        log.info("Surveillance: get alerts [unacknowledgedOnly={}]", unacknowledgedOnly);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createAlertDefinition(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/surveillance/alerts/definitions";
        log.info("Surveillance: create alert definition [name={}]", body.get("name"));
        return extractData(restTemplate.postForEntity(url, body, JsonNode.class));
    }

    public JsonNode listAlertDefinitions() {
        String url = baseUrl + "/internal/v1/surveillance/alerts/definitions";
        log.info("Surveillance: list alert definitions");
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
