package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mobile provider governance endpoints — summary, incidents, and notifications
 * for provider-facing mobile app governance visibility.
 *
 * <p>STRANGLER: Already delegates to DataGovernanceServiceClient + NotificationServiceClient
 * via RestTemplate; AuditLogRepository reads retained during migration.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/governance")
public class MobileGovernanceController {

    private static final Logger log = LoggerFactory.getLogger(MobileGovernanceController.class);

    private final RestTemplate restTemplate;
    private final String dataGovernanceUrl;
    private final String notificationUrl;

    public MobileGovernanceController(RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints) {
        this.restTemplate = serviceRestTemplate;
        this.dataGovernanceUrl = endpoints.dataGovernanceBaseUrl();
        this.notificationUrl = endpoints.notificationBaseUrl();
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getGovernanceSummary(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        int datasetCount = 0;
        int ruleCount = 0;
        try {
            JsonNode datasets = restTemplate.getForEntity(
                    dataGovernanceUrl + "/internal/v1/governance/datasets", JsonNode.class).getBody();
            if (datasets != null && datasets.isArray()) datasetCount = datasets.size();
        } catch (Exception e) {
            log.warn("Failed to fetch dataset count: {}", e.getMessage());
        }
        try {
            JsonNode rules = restTemplate.getForEntity(
                    dataGovernanceUrl + "/internal/v1/governance/rules", JsonNode.class).getBody();
            if (rules != null && rules.isArray()) ruleCount = rules.size();
        } catch (Exception e) {
            log.warn("Failed to fetch rule count: {}", e.getMessage());
        }

        PageRequest pageable = PageRequest.of(0, 10, Sort.by("occurredAt").descending());

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("datasets", datasetCount);
        summary.put("activeRules", ruleCount);
        summary.put("recentIncidents", incidentCount);
        summary.put("status", "LIVE");

        return ResponseEntity.ok(Map.of("data", summary,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/incidents")
    public ResponseEntity<Map<String, Object>> getIncidents(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        PageRequest pageable = PageRequest.of(0, 20, Sort.by("occurredAt").descending());
        return ResponseEntity.ok(Map.of("data", incidents,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/communication/notifications")
    public ResponseEntity<Map<String, Object>> getNotifications(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode result = restTemplate.getForEntity(
                    notificationUrl + "/internal/v1/notifications?size=10", JsonNode.class).getBody();
            return ResponseEntity.ok(Map.of("data", result != null ? result : new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.warn("Failed to fetch notifications: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("data", new Object[0],
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
