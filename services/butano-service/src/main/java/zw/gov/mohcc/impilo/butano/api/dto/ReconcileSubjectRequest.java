package zw.gov.mohcc.impilo.butano.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /v1/internal/reconcile-subject.
 * Maps an old provisional CPID (O-CPID) to a verified CPID.
 */
public record ReconcileSubjectRequest(
        @NotBlank(message = "oldCpid is required")
        String oldCpid,

        @NotBlank(message = "newCpid is required")
        String newCpid,

        String reason
) {}
