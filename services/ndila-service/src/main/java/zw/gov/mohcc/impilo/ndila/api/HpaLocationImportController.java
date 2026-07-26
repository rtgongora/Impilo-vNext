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

    /**
     * HAR W7 — derive the distinct (locality, province) places the HPA estate uses and record each
     * as a gazetteer row awaiting a coordinate. Idempotent; refreshes facility counts without
     * disturbing a reviewer's placement.
     */
    @PostMapping("/build-gazetteer")
    public ResponseEntity<HpaGeocodeProposalService.GazetteerBuildSummary> buildGazetteer(
            @RequestBody(required = false) ProposeApiRequest request) {
        boolean dryRun = request == null || request.dryRun() == null || request.dryRun();
        return ResponseEntity.ok(proposalService.buildGazetteerFromLocations(
                tenant(request == null ? null : request.tenantId()), dryRun));
    }

    /** HAR W7 — the gazetteer worklist: places awaiting coordinates, biggest blast radius first. */
    @GetMapping("/gazetteer")
    public ResponseEntity<List<Map<String, Object>>> gazetteer(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "limit", defaultValue = "250") int limit) {
        return ResponseEntity.ok(jdbc.queryForList(
                "SELECT locality, province, facility_count, latitude, longitude, precision_tier, "
                        + "       coordinate_source, status, reviewed_by, reviewed_at "
                        + "  FROM ndila_place_gazetteer "
                        + " WHERE (? IS NULL OR status = ?) "
                        + " ORDER BY facility_count DESC, locality LIMIT ?",
                status, status, Math.min(Math.max(limit, 1), 1000)));
    }

    /**
     * HAR W7 — facilities whose recovered street address is ready for geocoding. This is the input
     * a geocoding run consumes; the estate has no geocoder of its own.
     */
    @GetMapping("/addresses-awaiting-geocode")
    public ResponseEntity<List<Map<String, Object>>> addressesAwaitingGeocode(
            @RequestParam(value = "tenantId", required = false) String tenantId,
            @RequestParam(value = "limit", defaultValue = "1000") int limit) {
        return ResponseEntity.ok(proposalService.addressesAwaitingGeocode(tenant(tenantId), limit));
    }

    /**
     * HAR W7 — record address-precision proposals from a geocoding run. Every point is revalidated
     * here regardless of who produced it, and lands as a proposal, never as a published pin.
     */
    @PostMapping("/propose-geocoded-addresses")
    public ResponseEntity<HpaGeocodeProposalService.AddressProposalSummary> proposeGeocodedAddresses(
            @RequestBody GeocodedAddressBatch request) {
        boolean dryRun = request.dryRun() == null || request.dryRun();
        return ResponseEntity.ok(proposalService.proposeFromGeocodedAddresses(
                tenant(request.tenantId()), request.results(), dryRun));
    }

    public record GeocodedAddressBatch(String tenantId, Boolean dryRun,
                                       List<HpaGeocodeProposalService.GeocodedAddress> results) {}

    private static UUID tenant(String raw) {
        return (raw == null || raw.isBlank()) ? null : UUID.fromString(raw.trim());
    }
}
