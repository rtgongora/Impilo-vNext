package zw.gov.mohcc.impilo.forms.api.dto;

import java.time.OffsetDateTime;

public record FormVersionResponse(
        String id,
        String formId,
        int versionNumber,
        String contentJson,
        String createdBy,
        OffsetDateTime createdAt
) {
}
