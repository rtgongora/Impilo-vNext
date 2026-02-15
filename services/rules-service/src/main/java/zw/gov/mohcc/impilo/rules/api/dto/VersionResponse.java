package zw.gov.mohcc.impilo.rules.api.dto;

import java.time.OffsetDateTime;

public record VersionResponse(
        String id,
        String ruleId,
        int version,
        String dslText,
        OffsetDateTime createdAt
) {
}
