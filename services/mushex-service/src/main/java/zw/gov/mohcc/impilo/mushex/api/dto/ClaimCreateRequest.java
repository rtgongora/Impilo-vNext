package zw.gov.mohcc.impilo.mushex.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ClaimCreateRequest(
        @NotBlank String billId,
        @NotBlank String insurerId,
        String facilityId,
        String totals,
        String patientCpid,
        String planCode,
        String serviceCode,
        Boolean preauthRequired
) {}
