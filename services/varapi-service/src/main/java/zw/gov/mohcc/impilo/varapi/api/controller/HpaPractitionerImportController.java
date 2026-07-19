package zw.gov.mohcc.impilo.varapi.api.controller;

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
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.core.HpaPractitionerImportService;

import java.util.List;
import java.util.Map;

/**
 * Internal admin surface for the HPA practitioner-in-charge candidate import.
 *
 * <p>Imports candidate practitioner-in-charge relationships (resolved best-effort
 * to existing providers by registration number) — grants NO authority. Read
 * endpoints expose the resolution summary and the unresolved onboarding queue.</p>
 */
@RestController
@RequestMapping("/v1/internal/providers/hpa-practitioner-import")
public class HpaPractitionerImportController {

    private static final Logger log = LoggerFactory.getLogger(HpaPractitionerImportController.class);

    private final HpaPractitionerImportService importService;
    private final JdbcTemplate jdbc;

    public HpaPractitionerImportController(HpaPractitionerImportService importService, JdbcTemplate jdbc) {
        this.importService = importService;
        this.jdbc = jdbc;
    }

    public record ImportApiRequest(String feedPath, String tenantId) {}

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<HpaPractitionerImportService.ImportSummary>> apply(
            @RequestBody ImportApiRequest request) {
        log.info("HPA practitioner import feedPath={}", request.feedPath());
        var summary = importService.importFeed(new HpaPractitionerImportService.ImportRequest(
                request.feedPath(), tenant(request.tenantId())));
        return ResponseEntity.ok(ApiResponse.ok(summary, correlationId()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> summary() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT varapi_resolution_status, count(*) AS n FROM varapi.hpa_practitioner_candidate "
                        + "GROUP BY varapi_resolution_status ORDER BY n DESC");
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

    @GetMapping("/onboarding-queue")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> onboardingQueue(
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, hpa_institution_id, practitioner_name, practitioner_registration_number, "
                        + "relationship_role, onboarding_status FROM varapi.hpa_practitioner_candidate "
                        + "WHERE varapi_resolution_status='UNRESOLVED' AND onboarding_status='OPEN' "
                        + "ORDER BY id LIMIT ?", limit);
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

    private static java.util.UUID tenant(String raw) {
        return (raw == null || raw.isBlank()) ? null : java.util.UUID.fromString(raw.trim());
    }

    private static String correlationId() {
        try {
            return zw.gov.mohcc.impilo.shared.auth.TrustContextHolder.require().correlationId().toString();
        } catch (Exception e) {
            return "hpa-practitioner-import";
        }
    }
}
