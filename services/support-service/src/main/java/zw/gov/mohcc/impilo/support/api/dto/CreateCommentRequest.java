package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(
        @NotBlank String body,
        @NotBlank String authorRef) {}
