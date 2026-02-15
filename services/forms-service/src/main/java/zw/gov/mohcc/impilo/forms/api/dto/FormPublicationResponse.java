package zw.gov.mohcc.impilo.forms.api.dto;

import java.time.OffsetDateTime;

public record FormPublicationResponse(
        String id,
        String formId,
        String versionId,
        int versionNumber,
        String publishedBy,
        OffsetDateTime publishedAt
) {
}
