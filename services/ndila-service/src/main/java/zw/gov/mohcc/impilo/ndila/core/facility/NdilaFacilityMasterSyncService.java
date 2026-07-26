package zw.gov.mohcc.impilo.ndila.core.facility;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaGeocodeReviewEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.repository.NdilaGeocodeReviewRepository;
import zw.gov.mohcc.impilo.ndila.repository.NdilaLocationRepository;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class NdilaFacilityMasterSyncService {

    public static final String OWNER_SERVICE =
            zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.OWNER_SERVICE_TUSO;
    public static final String OWNER_ENTITY_TYPE =
            zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.OWNER_ENTITY_TYPE_FACILITY;
    public static final String LOCATION_TYPE =
            zw.gov.mohcc.impilo.ndila.core.location.NdilaLocationVocabulary.LOCATION_TYPE_HEALTH_FACILITY;

    private final NdilaLocationRepository locationRepository;
    private final NdilaLocationService locationService;
    private final NdilaGeocodeReviewRepository reviewRepository;
    private final ObjectMapper objectMapper;

    public NdilaFacilityMasterSyncService(
            NdilaLocationRepository locationRepository,
            NdilaLocationService locationService,
            NdilaGeocodeReviewRepository reviewRepository,
            ObjectMapper objectMapper) {
        this.locationRepository = locationRepository;
        this.locationService = locationService;
        this.reviewRepository = reviewRepository;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> loadSeedRecords(Path seedJson) throws IOException {
        return objectMapper.readValue(Files.readString(seedJson), new TypeReference<>() {});
    }

    @Transactional
    public SyncResult syncFromSeed(UUID tenantId, List<Map<String, Object>> records, UUID correlationId) {
        int locationsCreated = 0;
        int locationsUpdated = 0;
        int reviewQueued = 0;
        List<Map<String, Object>> rowResults = new ArrayList<>();

        for (Map<String, Object> record : records) {
            String facilityUid = str(record, "facility_uid");
            String facilityCode = str(record, "facility_code");
            String facilityName = str(record, "facility_name");
            String ownerEntityId = str(record, "tuso_facility_id");
            if (ownerEntityId == null || ownerEntityId.isBlank()) {
                ownerEntityId = facilityUid;
            }

            boolean hasCoordinates = Boolean.TRUE.equals(record.get("has_coordinates"))
                    || hasValidCoordinates(record);

            if (!hasCoordinates) {
                queueReview(tenantId, ownerEntityId, facilityUid, facilityCode, facilityName, record);
                reviewQueued++;
                rowResults.add(resultRow(facilityUid, "REVIEW_QUEUED", "MISSING_COORDINATES", null));
                continue;
            }

            double lat = doubleVal(record.get("latitude"));
            double lng = doubleValue(record.get("longitude"));
            Optional<NdilaLocationEntity> existing = locationRepository
                    .findByTenantIdAndOwnerServiceAndOwnerEntityTypeAndOwnerEntityId(
                            tenantId, OWNER_SERVICE, OWNER_ENTITY_TYPE, ownerEntityId);

            if (existing.isPresent()) {
                locationService.updateCoordinates(
                        existing.get().getId(),
                        BigDecimal.valueOf(lat),
                        BigDecimal.valueOf(lng),
                        null,
                        "MASTER_PACK_IMPORT",
                        correlationId);
                locationsUpdated++;
                rowResults.add(resultRow(facilityUid, "UPDATED", null, existing.get().getId()));
            } else {
                NdilaLocationEntity created = locationService.create(
                        new NdilaLocationService.CreateLocation(
                                tenantId,
                                OWNER_SERVICE,
                                OWNER_ENTITY_TYPE,
                                ownerEntityId,
                                facilityName,
                                facilityName + " — " + (facilityCode != null ? facilityCode : facilityUid),
                                LOCATION_TYPE,
                                BigDecimal.valueOf(lat),
                                BigDecimal.valueOf(lng),
                                null,
                                null,
                                null,
                                null,
                                str(record, "district"),
                                str(record, "province"),
                                "ZWE",
                                "MASTER_PACK_IMPORT",
                                "PUBLIC",
                                null),
                        correlationId);
                locationsCreated++;
                rowResults.add(resultRow(facilityUid, "CREATED", null, created.getId()));
            }
        }

        return new SyncResult(records.size(), locationsCreated, locationsUpdated, reviewQueued, rowResults);
    }

    private void queueReview(
            UUID tenantId,
            String ownerEntityId,
            String facilityUid,
            String facilityCode,
            String facilityName,
            Map<String, Object> record) {
        Optional<NdilaGeocodeReviewEntity> existing = reviewRepository
                .findByTenantIdAndOwnerServiceAndOwnerEntityId(tenantId, OWNER_SERVICE, ownerEntityId);
        if (existing.isPresent()) {
            return;
        }
        NdilaGeocodeReviewEntity review = new NdilaGeocodeReviewEntity();
        review.setTenantId(tenantId);
        review.setOwnerEntityId(ownerEntityId);
        review.setFacilityUid(facilityUid);
        review.setFacilityCode(facilityCode);
        review.setFacilityName(facilityName);
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
        double la = doubleVal(lat);
        double lo = doubleValue(lng);
        if (la == 0 && lo == 0) {
            return false;
        }
        return la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
    }

    private static Map<String, Object> resultRow(String facilityUid, String outcome, String flag, UUID locationId) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("facility_uid", facilityUid);
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

    private static double doubleVal(Object v) {
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(String.valueOf(v));
    }

    private static double doubleValue(Object v) {
        return doubleVal(v);
    }

    public record SyncResult(
            int recordsTotal,
            int locationsCreated,
            int locationsUpdated,
            int reviewQueued,
            List<Map<String, Object>> results) {}
}
