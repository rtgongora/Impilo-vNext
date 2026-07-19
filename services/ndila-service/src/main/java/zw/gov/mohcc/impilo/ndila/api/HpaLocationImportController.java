package zw.gov.mohcc.impilo.ndila.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ndila.core.location.HpaLocationImportService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal admin surface for the HPA location/coordinate import into Ndila.
 *
 * <p>Imports address/locality assertions as UNVERIFIED locations and enqueues
 * geocode review for facilities without a plausible, corroborated coordinate
 * ("Map location awaiting confirmation") — never publishes an unverified pin.</p>
 */
@RestController
@RequestMapping("/internal/v1/locations/hpa-import")
public class HpaLocationImportController {

    private static final Logger log = LoggerFactory.getLogger(HpaLocationImportController.class);

    private final HpaLocationImportService importService;
    private final JdbcTemplate jdbc;

    public HpaLocationImportController(HpaLocationImportService importService, JdbcTemplate jdbc) {
        this.importService = importService;
        this.jdbc = jdbc;
    }

    public record ImportApiRequest(String feedPath, String tenantId) {}

    @PostMapping("/apply")
    public ResponseEntity<HpaLocationImportService.ImportSummary> apply(@RequestBody ImportApiRequest request) {
        log.info("HPA location import feedPath={}", request.feedPath());
        return ResponseEntity.ok(importService.importFeed(new HpaLocationImportService.ImportRequest(
                request.feedPath(), tenant(request.tenantId()))));
    }

    @GetMapping("/geocode-review-queue")
    public ResponseEntity<List<Map<String, Object>>> geocodeReviewQueue(
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, owner_entity_id, facility_name, province, reason, status FROM ndila_geocode_review_queue "
                        + "WHERE owner_service='TUSO' AND reason='MISSING_COORDINATES' AND status='PENDING' "
                        + "ORDER BY created_at LIMIT ?", limit));
    }

    private static UUID tenant(String raw) {
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw.trim());
    }
}
