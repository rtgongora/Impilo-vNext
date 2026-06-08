package zw.gov.mohcc.impilo.tuso.api.dto;

import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Extracts master health facility pack fields stored in {@code facility.metadata} JSONB.
 */
public final class FacilityMasterPackMetadata {

    private FacilityMasterPackMetadata() {}

    public record Flags(
            String facilityUid,
            Boolean hasValidCoordinates,
            Boolean missingFacilityCode,
            String locationContext,
            Integer bedCapacity,
            String importPackId
    ) {}

    public static Flags from(FacilityEntity entity) {
        Map<String, Object> meta = entity.getMetadata();
        if (meta == null || meta.isEmpty()) {
            return derivedFromEntity(entity, null, null, null, null, null);
        }
        return derivedFromEntity(
                entity,
                stringVal(meta.get("facility_uid")),
                boolVal(meta.get("has_valid_coordinates")),
                boolVal(meta.get("missing_facility_code")),
                stringVal(meta.get("location_context")),
                intVal(meta.get("bed_capacity")));
    }

    private static Flags derivedFromEntity(
            FacilityEntity entity,
            String facilityUid,
            Boolean hasValidCoordinates,
            Boolean missingFacilityCode,
            String locationContext,
            Integer bedCapacity) {
        if (hasValidCoordinates == null) {
            hasValidCoordinates = hasValidCoordinates(entity.getLatitude(), entity.getLongitude());
        }
        return new Flags(
                facilityUid,
                hasValidCoordinates,
                missingFacilityCode,
                locationContext,
                bedCapacity,
                "master-health-facility-2024-07-23");
    }

    public static boolean hasValidCoordinates(BigDecimal lat, BigDecimal lng) {
        if (lat == null || lng == null) {
            return false;
        }
        double la = lat.doubleValue();
        double lo = lng.doubleValue();
        if (la == 0 && lo == 0) {
            return false;
        }
        return la >= -90 && la <= 90 && lo >= -180 && lo <= 180;
    }

    private static String stringVal(Object v) {
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean boolVal(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean b) return b;
        return Boolean.parseBoolean(String.valueOf(v));
    }

    private static Integer intVal(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return Integer.parseInt(String.valueOf(v));
    }
}
