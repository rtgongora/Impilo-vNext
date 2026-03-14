package zw.gov.mohcc.impilo.offlineedge.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record IssueEntitlementRequest(
        @NotBlank String actorId,
        @NotBlank String facilityRef,
        String workflowType,
        Map<String, Object> scope) {}
