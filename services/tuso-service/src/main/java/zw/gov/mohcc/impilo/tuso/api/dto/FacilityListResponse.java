package zw.gov.mohcc.impilo.tuso.api.dto;

import java.math.BigDecimal;
import java.util.List;

public record FacilityListResponse(
        List<FacilitySummary> facilities
) {
    public record FacilitySummary(
            Long id,
            String facilityUuid,
            String name,
            String code,
            String type,
            String status,
            String district,
            String province,
            BigDecimal latitude,
            BigDecimal longitude,
            String ownership,
            String level,
            String operationalStatus,
            String facilityUid,
            Boolean hasValidCoordinates,
            Boolean missingFacilityCode,
            String locationContext,
            Integer bedCapacity,
            /**
             * HAR W5 — {@code IMPORTED_PENDING_CONFIGURATION} means the regulator lists this
             * facility but nobody has confirmed what it actually does. Consumers use it to label a
             * result honestly instead of presenting a register entry as an operating service.
             */
            String regulatoryStatus
    ) {
        /** Pre-HAR-W5 arity, for callers that do not carry a regulatory status. */
        public FacilitySummary(Long id, String facilityUuid, String name, String code, String type,
                               String status, String district, String province, BigDecimal latitude,
                               BigDecimal longitude, String ownership, String level,
                               String operationalStatus, String facilityUid, Boolean hasValidCoordinates,
                               Boolean missingFacilityCode, String locationContext, Integer bedCapacity) {
            this(id, facilityUuid, name, code, type, status, district, province, latitude, longitude,
                    ownership, level, operationalStatus, facilityUid, hasValidCoordinates,
                    missingFacilityCode, locationContext, bedCapacity, null);
        }
    }
}
