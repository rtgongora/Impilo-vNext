package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

public record SendMessageRequest(
        @NotBlank String senderRef,
        @NotBlank String senderType,
        @NotBlank String body) {}
