package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTicketRequest(
        @NotBlank String title,
        @NotBlank String description,
        @NotBlank String reporterRef,
        String category,
        String priority,
        String facilityRef) {}
