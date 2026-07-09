package zw.gov.mohcc.impilo.varapi.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reviewer decision on a provider-access request (IATG Trust Console).
 * {@code decision} is one of APPROVED | REJECTED | NEEDS_MORE_INFORMATION.
 */
public record DecideProviderAccessRequest(
        @NotBlank String decision,
        @Size(max = 500) String note
) {}
