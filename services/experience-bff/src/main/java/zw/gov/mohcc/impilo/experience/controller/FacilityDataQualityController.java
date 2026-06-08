package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.NdilaServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/facilities/data-quality")
public class FacilityDataQualityController {

    private static final Logger log = LoggerFactory.getLogger(FacilityDataQualityController.class);

    private final TusoServiceClient tusoClient;
    private final NdilaServiceClient ndilaClient;

    public FacilityDataQualityController(TusoServiceClient tusoClient, NdilaServiceClient ndilaClient) {
        this.tusoClient = tusoClient;
        this.ndilaClient = ndilaClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        try {
            JsonNode quality = tusoClient.getFacilityMasterQualityReport();
            payload.put("qualityReport", quality);
        } catch (Exception e) {
            log.warn("Facility quality report unavailable: {}", e.getMessage());
            payload.put("qualityReport", Map.of("status", "UNAVAILABLE", "message", e.getMessage()));
        }
        try {
            JsonNode review = ndilaClient.geocodeReviewQueue();
            Object reviewData = review != null && review.has("data") ? review.get("data") : review;
            int pending = 0;
            if (reviewData instanceof JsonNode node && node.isArray()) {
                pending = node.size();
            } else if (review != null && review.isArray()) {
                pending = review.size();
            }
            payload.put("geocodeReviewPending", pending);
            payload.put("geocodeReviewQueue", reviewData != null ? reviewData : List.of());
        } catch (Exception e) {
            log.warn("Geocode review queue unavailable: {}", e.getMessage());
            payload.put("geocodeReviewPending", 0);
            payload.put("geocodeReviewQueue", List.of());
            payload.put("geocodeReviewError", e.getMessage());
        }
        return ResponseEntity.ok(Map.of(
                "data", payload,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }
}
