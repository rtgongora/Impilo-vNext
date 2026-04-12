package zw.gov.mohcc.impilo.coverage.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record InternalEligibilityCheckRequest(
        @NotBlank @JsonProperty("patient_cpid") String patientCpid,
        @NotBlank @JsonProperty("plan_code") String planCode,
        @JsonProperty("service_code") String serviceCode
) {}
