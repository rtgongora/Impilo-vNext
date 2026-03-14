package zw.gov.mohcc.impilo.forms.api.dto;

import java.time.OffsetDateTime;

public record FormSchemaResponse(
        String id,
        String formKey,
        String name,
        String description,
        int currentVersion,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
