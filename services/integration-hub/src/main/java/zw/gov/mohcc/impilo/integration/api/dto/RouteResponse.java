package zw.gov.mohcc.impilo.integration.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record RouteResponse(
        String id,
        String sourceService,
        String eventTypePrefix,
        String targetService,
        String targetUrl,
        boolean enabled,
        String matchMethod,
        String matchPathRegex,
        Map<String, String> transformHeaders,
        Map<String, String> transformFieldRenames,
        Integer targetTimeoutMs,
        String connectorType,
        String mappingTemplateId,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
