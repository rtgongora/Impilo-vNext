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
import zw.gov.mohcc.impilo.ndila.core.location.HpaGeocodeProposalService;
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
    private final HpaGeocodeProposalService proposalService;
    private final JdbcTemplate jdbc;

    public HpaLocationImportController(HpaLocationImportService importService,
                                       HpaGeocodeProposalService proposalService,
                                       JdbcTemplate jdbc) {
        this.importService = importService;
        this.proposalService = proposalService;
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
            @RequestParam(value = "limit", defaultValue = "200") int limit,
            @RequestParam(value = "province", required = false) String province) {
        // HAR W4 — the proposal columns are returned so a steward sees a starting point rather than
        // 6,327 rows with nothing but a name. They are proposals, and the payload says so.
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT id, owner_entity_id, facility_name, province, district, reason, status, "
                        + "       proposed_latitude, proposed_longitude, proposed_source, "
                        + "       proposed_confidence, proposed_locality, proposed_address, proposal_status "
                        + "  FROM ndila_geocode_review_queue "
                        + " WHERE owner_service = ? AND reason='MISSING_COORDINATES' AND status='PENDING' "
                        + "   AND (? IS NULL OR lower(province) = lower(?)) "
                        + " ORDER BY created_at LIMIT ?",
                zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.OWNER_SERVICE_TUSO,
                province, province, limit));
    }

    /**
     * HAR W4 — propose a starting point for each pending review row from the district centroid of
     * facilities we have already surveyed. Writes only to the review queue; publishes no pin.
     */
    @PostMapping("/propose-geocodes")
    public ResponseEntity<HpaGeocodeProposalService.ProposalSummary> proposeGeocodes(
            @RequestBody(required = false) ProposeApiRequest request) {
        boolean dryRun = request == null || request.dryRun() == null || request.dryRun();
        int limit = request == null || request.limit() == null ? 1000 : request.limit();
        return ResponseEntity.ok(proposalService.proposeForPendingReviews(
                tenant(request == null ? null : request.tenantId()), dryRun, limit));
    }

    public record ProposeApiRequest(String tenantId, Boolean dryRun, Integer limit) {}

    private static UUID tenant(String raw) {
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw.trim());
    }
}
