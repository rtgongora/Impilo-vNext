package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMessageRequest(
        @NotNull UUID sessionId,
        String contentType,
        @NotBlank String payloadJson
) {}
