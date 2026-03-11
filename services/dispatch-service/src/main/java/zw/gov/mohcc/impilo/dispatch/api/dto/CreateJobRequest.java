package zw.gov.mohcc.impilo.dispatch.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateJobRequest(
        @NotBlank String facilityRef,
        String requestRef
) {}
