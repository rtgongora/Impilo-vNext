package zw.gov.mohcc.impilo.support.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(UUID messageId, UUID ticketId, String senderRef, String senderType,
                               String body, OffsetDateTime createdAt) {}
