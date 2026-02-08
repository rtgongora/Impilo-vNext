package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SubstitutionApproveRequest(
        @NotBlank String lineId
) {}
