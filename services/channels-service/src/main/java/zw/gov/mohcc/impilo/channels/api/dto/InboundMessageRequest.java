package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record InboundMessageRequest(
        @NotNull UUID sessionId,
        @NotBlank String channelType,
        String contentType,
        @NotBlank String payloadJson,
        String senderId
) {}
