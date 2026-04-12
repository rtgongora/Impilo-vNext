package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.CoverageServiceClient;

/**
 * Member appeal submission — canonical path {@code POST /internal/v1/appeals} (proxies to coverage-service).
 */
@RestController
@RequestMapping("/internal/v1/appeals")
public class AppealsExperienceController {

    private static final Logger log = LoggerFactory.getLogger(AppealsExperienceController.class);
    private final CoverageServiceClient coverageClient;

    public AppealsExperienceController(CoverageServiceClient coverageClient) {
        this.coverageClient = coverageClient;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> submitAppeal(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = coverageClient.createAppeal(body);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data != null ? data : Map.of(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            log.error("Appeal submission failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(Map.of("error", Map.of("code", "APPEAL_FAILED", "message", e.getMessage())));
        }
    }
}
