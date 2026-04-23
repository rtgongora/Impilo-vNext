package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.Map;

/**
 * HTTP client for the Reporting sovereign service.
 */
@Component
public class ReportingServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ReportingServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ReportingServiceClient(RestTemplate serviceRestTemplate,
                                  ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.reportingBaseUrl();
    }

    public JsonNode createReport(Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/reports";
        log.info("Reporting: create report [key={}]", request.get("reportKey"));
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    public JsonNode runReport(String reportKey, Map<String, Object> requestOrNull) {
        String url = baseUrl + "/internal/v1/reports/" + reportKey + "/run";
        log.info("Reporting: run report [key={}]", reportKey);
        return extractData(restTemplate.postForEntity(url, requestOrNull, JsonNode.class));
    }

    public JsonNode listReportRuns(String reportKey, int page, int size) {
        String url = baseUrl + "/internal/v1/reports/" + reportKey + "/runs?page=" + page + "&size=" + size;
        log.info("Reporting: list runs [key={}, page={}, size={}]", reportKey, page, size);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    public JsonNode createSchedule(String reportKey, Map<String, Object> request) {
        String url = baseUrl + "/internal/v1/reports/" + reportKey + "/schedules";
        log.info("Reporting: create schedule [key={}]", reportKey);
        return extractData(restTemplate.postForEntity(url, request, JsonNode.class));
    }

    /** Tenant-wide recent runs (newest first) — {@code GET /internal/v1/reports/tenant-runs}. */
    public JsonNode listTenantReportRuns(int page, int size) {
        String url = baseUrl + "/internal/v1/reports/tenant-runs?page=" + page + "&size=" + size;
        log.info("Reporting: list tenant runs [page={}, size={}]", page, size);
        return extractData(restTemplate.getForEntity(url, JsonNode.class));
    }

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
