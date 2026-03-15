package zw.gov.mohcc.impilo.support.api.dto;

public record UpdateArticleRequest(
        String title,
        String body,
        String category,
        String tags,
        String status) {}
