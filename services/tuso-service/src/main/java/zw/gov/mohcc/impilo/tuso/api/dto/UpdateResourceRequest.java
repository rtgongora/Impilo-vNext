package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record UpdateResourceRequest(
        @Size(max = 255, message = "Resource name must not exceed 255 characters")
        String name,

        @Size(max = 50, message = "Resource type must not exceed 50 characters")
        String resourceType,

        @Min(value = 1, message = "Capacity must be at least 1")
        Integer capacity,

        @Size(max = 255, message = "Location description must not exceed 255 characters")
        String locationDesc,

        Map<String, Object> metadata
) {}
