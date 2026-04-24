package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateVisibilityEscalationRequest(
        @NotBlank @Size(max = 64) String workflowType,
        @NotBlank @Size(max = 4000) String justification,
        @NotBlank @Size(max = 64) String requestedVisibilityCeiling
) {}
