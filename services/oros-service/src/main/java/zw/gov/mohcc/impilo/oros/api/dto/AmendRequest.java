package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO to amend or add an addendum to a finalized report. A reason is mandatory; the
 * narrative fields are optional and inherit from the superseded report when omitted.
 */
public record AmendRequest(
        @NotBlank String reason,
        Object summary,
        String impression,
        String recommendations
) {}
