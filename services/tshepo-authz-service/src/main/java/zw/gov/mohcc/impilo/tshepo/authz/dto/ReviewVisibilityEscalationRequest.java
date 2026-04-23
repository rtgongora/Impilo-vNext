package zw.gov.mohcc.impilo.tshepo.authz.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewVisibilityEscalationRequest(
        @NotNull Boolean approved,
        @Size(max = 4000) String reviewNotes
) {}
