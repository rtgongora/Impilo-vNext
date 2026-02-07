package zw.gov.mohcc.impilo.tuso.api.dto;

import java.util.List;

public record FacilityListResponse(
        List<FacilitySummary> facilities
) {
    public record FacilitySummary(
            Long id,
            String name,
            String code,
            String type,
            String status,
            String district,
            String province
    ) {}
}
