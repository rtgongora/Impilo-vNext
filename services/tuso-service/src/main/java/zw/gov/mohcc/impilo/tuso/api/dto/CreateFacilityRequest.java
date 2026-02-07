package zw.gov.mohcc.impilo.tuso.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CreateFacilityRequest(
        @NotBlank(message = "Facility name is required")
        @Size(max = 255, message = "Facility name must not exceed 255 characters")
        String name,

        @NotBlank(message = "Facility code is required")
        @Size(max = 50, message = "Facility code must not exceed 50 characters")
        String facilityCode,

        @Size(max = 50, message = "Facility type must not exceed 50 characters")
        String facilityType,

        @Size(max = 100, message = "Province must not exceed 100 characters")
        String province,

        @Size(max = 100, message = "District must not exceed 100 characters")
        String district,

        BigDecimal latitude,
        BigDecimal longitude,

        @Size(max = 50, message = "Ownership must not exceed 50 characters")
        String ownership,

        @Size(max = 50, message = "Level must not exceed 50 characters")
        String level,

        Long parentId,

        String description,

        LocalDate openedDate,

        @Valid
        List<FacilityIdentifierDto> identifiers,

        @Valid
        List<FacilityContactDto> contacts,

        List<String> capabilities
) {}
