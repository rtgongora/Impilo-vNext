package zw.gov.mohcc.impilo.experience.controller.dto;

public record ApiEnvelope<T>(T data, ApiMeta meta) {
}
