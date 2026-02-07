package zw.gov.mohcc.impilo.varapi.api.dto;

import java.time.Instant;

public record TokenIssueResponse(
        String providerPublicId,
        String vaToken,
        Instant issuedAt,
        String message
) {}
