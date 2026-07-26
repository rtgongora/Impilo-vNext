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
import zw.gov.mohcc.impilo.varapi.core.HpaPractitionerMaterializationService;

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
    private final HpaPractitionerMaterializationService materializationService;
    private final zw.gov.mohcc.impilo.varapi.core.HpaPractitionerRegisterBackfillService registerBackfillService;
    private final JdbcTemplate jdbc;

    public HpaPractitionerImportController(HpaPractitionerImportService importService,
                                           HpaPractitionerMaterializationService materializationService,
                                           zw.gov.mohcc.impilo.varapi.core.HpaPractitionerRegisterBackfillService registerBackfillService,
                                           JdbcTemplate jdbc) {
        this.importService = importService;
        this.materializationService = materializationService;
        this.registerBackfillService = registerBackfillService;
        this.jdbc = jdbc;
    }

    /** HAR W2 — request shape for the council register-record backfill. */
    public record RegisterBackfillApiRequest(String tenantId, Boolean dryRun, Integer limit) {}

    /**
     * HAR W2 — create the HPA council register record for each imported practitioner.
     *
     * <p>Without this the registration number lives only on {@code provider.practice_number}, which
     * the public verification lane never reads, so every lookup returns NOT_FOUND. The record is
     * written against the HPA council with status LISTED_PENDING_COUNCIL_VERIFICATION: findable,
     * explicitly not council-verified, and inert to every access gate.</p>
     */
    @PostMapping("/backfill-register-records")
    public ResponseEntity<ApiResponse<zw.gov.mohcc.impilo.varapi.core.HpaPractitionerRegisterBackfillService.BackfillSummary>>
            backfillRegisterRecords(@RequestBody(required = false) RegisterBackfillApiRequest request) {
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        Integer limit = request == null ? null : request.limit();
        log.info("HAR W2 register backfill requested dryRun={} limit={}", dryRun, limit);
        var summary = registerBackfillService.backfill(
                tenant(request == null ? null : request.tenantId()), dryRun, limit);
        return ResponseEntity.ok(ApiResponse.ok(summary, correlationId()));
    }

    @GetMapping("/register-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> registerSummary(
            @RequestParam(required = false) String tenantId) {
        return ResponseEntity.ok(ApiResponse.ok(
                registerBackfillService.summary(tenant(tenantId)), correlationId()));
    }

    public record ImportApiRequest(String feedPath, String tenantId) {}

    public record MaterializeApiRequest(String tenantId) {}

    /**
     * Phase 2 of the HPA import: materialise each distinct candidate practitioner as a
     * PRELOADED (regulator-listed, unclaimed) provider identity. Grants no access —
     * PRELOADED providers are INACTIVE until the real person claims them.
     */
    @PostMapping("/materialize-providers")
    public ResponseEntity<ApiResponse<HpaPractitionerMaterializationService.MaterializeSummary>> materializeProviders(
            @RequestBody MaterializeApiRequest request) {
        log.info("HPA practitioner materialisation requested tenant={}", request.tenantId());
        var summary = materializationService.materializeProviders(tenant(request.tenantId()));
        return ResponseEntity.ok(ApiResponse.ok(summary, correlationId()));
    }

    /**
     * (facility, provider) pairs for materialised candidates — consumed by TUSO to seed
     * PIC nominations in the HPA-2017 state machine. Only rows resolved to a provider are
     * returned; the provider public id is the durable Provider ID (never a free-text name).
     */
    @GetMapping("/nomination-pairs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> nominationPairs() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT c.hpa_institution_id AS hpa_institution_id, p.provider_public_id AS provider_public_id, "
                        + "min(c.practitioner_sequence) AS practitioner_sequence "
                        + "FROM varapi.hpa_practitioner_candidate c "
                        + "JOIN varapi.provider p ON p.id = c.resolved_provider_id "
                        + "WHERE c.resolved_provider_id IS NOT NULL "
                        + "GROUP BY c.hpa_institution_id, p.provider_public_id "
                        + "ORDER BY c.hpa_institution_id");
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

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
