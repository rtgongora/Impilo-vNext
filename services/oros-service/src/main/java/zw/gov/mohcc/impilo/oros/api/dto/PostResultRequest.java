package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.ResultKind;

/**
 * Request DTO for posting a result against an order.
 */
public record PostResultRequest(
        @NotNull ResultKind kind,
        Object summary,
        String ziboResultCodes,
        Object docIds,
        boolean isCritical
) {}
