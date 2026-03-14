package zw.gov.mohcc.impilo.forms.api.dto;

import java.time.OffsetDateTime;

public record FormVersionResponse(
        String id,
        String formSchemaId,
        int version,
        String schemaJson,
        String changelog,
        OffsetDateTime createdAt
) {
}
