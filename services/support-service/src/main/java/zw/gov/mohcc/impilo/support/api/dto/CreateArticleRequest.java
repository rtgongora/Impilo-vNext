package zw.gov.mohcc.impilo.support.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateArticleRequest(
        @NotBlank String title,
        @NotBlank String body,
        @NotBlank String authorRef,
        String category,
        String tags) {}
