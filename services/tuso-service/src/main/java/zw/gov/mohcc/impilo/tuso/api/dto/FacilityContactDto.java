package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.constraints.NotBlank;

public record FacilityContactDto(
        @NotBlank(message = "Contact type is required")
        String contactType,

        String name,
        String phone,
        String email,
        String role
) {}
