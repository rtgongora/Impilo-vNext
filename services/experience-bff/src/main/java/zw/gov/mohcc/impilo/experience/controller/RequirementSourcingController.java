package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.MsikaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requirement sourcing composition: HPA requirement codes (tuso regulatory
 * truth) ↔ marketplace sourcing categories (msika). Regulator-neutral —
 * requirements resolve to categories with live listing counts, never to
 * suppliers. Supplier demand is AGGREGATE ONLY: tuso's demand signal carries
 * no facility identifiers, and this composition only narrows it further to
 * per-category counts.
 */
@RestController
@RequestMapping("/internal/v1/marketplace/sourcing")
public class RequirementSourcingController {

    private static final Logger log = LoggerFactory.getLogger(RequirementSourcingController.class);

    private final MsikaServiceClient msika;
    private final TusoServiceClient tuso;
    private final ObjectMapper objectMapper;

    public RequirementSourcingController(MsikaServiceClient msika, TusoServiceClient tuso,
                                         ObjectMapper objectMapper) {
        this.msika = msika;
        this.tuso = tuso;
        this.objectMapper = objectMapper;
    }

    public record ResolveRequest(List<String> codes) {}

    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> categories(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) throws Exception {
        JsonNode categories = objectMapper.readTree(msika.listSourcingCategories().getBody());
        return envelope(Map.of("categories", categories), requestId, correlationId);
    }

    @PostMapping("/requirements/resolve")
    public ResponseEntity<Map<String, Object>> resolve(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody ResolveRequest request) throws Exception {
        Map<String, Object> resolutions = resolveCodes(request.codes());
        return envelope(Map.of("resolutions", resolutions), requestId, correlationId);
    }

    @GetMapping("/requirements/{code}")
    public ResponseEntity<Map<String, Object>> requirement(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @PathVariable String code) throws Exception {
        Map<String, Object> resolutions = resolveCodes(List.of(code));
        Object resolution = resolutions.get(code);
        if (resolution == null) {
            return ResponseEntity.notFound().build();
        }
        return envelope(Map.of("resolution", resolution), requestId, correlationId);
    }

    /**
     * Supply-side demand insights: tuso's aggregate demand signal folded onto
     * sourcing categories. Facility identifiers never exist in the upstream
     * payload; this endpoint only aggregates further.
     */
    @GetMapping("/supplier-demand")
    public ResponseEntity<Map<String, Object>> supplierDemand(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) throws Exception {
        JsonNode signal = tuso.regulatoryGet("/demand-signal");
        JsonNode data = signal.has("data") ? signal.get("data") : signal;

        // Collect every checklist code in the signal, resolve to categories once.
        List<String> codes = new ArrayList<>();
        data.path("openFindings").forEach(row -> codes.add(row.path("checklistItemCode").asText()));
        data.path("openComplianceActions").forEach(row -> codes.add(row.path("checklistItemCode").asText()));
        Map<String, JsonNode> resolutionsByCode = new LinkedHashMap<>();
        if (!codes.isEmpty()) {
            JsonNode resolved = objectMapper.readTree(msika.resolveSourcing(
                    objectMapper.writeValueAsString(Map.of("codes", codes.stream().distinct().toList()))).getBody());
            resolved.fields().forEachRemaining(entry -> resolutionsByCode.put(entry.getKey(), entry.getValue()));
        }

        Map<String, Map<String, Object>> byCategory = new LinkedHashMap<>();
        fold(data.path("openFindings"), resolutionsByCode, byCategory, "openFindings");
        fold(data.path("openComplianceActions"), resolutionsByCode, byCategory, "openComplianceActions");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", Instant.now().toString());
        payload.put("smallCellFloor", data.path("smallCellFloor").asInt());
        payload.put("categories", byCategory.values());
        payload.put("openApplicationsByFacilityType", data.path("openApplicationsByFacilityType"));
        return envelope(payload, requestId, correlationId);
    }

    private Map<String, Object> resolveCodes(List<String> codes) throws Exception {
        if (codes == null || codes.isEmpty()) {
            return Map.of();
        }
        JsonNode resolved = objectMapper.readTree(msika.resolveSourcing(
                objectMapper.writeValueAsString(Map.of("codes", codes))).getBody());
        Map<String, Object> resolutions = new LinkedHashMap<>();
        resolved.fields().forEachRemaining(entry -> {
            Map<String, Object> view = objectMapper.convertValue(entry.getValue(), LinkedHashMap.class);
            view.put("marketplaceLink",
                    "/marketplace/store?category=" + entry.getValue().path("categoryCode").asText());
            resolutions.put(entry.getKey(), view);
        });
        return resolutions;
    }

    @SuppressWarnings("unchecked")
    private void fold(JsonNode rows, Map<String, JsonNode> resolutionsByCode,
                      Map<String, Map<String, Object>> byCategory, String metric) {
        for (JsonNode row : rows) {
            String code = row.path("checklistItemCode").asText();
            JsonNode resolution = resolutionsByCode.get(code);
            if (resolution == null) {
                continue; // unmapped requirement — no marketplace demand story
            }
            String categoryCode = resolution.path("categoryCode").asText();
            Map<String, Object> bucket = byCategory.computeIfAbsent(categoryCode, key -> {
                Map<String, Object> fresh = new LinkedHashMap<>();
                fresh.put("categoryCode", key);
                fresh.put("categoryLabel", resolution.path("categoryLabel").asText());
                fresh.put("restricted", resolution.path("restricted").asBoolean());
                fresh.put("openFindings", 0L);
                fresh.put("openComplianceActions", 0L);
                fresh.put("byProvince", new LinkedHashMap<String, Long>());
                return fresh;
            });
            long count = row.path("count").asLong();
            bucket.put(metric, (Long) bucket.get(metric) + count);
            Map<String, Long> byProvince = (Map<String, Long>) bucket.get("byProvince");
            byProvince.merge(row.path("province").asText("UNKNOWN"), count, Long::sum);
        }
    }

    private ResponseEntity<Map<String, Object>> envelope(Map<String, Object> data,
                                                         String requestId, String correlationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", data);
        body.put("meta", Map.of("requestId", requestId, "correlationId", correlationId));
        return ResponseEntity.ok(body);
    }
}
