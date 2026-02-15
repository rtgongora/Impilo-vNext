package zw.gov.mohcc.impilo.rules.api.dto;

import java.time.OffsetDateTime;

public record RuleResponse(
        String id,
        String key,
        String name,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
