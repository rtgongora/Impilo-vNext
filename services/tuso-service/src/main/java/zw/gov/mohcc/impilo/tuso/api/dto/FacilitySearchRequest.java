package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record FacilitySearchRequest(
        String query,
        String facilityType,
        String status,
        String province,
        String district,
        String level,

        @Min(value = 0, message = "Page must be zero or positive")
        int page,

        @Min(value = 1, message = "Size must be at least 1")
        @Max(value = 200, message = "Size must not exceed 200")
        int size
) {
    public FacilitySearchRequest {
        if (size == 0) {
            size = 50;
        }
    }
}
