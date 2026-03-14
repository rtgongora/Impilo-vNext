package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ArticleResponse(UUID articleId, UUID tenantId, String title, String body,
                               String category, String status, String authorRef, String tags,
                               int version, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                               OffsetDateTime publishedAt) {}
