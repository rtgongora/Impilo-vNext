package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CommentResponse(UUID commentId, UUID ticketId, String authorRef, String body,
                               OffsetDateTime createdAt) {}
