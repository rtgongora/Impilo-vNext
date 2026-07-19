package zw.gov.mohcc.impilo.ndila.core.place;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeocodeReviewEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaGeocodeReviewRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Indawo site → Ndila location sync (D-L1), the INDAWO/SITE mirror of
 * {@code NdilaFacilityMasterSyncService}: brings public-health sites into the
 * owner-keyed location index so proximity/dup-detection and shared anchors work
 * across BOTH registries. Ndila stores geometry + owner keys only — Indawo
 * stays the authority for everything else.
 *
 * <p>Sites without usable coordinates reuse the geocode review queue
 * ({@code owner_service=INDAWO}; the queue's facility-named columns carry the
 * site code/name — same review surface, no schema churn).</p>
 */
@Service
public class NdilaSiteMasterSyncService {

    public static final String OWNER_SERVICE = "INDAWO";
    public static final String OWNER_ENTITY_TYPE = "SITE";
    public static final String LOCATION_TYPE = "PUBLIC_HEALTH_SITE";

    private final NdilaLocationRepository locationRepository;
    private final NdilaLocationService locationService;
    private final NdilaGeocodeReviewRepository reviewRepository;

    public NdilaSiteMasterSyncService(NdilaLocationRepository locationRepository,
                                      NdilaLocationService locationService,
                                      NdilaGeocodeReviewRepository reviewRepository) {
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.reviewRepository = reviewRepository;
    }

    @Transactional
    public SyncResult syncRecords(UUID tenantId, List<Map<String, Object>> records, UUID correlationId) {
        int created = 0;
        int updated = 0;
        int reviewQueued = 0;
        List<Map<String, Object>> rowResults = new ArrayList<>();

        for (Map<String, Object> record : records) {
            String siteId = str(record, "site_id");
            String siteCode = str(record, "site_code");
            String siteName = str(record, "site_name");
            String siteCategory = str(record, "site_category");
            if (siteId == null || siteId.isBlank() || siteName == null || siteName.isBlank()) {
                rowResults.add(resultRow(siteId, "FAILED", "MISSING_IDENTITY", null));
                continue;
            }

            if (!hasValidCoordinates(record)) {
                queueReview(tenantId, siteId, siteCode, siteName, record);
                reviewQueued++;
                rowResults.add(resultRow(siteId, "REVIEW_QUEUED", "MISSING_COORDINATES", null));
                continue;
            }

            BigDecimal lat = decimal(record.get("latitude"));
            BigDecimal lng = decimal(record.get("longitude"));
            Optional<NdilaLocationEntity> existing = locationRepository
                    .findByTenantIdAndOwnerServiceAndOwnerEntityTypeAndOwnerEntityId(
                            tenantId, OWNER_SERVICE, OWNER_ENTITY_TYPE, siteId);

            if (existing.isPresent()) {
                locationService.updateCoordinates(existing.get().getId(), lat, lng, null,
                        "INDAWO_SITE_SYNC", correlationId);
                updated++;
                rowResults.add(resultRow(siteId, "UPDATED", null, existing.get().getId()));
            } else {
                NdilaLocationEntity createdLocation = locationService.create(
                        new NdilaLocationService.CreateLocation(
                                tenantId,
                                OWNER_SERVICE,
                                OWNER_ENTITY_TYPE,
                                siteId,
                                siteName,
                                siteName + (siteCategory != null ? " — " + siteCategory : ""),
                                LOCATION_TYPE,
                                lat,
                                lng,
                                null,
                                null,
                                null,
                                str(record, "ward"),
                                str(record, "district"),
                                str(record, "province"),
                                "ZWE",
                                "INDAWO_SITE_SYNC",
                                "PUBLIC",
                                null),
                        correlationId);
                created++;
                rowResults.add(resultRow(siteId, "CREATED", null, createdLocation.getId()));
            }
        }

        return new SyncResult(records.size(), created, updated, reviewQueued, rowResults);
    }

    private void queueReview(UUID tenantId, String siteId, String siteCode, String siteName,
                             Map<String, Object> record) {
        Optional<NdilaGeocodeReviewEntity> existing = reviewRepository
                .findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, OWNER_SERVICE, siteId);
        if (existing.isPresent()) {
            return;
        }
        NdilaGeocodeReviewEntity review = new NdilaGeocodeReviewEntity();
        review.setTenantId(tenantId);
        review.setOwnerService(OWNER_SERVICE);
        review.setOwnerEntityId(siteId);
        review.setFacilityUid(siteId);
        review.setFacilityCode(siteCode);
        review.setFacilityName(siteName);
        review.setProvince(str(record, "province"));
        review.setDistrict(str(record, "district"));
        review.setReason("MISSING_COORDINATES");
        review.setStatus("PENDING");
        reviewRepository.save(review);
    }

    private static boolean hasValidCoordinates(Map<String, Object> record) {
        Object lat = record.get("latitude");
        Object lng = record.get("longitude");
        if (lat == null || lng == null) {
            return false;
        }
        try {
            double la = Double.parseDouble(String.valueOf(lat));
            double lo = Double.parseDouble(String.valueOf(lng));
            if (la == 0 && lo == 0) {
                return false;
            }
            return la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static BigDecimal decimal(Object v) {
        return new BigDecimal(String.valueOf(v));
    }

    private static Map<String, Object> resultRow(String siteId, String outcome, String flag, UUID locationId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("site_id", siteId);
        row.put("outcome", outcome);
        if (flag != null) {
            row.put("quality_flag", flag);
        }
        if (locationId != null) {
            row.put("location_id", locationId);
        }
        return row;
    }

    private static String str(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public record SyncResult(
            int recordsTotal,
            int locationsCreated,
            int locationsUpdated,
            int reviewQueued,
            List<Map<String, Object>> results) {}
}
