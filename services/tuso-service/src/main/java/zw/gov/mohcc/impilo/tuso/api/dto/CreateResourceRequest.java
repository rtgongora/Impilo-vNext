package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

public record CreateResourceRequest(
        @NotBlank(message = "Resource name is required")
        @Size(max = 255, message = "Resource name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Resource type is required")
        @Size(max = 50, message = "Resource type must not exceed 50 characters")
        String resourceType,

        @Min(value = 1, message = "Capacity must be at least 1")
        int capacity,

        @Size(max = 255, message = "Location description must not exceed 255 characters")
        String locationDesc,

        Map<String, Object> metadata
) {
    public CreateResourceRequest {
        if (capacity == 0) {
            capacity = 1;
        }
    }
}
