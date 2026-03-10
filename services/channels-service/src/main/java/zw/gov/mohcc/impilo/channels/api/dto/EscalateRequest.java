package zw.gov.mohcc.impilo.channels.api.dto;

import jakarta.validation.constraints.NotBlank;

public record EscalateRequest(
        @NotBlank String agentId,
        String reason
) {}
