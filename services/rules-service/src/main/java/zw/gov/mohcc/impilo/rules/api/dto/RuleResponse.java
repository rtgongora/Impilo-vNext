package zw.gov.mohcc.impilo.rules.api.dto;

import java.time.OffsetDateTime;

public record RuleResponse(
        String id,
        String name,
        String expression,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
