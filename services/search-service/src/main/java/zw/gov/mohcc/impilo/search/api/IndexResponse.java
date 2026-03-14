package zw.gov.mohcc.impilo.search.api;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.List;

public record IndexResponse(
        String id,
        String entityType,
        String entityId,
        Map<String, Object> contentJson,
        List<String> tags,
        String searchableText,
        OffsetDateTime indexedAt
) {}
