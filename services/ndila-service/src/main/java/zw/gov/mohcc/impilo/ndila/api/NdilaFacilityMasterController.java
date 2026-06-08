package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.ndila.core.facility.NdilaFacilityMasterSyncService;
import zw.gov.mohcc.impilo.ndila.repository.NdilaGeocodeReviewRepository;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ndila/facilities")
public class NdilaFacilityMasterController {

    private final NdilaFacilityMasterSyncService syncService;
    private final NdilaGeocodeReviewRepository reviewRepository;

    public NdilaFacilityMasterController(
            NdilaFacilityMasterSyncService syncService,
            NdilaGeocodeReviewRepository reviewRepository) {
        this.syncService = syncService;
        this.reviewRepository = reviewRepository;
    }

    @PostMapping("/sync-master-seed")
    public ResponseEntity<Map<String, Object>> syncMasterSeed(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) UUID correlationId,
            @RequestBody(required = false) Map<String, Object> body) throws Exception {
        List<Map<String, Object>> records;
        if (body != null && body.containsKey("records")) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> inline = (List<Map<String, Object>>) body.get("records");
            records = inline;
        } else {
            Path seed = Path.of("docs/data/facility-master-2024-07-23/generated/ndila_facility_location_seed.json");
            records = syncService.loadSeedRecords(seed);
        }
        NdilaFacilityMasterSyncService.SyncResult result =
                syncService.syncFromSeed(tenantId, records, correlationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordsTotal", result.recordsTotal());
        payload.put("locationsCreated", result.locationsCreated());
        payload.put("locationsUpdated", result.locationsUpdated());
        payload.put("reviewQueued", result.reviewQueued());
        payload.put("results", result.results());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    @GetMapping("/geocode-review-queue")
    public ResponseEntity<Map<String, Object>> geocodeReviewQueue(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId) {
        var pending = reviewRepository.findByTenantIdAndStatus(tenantId, "PENDING");
        return ResponseEntity.ok(Map.of("data", pending));
    }
}
