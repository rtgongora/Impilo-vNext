package zw.gov.mohcc.impilo.rules.api.dto;

import java.time.OffsetDateTime;

public record ActivationResponse(
        String ruleKey,
        String versionId,
        int version,
        OffsetDateTime activeFrom
) {
}
