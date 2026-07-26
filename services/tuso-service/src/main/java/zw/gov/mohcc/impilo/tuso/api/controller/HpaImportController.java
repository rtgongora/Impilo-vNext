package zw.gov.mohcc.impilo.tuso.api.controller;

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
import zw.gov.mohcc.impilo.tuso.core.HpaEnrichmentImportService;

import java.util.List;
import java.util.Map;

/**
 * Internal admin surface for the HPA national facility enrichment import.
 *
 * <p>Mirrors {@code FacilityMasterImportController}: a dry-run that writes an
 * {@code hpa_import_batch} with {@code dry_run=true} and mutates no
 * {@code tuso.facility}, an apply that materialises safe outcomes, and read
 * endpoints for the batch, per-outcome counts and the review queues (identity
 * conflicts, possible-existing, unresolved-site). Review items never block the
 * safe match-or-create of other candidates.</p>
 */
@RestController
@RequestMapping("/v1/internal/facilities/hpa-import")
public class HpaImportController {

    private static final Logger log = LoggerFactory.getLogger(HpaImportController.class);

    private final HpaEnrichmentImportService importService;
    private final zw.gov.mohcc.impilo.tuso.core.HpaLegitimacyBackfillService legitimacyBackfillService;
    private final JdbcTemplate jdbc;

    public HpaImportController(HpaEnrichmentImportService importService,
                               zw.gov.mohcc.impilo.tuso.core.HpaLegitimacyBackfillService legitimacyBackfillService,
                               JdbcTemplate jdbc) {
        this.importService = importService;
        this.legitimacyBackfillService = legitimacyBackfillService;
        this.jdbc = jdbc;
    }

    /** HAR W1 — backfill the regulator's legitimacy verdict for already-imported HPA facilities. */
    public record LegitimacyBackfillRequest(Integer limit, Boolean dryRun, String tenantId) {}

    @PostMapping("/backfill-legitimacy")
    public ResponseEntity<ApiResponse<zw.gov.mohcc.impilo.tuso.core.HpaLegitimacyBackfillService.BackfillResult>>
            backfillLegitimacy(@RequestBody(required = false) LegitimacyBackfillRequest request) {
        var ctx = zw.gov.mohcc.impilo.shared.auth.TrustContextHolder.require();
        int limit = request == null || request.limit() == null ? 500 : request.limit();
        boolean dryRun = request != null && Boolean.TRUE.equals(request.dryRun());
        java.util.UUID tenantId = request == null ? null : tenant(request.tenantId());
        log.info("HAR W1 legitimacy backfill limit={} dryRun={} correlationId={}",
                limit, dryRun, ctx.correlationId());
        var result = legitimacyBackfillService.backfill(
                tenantId != null ? tenantId : ctx.tenantId(), ctx.actorId(), limit, dryRun);
        return ResponseEntity.ok(ApiResponse.ok(result, correlationId()));
    }

    @GetMapping("/legitimacy-summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> legitimacySummary() {
        var ctx = zw.gov.mohcc.impilo.shared.auth.TrustContextHolder.require();
        return ResponseEntity.ok(ApiResponse.ok(
                legitimacyBackfillService.summary(ctx.tenantId()), correlationId()));
    }

    public record HpaImportApiRequest(String feedPath, Boolean dryRun, String tenantId, String feedChecksum) {}

    @PostMapping("/dry-run")
    public ResponseEntity<ApiResponse<HpaEnrichmentImportService.HpaImportSummary>> dryRun(
            @RequestBody HpaImportApiRequest request) {
        log.info("HPA enrichment dry-run feedPath={}", request.feedPath());
        var summary = importService.importFeed(new HpaEnrichmentImportService.HpaImportRequest(
                request.feedPath(), true, tenant(request.tenantId()), request.feedChecksum()));
        return ResponseEntity.ok(ApiResponse.ok(summary, correlationId()));
    }

    @PostMapping("/apply")
    public ResponseEntity<ApiResponse<HpaEnrichmentImportService.HpaImportSummary>> apply(
            @RequestBody HpaImportApiRequest request) {
        boolean dryRun = request.dryRun() != null && request.dryRun();
        log.info("HPA enrichment apply feedPath={} dryRun={}", request.feedPath(), dryRun);
        var summary = importService.importFeed(new HpaEnrichmentImportService.HpaImportRequest(
                request.feedPath(), dryRun, tenant(request.tenantId()), request.feedChecksum()));
        return ResponseEntity.ok(ApiResponse.ok(summary, correlationId()));
    }

    @GetMapping("/runs")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> runs() {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, bundle_id, dry_run, candidates_total, live_tuso_scanned, enriched_existing, "
                        + "created_establishment, created_unresolved_site, routed_to_review, quarantined, "
                        + "unchanged_idempotent, counts_by_outcome, status, started_at, completed_at "
                        + "FROM tuso.hpa_import_batch ORDER BY id DESC LIMIT 50");
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

    @GetMapping("/runs/{runId}/review-queue")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> reviewQueue(
            @org.springframework.web.bind.annotation.PathVariable long runId,
            @RequestParam(value = "outcome", required = false) String outcome) {
        String sql = "SELECT id, source_record_key, decision_outcome, confidence, matched_facility_id, "
                + "reason_codes, reviewer_status FROM tuso.hpa_match_decision WHERE batch_id=? AND requires_review=true"
                + (outcome != null ? " AND decision_outcome=?" : "") + " ORDER BY id LIMIT 500";
        List<Map<String, Object>> rows = outcome != null
                ? jdbc.queryForList(sql, runId, outcome)
                : jdbc.queryForList(sql, runId);
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

    @GetMapping("/runs/{runId}/outcomes")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> outcomes(
            @org.springframework.web.bind.annotation.PathVariable long runId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT decision_outcome, count(*) AS n FROM tuso.hpa_match_decision WHERE batch_id=? "
                        + "GROUP BY decision_outcome ORDER BY n DESC", runId);
        return ResponseEntity.ok(ApiResponse.ok(rows, correlationId()));
    }

    private static java.util.UUID tenant(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return java.util.UUID.fromString(raw.trim());
    }

    private static String correlationId() {
        try {
            return zw.gov.mohcc.impilo.shared.auth.TrustContextHolder.require().correlationId().toString();
        } catch (Exception e) {
            return "hpa-import";
        }
    }
}
