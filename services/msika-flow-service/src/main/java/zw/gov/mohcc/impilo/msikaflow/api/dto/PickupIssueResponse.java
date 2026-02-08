package zw.gov.mohcc.impilo.msikaflow.api.dto;

import java.time.OffsetDateTime;

public record PickupIssueResponse(
        String tokenId,
        String rawToken,
        String otp,
        OffsetDateTime expiresAt,
        String qrPayload
) {}
