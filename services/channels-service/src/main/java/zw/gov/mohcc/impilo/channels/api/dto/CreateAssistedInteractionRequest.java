package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAssistedInteractionRequest(
        @NotNull UUID sessionId,
        @NotBlank String agentId,
        String clientId,
        String notesJson
) {}
