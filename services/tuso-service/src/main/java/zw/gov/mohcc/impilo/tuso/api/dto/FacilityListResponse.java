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
            Integer bedCapacity
    ) {}
}
