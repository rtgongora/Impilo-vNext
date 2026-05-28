package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wave 20 demo journey registry — seven production-readiness demonstration paths.
 * Registry rows are seeded in Flyway V40 ({@code demo_journey_anchors}) and mirrored in
 * {@code wave20/demo-journey-map.json}.
 */
@RestController
@RequestMapping("/internal/v1/demo-journeys")
public class DemoJourneyController {

    private static final String REGISTRY_RESOURCE = "wave20/demo-journey-map.json";

    private final ObjectMapper objectMapper;

    public DemoJourneyController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listJourneys(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) throws Exception {
        JsonNode root = loadRegistry();
        JsonNode journeys = root.path("journeys");
        return ResponseEntity.ok(Map.of(
                "data", journeys.isArray() ? journeys : objectMapper.createArrayNode(),
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId,
                        "count", journeys.isArray() ? journeys.size() : 0,
                        "golden_patient", root.path("golden_patient").asText("CPID-ZW-00001"))));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) throws Exception {
        JsonNode root = loadRegistry();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("journey_count", root.path("journeys").isArray() ? root.path("journeys").size() : 0);
        body.put("golden_patient", root.path("golden_patient").asText("CPID-ZW-00001"));
        body.put("wave", root.path("wave").asText("20g"));
        return ResponseEntity.ok(Map.of(
                "data", body,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private JsonNode loadRegistry() throws Exception {
        ClassPathResource resource = new ClassPathResource(REGISTRY_RESOURCE);
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        }
    }
}
